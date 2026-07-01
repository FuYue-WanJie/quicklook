package wanjie.quicklook.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import wanjie.quicklook.viewmodel.AndroidDataPrompt
import wanjie.quicklook.viewmodel.DataAccessMode

/**
 * Android/data 访问相关的统一弹窗：
 * - [AndroidDataPrompt.RequestBypass]：无权限读/写，询问是否坚持（零宽度空格绕过）。
 * - [AndroidDataPrompt.BypassFailed]：已绕过仍无权限，如实告知用户。
 */
@Composable
fun AndroidDataPromptDialog(
    prompt: AndroidDataPrompt,
    onConfirmBypass: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        is AndroidDataPrompt.RequestBypass -> {
            val verb = if (prompt.op.mode == DataAccessMode.READ) "读取" else "写入"
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("无权限$verb Android/data") },
                text = {
                    Text(
                        "Android 11 及以上版本限制直接访问 Android/data，当前无权限$verb该目录。\n" +
                            "是否坚持尝试访问？（将在 data 前插入零宽度空格进行绕过）"
                    )
                },
                confirmButton = { TextButton(onClick = onConfirmBypass) { Text("坚持访问") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
            )
        }

        is AndroidDataPrompt.BypassFailed -> {
            val verb = if (prompt.mode == DataAccessMode.READ) "读取" else "写入"
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("仍无权限$verb") },
                text = {
                    Text(
                        "已尝试在 data 前插入零宽度空格绕过，仍然无权限$verb：\n${prompt.path}\n" +
                            "该路径可能被系统严格限制，无法通过此方式访问。"
                    )
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
            )
        }
    }
}
