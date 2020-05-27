package com.mancersoft.litevpnserver;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class ConnectionParams {

    private short mMtu;
    private String mAddress;
    private byte mAddressPrefixLength;
    private String mRoute;
    private byte mRoutePrefixLength;
    private String mDnsServer;
    private String mSearchDomain;

    public ConnectionParams() {
    }

    public ConnectionParams(ConnectionParams connectionParams) {
        this.mMtu = connectionParams.mMtu;
        this.mAddress = connectionParams.mAddress;
        this.mAddressPrefixLength = connectionParams.mAddressPrefixLength;
        this.mRoute = connectionParams.mRoute;
        this.mRoutePrefixLength = connectionParams.mRoutePrefixLength;
        this.mAddressPrefixLength = connectionParams.mRoutePrefixLength;
        this.mDnsServer = connectionParams.mDnsServer;
        this.mSearchDomain = connectionParams.mSearchDomain;
    }

    public byte[] getBytes() {
        return this.toString().getBytes(US_ASCII);
    }

    public short getMtu() {
        return mMtu;
    }

    public void setMtu(short mtu) {
        this.mMtu = mtu;
    }

    public String getAddress() {
        return mAddress;
    }

    public byte getAddressPrefixLength() {
        return mAddressPrefixLength;
    }

    public void setAddress(String address, byte prefixLength) {
        this.mAddress = address;
        this.mAddressPrefixLength = prefixLength;
    }

    public String getRoute() {
        return mRoute;
    }

    public byte getRoutePrefixLength() {
        return mRoutePrefixLength;
    }

    public void setRoute(String route, byte prefixLength) {
        this.mRoute = route;
        this.mRoutePrefixLength = prefixLength;
    }

    public String getDnsServer() {
        return mDnsServer;
    }

    public void setDnsServer(String dnsServer) {
        this.mDnsServer = dnsServer;
    }

    public String getSearchDomain() {
        return mSearchDomain;
    }

    public void setSearchDomain(String searchDomain) {
        this.mSearchDomain = searchDomain;
    }

    @Override
    public String toString() {
        String result = String.format(" m,%d", mMtu);
        if (mAddress != null) {
            result += String.format(" a,%1$s,%2$d", mAddress, mAddressPrefixLength);
        }
        if (mDnsServer != null) {
            result += String.format(" d,%s", mDnsServer);
        }
        if (mRoute != null) {
            result += String.format(" r,%1$s,%2$d", mRoute, mRoutePrefixLength);
        }
        if (mSearchDomain != null) {
            result += String.format(" s,%s", mSearchDomain);
        }

        return result;
    }
}

