package com.mancersoft.litevpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.mancersoft.litevpn.transport.TransportType;

import java.util.Collections;
import java.util.Set;

public class LiteVpnService extends VpnService implements Handler.Callback {

    public static final String ACTION_CONNECT = "com.mancersoft.litevpn.START";
    public static final String ACTION_DISCONNECT = "com.mancersoft.litevpn.STOP";

    private Handler mHandler;
    private PendingIntent mConfigureIntent;

    private VpnManager mVpnManager;

    @Override
    public void onCreate() {
        if (mHandler == null) {
            mHandler = new Handler(this);
        }

        mConfigureIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnect();
            return START_NOT_STICKY;
        } else {
            connect();
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        disconnect();
    }

    @Override
    public boolean handleMessage(Message message) {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        if (message.what != R.string.disconnected) {
            updateForegroundNotification(message.what);
        }

        return true;
    }

    private void connect() {
        mHandler.sendEmptyMessage(R.string.connecting);

        final SharedPreferences prefs = getSharedPreferences(MainActivity.Prefs.NAME, MODE_PRIVATE);
        final String server = prefs.getString(MainActivity.Prefs.SERVER_ADDRESS, "");
        final String secret = prefs.getString(MainActivity.Prefs.SHARED_SECRET, "");
        final boolean allow = prefs.getBoolean(MainActivity.Prefs.ALLOW, true);
        final Set<String> packages =
                prefs.getStringSet(MainActivity.Prefs.PACKAGES, Collections.emptySet());
        final int port = prefs.getInt(MainActivity.Prefs.SERVER_PORT, 0);
        final String proxyHost = prefs.getString(MainActivity.Prefs.PROXY_HOSTNAME, "");
        final int proxyPort = prefs.getInt(MainActivity.Prefs.PROXY_PORT, 0);

        if (mVpnManager != null) {
            mVpnManager.disconnect(false);
        }


        mVpnManager = new VpnManager(this, server, TransportType.UDP, secret,
                proxyHost, proxyPort, allow, packages, mConfigureIntent, port);
        mVpnManager.setOnConnectionChangedListener((isConnected) ->
        {
            if (isConnected) {
                mHandler.sendEmptyMessage(R.string.connected);
            } else {
                disconnect();
            }
        });
        mVpnManager.connect();
    }

    private void disconnect() {
        mHandler.sendEmptyMessage(R.string.disconnected);
        mVpnManager.disconnect(false);
        stopForeground(true);
    }

    private void updateForegroundNotification(final int message) {
        final String NOTIFICATION_CHANNEL_ID = "LiteVpn";
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);
        assert mNotificationManager != null;
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        startForeground(1, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build());
    }
}
