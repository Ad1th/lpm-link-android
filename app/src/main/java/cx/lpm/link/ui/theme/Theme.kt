package cx.lpm.link.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LpmBlue = Color(0xFF2563EB)
private val LpmBlueDark = Color(0xFF60A5FA)

private val DarkColorScheme = darkColorScheme(
    primary = LpmBlueDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF8AB4F8),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    background = Color(0xFF0A0A0A),
    error = Color(0xFFEF4444),
)

private val LightColorScheme = lightColorScheme(
    primary = LpmBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF4B6B8A),
    surface = Color.White,
    surfaceVariant = Color(0xFFF5F5F5),
    background = Color(0xFFF8F9FA),
    error = Color(0xFFDC2626),
)

@Composable
fun LpmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
