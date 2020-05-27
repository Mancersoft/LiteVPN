package com.mancersoft.litevpnserver.transport;

import java.util.concurrent.CompletableFuture;

public interface IVpnTransport {

    interface MessageListener {
        void onMessage(Packet packet);
    }

    boolean isReliable();

    CompletableFuture<Boolean> start();

    void stop();

    void sendAsync(Packet packet);

    void setOnMessageListener(MessageListener messageListener);
}
