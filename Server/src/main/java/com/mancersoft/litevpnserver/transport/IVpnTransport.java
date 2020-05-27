package com.mancersoft.litevpnserver.transport;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface IVpnTransport {

    boolean start();

    void stop();

    boolean receiveConnection(Packet packet, String vpnIpAddress);

    void sendAsync(Packet packet);

    void receive(Packet packet);

}
