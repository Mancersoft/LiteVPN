package com.mancersoft.litevpn.transport;

import android.net.VpnService;
import android.util.Log;

import com.mancersoft.litevpn.ConnectionParams;
import com.mancersoft.litevpn.VpnManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CompletableFuture;

import kotlin.text.Charsets;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class UdpTransport implements IVpnTransport {

    private static final int MAX_HANDSHAKE_ATTEMPTS = 50;

    private DatagramChannel mUdpChannel;

    private final String mSharedSecret;
    private final String mServerName;
    private final int mServerPort;
    private final VpnService mService;

    public UdpTransport(String sharedSecret, String serverName, int serverPort, VpnService service) {
        mSharedSecret = sharedSecret;
        mServerName = serverName;
        mServerPort = serverPort;
        mService = service;
    }

    @Override
    public CompletableFuture<ConnectionParams> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                mUdpChannel = DatagramChannel.open();
                if (!mService.protect(mUdpChannel.socket())) {
                    throw new IllegalStateException("Cannot protect the tunnel");
                }

                SocketAddress serverAddress = new InetSocketAddress(mServerName, mServerPort);
                mUdpChannel.connect(serverAddress);
                mUdpChannel.configureBlocking(true);

                ByteBuffer packet = ByteBuffer.allocate(1024);
                packet.put((byte) 0).put(mSharedSecret.getBytes(Charsets.US_ASCII)).flip();
                for (int i = 0; i < 3; ++i) {
                    packet.position(0);
                    mUdpChannel.write(packet);
                }
                packet.clear();

                for (int i = 0; i < MAX_HANDSHAKE_ATTEMPTS; ++i) {
                    int length = mUdpChannel.read(packet);
                    if (packet.get(0) == 0) {
                        return getConnectionParams(
                                new String(packet.array(),
                                        1,
                                        length - 1,
                                        US_ASCII).trim());
                    }
                }
            } catch (IOException e) {
                Log.e(VpnManager.TAG, "UdpTransport connect error", e);
            }

            return null;
        });
    }

    private static ConnectionParams getConnectionParams(String parameters) {
        ConnectionParams params = new ConnectionParams();
        for (String parameter : parameters.split(" ")) {
            String[] fields = parameter.split(",");
            try {
                switch (fields[0].charAt(0)) {
                    case 'm':
                        params.setMtu(Short.parseShort(fields[1]));
                        break;
                    case 'a':
                        params.setAddress(fields[1], Byte.parseByte(fields[2]));
                        break;
                    case 'r':
                        params.setRoute(fields[1], Byte.parseByte(fields[2]));
                        break;
                    case 'd':
                        params.setDnsServer(fields[1]);
                        break;
                    case 's':
                        params.setSearchDomain(fields[1]);
                        break;
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad parameter: " + parameter);
            }
        }

        return params;
    }

    @Override
    public void send(byte[] data, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.limit(length);
        mUdpChannel.write(buffer);
    }

    @Override
    public int receive(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        return mUdpChannel.read(buffer);
    }

    @Override
    public void disconnect() throws IOException {
        if (mUdpChannel == null) {
            return;
        }

        if (mUdpChannel.isConnected()) {
            ByteBuffer packet = ByteBuffer.allocate(1024);
            packet.put((byte) 1).flip();
            for (int i = 0; i < 3; ++i) {
                packet.position(0);
                mUdpChannel.write(packet);
            }
        }

        mUdpChannel.close();
    }
}
