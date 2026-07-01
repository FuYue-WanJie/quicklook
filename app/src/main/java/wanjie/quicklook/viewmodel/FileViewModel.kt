package wanjie.quicklook.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import wanjie.quicklook.data.AndroidDataAccess
import wanjie.quicklook.data.FileEntry
import wanjie.quicklook.data.FileRepository
import wanjie.quicklook.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileUiState(
    val currentPath: String = "",
    val canGoUp: Boolean = false,
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Android/data 相关的待确认弹窗（无权限绕过 / 绕过失败）。 */
    val androidDataPrompt: AndroidDataPrompt? = null,
)

/** 内置文本编辑器的状态。 */
data class TextEditorState(
    val filePath: String = "",
    val content: String = "",
    val loading: Boolean = false,
    val dirty: Boolean = false, // 是否有未保存修改
    val error: String? = null,
    val saved: Boolean = false, // 最近一次保存是否成功
    /** 读写文本时的 Android/data 待确认弹窗。 */
    val androidDataPrompt: AndroidDataPrompt? = null,
)

class FileViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(FileUiState())
    val state: StateFlow<FileUiState> = _state.asStateFlow()

    private val _textEditorState = MutableStateFlow(TextEditorState())
    val textEditorState: StateFlow<TextEditorState> = _textEditorState.asStateFlow()

    init {
        open(FileRepository.defaultRoot())
    }

    /** 读取当前“显示隐藏文件夹”设置，供列表过滤使用。 */
    private fun showHidden(): Boolean = SettingsRepository.load(getApplication()).showHidden

    fun open(file: File) {
        val showHidden = showHidden()
        _state.update { it.copy(loading = true, error = null, androidDataPrompt = null) }
        viewModelScope.launch {
            val attempt = withContext(Dispatchers.IO) {
                runCatching { FileRepository.tryList(file, showHidden) }
                    .getOrDefault(FileRepository.ListAttempt.Denied)
            }
            when (attempt) {
                is FileRepository.ListAttempt.Ok -> _state.update {
                    it.copy(
                        currentPath = file.absolutePath,
                        canGoUp = FileRepository.parent(file) != null,
                        entries = attempt.entries,
                        loading = false,
                    )
                }
                is FileRepository.ListAttempt.Denied -> handleListDenied(file)
            }
        }
    }

    /**
     * 列目录被拒：若为 Android 11+ 下的常规形式 Android/data 路径，
     * 弹窗询问是否坚持（零宽度空格绕过）；否则直接提示无权限。
     */
    private fun handleListDenied(file: File) {
        if (AndroidDataAccess.isAndroid11Plus() && AndroidDataAccess.isNormalAndroidData(file.absolutePath)) {
            _state.update {
                it.copy(
                    loading = false,
                    androidDataPrompt = AndroidDataPrompt.RequestBypass(PendingDataOp.ListDir(file.absolutePath)),
                )
            }
        } else {
            _state.update { it.copy(loading = false, error = "无权限访问：${file.absolutePath}") }
        }
    }

    /** 用户在「无权限」弹窗中点击「坚持访问」：应用零宽度空格绕过并重试待恢复操作。 */
    fun confirmBypass() {
        val prompt = _state.value.androidDataPrompt
            ?: _textEditorState.value.androidDataPrompt
            ?: return
        val op = (prompt as? AndroidDataPrompt.RequestBypass)?.op ?: return
        // 清除弹窗，仅对相关状态标记加载中
        when (op) {
            is PendingDataOp.ListDir -> _state.update { it.copy(androidDataPrompt = null, loading = true) }
            is PendingDataOp.ReadText, is PendingDataOp.WriteText ->
                _textEditorState.update { it.copy(androidDataPrompt = null, loading = true) }
        }
        viewModelScope.launch {
            when (op) {
                is PendingDataOp.ListDir -> {
                    val bypassed = AndroidDataAccess.bypass(File(op.path))
                    val showHidden = showHidden()
                    val attempt = withContext(Dispatchers.IO) {
                        runCatching { FileRepository.tryList(bypassed, showHidden) }
                            .getOrDefault(FileRepository.ListAttempt.Denied)
                    }
                    when (attempt) {
                        is FileRepository.ListAttempt.Ok -> _state.update {
                            it.copy(
                                currentPath = bypassed.absolutePath,
                                canGoUp = FileRepository.parent(bypassed) != null,
                                entries = attempt.entries,
                                loading = false,
                            )
                        }
                        is FileRepository.ListAttempt.Denied -> _state.update {
                            it.copy(loading = false, androidDataPrompt = AndroidDataPrompt.BypassFailed(DataAccessMode.READ, bypassed.absolutePath))
                        }
                    }
                }
                is PendingDataOp.ReadText -> {
                    val bypassed = AndroidDataAccess.bypass(File(op.path))
                    val content = withContext(Dispatchers.IO) { FileRepository.readText(bypassed) }
                    if (content != null) {
                        _textEditorState.update {
                            it.copy(filePath = bypassed.absolutePath, content = content, loading = false, error = null, androidDataPrompt = null)
                        }
                    } else {
                        _textEditorState.update {
                            it.copy(loading = false, androidDataPrompt = AndroidDataPrompt.BypassFailed(DataAccessMode.READ, bypassed.absolutePath))
                        }
                    }
                }
                is PendingDataOp.WriteText -> {
                    val bypassed = AndroidDataAccess.bypass(File(op.path))
                    val ok = withContext(Dispatchers.IO) { FileRepository.writeText(bypassed, op.content) }
                    if (ok) {
                        _textEditorState.update {
                            it.copy(filePath = bypassed.absolutePath, dirty = false, saved = true, loading = false, error = null, androidDataPrompt = null)
                        }
                    } else {
                        _textEditorState.update {
                            it.copy(loading = false, androidDataPrompt = AndroidDataPrompt.BypassFailed(DataAccessMode.WRITE, bypassed.absolutePath))
                        }
                    }
                }
            }
        }
    }

    /** 关闭当前 Android/data 相关弹窗（取消绕过 / 知晓失败）。 */
    fun dismissAndroidDataPrompt() {
        _state.update { it.copy(androidDataPrompt = null) }
        _textEditorState.update { it.copy(androidDataPrompt = null, loading = false) }
    }

    fun goUp() {
        val parent = FileRepository.parent(File(_state.value.currentPath)) ?: return
        open(parent)
    }

    fun refresh() {
        val path = _state.value.currentPath
        if (path.isEmpty()) return
        open(File(path))
    }

    // ---- 内置文本编辑器 ----

    /** 加载文本文件内容到编辑器状态。 */
    fun loadText(file: File) {
        _textEditorState.value = TextEditorState(filePath = file.absolutePath, loading = true)
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) { FileRepository.readText(file) }
            if (content != null) {
                _textEditorState.value = TextEditorState(
                    filePath = file.absolutePath,
                    content = content,
                )
            } else {
                // 无权限读取：Android 11+ 下常规形式 Android/data 可尝试零宽度空格绕过
                if (AndroidDataAccess.isAndroid11Plus() && AndroidDataAccess.isNormalAndroidData(file.absolutePath)) {
                    _textEditorState.value = TextEditorState(
                        filePath = file.absolutePath,
                        loading = false,
                        androidDataPrompt = AndroidDataPrompt.RequestBypass(PendingDataOp.ReadText(file.absolutePath)),
                    )
                } else {
                    _textEditorState.value = TextEditorState(
                        filePath = file.absolutePath,
                        error = "无法读取文件",
                    )
                }
            }
        }
    }

    /** 用户编辑时更新内容并标记为脏。 */
    fun onContentChange(newContent: String) {
        _textEditorState.update { it.copy(content = newContent, dirty = true, saved = false) }
    }

    /** 保存当前编辑的文本到原文件。 */
    fun saveText() {
        val s = _textEditorState.value
        if (s.filePath.isEmpty()) return
        _textEditorState.update { it.copy(loading = true, error = null, saved = false) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                FileRepository.writeText(File(s.filePath), s.content)
            }
            if (ok) {
                _textEditorState.update {
                    it.copy(loading = false, dirty = false, saved = true)
                }
            } else {
                // 无权限写入：Android 11+ 下常规形式 Android/data 可尝试零宽度空格绕过
                if (AndroidDataAccess.isAndroid11Plus() && AndroidDataAccess.isNormalAndroidData(s.filePath)) {
                    _textEditorState.update {
                        it.copy(
                            loading = false,
                            androidDataPrompt = AndroidDataPrompt.RequestBypass(PendingDataOp.WriteText(s.filePath, s.content)),
                        )
                    }
                } else {
                    _textEditorState.update { it.copy(loading = false, error = "保存失败") }
                }
            }
        }
    }

    /** 重置编辑器状态（退出编辑器时调用）。 */
    fun clearTextEditor() {
        _textEditorState.value = TextEditorState()
    }

    /**
     * 按用户输入的原始路径字符串跳转目录。
     * @return 跳转结果，用于 UI 给出准确提示（不存在/不是目录/无权限/成功）。
     */
    fun navigateTo(rawPath: String): NavigateResult {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) return NavigateResult.Empty
        val file = File(trimmed)
        if (!file.exists()) return NavigateResult.NotFound(trimmed)
        if (!file.isDirectory) return NavigateResult.NotDirectory(trimmed)
        // Android 11+ 下常规形式 Android/data：交给 open() 走零宽度空格绕过弹窗流程，不在此处直接判无权限
        if (AndroidDataAccess.isAndroid11Plus() && AndroidDataAccess.isNormalAndroidData(trimmed)) {
            open(file)
            return NavigateResult.Success
        }
        if (!file.canRead()) return NavigateResult.NoPermission(trimmed)
        open(file)
        return NavigateResult.Success
    }
}

/** [FileViewModel.navigateTo] 的返回结果。 */
sealed interface NavigateResult {
    /** 成功跳转。 */
    data object Success : NavigateResult
    /** 输入为空。 */
    data object Empty : NavigateResult
    /** 路径不存在。 */
    data class NotFound(val path: String) : NavigateResult
    /** 路径不是目录。 */
    data class NotDirectory(val path: String) : NavigateResult
    /** 无读取权限。 */
    data class NoPermission(val path: String) : NavigateResult
}

/** Android/data 访问模式。 */
enum class DataAccessMode { READ, WRITE }

/** 待恢复的文件操作，用于零宽度空格绕过重试。 */
sealed interface PendingDataOp {
    val path: String
    val mode: DataAccessMode

    /** 列目录。 */
    data class ListDir(override val path: String) : PendingDataOp {
        override val mode: DataAccessMode = DataAccessMode.READ
    }

    /** 读取文本文件。 */
    data class ReadText(override val path: String) : PendingDataOp {
        override val mode: DataAccessMode = DataAccessMode.READ
    }

    /** 写入文本文件。 */
    data class WriteText(override val path: String, val content: String) : PendingDataOp {
        override val mode: DataAccessMode = DataAccessMode.WRITE
    }
}

/** Android/data 相关的 UI 弹窗。 */
sealed interface AndroidDataPrompt {
    /** 无权限读/写 Android/data，询问是否坚持（零宽度空格绕过），携带待恢复操作。 */
    data class RequestBypass(val op: PendingDataOp) : AndroidDataPrompt

    /** 已尝试零宽度空格绕过仍无权限，需如实告知用户。 */
    data class BypassFailed(val mode: DataAccessMode, val path: String) : AndroidDataPrompt
}
