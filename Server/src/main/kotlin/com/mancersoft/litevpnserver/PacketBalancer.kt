package com.mancersoft.litevpnserver

import com.google.common.cache.Cache
import trikita.log.Log
import java.util.concurrent.ConcurrentHashMap

object PacketBalancer {

    private val ipToIterators = ConcurrentHashMap<String, MutableIterator<MutableMap.MutableEntry<String, User>>>()

    fun getDestTransport(ip: String, connections: Cache<String, User>): Any? {
        try {
            val connectionsMap = connections.asMap()
            if (connectionsMap.isEmpty()) {
                return null
            }

            var iterator = ipToIterators[ip] ?: connectionsMap.iterator()

            if (!iterator.hasNext()) {
                iterator = connectionsMap.iterator()
            }

            return if (iterator.hasNext()) {
                ipToIterators[ip] = iterator
                iterator.next().value.connection
            } else {
                null
            }
        } catch (ignored: NoSuchElementException) {
            Log.d("Iterator error, connections count:${connections.size()}")
            return getDestTransport(ip, connections)
        }
    }

    fun collectionUpdate(ip: String, connections: Cache<String, User>?) {
        if (connections == null) {
            ipToIterators.remove(ip)
        } else {
            ipToIterators[ip] = connections.asMap().iterator()
        }
    }
}