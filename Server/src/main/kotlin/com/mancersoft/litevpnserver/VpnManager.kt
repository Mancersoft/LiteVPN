package com.mancersoft.litevpnserver

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalNotification
import com.mancersoft.litevpnserver.transport.IVpnTransport
import com.mancersoft.litevpnserver.transport.Packet
import trikita.log.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object VpnManager {
    const val TAG = "LiteVpnServer"
    const val MAX_PACKET_SIZE = Short.MAX_VALUE.toInt()
    const val PACKET_TYPE_BYTE_OFFSET = 0
    const val CONNECT_PACKET: Byte = 0
    private const val DISCONNECT_PACKET: Byte = 1
    private const val CONNECTION_TIMEOUT_SECONDS = 120

    private val mExecutorService = Executors.newCachedThreadPool()
    private val mIpToConnection: Cache<String, Any>
    private val mConectionToIp = ConcurrentHashMap<Any, String>()
    private val mIface = InterfaceManager
    private val mNatManager = NatManager
    private val mConnManager = ConnectionManager
    private val mReceiveConnections = AtomicBoolean(false)
    private var mInternetToUserProcessing: Future<*>? = null
    private lateinit var mTransport: IVpnTransport

    init {
        mIpToConnection = CacheBuilder.newBuilder()
                .expireAfterAccess(CONNECTION_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
                .removalListener { notification: RemovalNotification<Any, Any> ->
                    mConectionToIp.remove(notification.value)
                    val ipAddress = notification.key as String
                    mNatManager.clearDisconnectedPorts(ipAddress)
                    mConnManager.disconnect(ipAddress)
                }
                .build()
    }

    fun init(transport: IVpnTransport) {
        mTransport = transport
    }

    fun setReceiveConnections(isReceive: Boolean) {
        mReceiveConnections.set(isReceive)
    }

    fun startProcessConnections() {
        stopProcessConnections()
        mTransport.start().thenAccept { result: Boolean ->
            if (result) {
                mTransport.setOnMessageListener { packet: Packet -> userToInternetProcessing(packet) }
                mInternetToUserProcessing = mExecutorService.submit { internetToUserProcessing() }
            }
        }
    }

    private fun internetToUserProcessing() {
        try {
            val data = ByteArray(MAX_PACKET_SIZE)
            val packet = Packet()
            packet.data = data
            while (!Thread.currentThread().isInterrupted) {
                //System.out.println("Start read iface");
                val length = mIface.read(data)
                //System.out.println("Stop read iface");
                if (!mNatManager.convertFromInternet(data)) {
                    continue
                }
                val ipAddress = Utils.getDestinationIp(data)
                if (!mIpToConnection.asMap().containsKey(ipAddress)) {
                    continue
                }
                packet.length = length
                packet.destination = mIpToConnection.asMap()[ipAddress]

                //System.out.println("Start send transport");
                mTransport.sendAsync(packet)
                //System.out.println("Stop send transport");
            }
        } catch (e: Exception) {
            Log.e(TAG, "VpnManager internetToUserProcessing error", e)
            stopProcessConnections()
        }
    }

    private fun userToInternetProcessing(packet: Packet) {
        mExecutorService.execute {
            try {
                val data = packet.data
                if (data.size > MAX_PACKET_SIZE) {
                    return@execute
                }
                when (data[PACKET_TYPE_BYTE_OFFSET]) {
                    CONNECT_PACKET -> {
                        if (mReceiveConnections.get()) {
                            val packetSource = packet.source
                            synchronized(this) {
                                if (mConectionToIp.containsKey(packetSource)) {
                                    mConnManager.processConnection(mTransport, packet, mConectionToIp[packetSource])
                                    return@execute
                                }
                                val assignedIp = mConnManager.processConnection(mTransport, packet, null)
                                if (assignedIp != null) {
                                    mIpToConnection.put(assignedIp, packetSource!!)
                                    mConectionToIp[packetSource] = assignedIp
                                }
                            }
                        }
                        return@execute
                    }
                    DISCONNECT_PACKET -> {
                        val packetSource = packet.source
                        if (mConectionToIp.containsKey(packetSource)) {
                            mIpToConnection.asMap().remove(mConectionToIp[packetSource])
                        }
                        return@execute
                    }
                }
                val ipAddress = Utils.getSourceIp(data)
                if (!mIpToConnection.asMap().containsKey(ipAddress)) {
                    return@execute
                }
                if (!mNatManager.convertToInternet(data)) {
                    return@execute
                }

                //System.out.println("Start write iface");
                mIface.write(data, packet.length)
                //System.out.println("Stop write iface");
            } catch (e: Exception) {
                Log.e(TAG, "VpnManager userToInternetProcessing error", e)
                stopProcessConnections()
            }
        }
    }

    fun stopProcessConnections() {
        mTransport.setOnMessageListener(null)
        mTransport.stop()
        mInternetToUserProcessing?.cancel(true)
    }
}