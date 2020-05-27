package com.mancersoft.litevpnserver;

import com.mancersoft.litevpnserver.transport.IVpnTransport;
import com.mancersoft.litevpnserver.transport.TransportType;
import trikita.log.Log;

import java.util.Scanner;

import static com.mancersoft.litevpnserver.VpnManager.TAG;

public class Main {

    private static final String INTERNET_INTERFACE = "eth0";
    private static final String TUNNEL_INTERFACE = "tun0";

    private static final String VPN_NETWORK = "10.0.0.0";
    private static final byte VPN_NETWORK_PREFIX = 8;
    private static final String VPN_TUN_SOURCE = "10.255.255.254"; // "10.0.0.1";
    private static final String VPN_TUN_DEST = "10.255.255.255"; // "10.0.0.2";

    private static final short MTU = 1400;
    private static final String ROUTE = "0.0.0.0";
    private static final byte ROUTE_PREFIX_LENGTH = 0;
    private static final String DNS_SERVER = "8.8.8.8";
    private static final String SEARCH_DOMAIN = null;

    private static final String FIRST_CLIENT_IP = "10.0.0.1"; // "10.0.0.3";

    private static final int UDP_PORT = 8000;
    //private static final String WEB_SOCKET_SERVER_ID = "WebSocketServerLiteVPN";

    static {
        try {
            System.loadLibrary("tun");
        } catch (java.lang.UnsatisfiedLinkError ignore) {
            System.load(System.getProperty("user.dir") + "/build/libs/tun/shared/libtun.so");
        }
    }

    public static void main(String[] args) {
        try {
            String sudoPassword = "kali";
            String sharedSecret = "test";
            TransportType transportType = TransportType.UDP;

            var iface = InterfaceManager.getInstance();
            iface.init(sudoPassword, INTERNET_INTERFACE, TUNNEL_INTERFACE,
                    VPN_NETWORK, VPN_NETWORK_PREFIX,
                    VPN_TUN_SOURCE, VPN_TUN_DEST);
            var connMan = ConnectionManager.getInstance();
            connMan.init(MTU, ROUTE, ROUTE_PREFIX_LENGTH, DNS_SERVER, SEARCH_DOMAIN,
                    FIRST_CLIENT_IP, sharedSecret);
            var natMan = NatManager.getInstance();
            natMan.init(VPN_TUN_DEST);
            var vpnMan = VpnManager.getInstance();
            IVpnTransport transport = connMan.createTransport(transportType, UDP_PORT);
            //IVpnTransport transport = connMan.createTransport(transportType, WEB_SOCKET_SERVER_ID);
            vpnMan.init(transport);
            vpnMan.setReceiveConnections(true);

            System.out.println("Server started!");
            vpnMan.startProcessConnections();

            var scanner = new Scanner(System.in);
            String cmd = scanner.nextLine();
            while (!cmd.equals("exit")) {
                switch (cmd) {
                    case "startReceiveConnections" -> vpnMan.setReceiveConnections(true);
                    case "stopReceiveConnections" -> vpnMan.setReceiveConnections(false);
                    case "startProcessConnections" -> vpnMan.startProcessConnections();
                    case "stopProcessConnections" -> vpnMan.stopProcessConnections();
                }

                cmd = scanner.nextLine();
            }

            vpnMan.stopProcessConnections();
            iface.close();

        } catch (Exception e) {
            Log.e(TAG, "Main main error", e);
        }
    }
}
