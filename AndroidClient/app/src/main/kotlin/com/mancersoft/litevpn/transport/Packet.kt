package com.mancersoft.litevpn.transport

class Packet {

    lateinit var data: ByteArray
    var length = 0

    constructor()

    constructor(packet: Packet) {
        data = ByteArray(packet.length)
        System.arraycopy(packet.data, 0, data, 0, packet.length)
        length = packet.length
    }

}