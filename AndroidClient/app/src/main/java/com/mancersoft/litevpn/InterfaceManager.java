package com.mancersoft.litevpn;

import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

import static com.mancersoft.litevpn.VpnManager.TAG;

class InterfaceManager {

    private static InterfaceManager mInstance;

    private FileInputStream mInStream;
    private FileOutputStream mOutStream;

    private ParcelFileDescriptor mIface;

    static InterfaceManager getInstance() {
        if (mInstance == null) {
            mInstance = new InterfaceManager();
        }

        return mInstance;
    }

    private InterfaceManager() {
    }

    void init(final ConnectionParams params, final VpnService service, final String serverName,
              final PendingIntent configureIntent, final boolean allowPackages, final Set<String> packages) {
        VpnService.Builder builder = service.new Builder();
        builder.setMtu(params.getMtu());
        builder.addAddress(params.getAddress(), params.getAddressPrefixLength());
        builder.addRoute(params.getRoute(), params.getRoutePrefixLength());
        builder.addDnsServer(params.getDnsServer());
        if (params.getSearchDomain() != null) {
            builder.addSearchDomain(params.getSearchDomain());
        }

        for (String packageName : packages) {
            try {
                if (allowPackages) {
                    builder.addAllowedApplication(packageName);
                } else {
                    builder.addDisallowedApplication(packageName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Package not available: " + packageName, e);
            }
        }

        builder.setSession(serverName).setConfigureIntent(configureIntent);

        builder.setBlocking(true);
        builder.setConfigureIntent(configureIntent);
        mIface = builder.establish();
        assert mIface != null;
        mInStream = new FileInputStream(mIface.getFileDescriptor());
        mOutStream = new FileOutputStream(mIface.getFileDescriptor());
    }

    int read(byte[] data) throws IOException {
        return mInStream.read(data);
    }

    void write(byte[] data, int length) throws IOException {
        mOutStream.write(data, 0, length);
    }

    void close() throws IOException {
        if (mIface != null) {
            mIface.close();
        }
    }
}
