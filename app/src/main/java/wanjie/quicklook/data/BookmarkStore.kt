package wanjie.quicklook.data

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bookmarkDataStore by preferencesDataStore(name = "bookmarks")

/**
 * 书签条目：文件路径书签或 SAF 授权目录书签。
 * [id] 为去重主键：文件书签用绝对路径，SAF 书签用 treeUri 字符串。
 */
@Immutable
data class Bookmark(
    val id: String,
    val name: String,
    val path: String,        // 文件书签的绝对路径；SAF 书签为 ""
    val treeUri: String,     // SAF 书签的 tree URI 字符串；文件书签为 ""
    val isSaf: Boolean,
)

/**
 * 书签存储：DataStore 持久化，分文件书签与 SAF 书签两类。
 * 首次使用时播种默认书签（下载 / DCIM / 应用文件）。
 *
 * 存储格式：stringSet，每条以 `name||key` 编码，`||` 在文件名中极少出现。
 */
class BookmarkStore(private val context: Context) {

    private object Keys {
        val FILE = stringSetPreferencesKey("file_bookmarks")      // "name||absPath"
        val SAF = stringSetPreferencesKey("saf_bookmarks")        // "name||treeUri"
        val SEEDED = stringSetPreferencesKey("defaults_seeded")   // 非空表示已播种
    }

    private val appFilesDir = (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath

    /** 默认书签：下载、DCIM、应用文件 */
    private val defaultFileBookmarks: List<Pair<String, String>> = buildList {
        add("下载" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        add("DCIM" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath)
        add("应用文件" to appFilesDir)
    }

    val bookmarks: Flow<List<Bookmark>> = context.bookmarkDataStore.data.map { p ->
        val seeded = p[Keys.SEEDED] != null
        val fileSet = p[Keys.FILE] ?: emptySet()
        val safSet = p[Keys.SAF] ?: emptySet()
        val list = mutableListOf<Bookmark>()
        fileSet.mapNotNullTo(list) { decode(it, isSaf = false) }
        safSet.mapNotNullTo(list) { decode(it, isSaf = true) }
        if (!seeded) list // 首次未播种时返回空，由 [seedDefaultsIfEmpty] 异步补
        else list
    }

    /** 首次使用时播种默认书签。在 Application 创建后调用一次。 */
    suspend fun seedDefaultsIfEmpty() {
        context.bookmarkDataStore.edit { p ->
            if (p[Keys.SEEDED] != null) return@edit
            val encoded = defaultFileBookmarks
                .filter { (_, path) -> java.io.File(path).exists() }
                .map { (name, path) -> encode(name, path) }
                .toSet()
            p[Keys.FILE] = encoded
            p[Keys.SEEDED] = setOf("1")
        }
    }

    suspend fun addFileBookmark(name: String, path: String) {
        context.bookmarkDataStore.edit { p ->
            val cur = p[Keys.FILE]?.toMutableSet() ?: mutableSetOf()
            cur.add(encode(name, path))
            p[Keys.FILE] = cur
        }
    }

    suspend fun addSafBookmark(name: String, treeUri: String) {
        context.bookmarkDataStore.edit { p ->
            val cur = p[Keys.SAF]?.toMutableSet() ?: mutableSetOf()
            cur.add(encode(name, treeUri))
            p[Keys.SAF] = cur
        }
    }

    suspend fun remove(id: String) {
        context.bookmarkDataStore.edit { p ->
            val fileCur = (p[Keys.FILE] ?: emptySet()).filterNot { decode(it, false)?.id == id }.toSet()
            val safCur = (p[Keys.SAF] ?: emptySet()).filterNot { decode(it, true)?.id == id }.toSet()
            p[Keys.FILE] = fileCur
            p[Keys.SAF] = safCur
        }
    }

    private fun encode(name: String, key: String): String = "$name||$key"
    private fun decode(entry: String, isSaf: Boolean): Bookmark? {
        val idx = entry.indexOf("||")
        if (idx < 0) return null
        val name = entry.substring(0, idx)
        val key = entry.substring(idx + 2)
        if (name.isEmpty() || key.isEmpty()) return null
        return if (isSaf) Bookmark(key, name, "", key, true)
        else Bookmark(key, name, key, "", false)
    }
}
