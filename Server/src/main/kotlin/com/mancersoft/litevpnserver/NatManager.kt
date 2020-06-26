package com.mancersoft.litevpnserver

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NatManager {

    private const val FIRST_PORT: Short = 1024
    private const val MAXIMUM_PORT_COUNT = 48128
    private const val TABLE_TIMEOUT_SECONDS = 65

    private val mAvailablePorts: MutableSet<Short> = ConcurrentHashMap.newKeySet(MAXIMUM_PORT_COUNT)
    private val mIpPortToExternPort = ConcurrentHashMap<String, Short>()
    private val mExternPortToIpPort: Cache<Short, String>
    private val mIpToExternPorts = ConcurrentHashMap<String, MutableSet<Short>>()
    private lateinit var mVpnTunnelDestination: String

    init {
        for (i in FIRST_PORT until FIRST_PORT + MAXIMUM_PORT_COUNT) {
            mAvailablePorts.add(i.toShort())
        }
        mExternPortToIpPort = CacheBuilder.newBuilder()
                .expireAfterAccess(TABLE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
                .removalListener { notification: RemovalNotification<Any, Any?> ->
                    mIpPortToExternPort.remove(notification.value)
                    mAvailablePorts.add(notification.key as Short)
                }
                .build()
    }

    fun init(vpnTunnelDestination: String) {
        mVpnTunnelDestination = vpnTunnelDestination
    }

    fun convertToInternet(packet: ByteArray): Boolean {
        return try {
            val ipPort = Utils.getSourceIpPort(packet)
            if (!mIpPortToExternPort.containsKey(ipPort)) {
                val externalPort = mAvailablePorts.iterator().next()

                //externalPort = Utils.parseIpPort(ipPort).getSecond();
                mAvailablePorts.remove(externalPort)
                mIpPortToExternPort[ipPort] = externalPort
                mExternPortToIpPort.put(externalPort, ipPort)
                val ipAddress = Utils.getSourceIp(packet)
                if (!mIpToExternPorts.containsKey(ipAddress)) {
                    mIpToExternPorts[ipAddress] = ConcurrentHashMap.newKeySet()
                }
                mIpToExternPorts[ipAddress]!!.add(externalPort)
            }
            val currentExternalPort = mIpPortToExternPort[ipPort]!!
            Utils.changeIpPort(packet, mVpnTunnelDestination, currentExternalPort, true)
            true
        } catch (e: Exception) {
            //Log.e(TAG, "NatManager convertToInternet error", e);
            false
        }
    }

    fun convertFromInternet(packet: ByteArray): Boolean {
        return try {
            val destIp = Utils.getDestinationIp(packet)
            if (destIp != mVpnTunnelDestination) {
                return false
            }
            val port = Utils.getDestinationPort(packet)
            if (!mExternPortToIpPort.asMap().containsKey(port)) {
                return false
            }
            val currentIpPort = mExternPortToIpPort.asMap()[port]
            val ipPortPair = Utils.parseIpPort(currentIpPort!!)
            Utils.changeIpPort(packet, ipPortPair.first, ipPortPair.second, false)
            true
        } catch (e: Exception) {
            //Log.e(TAG, "NatManager convertFromInternet error", e);
            false
        }
    }

    fun clearDisconnectedPorts(ipAddress: String?) {
        val externalPortsList: Set<Short>? = mIpToExternPorts.remove(ipAddress)
        if (externalPortsList != null) {
            for (port in externalPortsList) {
                mExternPortToIpPort.asMap().remove(port)
            }
        }
    }
}