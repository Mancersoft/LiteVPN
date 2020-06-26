package com.mancersoft.litevpn

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.Toast
import com.mancersoft.litevpn.MainActivity.Prefs
import com.mancersoft.litevpn.transport.TransportType

const val ACTION_CONNECT = "com.mancersoft.litevpn.START"
const val ACTION_DISCONNECT = "com.mancersoft.litevpn.STOP"
private const val NOTIFICATION_CHANNEL_ID = "LiteVpn"

class LiteVpnService : VpnService(), Handler.Callback {

    private var mHandler: Handler? = null
    private var mConfigureIntent: PendingIntent? = null
    private var mVpnManager: VpnManager? = null

    override fun onCreate() {
        mHandler = mHandler ?: Handler(this)
        mConfigureIntent = PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return if (ACTION_DISCONNECT == intent.action) {
            disconnect()
            Service.START_NOT_STICKY
        } else {
            connect()
            Service.START_STICKY
        }
    }

    override fun onDestroy() {
        disconnect()
    }

    override fun handleMessage(message: Message): Boolean {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show()
        if (message.what != R.string.disconnected) {
            updateForegroundNotification(message.what)
        }
        return true
    }

    private fun connect() {
        mHandler!!.sendEmptyMessage(R.string.connecting)
        val prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val serverId = prefs.getString(Prefs.SERVER_ID, "")!!.trim { it <= ' ' }
        val serverParams = prefs.getString(Prefs.SERVER_PARAMS, "")!!.split(",").toTypedArray()
        for (i in serverParams.indices) {
            serverParams[i] = serverParams[i].trim { it <= ' ' }
        }
        val vpnType: TransportType = TransportType.valueOf(
                prefs.getString(Prefs.VPN_TYPE, TransportType.UDP.toString())!!)
        val secret = prefs.getString(Prefs.SHARED_SECRET, "")
        val allow = prefs.getBoolean(Prefs.ALLOW, true)
        val packages = prefs.getStringSet(Prefs.PACKAGES, emptySet())
        mVpnManager?.disconnect(false)
        try {
            mVpnManager = VpnManager(this, serverId, vpnType, secret!!,
                    allow, packages!!, mConfigureIntent!!, *serverParams)
            mVpnManager!!.setOnConnectionChangedListener { isConnected: Boolean ->
                if (isConnected) {
                    mHandler!!.sendEmptyMessage(R.string.connected)
                } else {
                    disconnect()
                }
            }
            mVpnManager!!.connect()
        } catch (e: Exception) {
            Log.e(TAG, "LiteVpnService connect error", e)
            disconnect()
        }
    }

    private fun disconnect() {
        mHandler!!.sendEmptyMessage(R.string.disconnected)
        mVpnManager?.disconnect(false)
        Handler(this.mainLooper).postDelayed({ stopForeground(true) }, 1000)
    }

    private fun updateForegroundNotification(message: Int) {
        val mNotificationManager = (getSystemService(
                Context.NOTIFICATION_SERVICE) as NotificationManager)
        mNotificationManager.createNotificationChannel(NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT))
        startForeground(1, Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build())
    }
}