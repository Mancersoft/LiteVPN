package com.mancersoft.litevpnserver.transport

import com.mancersoft.litevpnserver.VpnManager
import trikita.log.Log
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.DatagramChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future

class UdpTransport(private val mPort: Int) : IVpnTransport {
    private var mUdpChannel: DatagramChannel? = null
    private val mExecutorService = Executors.newCachedThreadPool()
    private var mIncomingMessageProcessing: Future<*>? = null
    private var mMessageListener: ((Packet) -> Unit)? = null
    override val isReliable: Boolean
        get() = false

    override fun start(): CompletableFuture<Boolean> {
        val result = CompletableFuture<Boolean>()
        try {
            stop()
            mUdpChannel = try {
                DatagramChannel.open(StandardProtocolFamily.INET6)
            } catch (e: UnsupportedOperationException) {
                DatagramChannel.open(StandardProtocolFamily.INET)
            }

            mUdpChannel!!.setOption(StandardSocketOptions.SO_REUSEADDR, true)
            mUdpChannel!!.configureBlocking(true)
            val address = InetSocketAddress(mPort)
            mUdpChannel!!.bind(address)
            mIncomingMessageProcessing = mExecutorService.submit { incomingMessagesProcessing() }
        } catch (e: Exception) {
            Log.e(VpnManager.TAG, "UdpTransport start error", e)
            result.complete(false)
            return result
        }
        result.complete(true)
        return result
    }

    override fun stop() {
        try {
            mIncomingMessageProcessing?.cancel(true)
            mUdpChannel?.close()
        } catch (e: Exception) {
            Log.e(VpnManager.TAG, "UdpTransport stop error", e)
        }
    }

    override fun sendAsync(packet: Packet) {
        val packetToSend = Packet(packet)
        mExecutorService.execute {
            try {
                val buffer = ByteBuffer.wrap(packetToSend.data, 0, packetToSend.length)
                mUdpChannel!!.send(buffer, packetToSend.destination as SocketAddress)
            } catch (e: Exception) {
                Log.e(VpnManager.TAG, "UdpTransport sendAsync error", e)
            }
        }
    }

    override fun setOnMessageListener(messageListener: ((Packet) -> Unit)?) {
        mMessageListener = messageListener
    }

    private fun incomingMessagesProcessing() {
        try {
            val packet = Packet()
            packet.data = ByteArray(VpnManager.MAX_PACKET_SIZE)
            while (!Thread.currentThread().isInterrupted) {
                //System.out.println("Start receive transport");
                receive(packet)
                //System.out.println("Stop receive transport");
                mMessageListener?.invoke(Packet(packet))
            }
        } catch (e: Exception) {
            Log.e(VpnManager.TAG, "UdpTransport incomingMessagesProcessing error", e)
            restart()
        }
    }

    private fun restart() {
        Log.d(VpnManager.TAG, "UdpTransport restarting...")
        start()
    }

    private fun receive(packet: Packet) {
        try {
            val buffer = ByteBuffer.wrap(packet.data)
            packet.source = mUdpChannel!!.receive(buffer)
            packet.length = buffer.position()
        } catch (ignored: ClosedByInterruptException) {
            Log.d(VpnManager.TAG, "UdpTransport receive interrupted")
        } catch (e: Exception) {
            Log.e(VpnManager.TAG, "UdpTransport receive error", e)
        }
    }

}