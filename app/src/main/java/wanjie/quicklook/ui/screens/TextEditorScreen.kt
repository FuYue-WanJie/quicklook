@file:OptIn(ExperimentalMaterial3Api::class)

package wanjie.quicklook.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import wanjie.quicklook.ui.components.AndroidDataPromptDialog
import wanjie.quicklook.viewmodel.FileViewModel
import java.io.File

@Composable
fun TextEditorScreen(
    viewModel: FileViewModel,
    file: File,
    onBack: () -> Unit,
    onOpenWithThirdParty: (File) -> Unit,
) {
    val state by viewModel.textEditorState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 进入页面时加载文件内容
    LaunchedEffect(file.absolutePath) {
        viewModel.loadText(file)
    }

    // 保存结果提示
    LaunchedEffect(state.saved, state.error) {
        when {
            state.saved -> {
                scope.launch { snackbarHostState.showSnackbar("已保存") }
            }
            state.error != null -> {
                scope.launch { snackbarHostState.showSnackbar(state.error!!) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearTextEditor()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 用第三方应用打开
                    IconButton(onClick = { onOpenWithThirdParty(file) }) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "用其他应用打开")
                    }
                    // 保存
                    IconButton(
                        onClick = { viewModel.saveText() },
                        enabled = state.dirty,
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = "保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null && state.content.isEmpty() -> Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> OutlinedTextField(
                    value = state.content,
                    onValueChange = { viewModel.onContentChange(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    placeholder = { Text("文件为空") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    isError = state.error != null,
                )
            }
        }
    }

    // Android/data 读写无权限绕过 / 绕过失败弹窗
    state.androidDataPrompt?.let { prompt ->
        AndroidDataPromptDialog(
            prompt = prompt,
            onConfirmBypass = { viewModel.confirmBypass() },
            onDismiss = { viewModel.dismissAndroidDataPrompt() },
        )
    }
}
