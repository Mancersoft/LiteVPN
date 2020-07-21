package com.mancersoft.litevpnserver

import com.mancersoft.litevpnserver.transport.*
import trikita.log.Log
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ConnectionManager {
    private const val NOT_RELIABLE_SEND_PARAMS_COUNT = 3

    private lateinit var mParams: ConnectionParams
    private var firstClientIpInt = 0
    private val occupiedIps = TreeSet<Int>()
    private lateinit var mSharedSecret: String

    private val mGroupIdToIp = ConcurrentHashMap<String, String>()

    fun init(mtu: Short, addressPrefixLength: Byte, route: String, routePrefixLength: Byte,
             dnsServer: String?, searchDomain: String?, firstClientIp: String,
             sharedSecret: String) {
        mSharedSecret = sharedSecret
        firstClientIpInt = Utils.ipToInt(firstClientIp)
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

    fun processConnection(transport: IVpnTransport, connectData: ConnectData): User? {
        try {
            if (mSharedSecret != connectData.secret) {
                return null
            }

            lateinit var newIp: String
            var integerIp: Int? = null
            val hasIp = mGroupIdToIp.containsKey(connectData.groupId)
            if (hasIp) {
                newIp = mGroupIdToIp[connectData.groupId]!!
            } else {
                integerIp = if (occupiedIps.isEmpty()) {
                    firstClientIpInt
                } else {
                    Utils.firstMissingOrNext(occupiedIps)
                }
                newIp = Utils.intIpToString(integerIp)
            }

            //newIp = "10.255.255.255";
            if (sendParams(transport, newIp, connectData.source)) {
                if (!hasIp) {
                    occupiedIps.add(integerIp!!)
                    mGroupIdToIp[connectData.groupId] = newIp
                }

                return User(connectData.source, connectData.groupId, newIp)
            }
        } catch (e: Exception) {
            Log.e(VpnManager.TAG, "ConnectionManager processConnection parse error", e)
        }
        return null
    }

    fun disconnectLastUser(lastUser: User) {

        mGroupIdToIp.remove(lastUser.id)

        if (occupiedIps.remove(Utils.ipToInt(lastUser.ip))) {
            Log.d("All users disconnected with ip=${lastUser.ip}; groupId=${lastUser.groupId}")
        }
    }
}