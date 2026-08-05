package wanjie.quicklook.data

import androidx.compose.runtime.Immutable

/**
 * 压缩包内单个条目（文件或目录）。
 * [path] 是压缩包内完整路径，目录以 "/" 结尾。
 */
@Immutable
data class ArchiveEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val lastModified: Long,
    val encrypted: Boolean = false,
) {
    /** 压缩率百分比，目录返回 0 */
    val compressionRatio: Int
        get() = if (size <= 0 || compressedSize <= 0) 0
        else ((1.0 - compressedSize.toDouble() / size) * 100).toInt().coerceIn(0, 99)
}

/**
 * 压缩包整体信息。
 */
@Immutable
data class ArchiveInfo(
    val filePath: String,
    val fileName: String,
    val isEncrypted: Boolean,
    val totalEntries: Int,
    val totalUncompressedSize: Long,
    val totalCompressedSize: Long,
)

/**
 * 虚拟路径面包屑段。
 */
@Immutable
data class ArchiveCrumb(
    val name: String,
    /** 压缩包内路径，根目录为 "" */
    val innerPath: String,
)
