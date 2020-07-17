package com.mancersoft.litevpnserver

import com.mancersoft.litevpnserver.transport.*
import trikita.log.Log
import java.util.*

object ConnectionManager {
    private const val SHARED_SECRET_OFFSET = 1
    private const val NOT_RELIABLE_SEND_PARAMS_COUNT = 3

    private lateinit var mParams: ConnectionParams
    private var clientIpAddress = 0
    private val occupiedIps = TreeSet<Int>()
    private lateinit var mSharedSecret: String

    fun init(mtu: Short, addressPrefixLength: Byte, route: String, routePrefixLength: Byte,
             dnsServer: String?, searchDomain: String?, firstClientIp: String,
             sharedSecret: String) {
        mSharedSecret = sharedSecret
        clientIpAddress = Utils.ipToInt(firstClientIp)
        mParams = ConnectionParams()
        mParams.mtu = mtu
        mParams.addressPrefixLength = addressPrefixLength
        mParams.route = route
        mParams.routePrefixLength = routePrefixLength
        mParams.dnsServer = dnsServer
        mParams.searchDomain = searchDomain
    }

    fun createTransport(transportType: TransportType, vararg transportParams: String): IVpnTransport {
        return when (transportType) {
            TransportType.WEBSOCKET -> WebSocketTransport(transportParams[0])
            else -> UdpTransport(transportParams[0].toInt())
        }
    }

    private fun checkSecret(packet: Packet): Boolean {
        return try {
            val receivedSecret = String(packet.data, SHARED_SECRET_OFFSET,
                    packet.length - SHARED_SECRET_OFFSET)
            mSharedSecret == receivedSecret
        } catch (e: Exception) {
            Log.e(VpnManager.TAG, "ConnectionManager checkSecret error", e)
            false
        }
    }

    private fun sendParams(transport: IVpnTransport, vpnIpAddress: String, userDest: Any): Boolean {
        return try {
            mParams.address = vpnIpAddress
            val parameters = mParams.bytes
            if (parameters.size > VpnManager.MAX_PACKET_SIZE) {
                throw Exception("Params is too long")
            }
            parameters[VpnManager.PACKET_TYPE_BYTE_OFFSET] = VpnManager.CONNECT_PACKET
            val paramPacket = Packet()
            paramPacket.data = parameters
            paramPacket.length = parameters.size
            paramPacket.destination = userDest
            val sendCount = if (transport.isReliable) 1 else NOT_RELIABLE_SEND_PARAMS_COUNT
            for (i in 0 until sendCount) {
                transport.sendAsync(paramPacket)
            }
            true
        } catch (e: Exception) {
            Log.e(VpnManager.TAG, "ConnectionManager sendParams error", e)
            false
        }
    }

    fun processConnection(transport: IVpnTransport, packet: Packet, ipAddress: String?): String? {
        var newIp = ipAddress
        var integerIp: Int? = null
        if (ipAddress == null) {
            integerIp = Utils.firstMissing(occupiedIps)
            if (integerIp == null) {
                integerIp = clientIpAddress
            }
            newIp = Utils.intIpToString(integerIp)
        }

        //newIp = "10.255.255.255";
        if (checkSecret(packet) && sendParams(transport, newIp!!, packet.source!!)) {
            if (ipAddress == null) {
                occupiedIps.add(integerIp!!)
                clientIpAddress++
                Log.d("User connected, ip: $newIp!")
            }

            return newIp
        }
        return null
    }

    fun disconnect(ipAddress: String) {
        if (occupiedIps.remove(Utils.ipToInt(ipAddress))) {
            Log.d("User disconnected, ip: $ipAddress!")
        }
    }
}