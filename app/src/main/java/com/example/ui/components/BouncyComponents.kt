package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Aplica el efecto bouncy (squish + rebound) a un [Animatable] externo.
 * Reutilizable por cualquier componente que quiera el efecto sin heredar
 * de los Bouncy* predefinidos.
 *
 * Lo siguen usando: FAB de HistoryScreen, play button redondo de ResultScreen,
 * NoteCard de HistoryScreen, TrashedNoteCard de TrashScreen, y el play button
 * del side panel de ResultScreen. Esos callers quieren un scale-inward.
 */
suspend fun observeBouncyPress(
    interactionSource: MutableInteractionSource,
    scale: Animatable<Float, AnimationVector1D>,
    pressedScale: Float = 0.94f
) {
    interactionSource.interactions.collect { interaction ->
        when (interaction) {
            is PressInteraction.Press -> {
                scale.animateTo(
                    pressedScale,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = 1800f
                    )
                )
            }
            is PressInteraction.Release, is PressInteraction.Cancel -> {
                if (scale.value > 0.96f) scale.snapTo(0.93f)
                scale.animateTo(
                    1f,
                    spring(dampingRatio = 0.40f, stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    }
}

/**
 * Estado booleano de press, derivado del InteractionSource.
 * Se usa para manejar la animación de expansión en los Bouncy* (contentPadding).
 */
@Composable
private fun rememberPressState(
    interactionSource: MutableInteractionSource,
    enabled: Boolean
): State<Boolean> {
    val isPressed = remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource, enabled) {
        if (!enabled) {
            isPressed.value = false
            return@LaunchedEffect
        }
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed.value = true
                is PressInteraction.Release, is PressInteraction.Cancel -> isPressed.value = false
            }
        }
    }
    return isPressed
}

private fun <T> bouncySpring() = spring<T>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

/**
 * Modificador clickable con efecto bouncy (scale squish).
 * Se mantiene para callers externos que quieran el squish en un container
 * arbitrario (ej: card clickeable en Settings).
 */
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        observeBouncyPress(interactionSource, scale)
    }
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

// ============================================================
// BouncyButton
// ============================================================

/**
 * Botón relleno con el comportamiento del Record button: al presionar, la
 * superficie visible crece horizontalmente y empuja a los vecinos.
 *
 * Se anima `contentPadding` (no el modifier externo). M3 Button internamente
 * hace `Row(Modifier.padding(contentPadding))` dentro de un Surface que se
 * dimensiona al Row. Si el contentPadding horizontal crece, la superficie del
 * botón crece → el usuario VE el botón agrandarse.
 *
 * Usar `Modifier.padding(horizontal = extraPad)` externo NO funciona: solo
 * agrega espacio vacío alrededor, la superficie visible no cambia.
 *
 * IMPORTANTE: si el caller pasa `Modifier.fillMaxWidth()` o `Modifier.weight(1f)`,
 * el botón no puede crecer (el ancho ya está asignado). En esos casos pasar
 * `expandOnPress = 0.dp`.
 */
@Composable
fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    expandOnPress: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by rememberPressState(interactionSource, enabled)
    val extraPad by animateDpAsState(
        targetValue = if (isPressed) expandOnPress else 0.dp,
        animationSpec = bouncySpring(),
        label = "bouncyButtonPad"
    )

    val layoutDirection = LocalLayoutDirection.current
    val basePadding = ButtonDefaults.ContentPadding
    val contentPadding = PaddingValues(
        start = basePadding.calculateStartPadding(layoutDirection) + extraPad,
        top = basePadding.calculateTopPadding(),
        end = basePadding.calculateEndPadding(layoutDirection) + extraPad,
        bottom = basePadding.calculateBottomPadding()
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shapes = ButtonDefaults.shapes(),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        modifier = modifier,
        content = content
    )
}

// ============================================================
// BouncyOutlinedButton
// ============================================================

/**
 * Variante outlined del BouncyButton. Misma filosofía: expansión horizontal
 * vía contentPadding al presionar, push a vecinos, sin scale.
 */
@Composable
fun BouncyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expandOnPress: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by rememberPressState(interactionSource, enabled)
    val extraPad by animateDpAsState(
        targetValue = if (isPressed) expandOnPress else 0.dp,
        animationSpec = bouncySpring(),
        label = "bouncyOutlinedPad"
    )

    val layoutDirection = LocalLayoutDirection.current
    val basePadding = ButtonDefaults.ContentPadding
    val contentPadding = PaddingValues(
        start = basePadding.calculateStartPadding(layoutDirection) + extraPad,
        top = basePadding.calculateTopPadding(),
        end = basePadding.calculateEndPadding(layoutDirection) + extraPad,
        bottom = basePadding.calculateBottomPadding()
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        modifier = modifier,
        content = content
    )
}

// ============================================================
// BouncyIconButton
// ============================================================

/**
 * Icon button con squish al presionar (scale-inward, como el original).
 *
 * Se mantiene con scale en vez de expansión porque los IconButton de M3 tienen
 * un tamaño interno fijo (`IconButtonTokens.StateLayerSize`) y no exponen
 * contentPadding. La única forma de agrandar el ripple sería romper el
 * encapsulamiento de M3. El squish funciona bien, es inmediato, y no afecta
 * layout — ideal para top bars y rows compactos.
 */
@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expandOnPress: Dp = 6.dp, // ignorado, conservado por compatibilidad de API
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        observeBouncyPress(interactionSource, scale, pressedScale = 0.88f)
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        content = content
    )
}

// ============================================================
// BouncyCapsule
// ============================================================

/**
 * Cápsula/pill horizontal con expansión al presionar.
 *
 * El padding interno está DESPUÉS del clip/background en la cadena de modifiers,
 * así que la superficie visible crece con el padding. La forma se mantiene:
 * CircleShape se estira horizontalmente y queda como cápsula más ancha.
 */
@Composable
fun BouncyCapsule(
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    expandOnPress: Dp = 10.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by rememberPressState(interactionSource, enabled = true)
    val extraPad by animateDpAsState(
        targetValue = if (isPressed) expandOnPress else 0.dp,
        animationSpec = bouncySpring(),
        label = "bouncyCapsulePad"
    )

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp + extraPad),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ============================================================
// BouncyChip
// ============================================================

/**
 * Chip compacto con expansión al presionar.
 * Igual que BouncyCapsule: el padding interno está después del background,
 * así que la superficie visible crece.
 */
@Composable
fun BouncyChip(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    expandOnPress: Dp = 10.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by rememberPressState(interactionSource, enabled = true)
    val extraPad by animateDpAsState(
        targetValue = if (isPressed) expandOnPress else 0.dp,
        animationSpec = bouncySpring(),
        label = "bouncyChipPad"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp + extraPad, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}