package com.mancersoft.litevpnserver;

import sun.misc.Unsafe;
import trikita.log.Log;

import java.io.*;
import java.lang.reflect.Field;

import static com.mancersoft.litevpnserver.VpnManager.TAG;

public class InterfaceManager {

    public static native void ioctl(int descriptor);

    private RandomAccessFile mBaseStream;
    private FileInputStream mInStream;
    private FileOutputStream mOutStream;

    private static InterfaceManager mInstance;

    public static InterfaceManager getInstance() {
        if (mInstance == null) {
           mInstance = new InterfaceManager();
        }

        return mInstance;
    }

    private InterfaceManager() {
    }

    private static void disableWarning() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe u = (Unsafe) theUnsafe.get(null);

            Class<?> cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field logger = cls.getDeclaredField("logger");
            u.putObjectVolatile(cls, u.staticFieldOffset(logger), null);
        } catch (Exception ignored) {
            // ignore
        }
    }

    public void init(String password, String networkIface, String tunnelIface,
                     String vpnNetwork, byte vpnNetworkPrefix,
                     String vpnTunnelSource, String vpnTunnelDestination) throws Exception {
        disableWarning();

        String[] cmd = {"/bin/bash","-c",String.format("echo %1$s| sudo -S " +
                "echo 1 > /proc/sys/net/ipv4/ip_forward ; " +
                "iptables -t nat -A POSTROUTING -s %2$s/%3$d -o %4$s -j MASQUERADE ; " +
                "ip tuntap add dev %5$s mode tun ; " +
                "ifconfig %5$s %6$s dstaddr %7$s up",
                password, vpnNetwork, vpnNetworkPrefix, networkIface, tunnelIface,
                vpnTunnelSource, vpnTunnelDestination)};
        Runtime.getRuntime().exec(cmd);

        File tunFile = new File("/dev/net/tun");
        mBaseStream = new RandomAccessFile(tunFile, "rwd");

        FileDescriptor fd = mBaseStream.getFD();
        mInStream = new FileInputStream(fd);
        mOutStream = new FileOutputStream(fd);


        Field f = fd.getClass().getDeclaredField("fd");
        f.setAccessible(true);
        int descriptor = f.getInt(fd);

        ioctl(descriptor);
    }

    public int read(byte[] data) throws IOException {
        return mInStream.read(data);
    }

    public void write(byte[] data, int length) throws IOException {
        mOutStream.write(data, 0, length);
    }

    public void close() {
        try {
            mInStream.close();
            mOutStream.close();
            mBaseStream.close();
        } catch (Exception e) {
            Log.e(TAG, "InterfaceManager close error", e);
        }
    }
}
