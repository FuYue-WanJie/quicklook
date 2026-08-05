package wanjie.quicklook.ui.viewer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wanjie.quicklook.R
import wanjie.quicklook.data.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 超过该字符数不做语法高亮，避免大文件编辑卡顿 */
private const val MAX_HIGHLIGHT_CHARS = 200_000

/** 超过该字节数只读，避免内存压力 */
private const val READONLY_BYTES = 5L * 1024 * 1024

/**
 * 文本/代码编辑器（全屏 Composable）。
 * 通过 FileProvider content URI 读写文件。
 * 支持按文件类型语法高亮、行号、等宽字体；大文件只读且关闭高亮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    path: String,
    displayName: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var content by remember { mutableStateOf<String?>(null) }
    var readOnly by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            // path 可以是绝对文件路径，或 content://file:// URI（外部 Intent）
            val contentUri: Uri? = if (path.startsWith("content://") || path.startsWith("file://")) {
                Uri.parse(path)
            } else {
                FileUtils.buildContentUri(context, File(path))
            }

            val resolver = context.contentResolver
            val size = try {
                resolver.openFileDescriptor(contentUri ?: Uri.parse(path), "r")?.use { it.statSize } ?: 0L
            } catch (e: Exception) {
                0L
            }

            if (size > READONLY_BYTES) {
                readOnly = true
            }

            content = try {
                resolver.openInputStream(contentUri ?: Uri.parse(path))?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: ""
            } catch (e: Exception) {
                // fallback：直接读 file
                File(path).takeIf { it.exists() }?.readText() ?: ""
            }
            loading = false
        }
    }

    fun save() {
        scope.launch {
            saving = true
            val ok = withContext(Dispatchers.IO) {
                runCatching { File(path).writeText(content ?: "") }.isSuccess
            }
            saving = false
            if (ok) {
                snackbarHost.showSnackbar(context.getString(R.string.snack_rename_success, displayName))
            } else {
                snackbarHost.showSnackbar(context.getString(R.string.snack_rename_failed))
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(displayName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!readOnly && !loading) {
                        IconButton(onClick = { save() }, enabled = !saving) {
                            Icon(Icons.Rounded.Save, contentDescription = stringResource(R.string.dialog_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator()
                else -> CodeEditorField(
                    text = content ?: "",
                    fileName = displayName,
                    readOnly = readOnly,
                    onTextChange = { content = it },
                )
            }
        }
    }
}

/**
 * 带语法高亮和行号的代码编辑区域。
 * 编辑框高度自适应，与行号列共用外层滚动，保证滚动同步。
 */
@Composable
private fun CodeEditorField(
    text: String,
    fileName: String,
    readOnly: Boolean,
    onTextChange: (String) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val highlightEnabled = text.length <= MAX_HIGHLIGHT_CHARS
    val highlighted = remember(text, dark, highlightEnabled) {
        if (highlightEnabled) CodeHighlighter.highlight(text, fileName, dark)
        else AnnotatedString(text)
    }

    // 用 TextFieldValue 保存光标位置；每次输入更新 editorValue，再叠加上高亮样式
    var editorValue by remember { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(text) {
        if (editorValue.text != text) {
            editorValue = TextFieldValue(text)
        }
    }

    val editorStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        lineHeight = 22.sp,
    )

    val lineNumbers = remember(text) {
        val count = text.lineSequence().count()
        (1..count).joinToString("\n")
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = lineNumbers,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
            style = editorStyle.copy(color = MaterialTheme.colorScheme.outline),
        )
        BasicTextField(
            value = editorValue.copy(annotatedString = highlighted),
            onValueChange = { editorValue = it; onTextChange(it.text) },
            readOnly = readOnly,
            textStyle = editorStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
        )
    }
}
