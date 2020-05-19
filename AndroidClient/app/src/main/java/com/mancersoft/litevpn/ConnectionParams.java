package com.mancersoft.litevpn;

public class ConnectionParams {

    private short mMtu;
    private String mAddress;
    private byte mAddressPrefixLength;
    private String mRoute;
    private byte mRoutePrefixLength;
    private String mDnsServer;
    private String mSearchDomain;

    short getMtu() {
        return mMtu;
    }

    public void setMtu(short mtu) {
        this.mMtu = mtu;
    }

    public String getAddress() {
        return mAddress;
    }

    byte getAddressPrefixLength() {
        return mAddressPrefixLength;
    }

    public void setAddress(String address, byte prefixLength) {
        this.mAddress = address;
        this.mAddressPrefixLength = prefixLength;
    }

    String getRoute() {
        return mRoute;
    }

    byte getRoutePrefixLength() {
        return mRoutePrefixLength;
    }

    public void setRoute(String route, byte prefixLength) {
        this.mRoute = route;
        this.mRoutePrefixLength = prefixLength;
    }

    String getDnsServer() {
        return mDnsServer;
    }

    public void setDnsServer(String dnsServer) {
        this.mDnsServer = dnsServer;
    }

    String getSearchDomain() {
        return mSearchDomain;
    }

    public void setSearchDomain(String searchDomain) {
        this.mSearchDomain = searchDomain;
    }
}
