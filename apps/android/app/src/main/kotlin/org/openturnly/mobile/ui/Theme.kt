package org.openturnly.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openturnly.mobile.Brand
import org.openturnly.mobile.rules.Appointments

/**
 * Colour, type and shape.
 *
 * The scheme is built from the brand colour only (no dynamic colour from the
 * wallpaper): a business owner should recognise their app at a glance. Greys
 * are zinc, like the web admin; the dark theme sits on near-black surfaces
 * with a hint of the brand in them.
 */
fun hex(value: String): Color {
    val clean = value.removePrefix("#")
    val number = clean.toLongOrNull(16) ?: return Color(0xFF0F766E)
    return Color(0xFF000000 or number)
}

/** A chip's tinted background and the strong text on it. */
@Immutable
data class Tone(val container: Color, val content: Color)

@Immutable
data class Extended(
    val dark: Boolean,
    val canvas: Color,
    val card: Color,
    val cardBorder: Color,
    val muted: Color,
    val subtle: Color,
    val skeleton: Color,
    val brand: Tone,
    val success: Tone,
    val danger: Tone,
    val warning: Tone,
    val orange: Tone,
    val info: Tone,
    val neutral: Tone,
    val whatsapp: Color,
    val people: List<Color>,
) {
    fun status(status: Appointments.Status): Tone = when (status) {
        Appointments.Status.UPCOMING -> brand
        Appointments.Status.CONFIRMED -> success
        Appointments.Status.CANCELLED -> danger
        Appointments.Status.NO_SHOW -> orange
        Appointments.Status.PENDING_PAYMENT -> warning
    }

    /** One colour per professional, by their position in the team. */
    fun person(index: Int): Color = if (index < 0) muted else people[index % people.size]
}

private val zinc50 = hex("#fafafa")
private val zinc100 = hex("#f4f4f5")
private val zinc200 = hex("#e4e4e7")
private val zinc300 = hex("#d4d4d8")
private val zinc400 = hex("#a1a1aa")
private val zinc500 = hex("#71717a")
private val zinc600 = hex("#52525b")
private val zinc700 = hex("#3f3f46")
private val zinc800 = hex("#27272a")
private val zinc900 = hex("#18181b")

private val peopleLight = listOf("#0d9488", "#6366f1", "#d97706", "#e11d48", "#0284c7", "#7c3aed", "#059669", "#ea580c").map(::hex)
private val peopleDark = listOf("#2dd4bf", "#818cf8", "#fbbf24", "#fb7185", "#38bdf8", "#a78bfa", "#34d399", "#fb923c").map(::hex)

object Palette {
    val brand: Color get() = hex(Brand.primaryColorHex)
    private val isDefaultBrand get() = Brand.primaryColorHex.equals(Brand.DEFAULT_COLOR, ignoreCase = true)

    /** The brand colour lifted so it reads on a dark background. */
    val brandDark: Color get() = if (isDefaultBrand) hex("#2dd4bf") else lerp(brand, Color.White, 0.45f)
    private val brandStrong get() = if (isDefaultBrand) hex("#115e59") else lerp(brand, Color.Black, 0.25f)
    private val tintLight get() = if (isDefaultBrand) hex("#ccfbf1") else lerp(brand, Color.White, 0.82f)
    private val tintLighter get() = if (isDefaultBrand) hex("#f0fdfa") else lerp(brand, Color.White, 0.94f)
    private val tintDark get() = if (isDefaultBrand) hex("#0b2622") else lerp(brand, Color.Black, 0.78f)
    private val tintDarkStrong get() = if (isDefaultBrand) hex("#134e4a") else lerp(brand, Color.Black, 0.55f)

    fun light(): ColorScheme = lightColorScheme(
        primary = brand, onPrimary = Color.White,
        primaryContainer = tintLight, onPrimaryContainer = brandStrong,
        secondary = brandStrong, onSecondary = Color.White,
        secondaryContainer = tintLighter, onSecondaryContainer = brandStrong,
        tertiary = hex("#b45309"), onTertiary = Color.White,
        tertiaryContainer = hex("#fef3c7"), onTertiaryContainer = hex("#78350f"),
        background = hex("#f5f6f6"), onBackground = zinc900,
        surface = hex("#f5f6f6"), onSurface = zinc900,
        surfaceVariant = zinc100, onSurfaceVariant = zinc600,
        surfaceTint = brand,
        surfaceContainerLowest = Color.White, surfaceContainerLow = hex("#fbfbfb"),
        surfaceContainer = hex("#f1f3f3"), surfaceContainerHigh = hex("#ebeded"), surfaceContainerHighest = zinc200,
        surfaceBright = Color.White, surfaceDim = zinc200,
        outline = zinc300, outlineVariant = zinc200,
        error = hex("#dc2626"), onError = Color.White, errorContainer = hex("#fee2e2"), onErrorContainer = hex("#991b1b"),
        inverseSurface = zinc800, inverseOnSurface = zinc50, inversePrimary = brandDark, scrim = Color.Black,
    )

    fun dark(): ColorScheme = darkColorScheme(
        primary = brandDark, onPrimary = hex("#042f2e"),
        primaryContainer = tintDarkStrong, onPrimaryContainer = hex("#ccfbf1"),
        secondary = lerp(brandDark, Color.White, 0.2f), onSecondary = hex("#042f2e"),
        secondaryContainer = tintDark, onSecondaryContainer = hex("#99f6e4"),
        tertiary = hex("#fbbf24"), onTertiary = hex("#451a03"),
        tertiaryContainer = hex("#451a03"), onTertiaryContainer = hex("#fde68a"),
        background = hex("#0a0f0e"), onBackground = zinc100,
        surface = hex("#0a0f0e"), onSurface = zinc100,
        surfaceVariant = hex("#1a2221"), onSurfaceVariant = zinc400,
        surfaceTint = brandDark,
        surfaceContainerLowest = hex("#070b0a"), surfaceContainerLow = hex("#111817"),
        surfaceContainer = hex("#141c1b"), surfaceContainerHigh = hex("#1a2423"), surfaceContainerHighest = hex("#222d2c"),
        surfaceBright = hex("#263130"), surfaceDim = hex("#0a0f0e"),
        outline = zinc700, outlineVariant = hex("#232b2a"),
        error = hex("#f87171"), onError = hex("#450a0a"), errorContainer = hex("#450a0a"), onErrorContainer = hex("#fecaca"),
        inverseSurface = zinc100, inverseOnSurface = zinc900, inversePrimary = brand, scrim = Color.Black,
    )

    fun extended(dark: Boolean): Extended = if (!dark) Extended(
        dark = false,
        canvas = hex("#f5f6f6"), card = Color.White, cardBorder = hex("#e8eaea"),
        muted = zinc500, subtle = zinc400, skeleton = hex("#e9ecec"),
        brand = Tone(tintLight, brandStrong),
        success = Tone(hex("#dcfce7"), hex("#166534")),
        danger = Tone(hex("#fee2e2"), hex("#b91c1c")),
        warning = Tone(hex("#fef3c7"), hex("#92400e")),
        orange = Tone(hex("#ffedd5"), hex("#9a3412")),
        info = Tone(hex("#dbeafe"), hex("#1e40af")),
        neutral = Tone(zinc100, zinc600),
        whatsapp = hex("#16a34a"),
        people = peopleLight,
    ) else Extended(
        dark = true,
        canvas = hex("#0a0f0e"), card = hex("#121a19"), cardBorder = hex("#1f2928"),
        muted = zinc400, subtle = zinc500, skeleton = hex("#1b2423"),
        brand = Tone(tintDark, hex("#5eead4")),
        success = Tone(hex("#052e16"), hex("#86efac")),
        danger = Tone(hex("#3b0d0d"), hex("#fca5a5")),
        warning = Tone(hex("#3a1d04"), hex("#fcd34d")),
        orange = Tone(hex("#3b1405"), hex("#fdba74")),
        info = Tone(hex("#132044"), hex("#93c5fd")),
        neutral = Tone(hex("#1c2423"), zinc300),
        whatsapp = hex("#4ade80"),
        people = peopleDark,
    )
}

val LocalExtended = staticCompositionLocalOf { Palette.extended(false) }

private val typography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Numbers that line up in columns: times, amounts. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")

@Composable
fun OpenTurnlyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) Palette.dark() else Palette.light()
    CompositionLocalProvider(LocalExtended provides Palette.extended(dark)) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
