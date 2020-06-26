package com.mancersoft.litevpn

import android.app.PendingIntent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object InterfaceManager {
    private var mInStream: FileInputStream? = null
    private var mOutStream: FileOutputStream? = null
    private var mIface: ParcelFileDescriptor? = null

    fun init(params: ConnectionParams, service: VpnService, serverName: String,
             configureIntent: PendingIntent, allowPackages: Boolean, packages: Set<String>) {
        val builder = service.Builder()
        builder.setMtu(params.mtu.toInt())
        builder.addAddress(params.address, params.addressPrefixLength.toInt())
        builder.addRoute(params.route, params.routePrefixLength.toInt())
        if (params.dnsServer != null) {
            builder.addDnsServer(params.dnsServer!!)
        }
        if (params.searchDomain != null) {
            builder.addSearchDomain(params.searchDomain!!)
        }
        for (packageName in packages) {
            try {
                if (allowPackages) {
                    builder.addAllowedApplication(packageName)
                } else {
                    builder.addDisallowedApplication(packageName)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Package not available: $packageName", e)
            }
        }
        builder.setSession(serverName).setConfigureIntent(configureIntent)
        builder.setBlocking(true)
        builder.setConfigureIntent(configureIntent)
        mIface = builder.establish()
        mInStream = FileInputStream(mIface!!.fileDescriptor)
        mOutStream = FileOutputStream(mIface!!.fileDescriptor)
    }

    @Throws(IOException::class)
    fun read(data: ByteArray): Int {
        return mInStream!!.read(data)
    }

    @Throws(IOException::class)
    fun write(data: ByteArray, length: Int) {
        mOutStream!!.write(data, 0, length)
    }

    @Throws(IOException::class)
    fun close() {
        mInStream?.close()
        mOutStream?.close()
        mIface?.close()
    }
}