package com.mancersoft.litevpnserver

import com.mancersoft.litevpnserver.transport.TransportType
import trikita.log.Log
import java.util.*

object Main {
    private const val INTERNET_INTERFACE = "eth0"
    private const val TUNNEL_INTERFACE = "tun0"
    private const val VPN_NETWORK = "10.0.0.0"
    private const val VPN_NETWORK_PREFIX: Byte = 8
    private const val VPN_TUN_SOURCE = "10.255.255.254" // "10.0.0.1";
    private const val VPN_TUN_DEST = "10.255.255.255" // "10.0.0.2";
    private const val MTU: Short = 1400
    private const val ROUTE = "0.0.0.0"
    private const val ROUTE_PREFIX: Byte = 0
    private const val DNS_SERVER = "8.8.8.8"
    private val SEARCH_DOMAIN: String? = null
    private const val FIRST_CLIENT_IP = "10.0.0.1" // "10.0.0.3";
    private const val UDP_PORT = 8000
    private const val WEB_SOCKET_SERVER_ID = "ServerLiteVPN"

    private fun loadJniLib(filename: String) {
        try {
            System.load(System.getProperty("user.dir") + "/" + filename)
        } catch (ignore: UnsatisfiedLinkError) {
        }
    }

    init {
        try {
            System.loadLibrary("tun")
        } catch (ignore: UnsatisfiedLinkError) {
            loadJniLib("build/libs/tun/shared/libtun.so")
            loadJniLib("tun/shared/libtun.so")
            loadJniLib("libtun.so")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val sudoPassword = "kali"
            val sharedSecret = "test"
            val iface = InterfaceManager
            iface.init(sudoPassword, INTERNET_INTERFACE, TUNNEL_INTERFACE,
                    VPN_NETWORK, VPN_NETWORK_PREFIX,
                    VPN_TUN_SOURCE, VPN_TUN_DEST)
            val connMan = ConnectionManager
            connMan.init(MTU, VPN_NETWORK_PREFIX, ROUTE, ROUTE_PREFIX, DNS_SERVER, SEARCH_DOMAIN,
                    FIRST_CLIENT_IP, sharedSecret)
            val natMan = NatManager
            natMan.init(VPN_TUN_DEST)
            val vpnMan = VpnManager
            //IVpnTransport transport = connMan.createTransport(TransportType.UDP, Integer.toString(UDP_PORT));
            val transport = connMan.createTransport(TransportType.WEBSOCKET, WEB_SOCKET_SERVER_ID)
            vpnMan.init(transport)
            vpnMan.setReceiveConnections(true)
            println("Server started!")
            vpnMan.startProcessConnections()
            val scanner = Scanner(System.`in`)
            var cmd = scanner.nextLine()
            while (cmd != "exit") {
                when (cmd) {
                    "startReceiveConnections" -> vpnMan.setReceiveConnections(true)
                    "stopReceiveConnections" -> vpnMan.setReceiveConnections(false)
                    "startProcessConnections" -> vpnMan.startProcessConnections()
                    "stopProcessConnections" -> vpnMan.stopProcessConnections()
                    else -> println("Command not found")
                }
                cmd = scanner.nextLine()
            }
            vpnMan.stopProcessConnections()
            iface.close()
        } catch (e: Exception) {
            Log.e(VpnManager.TAG, "Main main error", e)
        }
    }
}