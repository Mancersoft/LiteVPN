package com.mancersoft.litevpnserver;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class NatManager {

    private static final short FIRST_PORT = 1024;
    private static final int MAXIMUM_PORT_COUNT = 48128;
    private static final int TABLE_TIMEOUT_SECONDS = 65;

    private final Set<Short> mAvailablePorts = ConcurrentHashMap.newKeySet(MAXIMUM_PORT_COUNT);
    private final ConcurrentHashMap<String, Short> mIpPortToExternPort = new ConcurrentHashMap<>();
    private final Cache<Short, String> mExternPortToIpPort;
    private final ConcurrentHashMap<String, Set<Short>> mIpToExternPorts = new ConcurrentHashMap<>();

    private static NatManager mInstance;

    public static NatManager getInstance() {
        if (mInstance == null) {
            mInstance = new NatManager();
        }

        return mInstance;
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private NatManager() {
        for (int i = FIRST_PORT; i < FIRST_PORT + MAXIMUM_PORT_COUNT; ++i) {
            mAvailablePorts.add((short)i);
        }

        mExternPortToIpPort = CacheBuilder.newBuilder()
                .expireAfterAccess(TABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .removalListener(notification -> {
                    mIpPortToExternPort.remove(notification.getValue());
                    mAvailablePorts.add((short)notification.getKey());
                })
                .build();
    }

    private String mVpnTunnelDestination;

    public void init(String vpnTunnelDestination) {
        mVpnTunnelDestination = vpnTunnelDestination;
    }

    public boolean convertToInternet(byte[] packet) {
        try {
            String ipPort = Utils.getSourceIpPort(packet);
            if (!mIpPortToExternPort.containsKey(ipPort)) {
                short externalPort = mAvailablePorts.iterator().next();

                //externalPort = Utils.parseIpPort(ipPort).getSecond();

                mAvailablePorts.remove(externalPort);
                mIpPortToExternPort.put(ipPort, externalPort);
                mExternPortToIpPort.put(externalPort, ipPort);

                String ipAddress = Utils.getSourceIp(packet);
                if (!mIpToExternPorts.containsKey(ipAddress)) {
                    mIpToExternPorts.put(ipAddress, ConcurrentHashMap.newKeySet());
                }

                mIpToExternPorts.get(ipAddress).add(externalPort);
            }

            short currentExternalPort = mIpPortToExternPort.get(ipPort);
            Utils.changeIpPort(packet, mVpnTunnelDestination, currentExternalPort, true);
            return true;
        } catch (Exception e) {
            //Log.e(TAG, "NatManager convertToInternet error", e);
            return false;
        }
    }

    public boolean convertFromInternet(byte[] packet) {
        try {
            String destIp = Utils.getDestinationIp(packet);
            if (!destIp.equals(mVpnTunnelDestination)) {
                return false;
            }

            short port = Utils.getDestinationPort(packet);
            if (!mExternPortToIpPort.asMap().containsKey(port)) {
                return false;
            }

            String currentIpPort = mExternPortToIpPort.asMap().get(port);
            var ipPortPair = Utils.parseIpPort(currentIpPort);
            Utils.changeIpPort(packet, ipPortPair.getFirst(), ipPortPair.getSecond(), false);
            return true;
        } catch (Exception e) {
            //Log.e(TAG, "NatManager convertFromInternet error", e);
            return false;
        }
    }

    public void clearDisconnectedPorts(String ipAddress) {
        var externalPortsList = mIpToExternPorts.remove(ipAddress);
        if (externalPortsList != null) {
            for (short port : externalPortsList) {
                mExternPortToIpPort.asMap().remove(port);
            }
        }
    }
}
