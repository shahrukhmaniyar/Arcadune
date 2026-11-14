package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Spring press feedback shared across Arcadune pills, cards, and icon buttons. */
@Composable
fun Modifier.arcadunePressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    scaleDown: Float = 0.95f,
    interactions: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = ArcaduneMotion.press(),
        label = "arcadunePress",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactions,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

@Composable
fun ArcaduneGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    containerColor: Color = ArcaduneColors.Surface,
    borderColor: Color = ArcaduneColors.GlassEdge,
    clip: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (clip) Modifier.clip(shape) else Modifier)
                .background(containerColor, shape)
                .border(1.dp, borderColor, shape),
        content = content,
    )
}

@Composable
fun ArcaduneIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = ArcaduneColors.OnSurface,
    containerColor: Color = ArcaduneColors.SurfaceHigh,
    borderColor: Color = ArcaduneColors.GlassEdge,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
) {
    val shape = RoundedCornerShape((size.value * 0.31f).dp)
    Box(
        modifier =
            modifier
                .size(size)
                .background(containerColor, shape)
                .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun ArcaduneSystemBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier =
            modifier
                .background(ArcaduneColors.Purple.copy(alpha = 0.16f), shape)
                .border(1.dp, ArcaduneColors.Purple.copy(alpha = 0.45f), shape)
                .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = ArcaduneColors.Purple,
            maxLines = 1,
        )
    }
}

@Composable
fun ArcadunePillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 42.dp,
) {
    val shape = RoundedCornerShape(999.dp)
    val contentColor = if (highlighted) ArcaduneColors.OnAccent else ArcaduneColors.OnSurface
    Row(
        modifier =
            modifier
                .height(height)
                .then(if (enabled) Modifier else Modifier.alpha(0.45f))
                .semantics { role = Role.Button }
                .arcadunePressable(onClick = onClick, enabled = enabled)
                .background(if (highlighted) ArcaduneColors.Purple else ArcaduneColors.SurfaceHigh, shape)
                .then(
                    if (highlighted) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, ArcaduneColors.GlassEdge, shape)
                    },
                )
                .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ArcaduneIconPill(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .then(if (enabled) Modifier else Modifier.alpha(0.45f))
                .semantics { role = Role.Button }
                .arcadunePressable(onClick = onClick, enabled = enabled, scaleDown = 0.92f)
                .background(ArcaduneColors.Surface, CircleShape)
                .border(1.dp, ArcaduneColors.GlassEdge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ArcaduneColors.OnSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun ArcaduneRowDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 18.dp,
    endIndent: Dp = 18.dp,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = startIndent, end = endIndent)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.07f)),
    )
}

@Composable
fun ArcaduneDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    maxWidth: Dp = 460.dp,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.84f
    val shape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .border(1.dp, ArcaduneColors.GlassEdge, shape),
            shape = shape,
            color = ArcaduneColors.Surface,
            contentColor = ArcaduneColors.OnSurface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (icon != null) {
                        ArcaduneIconBadge(
                            icon = icon,
                            tint = ArcaduneColors.Purple,
                            containerColor = ArcaduneColors.Purple.copy(alpha = 0.16f),
                            borderColor = ArcaduneColors.Purple.copy(alpha = 0.45f),
                            size = 40.dp,
                            iconSize = 20.dp,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ProvideTextStyle(
                            value =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = ArcaduneColors.OnSurface,
                                ),
                        ) {
                            title()
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    content = content,
                )

                if (dismissButton != null || confirmButton != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton?.invoke()
                    }
                }
            }
        }
    }
}
