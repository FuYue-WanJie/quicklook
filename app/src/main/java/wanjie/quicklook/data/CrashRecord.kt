package wanjie.quicklook.data

import org.json.JSONObject

data class CrashRecord(
    val time: Long,
    val threadName: String,
    val type: String,
    val message: String,
    val stackTrace: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("time", time)
        put("thread", threadName)
        put("type", type)
        put("message", message)
        put("stack", stackTrace)
    }

    companion object {
        fun fromJson(o: JSONObject): CrashRecord = CrashRecord(
            time = o.optLong("time", 0L),
            threadName = o.optString("thread", "unknown"),
            type = o.optString("type", "UnknownError"),
            message = o.optString("message", ""),
            stackTrace = o.optString("stack", ""),
        )
    }
}
