package com.mancersoft.litevpn;

import android.app.PendingIntent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.text.TextUtils;
import android.util.Log;

import com.mancersoft.litevpn.transport.IVpnTransport;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class VpnManager {

    public interface OnConnectionChangedListener {
        void onConnectionChanged(boolean isConnected);
    }

    public static final String TAG = "LiteVpnClient";

    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;

    private final VpnService mService;

    private final String mServerName;

    private PendingIntent mConfigureIntent;
    private OnConnectionChangedListener mOnConnectionChangedListener;

    private String mProxyHostName;
    private int mProxyHostPort;

    private final boolean mAllowPackages;
    private final Set<String> mPackages;

    private final IVpnTransport mTransport;
    private final InterfaceManager mIface;

    private final AtomicBoolean mConnected = new AtomicBoolean(false);

    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    private static boolean isNetworkAvailable(Context context) {
        if (context == null) {
            return false;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
    }

    VpnManager(final VpnService service, final String serverName, IVpnTransport transport, final String proxyHostName, final int proxyHostPort,
               boolean allowPackages, final Set<String> packages) {
        mService = service;
        mServerName = serverName;

        if (!TextUtils.isEmpty(proxyHostName)) {
            mProxyHostName = proxyHostName;
            mProxyHostPort = proxyHostPort;
        }

        mAllowPackages = allowPackages;
        mPackages = packages;
        mTransport = transport;
        mIface = InterfaceManager.getInstance();
    }

    void setConfigureIntent(PendingIntent intent) {
        mConfigureIntent = intent;
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

            if (!isNetworkAvailable(mService)) {
                throw new Exception("Network is not available");
            }

            mTransport.connect().thenAccept((params) -> {
                if (params == null) {
                    return; // Show toast with error
                }

                mIface.init(params, mService, mServerName, mConfigureIntent,
                        mProxyHostName, mProxyHostPort, mAllowPackages, mPackages);
                mConnected.set(true);
                invokeOnConnectionChanged(mConnected.get());

                mExecutorService.execute(() -> {
                    try {
                        byte[] packet = new byte[VpnManager.MAX_PACKET_SIZE];
                        while (mConnected.get()) {
                            //Log.d(TAG, "Start read iface");
                            int length = mIface.read(packet);
                            //Log.d(TAG, "Stop read iface");
                            if (length > 0) {
                                //Log.d(TAG, "Start send transport");
                                mTransport.send(packet, length);
                                //Log.d(TAG, "Stop send transport");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Outgoing thread error", e);
                    }

                    disconnect(true);
                });
                mExecutorService.execute(() -> {
                    try {
                        byte[] packet = new byte[VpnManager.MAX_PACKET_SIZE];
                        while (mConnected.get()) {
                            //Log.d(TAG, "Start receive transport");
                            int length = mTransport.receive(packet);
                            //Log.d(TAG, "Stop receive transport");
                            if (packet[0] != 0) {
                                //Log.d(TAG, "Start write iface");
                                mIface.write(packet, length);
                                //Log.d(TAG, "Stop write iface");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Incoming thread error", e);
                    }

                    disconnect(true);
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "Connection failed, exiting", e);
        }
    }

    void disconnect(boolean notify) {
        if (!mConnected.get()) {
            return;
        }

        mConnected.set(false);
        try {
            mTransport.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Unable to disconnect transport layer", e);
        }

        try {
            mIface.close();
        } catch (Exception e) {
            Log.e(TAG, "Unable to close interface", e);
        }

        if (notify) {
            invokeOnConnectionChanged(mConnected.get());
        }
    }
}
