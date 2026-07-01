package wanjie.quicklook.data

/**
 * 零宽度字符隐写编解码器。
 *
 * 原理（即所利用的“漏洞”）：文本渲染器不会显示零宽度字符，因此可以把
 * 任意二进制数据编码成一串零宽度字符，插入普通文本后视觉上完全不可见，
 * 形成一条隐蔽的数据读写通道。
 *
 * 编码方案：用 4 个零宽度字符表示 2 bit，即 base-4：
 *   U+200B (ZWSP)   → 00
 *   U+200C (ZWNJ)   → 01
 *   U+200D (ZWJ)    → 10
 *   U+FEFF (ZWNBSP) → 11
 * 每个字节 = 4 个零宽度字符。前后用固定标记序列包裹，便于定位与提取。
 */
object ZeroWidthCodec {

    // 2 bit → 零宽度字符
    private val BITS_TO_CHAR = charArrayOf(
        '\u200B', // 00
        '\u200C', // 01
        '\u200D', // 10
        '\uFEFF', // 11
    )
    // 零宽度字符 → 2 bit
    private val CHAR_TO_BITS: Map<Char, Int> = BITS_TO_CHAR.withIndex().associate { it.value to it.index }

    // 起止标记：ZWNJ+ZWJ+ZWSP，三个字符的组合在正常文本里几乎不会连续出现
    private val START_MARK = "\u200C\u200D\u200B"
    private val END_MARK = "\u200B\u200D\u200C"

    /** 将任意字符串编码为零宽度字符序列（不含标记）。 */
    fun encode(payload: String): String {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size * 4)
        for (b in bytes) {
            // 拆成 4 组 2 bit（高位在前）
            sb.append(BITS_TO_CHAR[(b.toInt() ushr 6) and 0b11])
            sb.append(BITS_TO_CHAR[(b.toInt() ushr 4) and 0b11])
            sb.append(BITS_TO_CHAR[(b.toInt() ushr 2) and 0b11])
            sb.append(BITS_TO_CHAR[b.toInt() and 0b11])
        }
        return sb.toString()
    }

    /** 将零宽度字符序列（不含标记）解码回原字符串。 */
    fun decode(encoded: String): String {
        val bits = ArrayList<Int>(encoded.length)
        for (c in encoded) {
            val v = CHAR_TO_BITS[c] ?: continue // 跳过非零宽度字符
            bits.add(v)
        }
        // 每 4 组 2 bit 合成一个字节
        val bytes = ByteArray(bits.size / 4)
        var i = 0
        var out = 0
        while (i + 3 < bits.size) {
            val b = (bits[i] shl 6) or (bits[i + 1] shl 4) or (bits[i + 2] shl 2) or bits[i + 3]
            bytes[out++] = b.toByte()
            i += 4
        }
        return String(bytes, 0, out, Charsets.UTF_8)
    }

    /**
     * 把 [payload] 隐写到 [carrier] 文本中：可见文本末尾追加
     * 标记 + 编码 + 标记，视觉上无任何变化。
     * @return 含隐写数据的新文本
     */
    fun embed(carrier: String, payload: String): String {
        return carrier + START_MARK + encode(payload) + END_MARK
    }

    /**
     * 从 [text] 中提取隐写数据。若不存在则返回 null。
     * 支持文本中含多段隐写时提取第一段。
     */
    fun extract(text: String): String? {
        val start = text.indexOf(START_MARK)
        if (start < 0) return null
        val payloadStart = start + START_MARK.length
        val end = text.indexOf(END_MARK, payloadStart)
        if (end < 0) return null
        return decode(text.substring(payloadStart, end))
    }

    /** 移除文本中所有隐写数据（标记 + 载荷），返回纯净可见文本。 */
    fun strip(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text.startsWith(START_MARK, i)) {
                val payloadStart = i + START_MARK.length
                val end = text.indexOf(END_MARK, payloadStart)
                i = if (end < 0) text.length else end + END_MARK.length
            } else {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }

    /** 判断文本是否包含隐写数据。 */
    fun hasHidden(text: String): Boolean =
        text.contains(START_MARK) && text.indexOf(END_MARK, text.indexOf(START_MARK) + START_MARK.length) >= 0
}
