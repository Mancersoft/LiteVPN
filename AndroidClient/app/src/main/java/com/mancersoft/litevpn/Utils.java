package com.mancersoft.litevpn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

class Utils {
    private Utils() {}

    static boolean isNetworkAvailable(Context context) {
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

    static ConnectionParams getConnectionParams(String parameters) {
        ConnectionParams params = new ConnectionParams();
        for (String parameter : parameters.split(" ")) {
            String[] fields = parameter.split(",");
            try {
                switch (fields[0].charAt(0)) {
                    case 'm':
                        params.setMtu(Short.parseShort(fields[1]));
                        break;
                    case 'a':
                        params.setAddress(fields[1], Byte.parseByte(fields[2]));
                        break;
                    case 'r':
                        params.setRoute(fields[1], Byte.parseByte(fields[2]));
                        break;
                    case 'd':
                        params.setDnsServer(fields[1]);
                        break;
                    case 's':
                        params.setSearchDomain(fields[1]);
                        break;
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad parameter: " + parameter);
            }
        }

        return params;
    }
}
