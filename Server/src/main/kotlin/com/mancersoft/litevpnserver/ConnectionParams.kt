package com.mancersoft.litevpnserver

import java.nio.charset.StandardCharsets

class ConnectionParams {
    var mtu: Short = 0
    lateinit var address: String
    var addressPrefixLength: Byte = 0
    lateinit var route: String
    var routePrefixLength: Byte = 0
    var dnsServer: String? = null
    var searchDomain: String? = null

    constructor()
    constructor(connectionParams: ConnectionParams) {
        mtu = connectionParams.mtu
        address = connectionParams.address
        addressPrefixLength = connectionParams.addressPrefixLength
        route = connectionParams.route
        routePrefixLength = connectionParams.routePrefixLength
        addressPrefixLength = connectionParams.routePrefixLength
        dnsServer = connectionParams.dnsServer
        searchDomain = connectionParams.searchDomain
    }

    val bytes: ByteArray
        get() = this.toString().toByteArray(StandardCharsets.US_ASCII)

    override fun toString(): String {
        return " m,$mtu" +
                " a,$address,$addressPrefixLength" +
                (if (dnsServer == null) "" else " d,$dnsServer") +
                " r,$route,$routePrefixLength" +
                (if (searchDomain == null) "" else " s,$searchDomain")
    }
}