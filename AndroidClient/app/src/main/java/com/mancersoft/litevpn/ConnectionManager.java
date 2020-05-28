package com.mancersoft.litevpn;

import android.net.VpnService;
import android.util.Log;

import com.mancersoft.litevpn.transport.IVpnTransport;
import com.mancersoft.litevpn.transport.Packet;
import com.mancersoft.litevpn.transport.TelegramTransport;
import com.mancersoft.litevpn.transport.TransportType;
import com.mancersoft.litevpn.transport.UdpTransport;
import com.mancersoft.litevpn.transport.WebSocketTransport;

import java.io.Closeable;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

import static com.mancersoft.litevpn.VpnManager.MAX_PACKET_SIZE;
import static com.mancersoft.litevpn.VpnManager.PACKET_TYPE_BYTE_OFFSET;
import static com.mancersoft.litevpn.VpnManager.TAG;
import static java.nio.charset.StandardCharsets.US_ASCII;

class ConnectionManager {

    private static final byte CONNECT_PACKET = 0;
    private static final byte DISCONNECT_PACKET = 1;
    private static final int SHARED_SECRET_OFFSET = 1;

    private static final int NOT_RELIABLE_SEND_PARAMS_COUNT = 3;
    private static final int NOT_RELIABLE_HANDSHAKE_ATTEMPTS_COUNT = 50;

    private final String mSharedSecret;
    private final VpnService mService;

    private int mHandshakeAttempt = 0;

    ConnectionManager(String sharedSecret, VpnService vpnService) {
        mSharedSecret = sharedSecret;
        mService = vpnService;
    }

    IVpnTransport createTransport(TransportType transportType, String... transportParams) {
        switch (transportType) {
            case WEBSOCKET:
                return new WebSocketTransport(transportParams[0]);
            case TELEGRAM:
                return new TelegramTransport();
            default:
                return new UdpTransport(transportParams[0], Integer.parseInt(transportParams[1]));
        }
    }

    CompletableFuture<ConnectionParams> sendConnectQuery(IVpnTransport transport) {
        CompletableFuture<ConnectionParams> result = new CompletableFuture<>();
        Packet packet = new Packet();

        byte[] secretBytes = mSharedSecret.getBytes(US_ASCII);
        if (secretBytes.length + 1 > MAX_PACKET_SIZE) {
            completeConnection(transport, result, null);
            return result;
        }

        byte[] connectionData = new byte[secretBytes.length + 1];
        connectionData[PACKET_TYPE_BYTE_OFFSET] = CONNECT_PACKET;
        System.arraycopy(secretBytes, 0, connectionData, SHARED_SECRET_OFFSET, secretBytes.length);
        packet.setData(connectionData);
        packet.setLength(connectionData.length);
        mHandshakeAttempt = 0;
        transport.setOnMessageListener((receivedPacket) -> {
            mHandshakeAttempt++;
            byte[] data = receivedPacket.getData();
            if (data[0] == 0) {
                ConnectionParams params = Utils.getConnectionParams(
                        new String(data, 1, receivedPacket.getLength() - 1, US_ASCII).trim());
                completeConnection(transport, result, params);
            } else if (transport.isReliable()) {
                completeConnection(transport, result, null);
            }

            if (mHandshakeAttempt >= NOT_RELIABLE_HANDSHAKE_ATTEMPTS_COUNT) {
                completeConnection(transport, result, null);
            }
        });
        sendQuery(transport, packet);

        return result;
    }

    void sendDisconnectQuery(IVpnTransport transport) {
        Packet packet = new Packet();
        byte[] data = new byte[PACKET_TYPE_BYTE_OFFSET + 1];
        data[PACKET_TYPE_BYTE_OFFSET] = DISCONNECT_PACKET;
        packet.setData(data);
        packet.setLength(data.length);
        sendQuery(transport, packet);
    }

    private static void sendQuery(IVpnTransport transport, Packet packet) {
        int sendCount = transport.isReliable() ? 1 : NOT_RELIABLE_SEND_PARAMS_COUNT;
        for (int i = 0; i < sendCount; ++i) {
            transport.sendAsync(packet);
        }
    }

    private void completeConnection(IVpnTransport transport,
                                    CompletableFuture<ConnectionParams> result, ConnectionParams value) {
        mHandshakeAttempt = 0;
        transport.setOnMessageListener(null);
        result.complete(value);
    }

    boolean protectTransport(IVpnTransport transport) {
        try {
            Closeable socket = transport.createSocket();
            boolean isProtected;
            if (socket instanceof Socket) {
                isProtected = mService.protect((Socket) socket);
            } else {
                isProtected = mService.protect((DatagramSocket) socket);
            }

            if (!isProtected) {
                throw new IllegalStateException("Cannot protect the tunnel");
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "ConnectionManager protectTransport error", e);
            return false;
        }
    }
}