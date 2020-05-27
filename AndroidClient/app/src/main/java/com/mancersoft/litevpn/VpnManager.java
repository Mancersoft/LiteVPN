package com.mancersoft.litevpn;

import android.app.PendingIntent;
import android.net.VpnService;
import android.text.TextUtils;
import android.util.Log;

import com.mancersoft.litevpn.transport.IVpnTransport;
import com.mancersoft.litevpn.transport.Packet;
import com.mancersoft.litevpn.transport.TransportType;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class VpnManager {

    public interface OnConnectionChangedListener {
        void onConnectionChanged(boolean isConnected);
    }

    public static final String TAG = "LiteVpnClient";

    public static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    static final int PACKET_TYPE_BYTE_OFFSET = 0;
    private static final byte PARAMS_PACKET = 0;

    private final VpnService mService;

    private final String mServerName;

    private final PendingIntent mConfigureIntent;
    private OnConnectionChangedListener mOnConnectionChangedListener;

    private String mProxyHostName;
    private int mProxyHostPort;

    private final boolean mAllowPackages;
    private final Set<String> mPackages;

    private final ConnectionManager mConnManager;
    private final IVpnTransport mTransport;
    private final InterfaceManager mIface;

    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    private Future<?> mUserToInternetProcessing;

    private final AtomicBoolean mIsConnected = new AtomicBoolean(false);

    VpnManager(final VpnService service, final String serverName,
               TransportType transportType, String sharedSecret,
               final String proxyHostName, final int proxyHostPort,
               boolean allowPackages, final Set<String> packages, PendingIntent intent,
               Object... transportParams) {
        mService = service;
        mServerName = serverName;

        if (!TextUtils.isEmpty(proxyHostName)) {
            mProxyHostName = proxyHostName;
            mProxyHostPort = proxyHostPort;
        }

        mAllowPackages = allowPackages;
        mPackages = packages;
        mConfigureIntent = intent;
        mIface = InterfaceManager.getInstance();
        mConnManager = new ConnectionManager(sharedSecret, service);

        Object[] fullTransportParams = new Object[transportParams.length + 1];
        fullTransportParams[0] = serverName;
        System.arraycopy(transportParams, 0, fullTransportParams, 1, transportParams.length);
        mTransport = mConnManager.createTransport(transportType, fullTransportParams);
    }

    void setOnConnectionChangedListener(OnConnectionChangedListener listener) {
        mOnConnectionChangedListener = listener;
    }

    private void invokeOnConnectionChanged(boolean isConnected) {
        if (mOnConnectionChangedListener != null) {
            mOnConnectionChangedListener.onConnectionChanged(isConnected);
        }
    }

    void connect() {
        disconnect(false);
        try {
            Log.i(TAG, "Start connection");

            if (!Utils.isNetworkAvailable(mService)) {
                throw new Exception("Network is not available");
            }

            if (!mConnManager.protectTransport(mTransport)) {
                throw new Exception("Cannot protect the tunnel");
            }

            mTransport.connect().thenAccept((isConnected) -> {
                if (!isConnected) {
                    disconnect(true);
                    return;
                }

                mConnManager.sendConnectQuery(mTransport).thenAccept((params) -> {
                    mIsConnected.set(true);
                    mIface.init(params, mService, mServerName, mConfigureIntent,
                            mProxyHostName, mProxyHostPort, mAllowPackages, mPackages);

                    mTransport.setOnMessageListener(this::internetToUserProcessing);
                    mTransport.setOnClosedListener(this::connectionClosedProcessing);
                    mUserToInternetProcessing =
                            mExecutorService.submit(this::userToInternetProcessing);
                    invokeOnConnectionChanged(true);
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "Connection failed, exiting", e);
            disconnect(true);
        }
    }

    private void userToInternetProcessing() {
        try {
            byte[] data = new byte[VpnManager.MAX_PACKET_SIZE];
            Packet packet = new Packet();
            packet.setData(data);
            while (!Thread.currentThread().isInterrupted()) {
                //System.out.println("Start read iface");
                int length = mIface.read(data);
                //System.out.println("Stop read iface");

                packet.setLength(length);
                mTransport.sendAsync(packet);
            }
        } catch (Exception e) {
            Log.e(TAG, "VpnManager userToInternetProcessing error", e);
            disconnect(true);
        }
    }

    private void internetToUserProcessing(Packet packet) {
        mExecutorService.execute(() -> {
            try {
                byte[] data = packet.getData();
                if (data.length > MAX_PACKET_SIZE) {
                    return;
                }

                if (data[PACKET_TYPE_BYTE_OFFSET] != PARAMS_PACKET) {
                    //Log.d(TAG, "Start write iface");
                    mIface.write(data, packet.getLength());
                    //Log.d(TAG, "Stop write iface");
                }
            } catch (Exception e) {
                Log.e(TAG, "VpnManager internetToUserProcessing error", e);
                disconnect(true);
            }
        });
    }

    private void connectionClosedProcessing(boolean isByUser) {
        disconnect(!isByUser);
    }

    void disconnect(boolean notify) {
        mTransport.setOnMessageListener(null);
        mTransport.setOnClosedListener(null);
        if (mUserToInternetProcessing != null) {
            mUserToInternetProcessing.cancel(true);
        }

        if (mIsConnected.get()) {
            mExecutorService.execute(() -> {
                try {
                    mConnManager.sendDisconnectQuery(mTransport);
                    Thread.sleep(100);
                    mTransport.disconnect();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Unable to disconnect transport layer", e);
                } catch (Exception ignored) {
                    // normal exit
                }
            });
        }

        try {
            mIface.close();
        } catch (Exception e) {
            Log.e(TAG, "Unable to close interface", e);
        }

        mIsConnected.set(false);
        if (notify) {
            invokeOnConnectionChanged(false);
        }
    }
}
