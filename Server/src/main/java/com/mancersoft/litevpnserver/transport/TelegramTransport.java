package com.mancersoft.litevpnserver.transport;

import java.util.concurrent.CompletableFuture;

public class TelegramTransport implements IVpnTransport {
    @Override
    public boolean start() {
        return false;
    }

    @Override
    public void stop() {

    }

    @Override
    public boolean receiveConnection(Packet packet, String vpnIpAddress) {
        return false;
    }

    @Override
    public void sendAsync(Packet packet) {

    }

    @Override
    public void receive(Packet packet) {

    }
}
