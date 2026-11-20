package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.export.Formatters
import com.example.domain.model.Account
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserSettings
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.FinCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.LocalFinLedgerColors
import com.example.ui.viewmodel.FinanceWorkspaceData

@Composable
fun MoreAndSettingsScreen(
    data: FinanceWorkspaceData,
    storedPin: String?,
    onOpenStatements: () -> Unit = {},
    onOpenLendingBorrowing: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenNotifications: () -> Unit,
    onUpdateSettings: (Map<String, Any?>, String?) -> Unit,
    onSetPinAndLock: (String, Boolean) -> Unit,
    onExportCsv: (android.net.Uri) -> Unit,
    onExportBackupJson: (android.net.Uri) -> Unit,
    onRestoreBackupJson: (android.net.Uri) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    val settings = data.settings
    val activeAccounts = remember(data.accounts) { data.accounts.filter { !it.archived } }

    var showProfileDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showDefaultAccountDialog by remember { mutableStateOf(false) }
    var showDateFormatDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onExportCsv) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExportBackupJson) }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onRestoreBackupJson) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onUpdateSettings(mapOf("notificationsEnabled" to true), "Notifications enabled")
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("More & Settings", style = MaterialTheme.typography.headlineMedium)
            }

            // Financial Tools Hub
            item {
                SectionHeader(title = "Financial Modules")
                Spacer(Modifier.height(8.dp))
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(
                        icon = Icons.Outlined.Description,
                        title = "Financial Statements",
                        subtitle = "Generate Custom, Weekly, Monthly, Yearly & Account statements",
                        onClick = onOpenStatements,
                        modifier = Modifier.testTag("more_statements_row")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.SwapHoriz,
                        title = "Money Lent & Borrowed",
                        subtitle = "Manage loans, repayments & due reminders",
                        onClick = onOpenLendingBorrowing,
                        modifier = Modifier.testTag("more_lending_row")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.PieChart,
                        title = "Budgets & Spending Alerts",
                        subtitle = "${data.budgets.size} configured budget(s)",
                        onClick = onOpenBudgets,
                        modifier = Modifier.testTag("more_budgets_row")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Category,
                        title = "Categories Manager",
                        subtitle = "${data.categories.size} income & expense categories",
                        onClick = onOpenCategories,
                        modifier = Modifier.testTag("more_categories_row")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Notifications Center",
                        subtitle = "${data.notifications.count { !it.read }} unread alert(s)",
                        onClick = onOpenNotifications
                    )
                }
            }

            // Profile & Workspace Preferences
            item {
                SectionHeader(title = "Preferences")
                Spacer(Modifier.height(8.dp))
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(
                        icon = Icons.Outlined.Person,
                        title = "User Profile",
                        subtitle = "${settings.displayName} (${settings.email.ifBlank { "Google Account" }})",
                        onClick = { showProfileDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Palette,
                        title = "App Theme",
                        subtitle = settings.themeModeEnum().name.lowercase().replaceFirstChar { it.uppercase() },
                        onClick = { showThemeDialog = true },
                        modifier = Modifier.testTag("setting_theme_row")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.CurrencyExchange,
                        title = "Currency",
                        subtitle = "${settings.currencyCode} (${settings.currencySymbol})",
                        onClick = { showCurrencyDialog = true },
                        modifier = Modifier.testTag("setting_currency_row")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    val defAccName = activeAccounts.firstOrNull { it.id == settings.defaultAccountId }?.name
                        ?: activeAccounts.firstOrNull { it.defaultAccount }?.name
                        ?: "Not set"
                    SettingsRow(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = "Default Account",
                        subtitle = defAccName,
                        onClick = { showDefaultAccountDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Schedule,
                        title = "Date Format",
                        subtitle = settings.dateFormat,
                        onClick = { showDateFormatDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Language,
                        title = "Language",
                        subtitle = settings.language,
                        onClick = { showLanguageDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Notification Settings",
                        subtitle = if (settings.notificationsEnabled) "Alerts at ${settings.budgetAlertThresholds.joinToString("%/")}% • ${settings.dueDateReminderDays}d reminder" else "Muted",
                        onClick = { showNotificationSettingsDialog = true }
                    )
                }
            }

            // Data, Backup & Privacy
            item {
                SectionHeader(title = "Backup, Export & Privacy")
                Spacer(Modifier.height(8.dp))
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(
                        icon = Icons.Outlined.Backup,
                        title = "Backup Workspace (JSON)",
                        subtitle = "Save accounts, transactions, budgets & loans",
                        onClick = { backupLauncher.launch("finledger_backup.json") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Restore,
                        title = "Restore from Backup",
                        subtitle = "Import a saved FinLedger JSON backup",
                        onClick = { restoreLauncher.launch(arrayOf("application/json")) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Download,
                        title = "Export Transactions (CSV)",
                        subtitle = "Download all ledger entries as spreadsheet",
                        onClick = { csvLauncher.launch("finledger_transactions.csv") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Hide Balances", style = MaterialTheme.typography.titleSmall)
                                Text("Mask financial figures across screens", style = MaterialTheme.typography.bodySmall, color = semantic.textSecondary)
                            }
                        }
                        Switch(
                            checked = settings.hideBalances,
                            onCheckedChange = { onUpdateSettings(mapOf("hideBalances" to it), null) }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = "App PIN Lock",
                        subtitle = if (settings.appLockEnabled && !storedPin.isNullOrBlank()) "Enabled (4-digit PIN active)" else "Disabled",
                        onClick = { showPinDialog = true }
                    )
                }
            }

            // Support & Account Sign Out
            item {
                SectionHeader(title = "Support & Account")
                Spacer(Modifier.height(8.dp))
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        title = "Help & Security Info",
                        subtitle = "Accounting rules, Google Sign-In & sync details",
                        onClick = { showHelpDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "About FinLedger",
                        subtitle = "Version 1.0 • Cloud Firestore Workspace",
                        onClick = { showAboutDialog = true }
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showSignOutConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = semantic.expense, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", color = semantic.expense, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showProfileDialog) {
        var name by remember { mutableStateOf(settings.displayName) }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Email: ${settings.email.ifBlank { "Managed by Google Sign-In" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onUpdateSettings(mapOf("displayName" to name.trim()), "Profile updated")
                        showProfileDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showProfileDialog = false }) { Text("Cancel") } }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSettings(mapOf("themeMode" to mode.name), "Theme updated")
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.themeModeEnum() == mode, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Close") } }
        )
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Currency") },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(Formatters.supportedCurrencies.size) { idx ->
                        val cur = Formatters.supportedCurrencies[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSettings(
                                        mapOf("currencyCode" to cur.code, "currencySymbol" to cur.symbol),
                                        "Currency set to ${cur.code} (${cur.symbol})"
                                    )
                                    showCurrencyDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.currencyCode == cur.code, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${cur.symbol}  ${cur.code} — ${cur.name}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCurrencyDialog = false }) { Text("Close") } }
        )
    }

    if (showDefaultAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultAccountDialog = false },
            title = { Text("Default Account") },
            text = {
                Column {
                    activeAccounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSettings(mapOf("defaultAccountId" to acc.id), "Default account set to ${acc.name}")
                                    showDefaultAccountDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.defaultAccountId == acc.id, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${acc.name} (${acc.typeEnum().label})")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDefaultAccountDialog = false }) { Text("Close") } }
        )
    }

    if (showDateFormatDialog) {
        AlertDialog(
            onDismissRequest = { showDateFormatDialog = false },
            title = { Text("Date Format") },
            text = {
                Column {
                    Formatters.supportedDateFormats.forEach { pattern ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSettings(mapOf("dateFormat" to pattern), "Date format updated")
                                    showDateFormatDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.dateFormat == pattern, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text("$pattern (${Formatters.formatDate(System.currentTimeMillis(), pattern)})")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDateFormatDialog = false }) { Text("Close") } }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Language") },
            text = {
                Column {
                    listOf("English", "বাংলা").forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSettings(mapOf("language" to lang), "Language preference saved")
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.language == lang, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(lang)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Close") } }
        )
    }

    if (showNotificationSettingsDialog) {
        var enabled by remember { mutableStateOf(settings.notificationsEnabled) }
        var thresholds by remember { mutableStateOf(settings.budgetAlertThresholds) }
        var reminderDaysText by remember { mutableStateOf(settings.dueDateReminderDays.toString()) }

        AlertDialog(
            onDismissRequest = { showNotificationSettingsDialog = false },
            title = { Text("Notification Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Alerts", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = { want ->
                                enabled = want
                                if (want && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                    Text("Budget Alert Thresholds", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(50, 75, 90, 100).forEach { pct ->
                            val active = pct in thresholds
                            FilterChip(
                                selected = active,
                                onClick = { thresholds = if (active) (thresholds - pct).sorted() else (thresholds + pct).sorted() },
                                label = { Text("$pct%") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = reminderDaysText,
                        onValueChange = { reminderDaysText = it },
                        label = { Text("Loan Due Reminder (days ahead)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val days = (reminderDaysText.toIntOrNull() ?: 3).coerceIn(0, 30)
                    onUpdateSettings(
                        mapOf(
                            "notificationsEnabled" to enabled,
                            "budgetAlertThresholds" to thresholds,
                            "dueDateReminderDays" to days
                        ),
                        "Notification settings saved"
                    )
                    showNotificationSettingsDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showNotificationSettingsDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var enableLock by remember { mutableStateOf(settings.appLockEnabled) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("App PIN Lock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Require PIN on launch")
                        Switch(checked = enableLock, onCheckedChange = { enableLock = it })
                    }
                    if (enableLock) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                            label = { Text("4-6 Digit PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!enableLock) {
                        onSetPinAndLock("", false)
                        showPinDialog = false
                    } else if (pinInput.length >= 4 || !storedPin.isNullOrBlank()) {
                        onSetPinAndLock(pinInput, true)
                        showPinDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancel") } }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help & Accounting Rules") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Transfers between accounts move balances atomically without inflating income or expenses. Transfer charges are counted as expenses.", style = MaterialTheme.typography.bodySmall)
                    Text("• Credit card purchases increase card debt and reduce available credit; bill repayments reduce bank balance and card debt without double-counting expenses.", style = MaterialTheme.typography.bodySmall)
                    Text("• Money Lent & Borrowed update account balances without distorting ordinary income/expense reports.", style = MaterialTheme.typography.bodySmall)
                    Text("• Account authentication and password security are managed by your Google Account via Google Sign-In.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("Got it") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About FinLedger") },
            text = {
                Text(
                    "FinLedger v1.0\n\nModern personal finance ledger built with Kotlin, Jetpack Compose, Material 3, and Cloud Firestore real-time atomic transactions.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Close") } }
        )
    }

    if (showSignOutConfirm) {
        ConfirmationDialog(
            title = "Sign Out?",
            message = "Your data remains safely synced in your Cloud Firestore workspace.",
            confirmLabel = "Sign Out",
            isDestructive = true,
            onConfirm = {
                showSignOutConfirm = false
                onSignOut()
            },
            onDismiss = { showSignOutConfirm = false }
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalFinLedgerColors.current.textSecondary)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = LocalFinLedgerColors.current.textSecondary)
    }
}
