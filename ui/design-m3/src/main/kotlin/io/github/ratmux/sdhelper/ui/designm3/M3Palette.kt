package io.github.ratmux.sdhelper.ui.designm3

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The palette of the owner's Material You 3 design (indigo, static). Primary, secondary, surface and outline roles
 * are taken from the reference stylesheet, light and dark. The reference does not define the roles below, so they
 * are chosen to match: tertiary, error, the lowest and dimmed surfaces, inverse colors. Dynamic color is not used
 * (the design does not ask for it).
 */
internal object M3Palette {
    val light: ColorScheme =
        lightColorScheme(
            primary = Color(0xFF3F51B5),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDFE2FF),
            onPrimaryContainer = Color(0xFF00105C),
            secondary = Color(0xFF5B5E71),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE1E2F9),
            onSecondaryContainer = Color(0xFF171A2B),
            tertiary = Color(0xFF77536D),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFD7F1),
            onTertiaryContainer = Color(0xFF2D1228),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            background = Color(0xFFFBF8FF),
            onBackground = Color(0xFF1B1B21),
            surface = Color(0xFFFBF8FF),
            onSurface = Color(0xFF1B1B21),
            surfaceVariant = Color(0xFFE3E1EC),
            onSurfaceVariant = Color(0xFF45464F),
            surfaceTint = Color(0xFF3F51B5),
            inverseSurface = Color(0xFF303036),
            inverseOnSurface = Color(0xFFF2F0F7),
            inversePrimary = Color(0xFFBEC2FF),
            outline = Color(0xFF767680),
            outlineVariant = Color(0xFFC6C5D0),
            scrim = Color(0xFF000000),
            surfaceBright = Color(0xFFFBF8FF),
            surfaceDim = Color(0xFFDBD9E0),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF5F2FA),
            surfaceContainer = Color(0xFFEFEDF5),
            surfaceContainerHigh = Color(0xFFEAE7EF),
            surfaceContainerHighest = Color(0xFFE4E1E9),
        )

    val dark: ColorScheme =
        darkColorScheme(
            primary = Color(0xFFBEC2FF),
            onPrimary = Color(0xFF202765),
            primaryContainer = Color(0xFF38407D),
            onPrimaryContainer = Color(0xFFDFE0FF),
            secondary = Color(0xFFC3C4DC),
            onSecondary = Color(0xFF2C2F42),
            secondaryContainer = Color(0xFF43465A),
            onSecondaryContainer = Color(0xFFE0E1FA),
            tertiary = Color(0xFFE6BAD7),
            onTertiary = Color(0xFF44263D),
            tertiaryContainer = Color(0xFF5D3C55),
            onTertiaryContainer = Color(0xFFFFD7F1),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF121318),
            onBackground = Color(0xFFE3E1E9),
            surface = Color(0xFF121318),
            onSurface = Color(0xFFE3E1E9),
            surfaceVariant = Color(0xFF46464F),
            onSurfaceVariant = Color(0xFFC6C5D0),
            surfaceTint = Color(0xFFBEC2FF),
            inverseSurface = Color(0xFFE3E1E9),
            inverseOnSurface = Color(0xFF2F3036),
            inversePrimary = Color(0xFF3F51B5),
            outline = Color(0xFF90909A),
            outlineVariant = Color(0xFF46464F),
            scrim = Color(0xFF000000),
            surfaceBright = Color(0xFF38393F),
            surfaceDim = Color(0xFF121318),
            surfaceContainerLowest = Color(0xFF0D0E13),
            surfaceContainerLow = Color(0xFF1B1C22),
            surfaceContainer = Color(0xFF1F2026),
            surfaceContainerHigh = Color(0xFF2A2B31),
            surfaceContainerHighest = Color(0xFF35363D),
        )
}
