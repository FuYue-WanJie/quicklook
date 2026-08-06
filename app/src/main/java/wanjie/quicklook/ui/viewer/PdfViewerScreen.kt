package wanjie.quicklook.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wanjie.quicklook.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 查看器：基于系统内置 PdfRenderer 渲染页面为位图。
 * - 支持绝对路径 / file:// / content:// 三种来源
 * - 页面按屏幕宽度渲染、纵向滚动
 * - 渲染串行化（PdfRenderer 非线程安全），仅渲染可见页
 */
@Composable
fun PdfViewerScreen(
    path: String,
    displayName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    val singleThread = remember { Dispatchers.IO.limitedParallelism(1) }

    BackHandler(onBack = onBack)

    LaunchedEffect(path) {
        renderer = withContext(Dispatchers.IO) {
            runCatching { openRenderer(context, path) }.getOrElse {
                error = it.message ?: "PDF 打开失败"
                null
            }
        }
        pageCount = renderer?.pageCount ?: 0
    }
    DisposableEffect(Unit) {
        onDispose {
            // PdfRenderer.close() 内部会关闭 ParcelFileDescriptor
            renderer?.close()
        }
    }

    val rendererForPages = renderer
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFDDDDDD))) {
        PdfTopBar(title = displayName, onBack = onBack)

        when {
            error != null -> Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFDDDDDD)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            rendererForPages == null -> Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFDDDDDD)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().background(Color(0xFFDDDDDD)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pageCount) { index ->
                    PdfPageItem(
                        renderer = rendererForPages,
                        pageIndex = index,
                        renderDispatcher = singleThread,
                    )
                }
            }
        }
    }
}

/** 顶部栏：返回 + 文件名 + 总页数 */
@Composable
private fun PdfTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 单页渲染：按屏幕宽度渲染，进入组合时才加载，离开时回收位图。 */
@Composable
private fun PdfPageItem(
    renderer: PdfRenderer,
    pageIndex: Int,
    renderDispatcher: CoroutineDispatcher,
) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val targetWidth = remember(screenWidthDp, density) {
        with(density) { screenWidthDp.dp.roundToPx() }
    }
    var bitmap by remember(pageIndex) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(pageIndex, targetWidth) {
        bitmap = withContext(renderDispatcher) {
            runCatching {
                val page = renderer.openPage(pageIndex)
                try {
                    val height = (page.height * targetWidth / page.width.toFloat()).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp.asImageBitmap()
                } finally {
                    page.close()
                }
            }.getOrNull()
        }
    }
    DisposableEffect(pageIndex) {
        onDispose {
            bitmap?.asAndroidBitmap()?.recycle()
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/** 打开 PdfRenderer，兼容绝对路径 / file:// / content:// */
private fun openRenderer(context: android.content.Context, path: String): PdfRenderer {
    val fd = when {
        path.startsWith("content://") ->
            context.contentResolver.openFileDescriptor(android.net.Uri.parse(path), "r")
        path.startsWith("file://") ->
            ParcelFileDescriptor.open(File(android.net.Uri.parse(path).path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        else ->
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
    } ?: throw IllegalStateException("无法打开文件")
    return PdfRenderer(fd)
}
