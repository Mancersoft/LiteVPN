package com.mancersoft.litevpnserver.transport

import com.mancersoft.litevpnserver.VpnManager
import trikita.log.Log
import java.lang.Exception

private const val SHARED_SECRET_OFFSET = 1

class ConnectData private constructor(var source: Any, var secret: String, var groupId: String) {

    companion object {
        fun parsePacket(packet: Packet): ConnectData? {
            return try {
                val connPacket = String(packet.data, SHARED_SECRET_OFFSET, packet.length - SHARED_SECRET_OFFSET)
                val connParams = connPacket.split(';')
                ConnectData(packet.source!!, connParams[0], connParams[1])
            } catch (e: Exception) {
                Log.e(VpnManager.TAG, "ConnectPacket parse error", e)
                null
            }
        }
    }
}