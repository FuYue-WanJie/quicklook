package wanjie.quicklook.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 简易垂直滚动条 Modifier。仅当内容超过视口时绘制 thumb。
 */
@Composable
fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    padding: Dp = 2.dp,
): Modifier {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (total == 0 || visible.isEmpty()) return this

    val viewportHeight = info.viewportSize.height.toFloat()
    val avgItemHeight = visible.first().size.toFloat()
    val contentHeight = avgItemHeight * total
    if (contentHeight <= viewportHeight) return this

    val firstOffset = visible.first().offset.toFloat()
    val scrolledPx = visible.first().index * avgItemHeight + firstOffset.coerceAtMost(0f) * -1
    val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(1f)
    val thumbHeight = (viewportHeight * viewportHeight / contentHeight).coerceAtLeast(24f)
    val thumbTop = (scrolledPx / maxScroll).coerceIn(0f, 1f) * (viewportHeight - thumbHeight)

    return this.then(
        Modifier.drawWithContent {
            drawContent()
            val wPx = width.toPx()
            val padPx = padding.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = (size.width - wPx - padPx).coerceAtLeast(0f),
                    y = thumbTop,
                ),
                size = Size(wPx, thumbHeight),
                cornerRadius = CornerRadius(wPx / 2f, wPx / 2f),
            )
        }
    )
}
