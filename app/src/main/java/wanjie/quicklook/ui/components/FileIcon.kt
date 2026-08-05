package wanjie.quicklook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import wanjie.quicklook.data.FileCategory
import wanjie.quicklook.data.FileUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * APK 图标内存缓存：path → ImageBitmap（null 表示已尝试但无图标）。
 * 避免列表滚动时反复调用 PackageManager。
 */
private object ApkIconCache {
    private val cache = mutableMapOf<String, ImageBitmap?>()
    private val tried = mutableSetOf<String>()

    fun get(path: String): ImageBitmap? = cache[path]
    fun isTried(path: String): Boolean = path in tried

    suspend fun load(context: android.content.Context, path: String, targetPx: Int): ImageBitmap? {
        if (path in cache) return cache[path]
        tried.add(path)
        // Drawable 非线程安全，加载与转 Bitmap 都在 IO 线程完成
        val bitmap = withContext(Dispatchers.IO) {
            val drawable = FileUtils.loadApkIcon(context, path)
            drawable?.toBitmap(targetPx, targetPx)?.asImageBitmap()
        }
        cache[path] = bitmap
        return bitmap
    }

    fun clear() { cache.clear(); tried.clear() }
}

/**
 * 圆角方形图标容器。
 * - IMAGE：直接显示图片缩略图
 * - VIDEO：直接加载视频文件第 1 秒帧（Coil VideoFrameDecoder），叠加播放按钮；失败回退图标
 * - APP：异步加载 APK 内嵌图标，成功显示应用图标，失败回退 Android 图标
 * - 其他类型：Material 图标 + tonal 容器配色
 */
@Composable
fun FileIcon(
    category: FileCategory,
    name: String,
    size: Int = 40,
    path: String? = null,
    modifier: Modifier = Modifier,
) {
    val (container, onContainer) = categoryColors(category)
    val vector = vectorFor(category)
    val context = LocalContext.current
    val density = LocalDensity.current

    // APK 图标异步加载（带内存缓存，目标尺寸按 density 超采样保证清晰）
    var apkIcon by remember(path) {
        mutableStateOf(if (path != null) ApkIconCache.get(path) else null)
    }
    if (category == FileCategory.APP && path != null && apkIcon == null && !ApkIconCache.isTried(path)) {
        LaunchedEffect(path, size) {
            val targetPx = with(density) { (size * 2).dp.toPx().roundToInt() }
            apkIcon = ApkIconCache.load(context, path, targetPx)
        }
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .background(container, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            category == FileCategory.IMAGE && path != null -> AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)),
            )
            category == FileCategory.VIDEO && path != null -> Box(
                modifier = Modifier.size(size.dp),
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)),
                )
                // 叠加半透明播放按钮
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size((size * 0.5).dp)
                        .background(
                            Color.Black.copy(alpha = 0.45f),
                            RoundedCornerShape(50),
                        )
                        .padding(2.dp),
                )
            }
            category == FileCategory.APP && apkIcon != null -> Image(
                bitmap = apkIcon!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)),
            )
            vector != null -> Icon(
                imageVector = vector,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size((size * 0.55).dp),
            )
            else -> Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelLarge,
                color = onContainer,
            )
        }
    }
}

private fun vectorFor(category: FileCategory): ImageVector = when (category) {
    FileCategory.FOLDER -> Icons.Rounded.Folder
    FileCategory.IMAGE -> Icons.Rounded.Image
    FileCategory.VIDEO -> Icons.Rounded.VideoFile
    FileCategory.AUDIO -> Icons.Rounded.MusicNote
    FileCategory.PDF -> Icons.Rounded.PictureAsPdf
    FileCategory.DOCUMENT -> Icons.Rounded.Description
    FileCategory.SPREADSHEET -> Icons.Rounded.Calculate
    FileCategory.PRESENTATION -> Icons.Rounded.Slideshow
    FileCategory.TEXT -> Icons.Rounded.TextSnippet
    FileCategory.CODE -> Icons.Rounded.Code
    FileCategory.ARCHIVE -> Icons.Rounded.Archive
    FileCategory.APP -> Icons.Rounded.Android
    FileCategory.EBOOK -> Icons.Rounded.MenuBook
    FileCategory.FONT -> Icons.Rounded.FontDownload
    FileCategory.OTHER -> Icons.Rounded.InsertDriveFile
}

/** MD3 容器色调族：每个分类对应一个 tonal container 配色对。 */
private enum class CatTone { PRIMARY, SECONDARY, TERTIARY, ERROR, SURFACE }

@Composable
private fun CatTone.colors(): Pair<Color, Color> = when (this) {
    CatTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    CatTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    CatTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    CatTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    CatTone.SURFACE -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
}

private fun toneFor(category: FileCategory): CatTone = when (category) {
    FileCategory.FOLDER, FileCategory.DOCUMENT, FileCategory.APP -> CatTone.PRIMARY
    FileCategory.VIDEO, FileCategory.SPREADSHEET, FileCategory.CODE, FileCategory.FONT -> CatTone.SECONDARY
    FileCategory.IMAGE, FileCategory.AUDIO, FileCategory.PRESENTATION, FileCategory.EBOOK -> CatTone.TERTIARY
    FileCategory.PDF -> CatTone.ERROR
    FileCategory.TEXT, FileCategory.ARCHIVE, FileCategory.OTHER -> CatTone.SURFACE
}

@Composable
private fun categoryColors(category: FileCategory): Pair<Color, Color> = toneFor(category).colors()
