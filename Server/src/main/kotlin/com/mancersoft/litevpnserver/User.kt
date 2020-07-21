package com.mancersoft.litevpnserver

class User constructor(var connection: Any, var groupId: String, var ip: String) {
    val id: String?
        get() = connection.toString()
}