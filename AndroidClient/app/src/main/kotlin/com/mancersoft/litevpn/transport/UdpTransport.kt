package com.mancersoft.litevpn.transport

import android.util.Log
import com.mancersoft.litevpn.MAX_PACKET_SIZE
import com.mancersoft.litevpn.TAG
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.ClosedChannelException
import java.nio.channels.DatagramChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future

class UdpTransport(private val mServerName: String, private val mServerPort: Int) : IVpnTransport {

    private var mUdpChannel: DatagramChannel? = null
    private val mExecutorService = Executors.newCachedThreadPool()
    private var mIncomingMessageProcessing: Future<*>? = null
    private var mMessageListener: ((Packet) -> Unit)? = null
    private var mClosedListener: ((isByUser: Boolean) -> Unit)? = null

    @Throws(IOException::class)
    override fun createSocket(): Closeable {
        disconnect()
        mUdpChannel = DatagramChannel.open()
        return mUdpChannel!!.socket()
    }

    override val isReliable: Boolean
        get() = false

    override fun setOnMessageListener(messageListener: ((Packet) -> Unit)?) {
        mMessageListener = messageListener
    }

    override fun setOnClosedListener(closedListener: ((isByUser: Boolean) -> Unit)?) {
        mClosedListener = closedListener
    }

    override fun connect(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                disconnect()
                val serverAddress: SocketAddress = InetSocketAddress(mServerName, mServerPort)
                mUdpChannel!!.connect(serverAddress)
                mUdpChannel!!.configureBlocking(true)
                mIncomingMessageProcessing = mExecutorService.submit { incomingMessagesProcessing() }
                return@supplyAsync true
            } catch (e: IOException) {
                Log.e(TAG, "UdpTransport connect error", e)
                return@supplyAsync false
            }
        }
    }

    private fun disconnect(isByUser: Boolean) {
        disconnect()
        mClosedListener?.invoke(isByUser)
    }

    override fun disconnect() {
        try {
            mIncomingMessageProcessing?.cancel(true)
            if (mUdpChannel != null && mUdpChannel!!.isConnected) {
                mUdpChannel!!.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "UdpTransport disconnect error", e)
        }
    }

    override fun sendAsync(packet: Packet) {
        val packetToSend = Packet(packet)
        mExecutorService.execute {
            try {
                send(packetToSend.data, packetToSend.length)
            } catch (e1: ClosedChannelException) {
                disconnect(true)
            } catch (e2: Exception) {
                Log.e(TAG, "UdpTransport sendAsync error", e2)
                disconnect(false)
            }
        }
    }

    @Throws(IOException::class)
    private fun send(data: ByteArray, length: Int) {
        val buffer = ByteBuffer.wrap(data, 0, length)
        mUdpChannel!!.write(buffer)
    }

    @Throws(IOException::class)
    private fun receive(packet: Packet) {
        val buffer = ByteBuffer.wrap(packet.data)
        packet.length = mUdpChannel!!.read(buffer)
    }

    private fun incomingMessagesProcessing() {
        try {
            val packet = Packet()
            packet.data = ByteArray(MAX_PACKET_SIZE)
            while (!Thread.currentThread().isInterrupted) {
                //System.out.println("Start receive transport");
                receive(packet)
                //System.out.println("Stop receive transport");
                mMessageListener?.invoke(Packet(packet))
            }
        } catch (e1: ClosedByInterruptException) {
            disconnect(true)
        } catch (e2: Exception) {
            Log.e(TAG, "UdpTransport incomingMessagesProcessing error", e2)
            disconnect(false)
        }
    }

}