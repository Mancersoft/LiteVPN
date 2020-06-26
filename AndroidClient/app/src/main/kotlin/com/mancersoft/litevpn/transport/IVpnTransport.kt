package com.mancersoft.litevpn.transport

import java.io.Closeable
import java.util.concurrent.CompletableFuture

interface IVpnTransport {

    @Throws(Exception::class)
    fun createSocket(): Closeable

    val isReliable: Boolean

    fun connect(): CompletableFuture<Boolean>

    fun disconnect()

    fun sendAsync(packet: Packet)

    fun setOnMessageListener(messageListener: ((Packet) -> Unit)?)

    fun setOnClosedListener(closedListener: ((isByUser: Boolean) -> Unit)?)
}