package wanjie.quicklook.ui.viewer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import wanjie.quicklook.R
import wanjie.quicklook.data.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文本编辑器（全屏 Composable）。
 * 通过 FileProvider content URI 读写文件。
 * 大文件（>5MB）只读，避免内存压力。
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

            if (size > 5L * 1024 * 1024) {
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
                else -> OutlinedTextField(
                    value = content ?: "",
                    onValueChange = { content = it },
                    readOnly = readOnly,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
