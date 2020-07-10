package com.mancersoft.litevpnserver

import sun.misc.Unsafe
import trikita.log.Log
import java.io.*

object InterfaceManager {

    private var mBaseStream: RandomAccessFile? = null
    private var mInStream: FileInputStream? = null
    private var mOutStream: FileOutputStream? = null

    private external fun ioctl(descriptor: Int)

    private fun disableWarning() {
        try {
            val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
            theUnsafe.isAccessible = true
            val u = theUnsafe[null] as Unsafe
            val cls = Class.forName("jdk.internal.module.IllegalAccessLogger")
            val logger = cls.getDeclaredField("logger")
            u.putObjectVolatile(cls, u.staticFieldOffset(logger), null)
        } catch (ignored: Exception) {
            // ignore
        }
    }

    @Throws(Exception::class)
    fun init(password: String?, networkIface: String, tunnelIface: String,
             vpnNetwork: String, vpnNetworkPrefix: Byte,
             vpnTunnelSource: String, vpnTunnelDestination: String) {
        disableWarning()
        val cmd = arrayOf("/bin/bash",
                "-c",
                if (password != null) "echo $password| sudo -S " else "" +
                        "echo 1 > /proc/sys/net/ipv4/ip_forward ; " +
                        "iptables -t nat -A POSTROUTING -s $vpnNetwork/$vpnNetworkPrefix -o $networkIface -j MASQUERADE ; " +
                        "ip tuntap add dev $tunnelIface mode tun ; " +
                        "ifconfig $tunnelIface $vpnTunnelSource dstaddr $vpnTunnelDestination up")
        Runtime.getRuntime().exec(cmd)
        val tunFile = File("/dev/net/tun")
        mBaseStream = RandomAccessFile(tunFile, "rwd")
        val fd = mBaseStream!!.fd
        mInStream = FileInputStream(fd)
        mOutStream = FileOutputStream(fd)
        val f = fd.javaClass.getDeclaredField("fd")
        f.isAccessible = true
        val descriptor = f.getInt(fd)
        ioctl(descriptor)
    }

    @Throws(IOException::class)
    fun read(data: ByteArray): Int {
        return mInStream!!.read(data)
    }

    @Throws(IOException::class)
    fun write(data: ByteArray, length: Int) {
        mOutStream!!.write(data, 0, length)
    }

    fun close() {
        try {
            mInStream?.close()
            mOutStream?.close()
            mBaseStream?.close()
        } catch (e: Exception) {
            Log.e(VpnManager.TAG, "InterfaceManager close error", e)
        }
    }
}