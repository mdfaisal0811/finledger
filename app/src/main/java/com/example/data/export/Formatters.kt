package com.example.data.export

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CurrencyOption(
    val code: String,
    val symbol: String,
    val name: String
)

object Formatters {
    val supportedCurrencies = listOf(
        CurrencyOption("BDT", "৳", "Bangladeshi Taka"),
        CurrencyOption("USD", "$", "US Dollar"),
        CurrencyOption("EUR", "€", "Euro"),
        CurrencyOption("GBP", "£", "British Pound"),
        CurrencyOption("INR", "₹", "Indian Rupee"),
        CurrencyOption("JPY", "¥", "Japanese Yen"),
        CurrencyOption("AUD", "A$", "Australian Dollar"),
        CurrencyOption("CAD", "C$", "Canadian Dollar"),
        CurrencyOption("SAR", "﷼", "Saudi Riyal"),
        CurrencyOption("AED", "د.إ", "UAE Dirham")
    )

    val supportedDateFormats = listOf(
        "dd MMM yyyy",
        "MM/dd/yyyy",
        "yyyy-MM-dd",
        "dd/MM/yyyy"
    )

    fun formatMoney(
        amount: Double,
        currencySymbol: String = "৳",
        hideBalances: Boolean = false,
        showPlusSign: Boolean = false
    ): String {
        if (hideBalances) return "$currencySymbol••••"
        val formatter = DecimalFormat("#,##0.##")
        val absFormatted = formatter.format(kotlin.math.abs(amount))
        return when {
            amount < -0.001 -> "-$currencySymbol$absFormatted"
            showPlusSign && amount > 0.001 -> "+$currencySymbol$absFormatted"
            else -> "$currencySymbol$absFormatted"
        }
    }

    fun formatDate(millis: Long, pattern: String = "dd MMM yyyy"): String {
        return try {
            SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
        } catch (_: Exception) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
        }
    }

    fun formatTime(millis: Long): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))
    }

    fun formatDateTime(millis: Long, datePattern: String = "dd MMM yyyy"): String {
        return "${formatDate(millis, datePattern)} • ${formatTime(millis)}"
    }
}

object IconRegistry {
    val allIcons: List<Pair<String, ImageVector>> = listOf(
        "wallet" to Icons.Outlined.AccountBalanceWallet,
        "account_balance" to Icons.Outlined.AccountBalance,
        "bank" to Icons.Outlined.AccountBalance,
        "phone_android" to Icons.Outlined.PhoneAndroid,
        "credit_card" to Icons.Outlined.CreditCard,
        "savings" to Icons.Outlined.Savings,
        "payments" to Icons.Outlined.Payments,
        "attach_money" to Icons.Outlined.AttachMoney,
        "restaurant" to Icons.Outlined.Restaurant,
        "directions_car" to Icons.Outlined.DirectionsCar,
        "shopping_bag" to Icons.Outlined.ShoppingBag,
        "receipt_long" to Icons.AutoMirrored.Outlined.ReceiptLong,
        "local_hospital" to Icons.Outlined.LocalHospital,
        "school" to Icons.Outlined.School,
        "movie" to Icons.Outlined.Movie,
        "flight" to Icons.Outlined.Flight,
        "home" to Icons.Outlined.Home,
        "spa" to Icons.Outlined.Spa,
        "family_restroom" to Icons.Outlined.FamilyRestroom,
        "storefront" to Icons.Outlined.Storefront,
        "laptop_mac" to Icons.Outlined.LaptopMac,
        "trending_up" to Icons.AutoMirrored.Outlined.TrendingUp,
        "card_giftcard" to Icons.Outlined.CardGiftcard,
        "replay" to Icons.Outlined.Replay,
        "category" to Icons.Outlined.Category
    )

    val presetColors: List<Long> = listOf(
        0xFF2563EB,
        0xFF16A34A,
        0xFFDC2626,
        0xFFD97706,
        0xFF7C3AED,
        0xFF0891B2,
        0xFFDB2777,
        0xFF0F766E,
        0xFF4F46E5,
        0xFF64748B
    )

    fun resolve(key: String): ImageVector =
        allIcons.firstOrNull { it.first == key }?.second ?: Icons.Outlined.Category
}
