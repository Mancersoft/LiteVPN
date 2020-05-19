package com.mancersoft.litevpnserver;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mancersoft.litevpnserver.transport.IVpnTransport;
import com.mancersoft.litevpnserver.transport.Packet;
import trikita.log.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class VpnManager {

    public static final String TAG = "LiteVpnServer";

    public static final int PACKET_TYPE_BYTE_OFFSET = 0;
    public static final byte CONNECT_PACKET = 0;

    private static final byte DISCONNECT_PACKET = 1;
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    private static final int CONNECTION_TIMEOUT_SECONDS = 120;

    private static VpnManager mInstance;

    public static VpnManager getInstance() {
        if (mInstance == null) {
            mInstance = new VpnManager();
        }

        return mInstance;
    }

    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();
    private final Cache<String, Object> mIpToConnection;
    private final ConcurrentHashMap<Object, String> mConectionToIp = new ConcurrentHashMap<>();
    private final InterfaceManager mIface = InterfaceManager.getInstance();
    private final NatManager mNatManager = NatManager.getInstance();
    private final ConnectionManager mConnManager = ConnectionManager.getInstance();

    private final AtomicBoolean mReceiveConnections = new AtomicBoolean(false);
    private final AtomicBoolean mProcessConnections = new AtomicBoolean(false);

    private IVpnTransport mTransport;

    private VpnManager() {
        mIpToConnection = CacheBuilder.newBuilder()
                .expireAfterAccess(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .removalListener(notification -> {
                    mConectionToIp.remove(notification.getValue());
                    String ipAddress = (String) notification.getKey();
                    mNatManager.clearDisconnectedPorts(ipAddress);
                    mConnManager.disconnect(ipAddress);
                })
                .build();
    }

    public void init(IVpnTransport transport) {
        mTransport = transport;
    }

    public void setReceiveConnections(boolean isReceive) {
        mReceiveConnections.set(isReceive);
    }

    public void startProcessConnections() {
        stopProcessConnections();
        if (mTransport.start()) {
            mProcessConnections.set(true);
            mExecutorService.execute(this::internetToUserProcessing);
            mExecutorService.execute(this::userToInternetProcessing);
        }
    }

    private void internetToUserProcessing() {
        try {
            byte[] data = new byte[VpnManager.MAX_PACKET_SIZE];
            while (mProcessConnections.get()) {
                //System.out.println("Start read iface");
                int length = mIface.read(data);
                //System.out.println("Stop read iface, length: " + length);

                if (!mNatManager.convertFromInternet(data)) {
                    continue;
                }

                String ipAddress = Utils.getDestinationIp(data);
                if (!mIpToConnection.asMap().containsKey(ipAddress)) {
                    continue;
                }

                var packet = new Packet();
                packet.setData(data);
                packet.setLength(length);
                packet.setDestination(mIpToConnection.asMap().get(ipAddress));

                //System.out.println("Start send transport");
                mTransport.sendAsync(packet);
                //System.out.println("Stop send transport");
            }
        } catch (Exception e) {
            Log.e(TAG, "VpnManager internetToUserProcessing error", e);
            stopProcessConnections();
        }
    }

    private void userToInternetProcessing() {
        try {
            byte[] data = new byte[VpnManager.MAX_PACKET_SIZE];
            var packet = new Packet();
            packet.setData(data);
            while (mProcessConnections.get()) {
                //System.out.println("Start receive transport");
                mTransport.receive(packet);
                //System.out.println("Stop receive transport, length: " + packet.getLength());

                switch (data[PACKET_TYPE_BYTE_OFFSET]) {
                    case CONNECT_PACKET -> {
                        if (mReceiveConnections.get()) {
                            Object packetSource = packet.getSource();
                            if (mConectionToIp.containsKey(packetSource)) {
                                mConnManager.processConnection(mTransport, packet, mConectionToIp.get(packetSource));
                                continue;
                            }

                            String assignedIp = mConnManager.processConnection(mTransport, packet, null);
                            if (assignedIp != null) {
                                mIpToConnection.put(assignedIp, packetSource);
                            }
                        }

                        continue;
                    }
                    case DISCONNECT_PACKET -> {
                        Object packetSource = packet.getSource();
                        if (mConectionToIp.containsKey(packetSource)) {
                            mIpToConnection.asMap().remove(mConectionToIp.get(packetSource));
                        }

                        continue;
                    }
                }

                String ipAddress = Utils.getSourceIp(data);
                if (!mIpToConnection.asMap().containsKey(ipAddress)) {
                    continue;
                }

                if (!mNatManager.convertToInternet(data)) {
                    continue;
                }

                //System.out.println("Start write iface");
                mIface.write(data, packet.getLength());
                //System.out.println("Stop write iface");
            }
        } catch (Exception e) {
            Log.e(TAG, "VpnManager userToInternetProcessing error", e);
            stopProcessConnections();
        }
    }

    public void stopProcessConnections() {
        if (!mProcessConnections.get()) {
            return;
        }

        mTransport.stop();
        mProcessConnections.set(false);
    }
}
