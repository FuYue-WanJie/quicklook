package wanjie.quicklook.data

import android.os.Build
import java.io.File

/**
 * Android/data 访问工具。
 *
 * 背景：Android 11（API 30）起，即便拥有「所有文件访问权限」，
 * 通过 java.io.File 直接访问 `/Android/data`、`/Android/obb` 仍会被
 * FUSE/Scoped Storage 层拦截，listFiles()/canRead() 表现为无权限。
 *
 * 绕过手段：在路径段 `data` 前插入一个零宽度空格（U+200B），使路径
 * 字符串不再精确匹配系统拦截的 `/Android/data` 模式，从而尝试继续读取。
 * 该手段是否生效取决于设备 ROM 与系统版本，因此失败时需如实告知用户。
 *
 * 一旦对某个路径应用了绕过，其所有子路径都会自然带上零宽度空格，
 * 后续文件操作无需再次手动绕过。
 */
object AndroidDataAccess {

    /** 零宽度空格 U+200B。 */
    private const val ZWSP = '\u200B'

    private const val NORMAL_MARKER = "/Android/data"
    private const val BYPASSED_MARKER = "/Android/\u200Bdata"

    // 仅匹配完整路径段，避免误伤 /Android/database 等
    private val NORMAL_REGEX = Regex("/Android/data(?=/|\$)")
    private val BYPASSED_REGEX = Regex("/Android/\u200Bdata(?=/|\$)")
    private val STRICT_UNDER_NORMAL = Regex("/Android/data/")
    private val STRICT_UNDER_BYPASSED = Regex("/Android/\u200Bdata/")

    /** 是否 Android 11（API 30）及以上。 */
    fun isAndroid11Plus(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** 路径位于 Android/data 之下或即该目录本身，且为「未绕过」的常规形式。 */
    fun isNormalAndroidData(path: String): Boolean = NORMAL_REGEX.containsMatchIn(path)

    /** 路径已应用零宽度空格绕过。 */
    fun isBypassedAndroidData(path: String): Boolean = BYPASSED_REGEX.containsMatchIn(path)

    /** 路径位于 Android/data 之下或即该目录本身（常规或已绕过形式均算）。 */
    fun isUnderAndroidData(path: String): Boolean =
        isNormalAndroidData(path) || isBypassedAndroidData(path)

    /**
     * 路径「严格位于」Android/data 之内（即 data 目录下的子项），
     * 不含 data 目录自身。用于触发「操作 data 下的文件/文件夹」安全警告。
     */
    fun isStrictlyUnderAndroidData(path: String): Boolean =
        STRICT_UNDER_NORMAL.containsMatchIn(path) || STRICT_UNDER_BYPASSED.containsMatchIn(path)

    /** 是否已对路径应用绕过。 */
    fun hasBypass(path: String): Boolean = isBypassedAndroidData(path)

    /**
     * 在 `Android/data` 的 `data` 前插入零宽度空格，返回绕过形式路径。
     * 若已绕过则原样返回。仅替换首个匹配段。
     */
    fun bypass(path: String): String =
        if (hasBypass(path)) path else NORMAL_REGEX.replace(path) { BYPASSED_MARKER }

    /** [bypass] 的 File 版本。 */
    fun bypass(file: File): File = File(bypass(file.absolutePath))
}
