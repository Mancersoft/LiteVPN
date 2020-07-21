package com.mancersoft.litevpnserver

import java.util.*

object Utils {
    private const val SOURCE_IP_OFFSET = 12
    private const val DEST_IP_OFFSET = 16
    private const val SOURCE_PORT_OFFSET = 0
    private const val DEST_PORT_OFFSET = 2
    private const val IP_CHECKSUM_OFFSET = 10
    private const val PROTOCOL_ID_OFFSET = 9
    private const val IP_HEADER_SIZE_OFFSET = 0
    private const val ID_TCP: Byte = 6
    private const val ID_UDP: Byte = 17
    private const val TCP_CHECKSUM_OFFSET = 16
    private const val UDP_CHECKSUM_OFFSET = 6

    private fun checksumChange(packet: ByteArray, offset: Int, checksumChange: Int) {
        var checksum = ((packet[offset].toInt() shl 8 and 0xFF00) + (packet[offset + 1].toInt() and 0xFF)).inv().toShort()
        var finalSum: Int = (checksum.toInt() and 0xFFFF) + checksumChange
        finalSum = (finalSum and 0xFFFF) + (finalSum ushr 16 and 0xFF)
        finalSum += finalSum ushr 16 and 0xFF
        checksum = (finalSum and 0xFFFF).inv().toShort()
        setChecksum(packet, offset, checksum)
    }

    private fun getIpChecksum(data: ByteArray, offset: Int): Int {
        val ipChecksum: Int = (data[offset].toInt() shl 8 and 0xFF00) + (data[offset + 1].toInt() and 0xFF) +
                (data[offset + 2].toInt() shl 8 and 0xFF00) + (data[offset + 3].toInt() and 0xFF)
        return (ipChecksum and 0xFFFF) + (ipChecksum ushr 16 and 0xFF) and 0xFFFF
    }

    private fun getIpPortChecksum(ipChecksum: Int, port: Short): Int {
        val checksum: Int = ipChecksum + (port.toInt() and 0xFFFF)
        return (checksum and 0xFFFF) + (checksum ushr 16 and 0xFF) and 0xFFFF
    }

    private fun setChecksum(packet: ByteArray, offset: Int, checksum: Short) {
        val checksumBytes = shortToByteArray(checksum)
        packet[offset] = checksumBytes[0]
        packet[offset + 1] = checksumBytes[1]
    }

    private fun ipToString(packet: ByteArray, offset: Int): String {
        return ((packet[offset].toInt() and 0xFF)
                .toString() + "." + (packet[offset + 1].toInt() and 0xFF)
                + "." + (packet[offset + 2].toInt() and 0xFF)
                + "." + (packet[offset + 3].toInt() and 0xFF))
    }

    private fun getWordValue(packet: ByteArray, offset: Int): Short {
        return ((packet[offset].toInt() shl 8 and 0xFF00) + (packet[offset + 1].toInt() and 0xFF)).toShort()
    }

    private fun portToString(packet: ByteArray, offset: Int): String {
        return (getWordValue(packet, offset).toInt() and 0xFFFF).toString()
    }

    private fun ipToByteArray(ipAddress: String): ByteArray {
        val octets = ipAddress.split(".").toTypedArray()
        val result = ByteArray(4)
        for (i in octets.indices) {
            result[i] = octets[i].toInt().toByte()
        }
        return result
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf((value.toInt() ushr 8).toByte(), (value.toInt() and 0xFF).toByte())
    }

    private fun getIpHeaderSize(packet: ByteArray): Byte {
        return ((packet[IP_HEADER_SIZE_OFFSET].toInt() and 0x0F) * 4).toByte()
    }

    fun changeIpPort(packet: ByteArray, newIp: String, newPort: Short, changeSource: Boolean) {
        val ipHeaderSize = getIpHeaderSize(packet)
        val newIpBytes = ipToByteArray(newIp)
        val ipOffset = if (changeSource) SOURCE_IP_OFFSET else DEST_IP_OFFSET
        val oldIpChecksum = getIpChecksum(packet, ipOffset)
        val oldIpChecksumNegative = oldIpChecksum.inv() and 0xFFFF
        val newIpChecksum = getIpChecksum(newIpBytes, 0)
        val ipChecksumChange = newIpChecksum + oldIpChecksumNegative
        checksumChange(packet, IP_CHECKSUM_OFFSET, ipChecksumChange)
        System.arraycopy(newIpBytes, 0, packet, ipOffset, newIpBytes.size)
        if (packet[PROTOCOL_ID_OFFSET] != ID_TCP && packet[PROTOCOL_ID_OFFSET] != ID_UDP) {
            return
        }
        val portOffset = if (changeSource) ipHeaderSize + SOURCE_PORT_OFFSET else ipHeaderSize + DEST_PORT_OFFSET
        val newPortBytes = shortToByteArray(newPort)
        val oldPort = getWordValue(packet, portOffset)
        val oldIpPortChecksum = getIpPortChecksum(oldIpChecksum, oldPort)
        val oldIpPortChecksumNegative = oldIpPortChecksum.inv() and 0xFFFF
        val newIpPortChecksum = getIpPortChecksum(newIpChecksum, newPort)
        val ipPortChecksumChange = newIpPortChecksum + oldIpPortChecksumNegative
        System.arraycopy(newPortBytes, 0, packet, portOffset, newPortBytes.size)
        val checksumOffset = if (packet[PROTOCOL_ID_OFFSET] == ID_TCP) ipHeaderSize + TCP_CHECKSUM_OFFSET else ipHeaderSize + UDP_CHECKSUM_OFFSET
        checksumChange(packet, checksumOffset, ipPortChecksumChange)
    }

    fun getDestinationIp(packet: ByteArray): String {
        return ipToString(packet, DEST_IP_OFFSET)
    }

    fun getSourceIp(packet: ByteArray): String {
        return ipToString(packet, SOURCE_IP_OFFSET)
    }

    fun getSourceIpPort(packet: ByteArray): String {
        val ipHeaderSize = getIpHeaderSize(packet)
        return ipToString(packet, SOURCE_IP_OFFSET) + ":" + portToString(packet, ipHeaderSize + SOURCE_PORT_OFFSET)
    }

    fun getDestinationPort(packet: ByteArray): Short {
        val ipHeaderSize = getIpHeaderSize(packet)
        return getWordValue(packet, ipHeaderSize + DEST_PORT_OFFSET)
    }

    fun parseIpPort(ipPort: String): Pair<String, Short> {
        val data = ipPort.split(":").toTypedArray()
        return Pair(data[0], data[1].toInt().toShort())
    }

    fun ipToInt(ipAddress: String): Int {
        val ipBytes = ipToByteArray(ipAddress)
        var result = 0
        for (i in ipBytes.indices) {
            result += ipBytes[i].toInt() shl 24 - 8 * i
        }
        return result
    }

    fun intIpToString(ipAddress: Int): String {
        return String.format("%d.%d.%d.%d",
                ipAddress ushr 24 and 0xFF,
                ipAddress ushr 16 and 0xFF,
                ipAddress ushr 8 and 0xFF,
                ipAddress and 0xFF)
    }

    fun firstMissingOrNext(set: NavigableSet<Int>): Int {
        var fullSet = set
        var first = fullSet.first()!!
        var last = fullSet.last()!!
        if (fullSet.size == last - first + 1) {
            return last + 1
        }
        while (true) {
            val middle = first + last ushr 1
            val sub = fullSet.headSet(middle, false)
            if (sub.size < middle - first) {
                fullSet = sub
                last = sub.last()!!
            } else {
                fullSet = fullSet.tailSet(middle, true)
                first = fullSet.first()
                if (first != middle) {
                    return middle
                }
            }
        }
    }
}