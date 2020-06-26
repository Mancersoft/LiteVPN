package com.mancersoft.litevpnserver.transport

class Packet {
    var source: Any? = null
    var destination: Any? = null
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