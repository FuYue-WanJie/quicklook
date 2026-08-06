package wanjie.quicklook.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.linc.amplituda.Amplituda
import java.io.File

/**
 * 音频波形数据加载器：用 Amplituda 提取振幅列表，带内存 LRU 缓存。
 *
 * - content:// URI 需先拷贝到临时文件再交给 Amplituda（它只接受 File/InputFile）
 * - 提取结果缓存为 List<Int>，按 URI 字符串为 key
 * - 缓存上限 5 首，避免内存膨胀
 *
 * 所有方法应在 IO 线程调用。
 */
object WaveformLoader {

    private const val TAG = "WaveformLoader"
    private const val MAX_CACHE = 5

    // 简单 LRU：LinkedHashMap 按 accessOrder 排列，超出容量移除最旧
    private val cache = object : LinkedHashMap<String, List<Int>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<Int>>): Boolean =
            size > MAX_CACHE
    }

    /**
     * 获取音频波形数据。优先读缓存，缓存未命中则提取。
     *
     * @param context 用于 ContentResolver
     * @param uri 音频文件的 content:// 或 file:// URI
     * @return 振幅列表，提取失败返回空列表
     */
    suspend fun load(context: Context, uri: String): List<Int> {
        synchronized(cache) {
            cache[uri]?.let { return it }
        }

        val result = extractAmplitudes(context, uri)

        synchronized(cache) {
            cache[uri] = result
        }
        return result
    }

    /** 预加载（不阻塞调用方），内部仍走 IO 线程 */
    suspend fun preload(context: Context, uri: String) {
        load(context, uri)
    }

    /** 清除指定 URI 的缓存 */
    fun evict(uri: String) {
        synchronized(cache) { cache.remove(uri) }
    }

    /** 清空全部缓存 */
    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    private suspend fun extractAmplitudes(context: Context, uriStr: String): List<Int> {
        val amplituda = Amplituda(context)
        val uri = Uri.parse(uriStr)

        // file:// 直接用路径；content:// 拷贝到临时文件
        val inputFile: File? = when (uri.scheme) {
            "file" -> File(uri.path ?: return emptyList())
            "content" -> copyToTempFile(context, uri)
            else -> null
        }

        if (inputFile == null || !inputFile.exists()) {
            Log.w(TAG, "Cannot access audio file: $uriStr")
            return emptyList()
        }

        val isTemp = uri.scheme == "content"

        return try {
            amplituda.processAudio(inputFile).get().amplitudesAsList()
        } catch (e: Exception) {
            Log.e(TAG, "Amplituda extraction failed", e)
            emptyList()
        } finally {
            if (isTemp) inputFile.delete()
        }
    }

    private fun copyToTempFile(context: Context, uri: Uri): File? {
        return try {
            val tempFile = File.createTempFile("waveform_", ".tmp", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy content URI to temp file", e)
            null
        }
    }
}
