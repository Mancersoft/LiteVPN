package com.mancersoft.litevpn.transport

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mancersoft.litevpn.MAX_PACKET_SIZE
import com.mancersoft.litevpn.TAG
import org.java_websocket.WebSocketImpl
import org.java_websocket.client.DnsResolver
import org.java_websocket.client.WebSocketClient
import org.java_websocket.exceptions.WebsocketNotConnectedException
import org.java_websocket.handshake.ServerHandshake
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.net.SocketFactory
import javax.net.ssl.SSLContext

private const val SERVER_URI = "wss://cloud.achex.ca"
private const val WEBSOCKET_CONNECT_TIMEOUT_MILLIS = 60000

class WebSocketTransport(private val mServerId: String) : IVpnTransport {

    private val uUID: String
        get() = UUID.randomUUID().toString().replace("-", "")

    private lateinit var serverUri: URI
    private var mWebSocketClient: WSClient? = null
    private var mSocketFactory: CustomSocketFactory? = null
    private var mMessageListener: ((Packet) -> Unit)? = null
    private var mClosedListener: ((isByUser: Boolean) -> Unit)? = null

    init {
        try {
            serverUri = URI(SERVER_URI)
        } catch (e: Exception) {
            Log.e(TAG, "WebSocketTransport URI error", e)
        }
    }

    @Throws(IOException::class)
    override fun createSocket(): Closeable {
        disconnect()
        mSocketFactory = CustomSocketFactory(serverUri)
        mWebSocketClient = WSClient(serverUri)
        mWebSocketClient!!.setSocketFactory(mSocketFactory)
        return mSocketFactory!!.createNewSocket()
    }

    override val isReliable: Boolean
        get() = true

    override fun connect(): CompletableFuture<Boolean> {
        disconnect()
        return mWebSocketClient!!.connectAsync()
    }

    private fun disconnect(isByUser: Boolean) {
        disconnect()
        mClosedListener?.invoke(isByUser)
    }

    override fun disconnect() {
        if (mWebSocketClient != null && mWebSocketClient!!.isOpen) {
            mWebSocketClient!!.disconnect()
        }
    }

    override fun sendAsync(packet: Packet) {
        try {
            val encoded = Base64.getEncoder().encode(ByteBuffer.wrap(packet.data, 0, packet.length))
            val dataString = String(encoded.array(), StandardCharsets.ISO_8859_1)
            val jObj = JsonObject()
            jObj.addProperty("to", mServerId)
            jObj.addProperty("data", dataString)
            mWebSocketClient!!.sendAsync(jObj.toString())
        } catch (e1: WebsocketNotConnectedException) {
            disconnect(true)
        } catch (e2: Exception) {
            Log.e(TAG, "WebSocketTransport sendAsync error", e2)
            disconnect(false)
        }
    }

    override fun setOnMessageListener(messageListener: ((Packet) -> Unit)?) {
        mMessageListener = messageListener
    }

    override fun setOnClosedListener(closedListener: ((isByUser: Boolean) -> Unit)?) {
        mClosedListener = closedListener
    }

    private class CustomSocketFactory internal constructor(private val mServerUri: URI?) : SocketFactory() {

        private var mSocket: Socket? = null

        fun createNewSocket(): Socket {
            mSocket = SocketChannel.open().socket()
            return mSocket!!
        }

        fun connectSocketAsync(): CompletableFuture<Boolean> {
            return CompletableFuture.supplyAsync {
                try {
                    val dnsResolver = DnsResolver { uri: URI -> InetAddress.getByName(uri.host) }
                    val addr = InetSocketAddress(dnsResolver.resolve(mServerUri), port)
                    mSocket!!.connect(addr, WEBSOCKET_CONNECT_TIMEOUT_MILLIS)
                    if ("wss" == mServerUri!!.scheme) {
                        val sslContext = SSLContext.getInstance("TLSv1.2")
                        sslContext.init(null, null, null)
                        val factory = sslContext.socketFactory
                        mSocket = factory.createSocket(mSocket, mServerUri.host, port, true)
                    }
                    return@supplyAsync true
                } catch (e: Exception) {
                    Log.e(TAG, "CustomSocketFactory connectSocketAsync error", e)
                    return@supplyAsync false
                }
            }
        }

        private val port: Int
            get() {
                val port = mServerUri!!.port
                val scheme = mServerUri.scheme
                return if ("wss" == scheme) {
                    if (port == -1) WebSocketImpl.DEFAULT_WSS_PORT else port
                } else if ("ws" == scheme) {
                    if (port == -1) WebSocketImpl.DEFAULT_PORT else port
                } else {
                    throw IllegalArgumentException("unknown scheme: $scheme")
                }
            }

        override fun createSocket(): Socket {
            return mSocket!!
        }

        override fun createSocket(host: String, port: Int): Socket {
            return mSocket!!
        }

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
            return mSocket!!
        }

        override fun createSocket(host: InetAddress, port: Int): Socket {
            return mSocket!!
        }

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
            return mSocket!!
        }

    }

    private inner class WSClient internal constructor(serverUri: URI?) : WebSocketClient(serverUri) {

        private var mConnectResult: CompletableFuture<Boolean>? = null

        fun connectAsync(): CompletableFuture<Boolean> {
            mConnectResult = CompletableFuture()
            mSocketFactory!!.connectSocketAsync().thenAccept { isConnected: Boolean ->
                if (isConnected) {
                    super.connect()
                } else {
                    mConnectResult?.complete(false)
                }
            }
            return mConnectResult!!
        }

        fun disconnect() {
            super.close()
        }

        override fun onOpen(handshakeData: ServerHandshake) {
            Log.d(TAG, "WebSocketTransport onOpen")
            val jObj = JsonObject()
            jObj.addProperty("auth", uUID)
            jObj.addProperty("passwd", uUID)
            sendAsync(jObj.toString())
        }

        override fun onMessage(message: String) {
            try {
                val jObj = JsonParser.parseString(message).asJsonObject
                val auth = jObj["auth"]
                if (auth != null) {
                    mConnectResult?.complete(auth.asString.toLowerCase(Locale.ROOT) == "ok")
                    return
                }
                val error = jObj["error"]
                if (error != null) {
                    this@WebSocketTransport.disconnect(false)
                    throw Exception(error.asString)
                }
                val dataString = jObj["data"].asString
                val packet = Packet()
                val data = Base64.getDecoder().decode(dataString)
                if (data.size > MAX_PACKET_SIZE) {
                    throw Exception("Oversized packet received")
                }
                packet.data = data
                packet.length = data.size
                if (jObj["FROM"].asString != mServerId) {
                    throw Exception("Received packet is not from server")
                }
                mMessageListener?.invoke(packet)
            } catch (e: Exception) {
                Log.e(TAG, "WebSocketTransport onMessage error", e)
            }
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            Log.d(TAG, "WebSocketTransport onClose reason: $reason")
            this@WebSocketTransport.disconnect(!remote)
        }

        override fun onError(e: Exception) {
            Log.e(TAG, "WebSocketTransport WSClient error", e)
            mConnectResult?.complete(false)
        }

        fun sendAsync(message: String?) {
            super.send(message)
        }
    }
}