package wanjie.quicklook.data

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import wanjie.quicklook.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 全局未捕获异常处理器：捕获崩溃后写入历史记录，并启动崩溃展示页。
 * 崩溃页自身崩溃时不再重复拉起，直接交给系统默认处理器终止进程。
 */
object CrashTracker {

    private const val TAG = "CrashTracker"
    private const val PREFS = "crash_tracker"
    private const val KEY_CRASHES = "crashes"
    private const val MAX_CRASHES = 20

    @Volatile
    private var installed = false

    @Volatile
    private var showingCrash = false

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(app: android.app.Application) {
        if (installed) return
        installed = true
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(app, thread, throwable)
        }
    }

    fun resetShowing() {
        showingCrash = false
    }

    private fun handleCrash(context: Context, thread: Thread, throwable: Throwable) {
        if (showingCrash) {
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }
        showingCrash = true
        try {
            val record = buildRecord(thread, throwable)
            saveRecord(context, record)

            val intent = Intent(context, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "handleCrash failed", t)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildRecord(thread: Thread, throwable: Throwable): CrashRecord {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return CrashRecord(
            time = System.currentTimeMillis(),
            threadName = thread.name,
            type = throwable.javaClass.simpleName,
            message = throwable.message ?: "",
            stackTrace = sw.toString(),
        )
    }

    private fun saveRecord(context: Context, record: CrashRecord) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CRASHES, null)
        val arr = if (json.isNullOrEmpty()) JSONArray()
        else runCatching { JSONArray(json) }.getOrDefault(JSONArray())
        arr.put(record.toJson())
        while (arr.length() > MAX_CRASHES) arr.remove(0)
        prefs.edit().putString(KEY_CRASHES, arr.toString()).commit()
    }

    fun loadRecords(context: Context): List<CrashRecord> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CRASHES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { i -> CrashRecord.fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }
}
