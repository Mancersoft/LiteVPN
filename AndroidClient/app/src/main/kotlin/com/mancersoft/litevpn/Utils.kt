package com.mancersoft.litevpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Utils {

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH))
    }

    fun getConnectionParams(parameters: String): ConnectionParams {
        val params = ConnectionParams()
        for (parameter in parameters.split(" ").toTypedArray()) {
            val fields = parameter.split(",").toTypedArray()
            try {
                when (fields[0][0]) {
                    'm' -> params.mtu = fields[1].toShort()
                    'a' -> {
                        params.address = fields[1]
                        params.addressPrefixLength = fields[2].toByte()
                    }
                    'r' -> {
                        params.route = fields[1]
                        params.routePrefixLength = fields[2].toByte()
                    }
                    'd' -> params.dnsServer = fields[1]
                    's' -> params.searchDomain = fields[1]
                }
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Bad parameter: $parameter")
            }
        }
        return params
    }
}