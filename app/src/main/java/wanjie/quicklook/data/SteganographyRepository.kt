package wanjie.quicklook.data

import java.io.File

/**
 * 文本文件级零宽度字符隐写读写。
 *
 * 利用 [ZeroWidthCodec] 在文本文件中嵌入/提取不可见数据：
 * - 写入：读取原文件可见文本 → 追加隐写载荷 → 覆盖写回。
 * - 读取：读出文件全文 → 提取隐写载荷。
 * 仅适用于可按 UTF-8 解码的文本文件（txt/md/log/json 等）。
 */
object SteganographyRepository {

    private val TEXT_EXTS = setOf(
        "txt", "md", "log", "json", "xml", "html", "css", "csv", "kt", "java", "py",
    )

    /** 判断文件是否适合隐写（文本类型 + 大小上限 1MB，避免内存爆掉）。 */
    fun canSteganograph(file: File): Boolean {
        if (file.isDirectory || !file.canRead()) return false
        if (file.length() > 1L * 1024 * 1024) return false
        return file.extension.lowercase() in TEXT_EXTS
    }

    /** 读取文本文件全文（UTF-8）。失败返回 null。 */
    fun readText(file: File): String? = runCatching {
        if (!file.canRead()) return null
        file.readText(Charsets.UTF_8)
    }.getOrNull()

    /** 覆盖写入文本文件（UTF-8）。失败返回 false。 */
    fun writeText(file: File, text: String): Boolean = runCatching {
        file.writeText(text, Charsets.UTF_8)
        true
    }.getOrDefault(false)

    /**
     * 向文件写入隐写数据：保留原可见文本，追加零宽度编码的 [payload]。
     * 若文件已含旧隐写数据，先剥离再写入新的。
     * @return 成功与否
     */
    fun embed(file: File, payload: String): Boolean {
        val raw = readText(file) ?: return false
        val clean = ZeroWidthCodec.strip(raw) // 去除旧的隐写数据
        val merged = ZeroWidthCodec.embed(clean, payload)
        return writeText(file, merged)
    }

    /** 从文件提取隐写数据。不存在则返回 null。 */
    fun extract(file: File): String? {
        val raw = readText(file) ?: return null
        return ZeroWidthCodec.extract(raw)
    }

    /** 移除文件中所有隐写数据，恢复纯净可见文本。 */
    fun strip(file: File): Boolean {
        val raw = readText(file) ?: return false
        if (!ZeroWidthCodec.hasHidden(raw)) return true
        return writeText(file, ZeroWidthCodec.strip(raw))
    }

    /** 判断文件是否含有隐写数据。 */
    fun hasHidden(file: File): Boolean {
        val raw = readText(file) ?: return false
        return ZeroWidthCodec.hasHidden(raw)
    }
}
