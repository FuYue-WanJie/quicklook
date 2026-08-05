package wanjie.quicklook.ui.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import wanjie.quicklook.R
import wanjie.quicklook.data.ArchiveCrumb
import wanjie.quicklook.data.ArchiveEntry
import wanjie.quicklook.data.ArchivePasswordRequiredException
import wanjie.quicklook.data.ArchiveRepository
import wanjie.quicklook.data.FileCategory
import wanjie.quicklook.data.FileUtils
import wanjie.quicklook.data.WrongArchivePasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ArchiveLoadState { Loading, NeedPassword, Ready, Error }

/** 提取完成后的待打开文件 */
data class PendingOpen(
    val path: String,
    val name: String,
    val category: FileCategory,
)

data class ArchiveUiState(
    val loadState: ArchiveLoadState = ArchiveLoadState.Loading,
    val innerPath: String = "",
    val crumbs: List<ArchiveCrumb> = emptyList(),
    val entries: List<ArchiveEntry> = emptyList(),
    val errorMessage: String? = null,
    val isEncrypted: Boolean = false,
    val totalEntries: Int = 0,
    val extracting: Boolean = false,
    val extractError: String? = null,
    val pendingOpen: PendingOpen? = null,
)

class ArchiveViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private var filePath: String = ""
    private var fileName: String = ""
    private var password: String? = null
    private var allEntries: List<ArchiveEntry> = emptyList()

    fun init(archivePath: String, archiveName: String) {
        filePath = archivePath
        fileName = archiveName
        load()
    }

    private fun load() {
        _uiState.update { it.copy(loadState = ArchiveLoadState.Loading) }
        viewModelScope.launch {
            val encrypted = withContext(Dispatchers.IO) { ArchiveRepository.isEncrypted(filePath) }
            if (encrypted && password == null) {
                _uiState.update { it.copy(loadState = ArchiveLoadState.NeedPassword, isEncrypted = true) }
                return@launch
            }
            try {
                val entries = withContext(Dispatchers.IO) {
                    ArchiveRepository.readAllEntries(filePath, password)
                }
                allEntries = entries
                updateCurrentView()
            } catch (e: ArchivePasswordRequiredException) {
                _uiState.update {
                    it.copy(loadState = ArchiveLoadState.NeedPassword, isEncrypted = true)
                }
            } catch (e: WrongArchivePasswordException) {
                password = null
                _uiState.update {
                    it.copy(
                        loadState = ArchiveLoadState.Error,
                        errorMessage = getApplication<Application>().getString(R.string.archive_password_wrong),
                        isEncrypted = true,
                    )
                }
            } catch (e: Exception) {
                val ctx = getApplication<Application>()
                val msg = ctx.getString(R.string.archive_read_failed, e.message ?: "")
                _uiState.update {
                    it.copy(loadState = ArchiveLoadState.Error, errorMessage = msg, isEncrypted = encrypted)
                }
            }
        }
    }

    fun submitPassword(pw: String) {
        password = pw
        load()
    }

    fun navigateTo(innerPath: String) {
        _uiState.update { it.copy(innerPath = innerPath) }
        updateCurrentView()
    }

    fun goUp(): Boolean {
        val current = _uiState.value.innerPath
        if (current.isEmpty()) return false
        val parent = current.trimEnd('/').substringBeforeLast('/', "")
        val parentPath = if (parent.isEmpty()) "" else "$parent/"
        navigateTo(parentPath)
        return true
    }

    private fun updateCurrentView() {
        val current = _uiState.value.innerPath
        val children = ArchiveRepository.listInPath(allEntries, current)
        val crumbs = ArchiveRepository.buildCrumbs(fileName, current)
        _uiState.update {
            it.copy(
                loadState = ArchiveLoadState.Ready,
                entries = children,
                crumbs = crumbs,
                totalEntries = allEntries.size,
                errorMessage = null,
            )
        }
    }

    /**
     * 提取单个文件条目到应用缓存目录。
     * 提取成功后设置 pendingOpen，由 UI 层消费并导航到对应查看器。
     */
    fun openEntry(entry: ArchiveEntry) {
        if (entry.isDirectory || entry.encrypted) return
        viewModelScope.launch {
            _uiState.update { it.copy(extracting = true, extractError = null) }
            try {
                val file = extractEntry(entry)
                if (file != null && file.exists() && file.length() > 0) {
                    val category = FileUtils.quickCategoryFor(file.name, false)
                    _uiState.update {
                        it.copy(
                            extracting = false,
                            pendingOpen = PendingOpen(file.absolutePath, file.name, category),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(extracting = false, extractError = getApplication<Application>().getString(R.string.archive_extract_failed))
                    }
                }
            } catch (e: WrongArchivePasswordException) {
                _uiState.update {
                    it.copy(extracting = false, extractError = getApplication<Application>().getString(R.string.archive_password_wrong))
                }
            } catch (e: ArchivePasswordRequiredException) {
                _uiState.update {
                    it.copy(extracting = false, extractError = getApplication<Application>().getString(R.string.archive_entry_encrypted))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(extracting = false, extractError = getApplication<Application>().getString(R.string.archive_extract_failed))
                }
            }
        }
    }

    /** UI 层消费 pendingOpen 后调用，清除状态避免重复导航。 */
    fun consumePendingOpen() {
        _uiState.update { it.copy(pendingOpen = null) }
    }

    /** 清除提取错误提示 */
    fun clearExtractError() {
        _uiState.update { it.copy(extractError = null) }
    }

    /**
     * 提取单个文件到应用缓存目录，返回临时 File。
     * 调用方负责使用后删除。
     */
    private suspend fun extractEntry(entry: ArchiveEntry): File? = withContext(Dispatchers.IO) {
        val cacheDir = getApplication<Application>().cacheDir
        val tempDir = File(cacheDir, "archive_extract").apply { mkdirs() }
        ArchiveRepository.extractEntry(filePath, password, entry.path, tempDir)
    }
}
