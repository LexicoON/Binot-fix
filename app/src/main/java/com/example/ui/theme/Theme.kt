package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle

private val ExpressiveShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(48.dp)
)

/**
 * Estilos de color disponibles. Cada uno genera una paleta distinta desde el mismo seed.
 * - TONAL_SPOT: el default de Material 3, equilibrado.
 * - VIBRANT: colores más saturados y vivos.
 * - EXPRESSIVE: paleta expresiva de M3, con más contraste.
 * - RAINBOW: máxima variedad cromática.
 * - NEUTRAL: paleta casi monocromática, minimalista.
 */
enum class ColorStyle(val label: String) {
    TONAL_SPOT("Tonal Spot"),
    VIBRANT("Vibrant"),
    EXPRESSIVE("Expressive"),
    RAINBOW("Rainbow"),
    NEUTRAL("Neutral")
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BinotTheme(
    themeMode: Int, // 0 = System, 1 = Light, 2 = Slate Dark, 3 = Amoled Dark
    colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        1 -> false
        2, 3 -> true
        else -> isSystemDark
    }
    val isAmoled = themeMode == 3

    // Mapeo de nuestro enum al PaletteStyle de MaterialKolor.
    val paletteStyle = when (colorStyle) {
        ColorStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
        ColorStyle.VIBRANT -> PaletteStyle.Vibrant
        ColorStyle.EXPRESSIVE -> PaletteStyle.Expressive
        ColorStyle.RAINBOW -> PaletteStyle.Rainbow
        ColorStyle.NEUTRAL -> PaletteStyle.Neutral
    }

    val context = LocalContext.current

    // OPCIÓN X — ramificación:
    // - Si dynamicColor && SDK >= S → colores del wallpaper vía MaterialExpressiveTheme estándar.
    //   (DynamicMaterialExpressiveTheme de MaterialKolor 5.x ya no acepta colorScheme,
    //    solo seedColor, así que para wallpaper hay que usar el theme de material3).
    // - Si no → colores generados desde seed vía DynamicMaterialExpressiveTheme.
    // FIX: antes, si el device soportaba wallpaper colors, se usaba MaterialExpressiveTheme
    // con el esquema del sistema y el paletteStyle se ignoraba por completo (de ahí que los
    // 5 estilos no hicieran nada). Ahora el wallpaper solo aporta el SEED, y la paleta
    // siempre la genera MaterialKolor aplicando el estilo elegido.
    val useWallpaper = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val seed = if (useWallpaper) {
        // El primary del esquema del sistema es el color dominante del wallpaper.
        dynamicLightColorScheme(context).primary
    } else {
        PrimaryPurple
    }

    DynamicMaterialExpressiveTheme(
        seedColor = seed,
        motionScheme = MotionScheme.expressive(),
        isDark = isDark,
        isAmoled = isAmoled,
        style = paletteStyle,
        shapes = ExpressiveShapes,
        typography = Typography,
        content = content
    )
}