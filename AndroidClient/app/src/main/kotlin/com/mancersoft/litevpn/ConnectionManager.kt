package com.mancersoft.litevpn

import android.net.VpnService
import android.util.Log
import com.mancersoft.litevpn.transport.*
import java.net.DatagramSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture

private const val CONNECT_PACKET: Byte = 0
private const val DISCONNECT_PACKET: Byte = 1
private const val SHARED_SECRET_OFFSET = 1
private const val NOT_RELIABLE_SEND_PARAMS_COUNT = 3
private const val NOT_RELIABLE_HANDSHAKE_ATTEMPTS_COUNT = 50

class ConnectionManager(private val mSharedSecret: String, private val mService: VpnService) {

    private var mHandshakeAttempt = 0
    private val mGroupId = UUID.randomUUID().toString()

    private fun sendQuery(transport: IVpnTransport, packet: Packet) {
        val sendCount = if (transport.isReliable) 1 else NOT_RELIABLE_SEND_PARAMS_COUNT
        for (i in 0 until sendCount) {
            transport.sendAsync(packet)
        }
    }

    fun createTransport(transportType: TransportType, vararg transportParams: String): IVpnTransport {
        return when (transportType) {
            TransportType.WEBSOCKET -> WebSocketTransport(transportParams[0])
            else -> UdpTransport(transportParams[0], transportParams[1].toInt())
        }
    }

    fun sendConnectQuery(transport: IVpnTransport): CompletableFuture<ConnectionParams?> {
        val result = CompletableFuture<ConnectionParams?>()
        val packet = Packet()
        val connectDataBytes = ("$mSharedSecret;$mGroupId").toByteArray(StandardCharsets.UTF_8)
        if (connectDataBytes.size + 1 > MAX_PACKET_SIZE) {
            completeConnection(transport, result, null)
            return result
        }
        val connectionData = ByteArray(connectDataBytes.size + 1)
        connectionData[PACKET_TYPE_BYTE_OFFSET] = CONNECT_PACKET
        System.arraycopy(connectDataBytes, 0, connectionData, SHARED_SECRET_OFFSET, connectDataBytes.size)
        packet.data = connectionData
        packet.length = connectionData.size
        mHandshakeAttempt = 0
        transport.setOnMessageListener { receivedPacket: Packet ->
            mHandshakeAttempt++
            val data = receivedPacket.data
            if (data[0] == 0.toByte()) {
                val params = Utils.getConnectionParams(
                        String(data, 1, receivedPacket.length - 1, StandardCharsets.US_ASCII).trim { it <= ' ' })
                completeConnection(transport, result, params)
            } else if (transport.isReliable) {
                completeConnection(transport, result, null)
            }
            if (mHandshakeAttempt >= NOT_RELIABLE_HANDSHAKE_ATTEMPTS_COUNT) {
                completeConnection(transport, result, null)
            }
        }
        sendQuery(transport, packet)
        return result
    }

    fun sendDisconnectQuery(transport: IVpnTransport) {
        val packet = Packet()
        val data = ByteArray(PACKET_TYPE_BYTE_OFFSET + 1)
        data[PACKET_TYPE_BYTE_OFFSET] = DISCONNECT_PACKET
        packet.data = data
        packet.length = data.size
        sendQuery(transport, packet)
    }

    private fun completeConnection(transport: IVpnTransport,
                                   result: CompletableFuture<ConnectionParams?>, value: ConnectionParams?) {
        mHandshakeAttempt = 0
        transport.setOnMessageListener(null)
        result.complete(value)
    }

    fun protectTransport(transport: IVpnTransport): Boolean {
        return try {
            val socket = transport.createSocket()
            val isProtected: Boolean
            isProtected = if (socket is Socket) {
                mService.protect(socket)
            } else {
                mService.protect(socket as DatagramSocket)
            }
            check(isProtected) { "Cannot protect the tunnel" }
            true
        } catch (e: Exception) {
            Log.e(TAG, "ConnectionManager protectTransport error", e)
            false
        }
    }
}