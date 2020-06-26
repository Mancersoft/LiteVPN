package com.mancersoft.litevpnserver.transport

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mancersoft.litevpnserver.VpnManager
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import trikita.log.Log
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture

private const val SERVER_URI = "wss://cloud.achex.ca"

class WebSocketTransport(private val mServerId: String) : IVpnTransport {

    private lateinit var serverUri: URI
    private var mMessageListener: ((Packet) -> Unit)? = null
    private var mWebSocketClient: WSClient? = null
    override val isReliable: Boolean
        get() = true

    init {
        try {
            serverUri = URI(SERVER_URI)
        } catch (e: Exception) {
            Log.e(VpnManager.TAG, "WebSocketTransport URI error", e)
        }
    }

    override fun start(): CompletableFuture<Boolean> {
        stop()
        mWebSocketClient = WSClient(serverUri)
        return mWebSocketClient!!.connectAsync()
    }

    override fun stop() {
        mWebSocketClient?.disconnect()
    }

    private fun restart() {
        Log.d(VpnManager.TAG, "WebSocketTransport restarting...")
        start()
    }

    override fun sendAsync(packet: Packet) {
        val encoded = Base64.getEncoder().encode(ByteBuffer.wrap(packet.data, 0, packet.length))
        val dataString = String(encoded.array(), StandardCharsets.ISO_8859_1)
        val jObj = JsonObject()
        jObj.addProperty("to", packet.destination.toString())
        jObj.addProperty("data", dataString)
        mWebSocketClient!!.sendAsync(jObj.toString())
    }

    override fun setOnMessageListener(messageListener: ((Packet) -> Unit)?) {
        mMessageListener = messageListener
    }

    private inner class WSClient(serverUri: URI?) : WebSocketClient(serverUri) {

        private var mConnectResult: CompletableFuture<Boolean>? = null

        fun connectAsync(): CompletableFuture<Boolean> {
            mConnectResult = CompletableFuture()
            super.connect()
            return mConnectResult!!
        }

        fun disconnect() {
            super.close()
        }

        override fun onOpen(handshakeData: ServerHandshake) {
            Log.d(VpnManager.TAG, "WebSocketTransport onOpen")
            val jObj = JsonObject()
            jObj.addProperty("auth", mServerId)
            jObj.addProperty("passwd", UUID.randomUUID().toString())
            sendAsync(jObj.toString())
        }

        override fun onMessage(message: String) {
            try {
                val jObj = JsonParser.parseString(message).asJsonObject
                val auth = jObj["auth"]
                if (auth != null) {
                    mConnectResult?.complete(auth.asString.toLowerCase() == "ok")
                    return
                }

                val error = jObj["error"];
                if (error != null) {
                    Log.d("WebSocketTransport onMessage Achex error: $error")
                    return
                }

                val dataString = jObj["data"].asString
                val packet = Packet()
                val data = Base64.getDecoder().decode(dataString)
                if (data.size > VpnManager.MAX_PACKET_SIZE) {
                    throw Exception("Oversized packet received")
                }
                packet.data = data
                packet.length = data.size
                packet.source = jObj["FROM"].asString
                mMessageListener?.invoke(packet)
            } catch (e: Exception) {
                Log.e(VpnManager.TAG, "WebSocketTransport onMessage error;" +
                        "\nmessage:\n$message", e)
            }
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            Log.d(VpnManager.TAG, "WebSocketTransport onClose reason: $reason")
            restart()
        }

        override fun onError(e: Exception) {
            Log.e(VpnManager.TAG, "WebSocketTransport WSClient error", e)
            mConnectResult?.complete(false)
        }

        fun sendAsync(message: String?) {
            super.send(message)
        }
    }
}