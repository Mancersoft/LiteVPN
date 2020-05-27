package com.mancersoft.litevpnserver.transport;

import java.util.concurrent.CompletableFuture;

public class TelegramTransport implements IVpnTransport {

    @Override
    public boolean isReliable() {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> start() {
        return null;
    }

    @Override
    public void stop() {

    }

    @Override
    public void sendAsync(Packet packet) {

    }

    @Override
    public void setOnMessageListener(MessageListener messageListener) {

    }
}
