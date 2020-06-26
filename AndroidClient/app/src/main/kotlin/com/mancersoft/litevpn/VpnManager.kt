package com.mancersoft.litevpn

import android.app.PendingIntent
import android.net.VpnService
import android.util.Log
import com.mancersoft.litevpn.transport.IVpnTransport
import com.mancersoft.litevpn.transport.Packet
import com.mancersoft.litevpn.transport.TransportType
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

const val TAG = "LiteVpnClient"
const val MAX_PACKET_SIZE = Short.MAX_VALUE.toInt()
const val PACKET_TYPE_BYTE_OFFSET = 0
private const val PARAMS_PACKET: Byte = 0

class VpnManager internal constructor(private val mService: VpnService,
                                      private val mServerName: String,
                                      transportType: TransportType, sharedSecret: String,
                                      private val mAllowPackages: Boolean,
                                      private val mPackages: Set<String>,
                                      private val mConfigureIntent: PendingIntent,
                                      vararg transportParams: String) {

    private var mOnConnectionChangedListener: ((isConnected: Boolean) -> Unit)? = null
    private val mConnManager: ConnectionManager = ConnectionManager(sharedSecret, mService)
    private val mTransport: IVpnTransport
    private val mIface: InterfaceManager = InterfaceManager
    private val mExecutorService = Executors.newCachedThreadPool()
    private var mUserToInternetProcessing: Future<*>? = null
    private val mIsConnected = AtomicBoolean(false)

    init {
        val fullTransportParams = Array(transportParams.size + 1) { "" }
        fullTransportParams[0] = mServerName
        System.arraycopy(transportParams, 0, fullTransportParams, 1, transportParams.size)
        mTransport = mConnManager.createTransport(transportType, *fullTransportParams)
    }

    fun setOnConnectionChangedListener(listener: ((isConnected: Boolean) -> Unit)?) {
        mOnConnectionChangedListener = listener
    }

    fun connect() {
        disconnect(false)
        try {
            Log.i(TAG, "Start connection")
            if (!Utils.isNetworkAvailable(mService)) {
                throw Exception("Network is not available")
            }
            if (!mConnManager.protectTransport(mTransport)) {
                throw Exception("Cannot protect the tunnel")
            }
            mTransport.setOnClosedListener { isByUser: Boolean -> connectionClosedProcessing(isByUser) }
            mTransport.connect().thenAccept { isConnected: Boolean ->
                if (!isConnected) {
                    disconnect(true)
                    return@thenAccept
                }
                mConnManager.sendConnectQuery(mTransport).thenAccept { params: ConnectionParams? ->
                    mIsConnected.set(true)
                    mIface.init(params!!, mService, mServerName, mConfigureIntent,
                            mAllowPackages, mPackages)
                    mTransport.setOnMessageListener { packet: Packet -> internetToUserProcessing(packet) }
                    mUserToInternetProcessing = mExecutorService.submit { userToInternetProcessing() }
                    mOnConnectionChangedListener?.invoke(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VPNManager connect error", e)
            disconnect(true)
        }
    }

    private fun userToInternetProcessing() {
        try {
            val data = ByteArray(MAX_PACKET_SIZE)
            val packet = Packet()
            packet.data = data
            while (!Thread.currentThread().isInterrupted) {
                //System.out.println("Start read iface");
                val length = mIface.read(data)
                //System.out.println("Stop read iface");
                packet.length = length
                mTransport.sendAsync(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "VpnManager userToInternetProcessing error", e)
            disconnect(true)
        }
    }

    private fun internetToUserProcessing(packet: Packet) {
        mExecutorService.execute {
            try {
                val data = packet.data
                if (data.size > MAX_PACKET_SIZE) {
                    return@execute
                }
                if (data[PACKET_TYPE_BYTE_OFFSET] != PARAMS_PACKET) {
                    //Log.d(TAG, "Start write iface");
                    mIface.write(data, packet.length)
                    //Log.d(TAG, "Stop write iface");
                }
            } catch (e: Exception) {
                Log.e(TAG, "VpnManager internetToUserProcessing error", e)
                disconnect(true)
            }
        }
    }

    private fun connectionClosedProcessing(isByUser: Boolean) {
        disconnect(!isByUser)
    }

    fun disconnect(notify: Boolean) {
        mTransport.setOnMessageListener(null)
        mTransport.setOnClosedListener(null)
        mUserToInternetProcessing?.cancel(true)
        if (mIsConnected.get()) {
            mExecutorService.execute {
                try {
                    mConnManager.sendDisconnectQuery(mTransport)
                    Thread.sleep(1000)
                    mTransport.disconnect()
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Unable to disconnect transport layer", e)
                } catch (ignored: Exception) {
                    // normal exit
                }
            }
        }
        try {
            mIface.close()
        } catch (e: Exception) {
            Log.e(TAG, "VPNManager disconnect error", e)
        }
        mIsConnected.set(false)
        if (notify) {
            mOnConnectionChangedListener?.invoke(false)
        }
    }
}