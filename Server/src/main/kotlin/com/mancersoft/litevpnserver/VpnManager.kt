package com.mancersoft.litevpnserver

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalNotification
import com.mancersoft.litevpnserver.transport.ConnectData
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
    //private val mIpToConnection: Cache<String, Any>
    private val mUserIdToIp = ConcurrentHashMap<String, String>()
    private val mIface = InterfaceManager
    private val mNatManager = NatManager
    private val mConnManager = ConnectionManager
    private val mReceiveConnections = AtomicBoolean(false)
    private var mInternetToUserProcessing: Future<*>? = null
    private lateinit var mTransport: IVpnTransport

    private val mIpToUsers = ConcurrentHashMap<String, Cache<String, User>>()

    private fun createCache(): Cache<String, User> {
        return CacheBuilder.newBuilder()
                .expireAfterAccess(CONNECTION_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
                .removalListener { notification: RemovalNotification<String, User> ->
                    val user = notification.value
                    mUserIdToIp.remove(user.id)
                    mNatManager.clearDisconnectedPorts(user.ip)
                    Log.d("User disconnected, id=${user.id}; ip=${user.ip}; groupId=${user.groupId}")

                    if (mIpToUsers[user.ip]!!.asMap().isEmpty()) {
                        mIpToUsers.remove(user.ip)
                        mConnManager.disconnectLastUser(user)
                        PacketBalancer.collectionUpdate(user.ip, null)
                    } else {
                        PacketBalancer.collectionUpdate(user.ip, mIpToUsers[user.ip])
                    }
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
                if (!mIpToUsers.containsKey(ipAddress)) {
                    continue
                }
                packet.length = length
                packet.destination = PacketBalancer.getDestTransport(ipAddress, mIpToUsers[ipAddress]!!)
                if (packet.destination == null) {
                    continue
                }

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
                            synchronized(this) {
                                var isNewUser = true
                                val connectData = ConnectData.parsePacket(packet) ?: return@execute
                                if (mUserIdToIp.containsKey(packet.sourceId)) {
                                    val user = mIpToUsers[mUserIdToIp[packet.sourceId]]!!
                                            .asMap()[packet.sourceId]!!
                                    if (connectData.groupId != user.groupId) {
                                        disconnectUser(user.id!!)
                                    } else {
                                        isNewUser = false
                                    }
                                }
                                val user = mConnManager.processConnection(mTransport, connectData) ?: return@execute
                                if (!mIpToUsers.containsKey(user.ip)) {
                                    val newCache = createCache()
                                    newCache.asMap()[user.id] = user
                                    mIpToUsers[user.ip] = newCache
                                } else if (isNewUser) {
                                    mIpToUsers[user.ip]!!.asMap()[user.id] = user
                                    PacketBalancer.collectionUpdate(user.ip, mIpToUsers[user.ip]!!)
                                }

                                if (isNewUser) {
                                    mUserIdToIp[user.id!!] = user.ip
                                    Log.d("User connected, id=${user.id}; ip=${user.ip}; groupId=${user.groupId}")
                                }
                            }
                        }
                        return@execute
                    }
                    DISCONNECT_PACKET -> {
                        disconnectUser(packet.sourceId!!)
                        return@execute
                    }
                }
                val ipAddress = Utils.getSourceIp(data)
                if (!mIpToUsers.containsKey(ipAddress)) {
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

    private fun disconnectUser(userId: String) {
        if (mUserIdToIp.containsKey(userId)) {
            val ipAddress = mUserIdToIp[userId]
            mIpToUsers[ipAddress]?.asMap()?.remove(userId)
        }
    }

    fun stopProcessConnections() {
        mTransport.setOnMessageListener(null)
        mTransport.stop()
        mInternetToUserProcessing?.cancel(true)
    }
}