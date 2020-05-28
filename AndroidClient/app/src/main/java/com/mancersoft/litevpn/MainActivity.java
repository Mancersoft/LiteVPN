package com.mancersoft.litevpn;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.mancersoft.litevpn.transport.TransportType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class MainActivity extends Activity {
    public interface Prefs {
        String NAME = "connection";
        String SERVER_ID = "server.id";
        String SERVER_PARAMS = "server.params";
        String VPN_TYPE = "vpn.type";
        String SHARED_SECRET = "shared.secret";
        String ALLOW = "allow";
        String PACKAGES = "packages";
    }

    private boolean mWaitForResult = false;

    @SuppressLint("ApplySharedPref")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView serverId = findViewById(R.id.serverId);
        final TextView params = findViewById(R.id.params);
        final TextView sharedSecret = findViewById(R.id.secret);

        final RadioButton allowed = findViewById(R.id.allowed);
        final RadioButton disallowed = findViewById(R.id.disallowed);
        final TextView packages = findViewById(R.id.packages);
        final RadioButton vpnTypeUdp = findViewById(R.id.udp);
        final RadioButton vpnTypeWebsocket = findViewById(R.id.websocket);
        final RadioButton vpnTypeTelegram = findViewById(R.id.telegram);

        final SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        serverId.setText(prefs.getString(Prefs.SERVER_ID, ""));
        params.setText(prefs.getString(Prefs.SERVER_PARAMS, ""));

        final String vpnType = prefs.getString(Prefs.VPN_TYPE, TransportType.UDP.toString());
        vpnTypeUdp.setChecked(vpnType.equals(TransportType.UDP.toString()));
        vpnTypeWebsocket.setChecked(vpnType.equals(TransportType.WEBSOCKET.toString()));
        vpnTypeTelegram.setChecked(vpnType.equals(TransportType.TELEGRAM.toString()));

        sharedSecret.setText(prefs.getString(Prefs.SHARED_SECRET, ""));
        allowed.setChecked(prefs.getBoolean(Prefs.ALLOW, true));
        disallowed.setChecked(!allowed.isChecked());
        packages.setText(String.join(", ", prefs.getStringSet(
                Prefs.PACKAGES, Collections.emptySet())));

        findViewById(R.id.connect).setOnClickListener(v -> {
            final Set<String> packageSet =
                    Arrays.stream(packages.getText().toString().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());
            if (!checkPackages(packageSet)) {
                return;
            }

            String newVpnType;
            if (vpnTypeUdp.isChecked()) {
                newVpnType = TransportType.UDP.toString();
            } else if (vpnTypeWebsocket.isChecked()) {
                newVpnType = TransportType.WEBSOCKET.toString();
            } else {
                newVpnType = TransportType.TELEGRAM.toString();
            }

            prefs.edit()
                    .putString(Prefs.SERVER_ID, serverId.getText().toString())
                    .putString(Prefs.SERVER_PARAMS, params.getText().toString())
                    .putString(Prefs.VPN_TYPE, newVpnType)
                    .putString(Prefs.SHARED_SECRET, sharedSecret.getText().toString())
                    .putBoolean(Prefs.ALLOW, allowed.isChecked())
                    .putStringSet(Prefs.PACKAGES, packageSet)
                    .commit();
            Intent intent = VpnService.prepare(MainActivity.this);
            mWaitForResult = true;
            if (intent != null) {
                startActivityForResult(intent, 0);
            } else {
                onActivityResult(0, RESULT_OK, null);
            }
        });
        findViewById(R.id.disconnect).setOnClickListener(v -> startService(getServiceIntent().setAction(LiteVpnService.ACTION_DISCONNECT)));
    }

    private boolean checkPackages(Set<String> packageNames) {
        final boolean hasCorrectPackageNames = packageNames.isEmpty() ||
                getPackageManager().getInstalledPackages(0).stream()
                        .map(pi -> pi.packageName)
                        .collect(Collectors.toSet())
                        .containsAll(packageNames);
        if (!hasCorrectPackageNames) {
            Toast.makeText(this, R.string.unknown_package_names, Toast.LENGTH_SHORT).show();
        }
        return hasCorrectPackageNames;
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (mWaitForResult && result == RESULT_OK) {
            startService(getServiceIntent().setAction(LiteVpnService.ACTION_CONNECT));
        }

        mWaitForResult = false;
    }

    private Intent getServiceIntent() {
        return new Intent(this, LiteVpnService.class);
    }
}
