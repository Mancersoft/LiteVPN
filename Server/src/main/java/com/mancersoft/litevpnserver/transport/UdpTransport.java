package com.mancersoft.litevpnserver.transport;

import com.mancersoft.litevpnserver.VpnManager;
import trikita.log.Log;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.mancersoft.litevpnserver.VpnManager.TAG;

public class UdpTransport implements IVpnTransport {

    private DatagramChannel mUdpChannel;
    private final int mPort;

    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    private Future<?> mIncomingMessageProcessing;

    private MessageListener mMessageListener;

    public UdpTransport(int port) {
        mPort = port;
    }

    @Override
    public boolean isReliable() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> start() {
        var result = new CompletableFuture<Boolean>();
        try {
            stop();
            mUdpChannel = DatagramChannel.open(StandardProtocolFamily.INET6);
            mUdpChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            mUdpChannel.configureBlocking(true);
            var address = new InetSocketAddress(mPort);
            mUdpChannel.bind(address);
            mIncomingMessageProcessing = mExecutorService.submit(this::incomingMessagesProcessing);
        } catch (Exception e) {
            Log.e(TAG, "UdpTransport start error", e);
            result.complete(false);
            return result;
        }

        result.complete(true);
        return result;
    }

    @Override
    public void stop() {
        try {
            if (mIncomingMessageProcessing != null) {
                mIncomingMessageProcessing.cancel(true);
            }

            if (mUdpChannel != null) {
                mUdpChannel.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "UdpTransport stop error", e);
        }
    }

    @Override
    public void sendAsync(Packet packetToSend) {
        var packet = new Packet(packetToSend);
        mExecutorService.execute(() -> {
            try {
                var buffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                mUdpChannel.send(buffer, (SocketAddress) packet.getDestination());
            } catch (Exception e) {
                Log.e(TAG, "UdpTransport sendAsync error", e);
            }
        });
    }

    @Override
    public void setOnMessageListener(MessageListener messageListener) {
        mMessageListener = messageListener;
    }

    private void incomingMessagesProcessing() {
        try {
            var packet = new Packet();
            packet.setData(new byte[VpnManager.MAX_PACKET_SIZE]);
            while (!Thread.currentThread().isInterrupted()) {
                //System.out.println("Start receive transport");
                this.receive(packet);
                //System.out.println("Stop receive transport");
                if (mMessageListener != null) {
                    mMessageListener.onMessage(new Packet(packet));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "UdpTransport incomingMessagesProcessing error", e);
            restart();
        }
    }

    private void restart() {
        Log.d(TAG, "UdpTransport restarting...");
        start();
    }

    private void receive(Packet packet) {
        try {
            var buffer = ByteBuffer.wrap(packet.getData());
            packet.setSource(mUdpChannel.receive(buffer));
            packet.setLength(buffer.position());
        } catch (Exception e) {
            Log.e(TAG, "UdpTransport receive error", e);
        }
    }
}
