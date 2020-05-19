package com.mancersoft.litevpn.transport;

import com.mancersoft.litevpn.ConnectionParams;

import java.util.concurrent.CompletableFuture;

public class WebSocketTransport implements IVpnTransport {

    @Override
    public CompletableFuture<ConnectionParams> connect() throws Exception {
        return null;
    }

    @Override
    public void send(byte[] data, int length) throws Exception {

    }

    @Override
    public int receive(byte[] data) throws Exception {
        return 0;
    }

    @Override
    public void disconnect() throws Exception {

    }
}
