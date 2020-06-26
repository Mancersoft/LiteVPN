package com.mancersoft.litevpnserver.transport

import java.util.concurrent.CompletableFuture

interface IVpnTransport {
    val isReliable: Boolean
    fun start(): CompletableFuture<Boolean>
    fun stop()
    fun sendAsync(packet: Packet)
    fun setOnMessageListener(messageListener: ((Packet) -> Unit)?)
}