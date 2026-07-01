package wanjie.quicklook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wanjie.quicklook.ui.AppRoot
import wanjie.quicklook.ui.theme.QuickLookTheme
import wanjie.quicklook.viewmodel.FileViewModel
import wanjie.quicklook.viewmodel.SettingsViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: FileViewModel by lazy {
        ViewModelProvider(this)[FileViewModel::class.java]
    }
    private val settingsViewModel: SettingsViewModel by lazy {
        ViewModelProvider(this)[SettingsViewModel::class.java]
    }
    private var hadStorageAccess = false

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) {
                Toast.makeText(this, "已获得部分读取权限", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()

        setContent {
            val settings by settingsViewModel.state.collectAsStateWithLifecycle()
            QuickLookTheme(settings) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(
                        fileViewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        onOpenWithThirdParty = { file -> openFile(file) },
                    )
                }
            }
        }
        hadStorageAccess = hasStorageAccess()
    }

    override fun onResume() {
        super.onResume()
        val now = hasStorageAccess()
        if (now != hadStorageAccess && now) viewModel.refresh()
        hadStorageAccess = now
    }

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：文件管理器走“所有文件访问权限”
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            }
        } else {
            val toRequest = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (toRequest.isNotEmpty()) permissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    private fun openFile(file: File) {
        val mime = contentResolver.getType(Uri.fromFile(file)) ?: guessMime(file)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, "打开方式"))
        }.onFailure {
            Toast.makeText(this, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guessMime(file: File): String? = when (file.extension.lowercase()) {
        "txt", "log", "md" -> "text/plain"
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        else -> null
    }
}
