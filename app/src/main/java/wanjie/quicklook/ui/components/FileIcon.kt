package wanjie.quicklook.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import wanjie.quicklook.data.FileCategory

@Composable
fun FileIcon(category: FileCategory, modifier: Modifier = Modifier) {
    val (icon, tint) = iconFor(category)
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier)
}

@Composable
private fun iconFor(category: FileCategory): Pair<ImageVector, Color> = when (category) {
    FileCategory.FOLDER -> Icons.Outlined.Folder to MaterialTheme.colorScheme.primary
    FileCategory.IMAGE -> Icons.Outlined.Image to Color(0xFFB45F00)
    FileCategory.VIDEO -> Icons.Outlined.Movie to Color(0xFF9C4A2D)
    FileCategory.AUDIO -> Icons.Outlined.AudioFile to Color(0xFF1B5E50)
    FileCategory.DOCUMENT -> Icons.Outlined.Description to MaterialTheme.colorScheme.tertiary
    FileCategory.ARCHIVE -> Icons.Outlined.Archive to Color(0xFF7E5A26)
    FileCategory.APK -> Icons.Outlined.Android to Color(0xFF3D7D2A)
    FileCategory.CODE -> Icons.Outlined.Code to Color(0xFF455A64)
    FileCategory.TEXT -> Icons.Outlined.Article to MaterialTheme.colorScheme.onSurfaceVariant
    FileCategory.OTHER -> Icons.Outlined.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
}
