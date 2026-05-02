package com.example.liftrix.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun LiftrixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    contentDescription: String? = null,
    border: BorderStroke? = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    ),
    content: @Composable () -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun CompactLiftrixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ),
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    LiftrixCard(
        modifier = modifier,
        onClick = onClick,
        elevation = elevation,
        colors = colors,
        shape = shape,
        enabled = enabled,
        contentPadding = contentPadding,
        contentDescription = contentDescription,
        content = content
    )
}

@Composable
fun ElevatedLiftrixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    LiftrixCard(
        modifier = modifier,
        onClick = onClick,
        elevation = elevation,
        colors = colors,
        shape = shape,
        enabled = enabled,
        contentPadding = contentPadding,
        contentDescription = contentDescription,
        content = content
    )
}

object CardSpacing {
    val XXS = 4.dp
    val XS = 8.dp
    val S = 12.dp
    val M = 16.dp
    val L = 24.dp
    val XL = 32.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

object CardElevations {
    @Composable
    fun subtle() = CardDefaults.cardElevation(defaultElevation = 1.dp)

    @Composable
    fun medium() = CardDefaults.cardElevation(defaultElevation = 4.dp)
}
