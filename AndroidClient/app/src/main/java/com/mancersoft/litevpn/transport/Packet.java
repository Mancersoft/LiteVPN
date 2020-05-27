package com.mancersoft.litevpn.transport;

public class Packet {

    private byte[] mData;
    private int mLength;

    public Packet() {
    }

    Packet(Packet packet) {
        mData = new byte[packet.mLength];
        System.arraycopy(packet.getData(), 0, mData, 0, packet.mLength);
        mLength = packet.mLength;
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