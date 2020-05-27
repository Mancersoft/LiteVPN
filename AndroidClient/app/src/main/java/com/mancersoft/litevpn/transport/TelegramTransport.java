package com.mancersoft.litevpn.transport;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public class TelegramTransport implements IVpnTransport {
    @Override
    public Closeable createSocket() throws Exception {
        return null;
    }

    @Override
    public boolean isReliable() {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return null;
    }

    @Override
    public void disconnect() {

    }

    @Override
    public void sendAsync(Packet packet) {

    }

    @Override
    public void setOnMessageListener(MessageListener messageListener) {

    }

    @Override
    public void setOnClosedListener(ClosedListener closedListener) {

    }
}
