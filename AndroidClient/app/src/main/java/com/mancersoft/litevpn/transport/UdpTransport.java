package com.mancersoft.litevpn.transport;

import android.util.Log;

import com.mancersoft.litevpn.VpnManager;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.mancersoft.litevpn.VpnManager.TAG;

public class UdpTransport implements IVpnTransport {

    private DatagramChannel mUdpChannel;

    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    private final String mServerName;
    private final int mServerPort;

    private Future<?> mIncomingMessageProcessing;

    private MessageListener mMessageListener;
    private ClosedListener mClosedListener;

    public UdpTransport(String serverName, int serverPort) {
        mServerName = serverName;
        mServerPort = serverPort;
    }

    @Override
    public Closeable createSocket() throws IOException {
        mUdpChannel = DatagramChannel.open();
        return mUdpChannel.socket();
    }

    @Override
    public boolean isReliable() {
        return false;
    }

    @Override
    public void setOnMessageListener(MessageListener messageListener) {
        mMessageListener = messageListener;
    }

    @Override
    public void setOnClosedListener(ClosedListener closedListener) {
        mClosedListener = closedListener;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                disconnect();
                SocketAddress serverAddress = new InetSocketAddress(mServerName, mServerPort);
                mUdpChannel.connect(serverAddress);
                mUdpChannel.configureBlocking(true);
                mIncomingMessageProcessing = mExecutorService.submit(this::incomingMessagesProcessing);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "UdpTransport connect error", e);
                return false;
            }
        });
    }

    private void disconnect(boolean isByUser) {
        disconnect();
        if (mClosedListener != null) {
            mClosedListener.onClosed(isByUser);
        }
    }

    @Override
    public void disconnect() {
        try {
            if (mIncomingMessageProcessing != null) {
                mIncomingMessageProcessing.cancel(true);
            }

            if (mUdpChannel != null && mUdpChannel.isConnected()) {
                mUdpChannel.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "UdpTransport disconnect error", e);
        }
    }

    @Override
    public void sendAsync(Packet packetToSend) {
        Packet packet = new Packet(packetToSend);
        mExecutorService.execute(() -> {
            try {
                send(packet.getData(), packet.getLength());
            } catch (ClosedChannelException e1) {
                disconnect(true);
            } catch (Exception e2) {
                Log.e(TAG, "UdpTransport sendAsync error", e2);
                disconnect(false);
            }
        });
    }

    private void send(byte[] data, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
        mUdpChannel.write(buffer);
    }

    private void receive(Packet packet) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
        packet.setLength(mUdpChannel.read(buffer));
    }

    private void incomingMessagesProcessing() {
        try {
            Packet packet = new Packet();
            packet.setData(new byte[VpnManager.MAX_PACKET_SIZE]);
            while (!Thread.currentThread().isInterrupted()) {
                //System.out.println("Start receive transport");
                this.receive(packet);
                //System.out.println("Stop receive transport");
                if (mMessageListener != null) {
                    mMessageListener.onMessage(new Packet(packet));
                }
            }
        } catch (ClosedByInterruptException e1) {
            disconnect(true);
        } catch (Exception e2) {
            Log.e(TAG, "UdpTransport incomingMessagesProcessing error", e2);
            disconnect(false);
        }
    }
}
