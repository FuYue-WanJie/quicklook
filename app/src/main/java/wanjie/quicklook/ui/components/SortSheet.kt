package wanjie.quicklook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wanjie.quicklook.R
import wanjie.quicklook.data.SortConfig
import wanjie.quicklook.data.SortOrder

/**
 * Bottom sheet for picking the sort order and the "folders first" toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    current: SortConfig,
    onConfigChange: (SortConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.sort_by),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SortOrder.entries.forEach { order ->
                    SegmentedButton(
                        selected = current.order == order,
                        onClick = { onConfigChange(current.copy(order = order)) },
                        shape = SegmentedButtonDefaults.itemShape(SortOrder.entries.indexOf(order), SortOrder.entries.size),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = order.label(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.folders_first),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = current.foldersFirst,
                    onCheckedChange = { v -> onConfigChange(current.copy(foldersFirst = v)) },
                )
            }
        }
    }
}

@Composable
private fun SortOrder.label(): String = when (this) {
    SortOrder.NAME_ASC -> stringResource(R.string.sort_name_asc)
    SortOrder.NAME_DESC -> stringResource(R.string.sort_name_desc)
    SortOrder.MODIFIED_DESC -> stringResource(R.string.sort_modified_desc)
    SortOrder.MODIFIED_ASC -> stringResource(R.string.sort_modified_asc)
    SortOrder.SIZE_DESC -> stringResource(R.string.sort_size_desc)
    SortOrder.SIZE_ASC -> stringResource(R.string.sort_size_asc)
    SortOrder.TYPE -> stringResource(R.string.sort_type)
}
