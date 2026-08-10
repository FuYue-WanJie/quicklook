package wanjie.quicklook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import wanjie.quicklook.data.SettingsStore
import wanjie.quicklook.ui.QuickLookRoot
import wanjie.quicklook.ui.theme.QuickLookTheme
import wanjie.quicklook.ui.theme.ThemeMode
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    /** 当由外部 VIEW Intent 启动时，包含要直接打开的目标。 */
    private var externalView: ExternalView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 初始化 Shizuku
        Shizuku.addBinderReceivedListenerSticky(object : rikka.shizuku.Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {}
        })

        externalView = parseViewIntent(intent)

        val settings = SettingsStore(applicationContext)

        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.System)
            val dynamic by settings.dynamicColor.collectAsState(initial = true)
            val initialView = remember { externalView }

            QuickLookTheme(themeMode = themeMode, dynamicColor = dynamic) {
                QuickLookRoot(initialExternalView = initialView)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 已运行时收到新 VIEW Intent：简单重启 Activity 以重新走 onCreate 流程
        val parsed = parseViewIntent(intent)
        if (parsed != null) {
            recreate()
        }
    }

    /**
     * 解析 ACTION_VIEW Intent，提取 URI + 名称 + mimeType 判断的类别。
     * 仅处理 content 和 file scheme。
     */
    private fun parseViewIntent(intent: Intent?): ExternalView? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri: Uri = intent.data ?: return null
        val scheme = uri.scheme ?: return null
        if (scheme != "content" && scheme != "file") return null

        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "未知"
        val mime = intent.type ?: contentResolver.getType(uri) ?: ""

        val category = when {
            mime.startsWith("image/") -> ExternalCategory.IMAGE
            mime.startsWith("video/") -> ExternalCategory.VIDEO
            mime.startsWith("audio/") -> ExternalCategory.AUDIO
            mime.startsWith("text/") -> ExternalCategory.TEXT
            else -> {
                // 按扩展名兜底
                val ext = name.substringAfterLast('.', "").lowercase()
                when (ext) {
                    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> ExternalCategory.IMAGE
                    "mp4", "mkv", "webm", "avi", "mov", "flv", "3gp" -> ExternalCategory.VIDEO
                    "mp3", "wav", "flac", "aac", "ogg", "m4a", "opus" -> ExternalCategory.AUDIO
                    "txt", "md", "log", "json", "xml", "yml", "yaml" -> ExternalCategory.TEXT
                    else -> return null
                }
            }
        }
        return ExternalView(uri = uri.toString(), name = name, category = category)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        } catch (e: Exception) {
            null
        }
    }
}

enum class ExternalCategory { IMAGE, VIDEO, AUDIO, TEXT }

data class ExternalView(
    val uri: String,
    val name: String,
    val category: ExternalCategory,
)
