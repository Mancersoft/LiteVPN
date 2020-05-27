package com.mancersoft.litevpnserver.transport;

import com.mancersoft.litevpnserver.ConnectionParams;
import trikita.log.Log;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.mancersoft.litevpnserver.VpnManager.*;

public class UdpTransport implements IVpnTransport {

    private DatagramChannel mUdpChannel;
    private final String mSecret;
    private final int mPort;
    private final ConnectionParams mParams;

    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    public UdpTransport(String secret, int port, ConnectionParams params) {
        mSecret = secret;
        mPort = port;
        mParams = params;
    }

    @Override
    public boolean start() {
        try {
            mUdpChannel = DatagramChannel.open(StandardProtocolFamily.INET6);
            mUdpChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            mUdpChannel.configureBlocking(true);
            var address = new InetSocketAddress(mPort);
            mUdpChannel.bind(address);
        } catch (Exception e) {
            Log.e(TAG, "UdpTransport start error", e);
            return false;
        }

        return true;
    }

    @Override
    public void stop() {
        try {
            if (mUdpChannel != null) {
                mUdpChannel.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "UdpTransport stop error", e);
        }
    }

    @Override
    public boolean receiveConnection(Packet packet, String vpnIpAddress) {
        try {
            String receivedSecret = new String(packet.getData(), 1, packet.getLength() - 1);
            boolean isCorrectSecret = mSecret.equals(receivedSecret);
            if (isCorrectSecret) {
                mParams.setAddress(vpnIpAddress, mParams.getAddressPrefixLength());
                byte[] parameters = mParams.getBytes();
                parameters[PACKET_TYPE_BYTE_OFFSET] = CONNECT_PACKET;
                var paramPacket = new Packet();
                paramPacket.setData(parameters);
                paramPacket.setLength(parameters.length);
                paramPacket.setDestination(packet.getSource());
                for (int i = 0; i < 3; ++i) {
                    this.sendAsync(paramPacket);
                }
            }

            return isCorrectSecret;
        } catch (Exception e) {
            Log.e(TAG, "UdpTransport receiveConnection error", e);
            return false;
        }
    }

    @Override
    public void sendAsync(Packet packet) {
        mExecutorService.execute(() -> {
            try {
                var buffer = ByteBuffer.wrap(packet.getData());
                buffer = buffer.limit(packet.getLength());
                mUdpChannel.send(buffer, (SocketAddress) packet.getDestination());
            } catch (Exception e) {
                Log.e(TAG, "UdpTransport sendAsync error", e);
            }
        });
    }

    @Override
    public void receive(Packet packet) {
        try {
            var buffer = ByteBuffer.wrap(packet.getData());
            packet.setSource(mUdpChannel.receive(buffer));
            packet.setLength(buffer.position());
        } catch (Exception e) {
            Log.e(TAG, "UdpTransport receive error", e);
        }
    }
}
