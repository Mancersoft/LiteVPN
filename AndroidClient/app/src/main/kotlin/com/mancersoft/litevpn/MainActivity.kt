package com.mancersoft.litevpn

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import com.mancersoft.litevpn.transport.TransportType
import java.util.*
import java.util.stream.Collectors

class MainActivity : Activity() {
    interface Prefs {
        companion object {
            const val NAME = "connection"
            const val SERVER_ID = "server.id"
            const val SERVER_PARAMS = "server.params"
            const val VPN_TYPE = "vpn.type"
            const val SHARED_SECRET = "shared.secret"
            const val ALLOW = "allow"
            const val PACKAGES = "packages"
        }
    }

    private val serviceIntent: Intent
        get() = Intent(this, LiteVpnService::class.java)
    private var mWaitForResult = false

    @SuppressLint("ApplySharedPref")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val serverId = findViewById<TextView>(R.id.serverId)
        val params = findViewById<TextView>(R.id.params)
        val sharedSecret = findViewById<TextView>(R.id.secret)
        val allowed = findViewById<RadioButton>(R.id.allowed)
        val disallowed = findViewById<RadioButton>(R.id.disallowed)
        val packages = findViewById<TextView>(R.id.packages)
        val vpnTypeUdp = findViewById<RadioButton>(R.id.udp)
        val vpnTypeWebsocket = findViewById<RadioButton>(R.id.websocket)
        val prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        serverId.text = prefs.getString(Prefs.SERVER_ID, "")
        params.text = prefs.getString(Prefs.SERVER_PARAMS, "")
        val vpnType = prefs.getString(Prefs.VPN_TYPE, TransportType.UDP.toString())
        vpnTypeUdp.isChecked = vpnType == TransportType.UDP.toString()
        vpnTypeWebsocket.isChecked = vpnType == TransportType.WEBSOCKET.toString()
        sharedSecret.text = prefs.getString(Prefs.SHARED_SECRET, "")
        allowed.isChecked = prefs.getBoolean(Prefs.ALLOW, true)
        disallowed.isChecked = !allowed.isChecked
        packages.text = java.lang.String.join(", ", prefs.getStringSet(
                Prefs.PACKAGES, emptySet())!!)
        findViewById<View>(R.id.connect).setOnClickListener {
            val packageSet = Arrays.stream(packages.text.toString().split(",").toTypedArray())
                    .map { obj: String -> obj.trim { it <= ' ' } }
                    .filter { s: String -> s.isNotEmpty() }
                    .collect(Collectors.toSet())
            if (!checkPackages(packageSet)) {
                return@setOnClickListener
            }
            val newVpnType: String = if (vpnTypeUdp.isChecked) {
                TransportType.UDP.toString()
            } else {
                TransportType.WEBSOCKET.toString()
            }
            prefs.edit()
                    .putString(Prefs.SERVER_ID, serverId.text.toString())
                    .putString(Prefs.SERVER_PARAMS, params.text.toString())
                    .putString(Prefs.VPN_TYPE, newVpnType)
                    .putString(Prefs.SHARED_SECRET, sharedSecret.text.toString())
                    .putBoolean(Prefs.ALLOW, allowed.isChecked)
                    .putStringSet(Prefs.PACKAGES, packageSet)
                    .commit()
            val intent = VpnService.prepare(this@MainActivity)
            mWaitForResult = true
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                onActivityResult(0, RESULT_OK, Intent())
            }
        }
        findViewById<View>(R.id.disconnect).setOnClickListener { startService(serviceIntent.setAction(ACTION_DISCONNECT)) }
    }

    private fun checkPackages(packageNames: Set<String>): Boolean {
        val hasCorrectPackageNames = packageNames.isEmpty() ||
                packageManager.getInstalledPackages(0).stream()
                        .map { pi: PackageInfo -> pi.packageName }
                        .collect(Collectors.toSet())
                        .containsAll(packageNames)
        if (!hasCorrectPackageNames) {
            Toast.makeText(this, R.string.unknown_package_names, Toast.LENGTH_SHORT).show()
        }
        return hasCorrectPackageNames
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent) {
        if (mWaitForResult && result == RESULT_OK) {
            startService(serviceIntent.setAction(ACTION_CONNECT))
        }
        mWaitForResult = false
    }
}