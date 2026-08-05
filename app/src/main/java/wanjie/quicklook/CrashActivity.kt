package wanjie.quicklook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wanjie.quicklook.data.CrashRecord
import wanjie.quicklook.data.CrashTracker
import wanjie.quicklook.ui.theme.QuickLookTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃展示页：在未捕获异常发生时由 [CrashTracker] 拉起，
 * 展示崩溃摘要与完整堆栈，提供复制、重启应用与退出操作。
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val record = CrashTracker.loadRecords(applicationContext).lastOrNull()
            ?: CrashRecord(
                time = System.currentTimeMillis(),
                threadName = "unknown",
                type = "UnknownError",
                message = "",
                stackTrace = "",
            )

        setContent {
            QuickLookTheme {
                CrashScreen(
                    record = record,
                    onRestart = { restartApp() },
                    onExit = { exitApp() },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CrashTracker.resetShowing()
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        Process.killProcess(Process.myPid())
    }

    private fun exitApp() {
        Process.killProcess(Process.myPid())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashScreen(record: CrashRecord, onRestart: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val pageScroll = rememberScrollState()
    val copyText = remember(record) { buildCopyText(record) }

    BackHandler { onExit() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crash_title)) },
                actions = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.crash_exit))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = onRestart, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.crash_restart))
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("crash_log", copyText))
                            Toast.makeText(context, R.string.crash_copy_done, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.crash_copy))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(pageScroll)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.crash_subtitle),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = formatTime(record.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InfoRow(stringResource(R.string.crash_info_type), record.type)
                    InfoRow(stringResource(R.string.crash_info_thread), record.threadName)
                    InfoRow(
                        stringResource(R.string.crash_info_message),
                        record.message.ifBlank { record.type },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.crash_stack),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = record.stackTrace.ifBlank { stringResource(R.string.crash_stack_empty) },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun buildCopyText(record: CrashRecord): String = buildString {
    appendLine("--- Crash Report ---")
    appendLine("time: ${formatTime(record.time)}")
    appendLine("thread: ${record.threadName}")
    appendLine("exception: ${record.type}: ${record.message}")
    append("stack:")
    append(record.stackTrace)
}

private fun formatTime(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
