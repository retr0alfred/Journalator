package dev.retr0alfred.journalator.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import dev.retr0alfred.journalator.settings.TextSize

/**
 * Material 3 is present as a behavioural substrate only — ripples, focus handling, text
 * selection, IME wiring, all the boring correctness. Its colour scheme is remapped onto the
 * street-art palette so that no default purple can leak through a component we did not
 * restyle by hand.
 */
@Composable
fun JournalatorTheme(
    reduceMotionSetting: Boolean,
    textSize: TextSize,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val context = LocalContext.current

    // Two independent ways for the user to say "stop moving": the in-app switch, and the
    // system-wide animator scale that accessibility users and battery savers already set.
    // Honouring only the first would ignore a preference they have already expressed.
    val systemAnimationsOff = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val motion = remember(reduceMotionSetting, systemAnimationsOff) {
        Motion(enabled = !reduceMotionSetting && !systemAnimationsOff)
    }
    val typography = remember(textSize) { journalatorTypography(textSize.scale) }

    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.secondary,
            onSecondary = colors.onPrimary,
            background = colors.surface,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.muted,
            error = colors.warning,
            onError = colors.onPrimary,
            outline = colors.muted,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.secondary,
            onSecondary = colors.onPrimary,
            background = colors.surface,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.muted,
            error = colors.warning,
            onError = colors.onPrimary,
            outline = colors.muted,
        )
    }

    CompositionLocalProvider(
        LocalJournalatorColors provides colors,
        LocalJournalatorTypography provides typography,
        LocalMotion provides motion,
        LocalContentColor provides colors.onSurface,
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

object JournalatorTheme {
    val colors: JournalatorColors
        @Composable @ReadOnlyComposable get() = LocalJournalatorColors.current

    val type: JournalatorTypography
        @Composable @ReadOnlyComposable get() = LocalJournalatorTypography.current

    val motion: Motion
        @Composable @ReadOnlyComposable get() = LocalMotion.current
}

/** Thin wrapper so screens never reach for Material's Text defaults by accident. */
@Composable
fun JText(
    text: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow =
        androidx.compose.ui.text.style.TextOverflow.Clip,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}
