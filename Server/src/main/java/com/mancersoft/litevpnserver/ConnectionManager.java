package com.mancersoft.litevpnserver;

import com.mancersoft.litevpnserver.transport.*;

import java.util.TreeSet;

public class ConnectionManager {

    private static ConnectionManager mInstance;

    public static ConnectionManager getInstance() {
        if (mInstance == null) {
            mInstance = new ConnectionManager();
        }

        return mInstance;
    }

    private ConnectionParams mParams;
    private int clientIpAddress;

    private final TreeSet<Integer> occupiedIps = new TreeSet<>();

    private String mSharedSecret;

    private ConnectionManager() {
    }

    public void init(short mtu, String route, byte routePrefixLength,
                     String dnsServer, String searchDomain, String firstClientIp,
                     String sharedSecret) {
        mSharedSecret = sharedSecret;
        clientIpAddress = Utils.ipToInt(firstClientIp);
        mParams = new ConnectionParams();
        mParams.setMtu(mtu);
        mParams.setRoute(route, routePrefixLength);
        mParams.setDnsServer(dnsServer);
        mParams.setSearchDomain(searchDomain);
    }

    public IVpnTransport createTransport(TransportType transportType, Object... transportParams) {
        return switch (transportType) {
            case WEBSOCKET -> new WebSocketTransport();
            case TELEGRAM -> new TelegramTransport();
            default -> new UdpTransport(mSharedSecret, (int) transportParams[0], mParams);
        };
    }

    public String processConnection(IVpnTransport transport, Packet packet, String ipAddress) {
        String newIp = ipAddress;
        Integer integerIp = null;
        if (ipAddress == null) {
            integerIp = Utils.firstMissing(occupiedIps);
            if (integerIp == null) {
                integerIp = clientIpAddress;
            }

            newIp = Utils.intIpToString(integerIp);
        }

        //newIp = "10.255.255.255";
        if (transport.receiveConnection(packet, newIp)) {
            if (ipAddress == null) {
                occupiedIps.add(integerIp);
                clientIpAddress++;
            }

            return newIp;
        }

        return null;
    }

    public void disconnect(String ipAddress) {
        occupiedIps.remove(Utils.ipToInt(ipAddress));
    }
}
