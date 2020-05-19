package com.mancersoft.litevpn.transport;

import com.mancersoft.litevpn.ConnectionParams;

import java.util.concurrent.CompletableFuture;

public interface IVpnTransport {

    CompletableFuture<ConnectionParams> connect() throws Exception;

    void send(byte[] data, int length) throws Exception;

    int receive(byte[] data) throws Exception;

    void disconnect() throws Exception;
}
