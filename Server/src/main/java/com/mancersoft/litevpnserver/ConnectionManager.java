package com.mancersoft.litevpnserver;

import com.mancersoft.litevpnserver.transport.*;
import trikita.log.Log;

import java.util.TreeSet;

import static com.mancersoft.litevpnserver.VpnManager.*;

public class ConnectionManager {

    private static final int SHARED_SECRET_OFFSET = 1;

    private static final int NOT_RELIABLE_SEND_PARAMS_COUNT = 3;

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

    public IVpnTransport createTransport(TransportType transportType, String... transportParams) {
        switch (transportType) {
            case WEBSOCKET:
                return new WebSocketTransport(transportParams[0]);
            case TELEGRAM:
                return new TelegramTransport();
            default:
                return new UdpTransport(Integer.parseInt(transportParams[0]));
        }
    }

    private boolean checkSecret(Packet packet) {
        try {
            String receivedSecret = new String(packet.getData(), SHARED_SECRET_OFFSET,
                    packet.getLength() - SHARED_SECRET_OFFSET);
            return mSharedSecret.equals(receivedSecret);
        } catch (Exception e) {
            Log.e(TAG, "ConnectionManager checkSecret error", e);
            return false;
        }
    }

    private boolean sendParams(IVpnTransport transport, String vpnIpAddress, Object userDest) {
        try {
            mParams.setAddress(vpnIpAddress, mParams.getAddressPrefixLength());
            byte[] parameters = mParams.getBytes();
            if (parameters.length > MAX_PACKET_SIZE) {
                throw new Exception("Params is too long");
            }

            parameters[PACKET_TYPE_BYTE_OFFSET] = CONNECT_PACKET;
            Packet paramPacket = new Packet();
            paramPacket.setData(parameters);
            paramPacket.setLength(parameters.length);
            paramPacket.setDestination(userDest);
            int sendCount = transport.isReliable() ? 1 : NOT_RELIABLE_SEND_PARAMS_COUNT;
            for (int i = 0; i < sendCount; ++i) {
                transport.sendAsync(paramPacket);
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "ConnectionManager sendParams error", e);
            return false;
        }
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
        if (checkSecret(packet) && this.sendParams(transport, newIp, packet.getSource())) {
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
