package wanjie.quicklook.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.core.net.toUri

class ShizukuManager(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuManager"
        private const val SYSTEM_DIR = "/sdcard/"
        val SENSITIVE_PATH_PREFIXES = setOf(
            "/Android/",
            "/data/",
            "/system/",
            "/vendor/",
            "/etc/",
            "/root/",
            "/sbin/",
            "/proc/",
            "/sys/",
            "/dev/",
        )
    }

    fun isSensitivePath(path: String): Boolean {
        val normalized = path.removePrefix("/").removeSuffix("/")
        return when {
            normalized.isEmpty() -> true
            normalized == "sdcard" -> false
            normalized.startsWith("sdcard") -> false
            else -> SENSITIVE_PATH_PREFIXES.any { normalized.startsWith(it.removePrefix("/").removeSuffix("/")) }
        }
    }

    val isAvailable: Boolean get() = try {
        Shizuku.getVersion() > 0
    } catch (_: Exception) { false }
    val isGranted: Boolean get() = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun requestPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Shizuku.requestPermission(1)
            }
        } catch (_: Exception) {}
    }

    fun canAccess(path: String): Boolean = isAvailable && isGranted

    /**
     * 通过 Shizuku 执行命令并返回输出。
     * 返回 null 表示执行失败。
     */
    fun executeCommand(command: String): String? = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append('\n')
        }
        process.waitFor()
        output.toString().trim()
    }.getOrNull()

    fun listDirectoryViaShizuku(dirPath: String, config: SortConfig, showHidden: Boolean): List<FileItem>? = runCatching {
        val escapedPath = dirPath.replace(" ", "\\ ")
        val output = executeCommand("ls -la \"$escapedPath\"") ?: return@runCatching null
        val lines = output.split('\n').filter { it.isNotBlank() }
        val items = parseLongList(lines, dirPath)
        val filtered = if (showHidden) items else items.filterNot { it.name.startsWith(".") }
        FileUtils.applySort(filtered, config)
    }.getOrElse {
        Log.e(TAG, "listDirectoryViaShizuku failed: $it")
        null
    }

    private fun parseLongList(lines: List<String>, dirPath: String): List<FileItem> {
        val items = mutableListOf<FileItem>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("total")) continue
            val parts = line.split("\\s+".toRegex())
            if (parts.size < 9) continue
            val isDir = parts[0].startsWith("d")
            val name = parts[8]
            if (name.isBlank()) continue
            val path = "$dirPath/$name"
            val mime = if (isDir) "inode/directory" else FileUtils.mimeTypeFor(name)
            val size = if (!isDir) parseSize(parts[4]) else 0L
            val modTime = parseModTime(parts)
            items.add(
                FileItem(
                    uri = File(path).toUri(),
                    name = name,
                    path = path,
                    isDirectory = isDir,
                    size = size,
                    lastModified = modTime,
                    mimeType = mime,
                    category = FileUtils.categoryFor(name, isDir, mime),
                )
            )
        }
        return items
    }

    private fun parseSize(sizeStr: String): Long {
        return runCatching { sizeStr.toLong() }.getOrNull() ?: 0L
    }

    private fun parseModTime(parts: List<String>): Long {
        return try {
            val month = parts[1]
            val day = parts[2].toLong()
            val yearOrTime = parts[3]
            val cal = java.util.Calendar.getInstance()
            val monthIdx = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec").indexOf(month)
            if (monthIdx >= 0) {
                cal.set(java.util.Calendar.MONTH, monthIdx)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day.toInt())
                if (yearOrTime.contains(":")) {
                    val (h, m) = yearOrTime.split(":")
                    cal.set(java.util.Calendar.HOUR_OF_DAY, h.toInt())
                    cal.set(java.util.Calendar.MINUTE, m.toInt())
                    cal.set(java.util.Calendar.YEAR, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
                } else {
                    cal.set(java.util.Calendar.YEAR, yearOrTime.toInt())
                }
            }
            cal.timeInMillis
        } catch (e: Exception) { 0L }
    }

    fun exists(path: String): Boolean = runCatching {
        val escapedPath = path.replace(" ", "\\ ")
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "test -e \"$escapedPath\""))
        process.waitFor() == 0
    }.getOrDefault(false)

    fun isDirectory(path: String): Boolean = runCatching {
        val escapedPath = path.replace(" ", "\\ ")
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "test -d \"$escapedPath\""))
        process.waitFor() == 0
    }.getOrDefault(false)

    fun delete(path: String): Boolean = runCatching {
        val escapedPath = path.replace(" ", "\\ ")
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "rm -rf \"$escapedPath\""))
        process.waitFor() == 0
    }.getOrElse { Log.e(TAG, "delete failed: $it"); false }

    fun createDirectory(path: String): Boolean = runCatching {
        val escapedPath = path.replace(" ", "\\ ")
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "mkdir -p \"$escapedPath\""))
        process.waitFor() == 0
    }.getOrDefault(false)

    fun createFile(path: String): Boolean = runCatching {
        val escapedPath = path.replace(" ", "\\ ")
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "touch \"$escapedPath\""))
        process.waitFor() == 0
    }.getOrDefault(false)

    fun rename(oldPath: String, newName: String): Boolean = runCatching {
        val escapedOld = oldPath.replace(" ", "\\ ")
        val escapedNew = newName.replace(" ", "\\ ")
        val parent = File(oldPath).parent?.replace(" ", "\\ ") ?: return@runCatching false
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "mv \"$escapedOld\" \"$parent/$escapedNew\""))
        process.waitFor() == 0
    }.getOrDefault(false)

    // ---- APK 安装相关 ----

    /**
     * 通过 Shizuku 静默安装 APK。
     * 返回 true 表示安装成功，false 表示失败。
     * @param apkPath APK 文件的绝对路径
     */
    fun installApk(apkPath: String): Boolean = runCatching {
        val escapedPath = apkPath.replace(" ", "\\ ")
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "pm install -r \"$escapedPath\""))
        val exitCode = process.waitFor()
        exitCode == 0
    }.getOrElse {
        Log.e(TAG, "installApk failed: $it")
        false
    }

    /**
     * 通过 Shizuku 卸载应用。
     * @param packageName 包名
     */
    fun uninstallApp(packageName: String): Boolean = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "pm uninstall \"$packageName\""))
        process.waitFor() == 0
    }.getOrElse {
        Log.e(TAG, "uninstallApp failed: $it")
        false
    }

    /**
     * 查询应用是否已安装。
     */
    fun isAppInstalled(packageName: String): Boolean = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "pm list packages | grep \"$packageName\""))
        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
        process.waitFor() == 0 && output.contains(packageName)
    }.getOrDefault(false)

    /**
     * 从 APK 路径解析包名。
     */
    fun getPackageFromApk(apkPath: String): String? = runCatching {
        val escapedPath = apkPath.replace(" ", "\\ ")
        val output = executeCommand("pm path \"$escapedPath\"") ?: return@runCatching null
        output.removePrefix("package:").trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * 获取已安装应用列表（包名）。
     */
    fun getInstalledPackages(): List<String> = runCatching {
        val output = executeCommand("pm list packages") ?: return@runCatching emptyList()
        output.split('\n')
            .mapNotNull { it.removePrefix("package:").trim().takeIf { s -> s.isNotEmpty() } }
            .toList()
    }.getOrDefault(emptyList())
}
