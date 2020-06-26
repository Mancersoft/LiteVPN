package com.mancersoft.litevpn

class ConnectionParams {
    var mtu: Short = 0
    lateinit var address: String
    var addressPrefixLength: Byte = 0
    lateinit  var route: String
    var routePrefixLength: Byte = 0
    var dnsServer: String? = null
    var searchDomain: String? = null
}