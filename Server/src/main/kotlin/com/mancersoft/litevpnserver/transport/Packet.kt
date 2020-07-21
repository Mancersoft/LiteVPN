package com.mancersoft.litevpnserver.transport

class Packet {
    var source: Any? = null
    val sourceId: String?
        get() = source?.toString()
    var destination: Any? = null
    val destId: String?
        get() = destination?.toString()
    lateinit var data: ByteArray
    var length = 0

    constructor()
    constructor(packet: Packet) {
        source = packet.source
        destination = packet.destination
        data = ByteArray(packet.length)
        System.arraycopy(packet.data, 0, data, 0, packet.length)
        length = packet.length
    }

}