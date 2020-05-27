package com.mancersoft.litevpn.transport;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface IVpnTransport {

    interface MessageListener {
        void onMessage(Packet packet);
    }

    interface ClosedListener {
        void onClosed(boolean isByUser);
    }

    Closeable createSocket() throws Exception;

    boolean isReliable();

    CompletableFuture<Boolean> connect();

    void disconnect();

    void sendAsync(Packet packet);

    void setOnMessageListener(MessageListener messageListener);

    void setOnClosedListener(ClosedListener closedListener);
}
