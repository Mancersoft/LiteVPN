package com.mancersoft.litevpnserver;

import kotlin.Pair;

import java.util.NavigableSet;

public class Utils {

    private final static int SOURCE_IP_OFFSET = 12;
    private final static int DEST_IP_OFFSET = 16;
    private final static int SOURCE_PORT_OFFSET = 0;
    private final static int DEST_PORT_OFFSET = 2;

    private final static int IP_CHECKSUM_OFFSET = 10;
    private final static int PROTOCOL_ID_OFFSET = 9;
    private final static int IP_HEADER_SIZE_OFFSET = 0;
    private final static int ID_TCP = 6;
    private final static int ID_UDP = 17;
    private final static int TCP_CHECKSUM_OFFSET = 16;
    private final static int UDP_CHECKSUM_OFFSET = 6;

    private static void checksumChange(byte[] packet, int offset, int checksumChange) {
        short checksum = (short) ~((packet[offset] << 8 & 0xFF00) + (packet[offset + 1] & 0xFF));
        int finalSum = (checksum & 0xFFFF) + checksumChange;
        finalSum = (finalSum & 0xFFFF) + (finalSum >>> 16 & 0xFF);
        finalSum += finalSum >>> 16 & 0xFF;

        checksum = (short) ~((short) finalSum & 0xFFFF);
        setChecksum(packet, offset, checksum);
}

    private static int getIpChecksum(byte[] data, int offset) {
        int ipChecksum = (data[offset] << 8 & 0xFF00) + (data[offset + 1] & 0xFF) +
                (data[offset + 2] << 8 & 0xFF00) + (data[offset + 3] & 0xFF);
        return  ((ipChecksum & 0xFFFF) + (ipChecksum >>> 16 & 0xFF)) & 0xFFFF;
    }

    private static int getIpPortChecksum(int ipChecksum, short port) {
        int checksum = ipChecksum + (port & 0xFFFF);
        return  ((checksum & 0xFFFF) + (checksum >>> 16 & 0xFF)) & 0xFFFF;
    }

    private static void setChecksum(byte[] packet, int offset, short checksum) {
        byte[] checksumBytes = shortToByteArray(checksum);
        packet[offset] = checksumBytes[0];
        packet[offset + 1] = checksumBytes[1];
    }

    private static String ipToString(byte[] packet, int offset) {
        return (packet[offset] & 0xFF)
                + "." + (packet[offset + 1] & 0xFF)
                + "." + (packet[offset + 2] & 0xFF)
                + "." + (packet[offset + 3] & 0xFF);
    }

    private static short getWordValue(byte[] packet, int offset) {
        return (short) ((packet[offset] << 8 & 0xFF00) + (packet[offset + 1] & 0xFF));
    }

    @SuppressWarnings("SameParameterValue")
    private static String portToString(byte[] packet, int offset) {
        return Integer.toString(getWordValue(packet, offset) & 0xFFFF);
    }

    private static byte[] ipToByteArray(String ipAddress) {
        String[] octets = ipAddress.split("\\.");
        byte[] result = new byte[4];
        for (int i = 0; i < octets.length; ++i) {
            result[i] = (byte)Integer.parseInt(octets[i]);
        }

        return result;
    }

    private static byte[] shortToByteArray(short value) {
        return new byte[] { (byte) (value >>> 8), (byte) (value & 0xFF) };
    }

    private static byte getIpHeaderSize(byte[] packet) {
        return (byte) ((packet[IP_HEADER_SIZE_OFFSET] & 0x0F) * 4);
    }

    public static void changeIpPort(byte[] packet, String newIp, short newPort, boolean changeSource) {
        byte ipHeaderSize = getIpHeaderSize(packet);
        byte[] newIpBytes = ipToByteArray(newIp);
        int ipOffset = changeSource ? SOURCE_IP_OFFSET : DEST_IP_OFFSET;
        int oldIpChecksum = getIpChecksum(packet, ipOffset);
        int oldIpChecksumNegative = (~oldIpChecksum) & 0xFFFF;
        int newIpChecksum = getIpChecksum(newIpBytes, 0);
        int ipChecksumChange = newIpChecksum + oldIpChecksumNegative;

        checksumChange(packet, IP_CHECKSUM_OFFSET, ipChecksumChange);
        System.arraycopy(newIpBytes, 0, packet, ipOffset, newIpBytes.length);

        if (packet[PROTOCOL_ID_OFFSET] != ID_TCP && packet[PROTOCOL_ID_OFFSET] != ID_UDP) {
            return;
        }

        int portOffset = changeSource ? ipHeaderSize + SOURCE_PORT_OFFSET : ipHeaderSize + DEST_PORT_OFFSET;
        byte[] newPortBytes = shortToByteArray(newPort);

        short oldPort = getWordValue(packet, portOffset);
        int oldIpPortChecksum = getIpPortChecksum(oldIpChecksum, oldPort);
        int oldIpPortChecksumNegative = (~oldIpPortChecksum) & 0xFFFF;
        int newIpPortChecksum = getIpPortChecksum(newIpChecksum, newPort);
        int ipPortChecksumChange = newIpPortChecksum + oldIpPortChecksumNegative;

        System.arraycopy(newPortBytes, 0, packet, portOffset, newPortBytes.length);

        int checksumOffset = packet[PROTOCOL_ID_OFFSET] == ID_TCP ?
                ipHeaderSize + TCP_CHECKSUM_OFFSET :
                ipHeaderSize + UDP_CHECKSUM_OFFSET;
        checksumChange(packet, checksumOffset, ipPortChecksumChange);
    }

    public static String getDestinationIp(byte[] packet) {
        return ipToString(packet, DEST_IP_OFFSET);
    }

    public static String getSourceIp(byte[] packet) {
        return ipToString(packet, SOURCE_IP_OFFSET);
    }

    public static String getSourceIpPort(byte[] packet) {
        byte ipHeaderSize = getIpHeaderSize(packet);
        return ipToString(packet, SOURCE_IP_OFFSET) + ":" + portToString(packet, ipHeaderSize + SOURCE_PORT_OFFSET);
    }

    public static short getDestinationPort(byte[] packet) {
        byte ipHeaderSize = getIpHeaderSize(packet);
        return getWordValue(packet, ipHeaderSize + DEST_PORT_OFFSET);
    }

    public static Pair<String, Short> parseIpPort(String ipPort) {
        String[] data = ipPort.split(":");
        return new Pair<>(data[0], (short) Integer.parseInt(data[1]));
    }

    public static int ipToInt(String ipAddress) {
        byte[] ipBytes = ipToByteArray(ipAddress);
        int result = 0;
        for (int i = 0; i < ipBytes.length; ++i) {
            result += ipBytes[i] << (24 - (8 * i));
        }

        return result;
    }

    public static String intIpToString(int ipAddress) {
        return String.format("%d.%d.%d.%d",
                (ipAddress >>> 24 & 0xFF),
                (ipAddress >>> 16 & 0xFF),
                (ipAddress >>> 8 & 0xFF),
                (ipAddress & 0xFF));
    }

    public static Integer firstMissing(NavigableSet<Integer> set) {
        if (set.size() <= 1) {
            return null;
        }

        Integer first = set.first();
        Integer last = set.last();
        if(set.size() == last - first + 1) {
            return null;
        }

        while(true) {
            int middle = (first + last) >>> 1;
            NavigableSet<Integer> sub = set.headSet(middle, false);
            if (sub.size() < middle - first) {
                set = sub;
                last = sub.last();
            } else {
                set = set.tailSet(middle, true);
                first = set.first();
                if (first != middle) {
                    return middle;
                }
            }
        }
    }
}
