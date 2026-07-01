@file:OptIn(ExperimentalMaterial3Api::class)

package wanjie.quicklook.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wanjie.quicklook.data.DarkThemeMode
import wanjie.quicklook.ui.theme.SeedSwatches
import wanjie.quicklook.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // 显示隐藏文件夹
            item {
                SwitchItem(
                    icon = Icons.Outlined.Folder,
                    title = "显示隐藏文件夹",
                    subtitle = "显示以点（.）开头的文件与文件夹",
                    checked = settings.showHidden,
                    onCheckedChange = settingsViewModel::setShowHidden,
                )
            }

            // 动态取色
            item {
                SwitchItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "动态取色",
                    subtitle = if (dynamicSupported) "根据壁纸自动生成配色"
                    else "需要 Android 12 及以上版本",
                    checked = settings.dynamicColor,
                    enabled = dynamicSupported,
                    onCheckedChange = settingsViewModel::setDynamicColor,
                )
            }

            // 自选配色：仅在关闭动态取色时出现（避免与动态取色重复）
            if (!settings.dynamicColor || !dynamicSupported) {
                item {
                    ColorPickerItem(
                        selected = settings.seedColor,
                        onSelect = settingsViewModel::setSeedColor,
                    )
                }
            }

            // 深色模式
            item {
                ChoiceItem(
                    icon = Icons.Outlined.Brightness6,
                    title = "深色模式",
                    valueLabel = when (settings.darkTheme) {
                        DarkThemeMode.FOLLOW_SYSTEM -> "跟随系统"
                        DarkThemeMode.LIGHT -> "浅色"
                        DarkThemeMode.DARK -> "深色"
                    },
                    onClick = {
                        val next = when (settings.darkTheme) {
                            DarkThemeMode.FOLLOW_SYSTEM -> DarkThemeMode.LIGHT
                            DarkThemeMode.LIGHT -> DarkThemeMode.DARK
                            DarkThemeMode.DARK -> DarkThemeMode.FOLLOW_SYSTEM
                        }
                        settingsViewModel.setDarkTheme(next)
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = {
            Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = ListItemDefaults.colors(
            leadingIconColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun ChoiceItem(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        trailingContent = {
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(
            leadingIconColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ColorPickerItem(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text("自选配色", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(SeedSwatches) { swatch ->
                ColorDot(
                    color = Color(swatch),
                    selected = selected == swatch,
                    onClick = { onSelect(swatch) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "点击色块即可切换主题色",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
    )
}
