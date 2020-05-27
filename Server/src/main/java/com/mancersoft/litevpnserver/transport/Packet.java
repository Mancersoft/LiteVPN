package com.mancersoft.litevpnserver.transport;

public class Packet {

    private Object mSource;
    private Object mDestination;
    private byte[] mData;
    private int mLength;

    public Object getSource() {
        return mSource;
    }

    public void setSource(Object source) {
        this.mSource = source;
    }

    public Object getDestination() {
        return mDestination;
    }

    public void setDestination(Object destination) {
        this.mDestination = destination;
    }

    public byte[] getData() {
        return mData;
    }

    public void setData(byte[] data) {
        this.mData = data;
    }

    public int getLength() {
        return mLength;
    }

    public void setLength(int length) {
        this.mLength = length;
    }
}
