package com.tbib.tbib_gms_google_play

class PluginResult {
    var isSuccess: Boolean
    var exception: String? = null
    var data: Any? = null
        private set

    fun setData(data: Any?): PluginResult {
        this.data = data
        return this
    }

    fun toMap(): Map<String, Any?> {
        val map: MutableMap<String, Any?> = HashMap()
        map["success"] = isSuccess
        map["data"] = data
        map["exception"] = exception
        return map
    }

    constructor() {
        isSuccess = true
    }

    constructor(exception: String?) {
        isSuccess = false
        this.exception = exception
    }
}