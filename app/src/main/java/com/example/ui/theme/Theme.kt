package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode

data class FinLedgerSemanticColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val transfer: Color,
    val transferContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val cardBorder: Color,
    val textSecondary: Color
)

val LocalFinLedgerColors = staticCompositionLocalOf {
    FinLedgerSemanticColors(
        income = SecondaryGreen,
        incomeContainer = SecondaryGreenContainerLight,
        expense = ExpenseRed,
        expenseContainer = ExpenseRedContainerLight,
        transfer = PrimaryBlue,
        transferContainer = PrimaryBlueContainerLight,
        warning = WarningAmber,
        warningContainer = WarningAmberContainerLight,
        cardBorder = LightBorder,
        textSecondary = LightTextSecondary
    )
}

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueContainerLight,
    onPrimaryContainer = PrimaryBlue,
    secondary = SecondaryGreen,
    onSecondary = Color.White,
    secondaryContainer = SecondaryGreenContainerLight,
    onSecondaryContainer = SecondaryGreen,
    tertiary = WarningAmber,
    onTertiary = Color.White,
    tertiaryContainer = WarningAmberContainerLight,
    onTertiaryContainer = WarningAmber,
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = ExpenseRedContainerLight,
    onErrorContainer = ExpenseRed,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightCard,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueContainerDark,
    onPrimaryContainer = PrimaryBlueContainerLight,
    secondary = SecondaryGreenLight,
    onSecondary = Color.White,
    secondaryContainer = SecondaryGreenContainerDark,
    onSecondaryContainer = SecondaryGreenContainerLight,
    tertiary = WarningAmberLight,
    onTertiary = Color.Black,
    tertiaryContainer = WarningAmberContainerDark,
    onTertiaryContainer = WarningAmberContainerLight,
    error = ExpenseRedLight,
    onError = Color.Black,
    errorContainer = ExpenseRedContainerDark,
    onErrorContainer = ExpenseRedContainerLight,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCard,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder
)

val FinLedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun FinLedgerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val semanticColors = if (useDark) {
        FinLedgerSemanticColors(
            income = SecondaryGreenLight,
            incomeContainer = SecondaryGreenContainerDark,
            expense = ExpenseRedLight,
            expenseContainer = ExpenseRedContainerDark,
            transfer = PrimaryBlueDark,
            transferContainer = PrimaryBlueContainerDark,
            warning = WarningAmberLight,
            warningContainer = WarningAmberContainerDark,
            cardBorder = DarkBorder,
            textSecondary = DarkTextSecondary
        )
    } else {
        FinLedgerSemanticColors(
            income = SecondaryGreen,
            incomeContainer = SecondaryGreenContainerLight,
            expense = ExpenseRed,
            expenseContainer = ExpenseRedContainerLight,
            transfer = PrimaryBlue,
            transferContainer = PrimaryBlueContainerLight,
            warning = WarningAmber,
            warningContainer = WarningAmberContainerLight,
            cardBorder = LightBorder,
            textSecondary = LightTextSecondary
        )
    }

    CompositionLocalProvider(LocalFinLedgerColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FinLedgerTypography,
            shapes = FinLedgerShapes,
            content = content
        )
    }
}
