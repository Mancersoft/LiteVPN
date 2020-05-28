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
import android.util.Log;
import android.widget.Toast;

import com.mancersoft.litevpn.transport.TransportType;

import java.util.Collections;
import java.util.Set;

import static com.mancersoft.litevpn.VpnManager.TAG;

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
        final String serverId = prefs.getString(MainActivity.Prefs.SERVER_ID, "").trim();
        final String[] serverParams = prefs.getString(MainActivity.Prefs.SERVER_PARAMS, "").split(",");
        for (int i = 0; i < serverParams.length; ++i) {
            serverParams[i] = serverParams[i].trim();
        }

        final TransportType vpnType = Enum.valueOf(TransportType.class,
                prefs.getString(MainActivity.Prefs.VPN_TYPE, TransportType.UDP.toString()));
        final String secret = prefs.getString(MainActivity.Prefs.SHARED_SECRET, "");
        final boolean allow = prefs.getBoolean(MainActivity.Prefs.ALLOW, true);
        final Set<String> packages =
                prefs.getStringSet(MainActivity.Prefs.PACKAGES, Collections.emptySet());

        if (mVpnManager != null) {
            mVpnManager.disconnect(false);
        }

        try {
            mVpnManager = new VpnManager(this, serverId, vpnType, secret,
                    allow, packages, mConfigureIntent, serverParams);
            mVpnManager.setOnConnectionChangedListener((isConnected) ->
            {
                if (isConnected) {
                    mHandler.sendEmptyMessage(R.string.connected);
                } else {
                    disconnect();
                }
            });
            mVpnManager.connect();
        } catch (Exception e) {
            Log.e(TAG, "LiteVpnService connect error", e);
            disconnect();
        }
    }

    private void disconnect() {
        mHandler.sendEmptyMessage(R.string.disconnected);
        if (mVpnManager != null) {
            mVpnManager.disconnect(false);
        }

        new Handler(this.getMainLooper()).postDelayed(() -> stopForeground(true), 100);
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
