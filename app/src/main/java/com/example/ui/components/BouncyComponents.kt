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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Aplica el efecto bouncy (squish + rebound) a un [Animatable] externo.
 * Reutilizable por cualquier componente que quiera el efecto sin heredar
 * de los Bouncy* predefinidos.
 *
 * Se mantiene público y con la firma original: lo siguen usando el FAB de
 * HistoryScreen, el play button de ResultScreen, NoteCard y el FAB de Result.
 * Esos callers quieren un scale-inward y no un push-outward, por eso no se
 * migraron al nuevo sistema de expansión.
 *
 * [pressedScale] controla cuánto se encoge el elemento al presionarlo.
 * Valores típicos: 0.94 para botones, 0.88 para icon buttons, 0.97 para cards.
 *
 * En taps rápidos, Press y Release llegan casi juntos y el animateTo del
 * press se cancela antes de ser visible. Para garantizar siempre una
 * reacción perceptible, si el scale nunca bajó del umbral se fuerza el
 * squish con snapTo antes del rebound.
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
 * Helper interno: estado booleano de press, derivado del InteractionSource.
 * Reemplaza al patrón `Animatable<Float>` que usaban los Bouncy* antes.
 *
 * El sistema nuevo no mide un valor continuo — solo binario pressed/notpressed.
 * La animación concreta (padding, width, etc.) la maneja cada componente con
 * `animateDpAsState`.
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

/**
 * Curva común a todos los Bouncy*: spring elástico de baja rigidez.
 * Usar la misma curva en todos los componentes hace que la sensación de
 * "presión elástica" sea consistente en toda la app.
 */
private fun <T> bouncySpring() = spring<T>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

/**
 * Modificador que aplica el efecto bouncy a cualquier elemento clickable.
 * Uso: Modifier.bouncyClickable { onClick() }
 *
 * Se mantiene con la firma original (scale squish). Los Bouncy* globales ya
 * no lo usan, pero callers externos que quieran un clickable con feedback
 * pueden seguir invocándolo.
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
 * Botón relleno con el comportamiento del Record button:
 * al presionar, se expande horizontalmente hacia los costados y empuja
 * a los vecinos en el Row/Column padre.
 *
 * Implementación: en vez de escalar el botón (scale inward), se anima el
 * padding horizontal aplicado al Modifier externo. El [Button] mide su
 * contenido natural; al crecer el padding, crece el ancho medido, y el
 * `Arrangement.spacedBy` del Row padre redistribuye el espacio.
 *
 * IMPORTANTE: si el caller pasa `Modifier.fillMaxWidth()` o `Modifier.weight(1f)`,
 * el botón NO puede crecer (el ancho ya está asignado). En esos casos, pasar
 * `expandOnPress = 0.dp` para evitar el efecto visual raro de "contenido que
 * se achica". Default 12.dp cubre la mayoría de los casos (botones sueltos
 * en dialogs, alineados al End de un Column, etc).
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

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shapes = ButtonDefaults.shapes(),
        interactionSource = interactionSource,
        modifier = modifier.padding(horizontal = extraPad),
        content = content
    )
}

// ============================================================
// BouncyOutlinedButton
// ============================================================

/**
 * Variante outlined del BouncyButton. Misma filosofía: expansión horizontal
 * al presionar, push a vecinos, sin scale.
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

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        interactionSource = interactionSource,
        modifier = modifier.padding(horizontal = extraPad),
        content = content
    )
}

// ============================================================
// BouncyIconButton
// ============================================================

/**
 * Icon button con expansión horizontal al presionar.
 *
 * El IconButton de M3 tiene un tamaño fijo de 48x48. Al agregar padding
 * horizontal externo, el ícono queda centrado dentro de un área más ancha,
 * y el CircleShape del ripple interno pasa a verse como un óvalo ancho.
 *
 * No usa `Modifier.padding` (que solo agrega espacio vacío fuera del botón
 * sin que el botón "crezca"): usa `Modifier.width(48.dp + extraPad*2)` para
 * forzar el ancho del botón entero. Así el ripple/click area crece con él.
 *
 * `expandOnPress` por defecto es chico (6.dp) porque los icon buttons suelen
 * estar en top bars y rows compactos donde una expansión grande rompe el
 * layout. Ajustar por caller si hace falta.
 */
@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expandOnPress: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by rememberPressState(interactionSource, enabled)
    val extraPad by animateDpAsState(
        targetValue = if (isPressed) expandOnPress else 0.dp,
        animationSpec = bouncySpring(),
        label = "bouncyIconPad"
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .width(48.dp + extraPad * 2)
            .height(48.dp),
        content = content
    )
}

// ============================================================
// BouncyCapsule
// ============================================================

/**
 * Cápsula/pill horizontal con expansión al presionar.
 *
 * Preserva la forma: el CircleShape se estira horizontalmente y queda como
 * una cápsula más ancha (mismo radio = altura/2). No se vuelve rectangular.
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
 *
 * Preserva la forma: RoundedCornerShape(50) sigue siendo pill, solo crece
 * horizontalmente.
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