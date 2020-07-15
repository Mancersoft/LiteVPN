package com.mancersoft.litevpnserver

import com.mancersoft.litevpnserver.transport.TransportType
import org.apache.commons.cli.*
import trikita.log.Log
import java.net.URLDecoder
import java.util.*
import kotlin.system.exitProcess


object Main {
    private const val TUNNEL_INTERFACE = "tun0"
    private const val VPN_NETWORK = "10.0.0.0"
    private const val VPN_NETWORK_PREFIX: Byte = 8
    private const val VPN_TUN_SOURCE = "10.255.255.254" // "10.0.0.1";
    private const val VPN_TUN_DEST = "10.255.255.255" // "10.0.0.2";
    private const val MTU: Short = 1400
    private const val ROUTE = "0.0.0.0"
    private const val ROUTE_PREFIX: Byte = 0
    private const val DNS_SERVER = "8.8.8.8"
    private const val FIRST_CLIENT_IP = "10.0.0.1" // "10.0.0.3";
    private const val JAR_NAME = "LiteVPNServer"
////    private const val INTERNET_INTERFACE = "eth0"
////    private const val UDP_PORT = 8000
////    private const val WEB_SOCKET_SERVER_ID = "ServerLiteVPN"

    private fun loadJniLib(filename: String) {
        try {
            val path: String =
                    URLDecoder.decode(Main::class.java.protectionDomain.codeSource.location.path, "UTF-8")
                            .substringBeforeLast("/", "")
            System.load("$path/$filename")
        } catch (ignore: UnsatisfiedLinkError) {
        }
    }

    init {
        try {
            System.loadLibrary("tun")
        } catch (ignore: UnsatisfiedLinkError) {
            loadJniLib("build/libs/tun/shared/libtun.so")
            loadJniLib("tun/shared/libtun.so")
            loadJniLib("tun/libtun.so")
            loadJniLib("libtun.so")
        }
    }

    private fun getOption(opt: String, longOpt: String, description: String, required: Boolean): Option {
        val option = Option(opt, longOpt, true, description)
        option.isRequired = required
        return option
    }

    private fun getOptionValue(cmd: CommandLine, opt: String, defaultValue: String? = null): String? {
        return cmd.getOptionValue(opt) ?: defaultValue
    }

    private inline fun <reified T : Enum<T>> enumsToString(): String {
        return enumValues<T>().joinToString { it.name }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val options = Options()
        val optionsList = arrayOf(
                getOption("s", "secret", "VPN secret", true),
                getOption("i", "inet-iface", "Internet network interface", true),
                getOption("v", "vpn-type", "VPN server type, allowed params: ${enumsToString<TransportType>()}", true),
                getOption("p", "vpn-params", "Type-specific additional parameters, port for UDP, server Id for WEBSOCKET", true),
                getOption("pwd", "sudo-password", "sudo password, no default value", false),
                getOption("t", "tun-iface", "Tunnel network interface, default: $TUNNEL_INTERFACE", false),
                getOption("n", "vpn-network", "VPN network address with prefix, default: $VPN_NETWORK/$VPN_NETWORK_PREFIX", false),
                getOption("src", "vpn-tun-source", "VPN tunnel source address, default: $VPN_TUN_SOURCE", false),
                getOption("dst", "vpn-tun-dest", "VPN tunnel destination address, default: $VPN_TUN_DEST", false),
                getOption("m", "mtu", "VPN transport layer Maximum Transport Unit, default: $MTU", false),
                getOption("r", "route", "VPN client route filter with prefix, default: $ROUTE/$ROUTE_PREFIX", false),
                getOption("d", "dns-server", "VPN client DNS server, default: $DNS_SERVER", false),
                getOption("sd", "search-domain", "VPN client search domain, no default value", false),
                getOption("f", "first-ip", "First IP address to be assigned to a client, default $FIRST_CLIENT_IP", false)
        )
        optionsList.forEach {
            options.addOption(it)
        }

        val parser: CommandLineParser = DefaultParser()
        val formatter = HelpFormatter()
        try {
            val startCmd = parser.parse(options, args)

            val iface = InterfaceManager
            val networkAndPrefix = getOptionValue(startCmd, "vpn-network",
                    "$VPN_NETWORK/$VPN_NETWORK_PREFIX")!!.split("/")
            iface.init(
                    getOptionValue(startCmd, "sudo-password", null),
                    getOptionValue(startCmd, "inet-iface")!!,
                    getOptionValue(startCmd, "tun-iface", TUNNEL_INTERFACE)!!,
                    networkAndPrefix[0],
                    networkAndPrefix[1].toByte(),
                    getOptionValue(startCmd, "vpn-tun-source", VPN_TUN_SOURCE)!!,
                    getOptionValue(startCmd, "vpn-tun-dest", VPN_TUN_DEST)!!)

            val connMan = ConnectionManager
            val routeAndPrefix = getOptionValue(startCmd, "route",
                    "$ROUTE/$ROUTE_PREFIX")!!.split("/")
            connMan.init(
                    getOptionValue(startCmd, "mtu", "$MTU")!!.toShort(),
                    networkAndPrefix[1].toByte(),
                    routeAndPrefix[0],
                    routeAndPrefix[1].toByte(),
                    getOptionValue(startCmd, "dns-server", DNS_SERVER),
                    getOptionValue(startCmd, "search-domain", null),
                    getOptionValue(startCmd, "first-ip", FIRST_CLIENT_IP)!!,
                    getOptionValue(startCmd, "secret")!!)

            val natMan = NatManager
            natMan.init(getOptionValue(startCmd, "vpn-tun-dest", VPN_TUN_DEST)!!)

            val vpnMan = VpnManager
            val transport = connMan.createTransport(
                    TransportType.valueOf(getOptionValue(startCmd, "vpn-type")!!),
                    getOptionValue(startCmd, "vpn-params")!!)
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
        }
        catch (e: ParseException) {
            println(e.message)
            formatter.printHelp(JAR_NAME, options)
            exitProcess(1)
        }
        catch (e: Exception) {
            println(e.message)
            Log.e(VpnManager.TAG, "Main main error", e)
        }
    }
}