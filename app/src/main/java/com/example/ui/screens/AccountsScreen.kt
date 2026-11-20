package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.export.Formatters
import com.example.data.export.IconRegistry
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.FinancialEngine
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.UserSettings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinCard
import com.example.ui.components.IconColorPickerDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.TransactionRow
import com.example.ui.theme.AmountTextStyle
import com.example.ui.theme.LocalFinLedgerColors

@Composable
fun AccountsScreen(
    accounts: List<Account>,
    transactions: List<TransactionRecord>,
    settings: UserSettings,
    initialSelectedAccount: Account? = null,
    onClearInitialSelected: () -> Unit = {},
    onSaveAccount: (Account) -> Unit,
    onArchiveAccount: (String, Boolean) -> Unit,
    onDeleteAccount: (String, Account?) -> Unit,
    onPayCreditCard: (Account) -> Unit,
    onEditTransaction: (TransactionRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    var showAccountForm by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var detailAccount by remember(initialSelectedAccount) { mutableStateOf(initialSelectedAccount) }
    var accountPendingDelete by remember { mutableStateOf<Account?>(null) }

    val activeAccounts = accounts.filter { !it.archived }
    val archivedAccounts = accounts.filter { it.archived }
    val totalAssets = activeAccounts.filter { it.typeEnum() != AccountType.CREDIT_CARD }.sumOf { it.currentBalance }
    val totalCardDebt = activeAccounts.filter { it.typeEnum() == AccountType.CREDIT_CARD }.sumOf { it.currentBalance }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Accounts & Cards", style = MaterialTheme.typography.headlineMedium)
                    Button(
                        onClick = {
                            editingAccount = null
                            showAccountForm = true
                        },
                        modifier = Modifier.testTag("add_account_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Account")
                    }
                }
            }

            // Summary strip
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FinCard(modifier = Modifier.weight(1f)) {
                        Text("Total Assets", style = MaterialTheme.typography.labelMedium, color = semantic.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = Formatters.formatMoney(totalAssets, settings.currencySymbol, settings.hideBalances),
                            style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                            color = semantic.income
                        )
                    }
                    FinCard(modifier = Modifier.weight(1f)) {
                        Text("Card Debt", style = MaterialTheme.typography.labelMedium, color = semantic.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = Formatters.formatMoney(totalCardDebt, settings.currencySymbol, settings.hideBalances),
                            style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                            color = semantic.expense
                        )
                    }
                }
            }

            if (activeAccounts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = "No active accounts",
                        message = "Create Cash, Bank, Mobile Wallet, Credit Card, or Savings accounts.",
                        actionLabel = "Add Account",
                        onAction = {
                            editingAccount = null
                            showAccountForm = true
                        }
                    )
                }
            } else {
                items(activeAccounts, key = { it.id }) { acc ->
                    AccountCardItem(
                        account = acc,
                        settings = settings,
                        onClick = { detailAccount = acc }
                    )
                }
            }

            if (archivedAccounts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionHeader(title = "Archived Accounts (${archivedAccounts.size})")
                }
                items(archivedAccounts, key = { it.id }) { acc ->
                    AccountCardItem(
                        account = acc,
                        settings = settings,
                        onClick = { detailAccount = acc }
                    )
                }
            }
        }
    }

    if (showAccountForm) {
        AccountFormDialog(
            editingAccount = editingAccount,
            currencySymbol = settings.currencySymbol,
            onSave = { acc ->
                onSaveAccount(acc)
                showAccountForm = false
            },
            onDismiss = { showAccountForm = false }
        )
    }

    detailAccount?.let { acc ->
        val freshAccount = accounts.firstOrNull { it.id == acc.id } ?: acc
        val accountTxs = transactions.filter { it.accountId == freshAccount.id || it.toAccountId == freshAccount.id }
        AccountDetailSheet(
            account = freshAccount,
            transactions = accountTxs,
            settings = settings,
            onEdit = {
                detailAccount = null
                onClearInitialSelected()
                editingAccount = freshAccount
                showAccountForm = true
            },
            onSetDefault = {
                onSaveAccount(freshAccount.copy(defaultAccount = true))
            },
            onArchiveToggle = {
                onArchiveAccount(freshAccount.id, !freshAccount.archived)
                detailAccount = null
                onClearInitialSelected()
            },
            onDeleteRequest = {
                detailAccount = null
                onClearInitialSelected()
                accountPendingDelete = freshAccount
            },
            onPayCardBill = {
                detailAccount = null
                onClearInitialSelected()
                onPayCreditCard(freshAccount)
            },
            onSelectTransaction = { tx ->
                detailAccount = null
                onClearInitialSelected()
                onEditTransaction(tx)
            },
            onDismiss = {
                detailAccount = null
                onClearInitialSelected()
            }
        )
    }

    accountPendingDelete?.let { target ->
        val hasTxs = transactions.any { it.accountId == target.id || it.toAccountId == target.id }
        val migrationCandidates = accounts.filter { it.id != target.id && !it.archived }
        DeleteAccountDialog(
            account = target,
            hasTransactions = hasTxs,
            migrationCandidates = migrationCandidates,
            onArchiveInstead = {
                onArchiveAccount(target.id, true)
                accountPendingDelete = null
            },
            onConfirmDelete = { migrateTo ->
                onDeleteAccount(target.id, migrateTo)
                accountPendingDelete = null
            },
            onDismiss = { accountPendingDelete = null }
        )
    }
}

@Composable
private fun AccountCardItem(
    account: Account,
    settings: UserSettings,
    onClick: () -> Unit
) {
    val semantic = LocalFinLedgerColors.current
    val isCard = account.typeEnum() == AccountType.CREDIT_CARD
    FinCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(account.colorHex).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconRegistry.resolve(account.iconKey),
                    contentDescription = account.name,
                    tint = Color(account.colorHex),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(account.name, style = MaterialTheme.typography.titleMedium)
                    if (account.defaultAccount) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "Default",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (account.archived) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "Archived",
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.textSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                val sub = buildString {
                    append(account.typeEnum().label)
                    if (!account.bankName.isNullOrBlank()) append(" • ${account.bankName}")
                    if (!account.accountNumber.isNullOrBlank()) append(" • ${account.accountNumber}")
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = semantic.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.formatMoney(account.currentBalance, settings.currencySymbol, settings.hideBalances),
                    style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                    color = if (isCard && account.currentBalance > 0) semantic.expense else MaterialTheme.colorScheme.onSurface
                )
                if (isCard) {
                    Text(
                        text = "Outstanding",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantic.textSecondary
                    )
                }
            }
        }

        if (isCard && account.creditLimit > 0.0) {
            Spacer(Modifier.height(10.dp))
            val utilization = (account.currentBalance / account.creditLimit).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { utilization },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (utilization > 0.8f) semantic.expense else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Available: ${Formatters.formatMoney(account.availableCredit, settings.currencySymbol, settings.hideBalances)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.income
                )
                Text(
                    text = "Limit: ${Formatters.formatMoney(account.creditLimit, settings.currencySymbol, settings.hideBalances)} • Due day ${account.paymentDueDay}",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.textSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDetailSheet(
    account: Account,
    transactions: List<TransactionRecord>,
    settings: UserSettings,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDeleteRequest: () -> Unit,
    onPayCardBill: () -> Unit,
    onSelectTransaction: (TransactionRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalFinLedgerColors.current
    val isCard = account.typeEnum() == AccountType.CREDIT_CARD
    val summary = remember(account, transactions) { FinancialEngine.computeAccountSummary(account, transactions) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(account.name, style = MaterialTheme.typography.headlineSmall)
                        Text(account.typeEnum().label, style = MaterialTheme.typography.bodySmall, color = semantic.textSecondary)
                    }
                    Text(
                        text = Formatters.formatMoney(account.currentBalance, settings.currencySymbol, settings.hideBalances),
                        style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
                        color = if (isCard && account.currentBalance > 0) semantic.expense else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isCard) {
                item {
                    FinCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Credit Card Details", style = MaterialTheme.typography.titleSmall)
                            Text("Bank: ${account.bankName ?: "—"}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Credit Limit: ${Formatters.formatMoney(account.creditLimit, settings.currencySymbol, settings.hideBalances)}  |  Available: ${Formatters.formatMoney(account.availableCredit, settings.currencySymbol, settings.hideBalances)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Billing Day: ${account.billingCycleDay}  |  Due Day: ${account.paymentDueDay}  |  Min Payment: ${Formatters.formatMoney(account.minimumPayment, settings.currencySymbol, settings.hideBalances)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = onPayCardBill,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Pay Credit Card Bill")
                            }
                        }
                    }
                }
            }

            // Account-specific report summary
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinCard(modifier = Modifier.weight(1f)) {
                        Text("Income", style = MaterialTheme.typography.labelSmall, color = semantic.textSecondary)
                        Text(
                            Formatters.formatMoney(summary.totalIncome, settings.currencySymbol, settings.hideBalances),
                            style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                            color = semantic.income
                        )
                    }
                    FinCard(modifier = Modifier.weight(1f)) {
                        Text("Expense", style = MaterialTheme.typography.labelSmall, color = semantic.textSecondary)
                        Text(
                            Formatters.formatMoney(summary.totalExpense, settings.currencySymbol, settings.hideBalances),
                            style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                            color = semantic.expense
                        )
                    }
                    FinCard(modifier = Modifier.weight(1f)) {
                        Text("Transfers", style = MaterialTheme.typography.labelSmall, color = semantic.textSecondary)
                        Text(
                            "+${Formatters.formatMoney(summary.totalTransfersIn, settings.currencySymbol, settings.hideBalances)}",
                            style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                            color = semantic.transfer
                        )
                    }
                }
            }

            // Action buttons
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    if (!account.defaultAccount && !account.archived) {
                        OutlinedButton(onClick = onSetDefault, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Default")
                        }
                    }
                    OutlinedButton(onClick = onArchiveToggle, modifier = Modifier.weight(1f)) {
                        Icon(
                            if (account.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (account.archived) "Restore" else "Archive")
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onDeleteRequest, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = semantic.expense, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Account", color = semantic.expense)
                }
            }

            item {
                val label = if (isCard) "Purchases & Bill Repayments (${transactions.size})" else "Account History (${transactions.size})"
                Text(label, style = MaterialTheme.typography.titleMedium)
            }

            if (transactions.isEmpty()) {
                item {
                    Text("No transactions recorded for this account yet.", style = MaterialTheme.typography.bodySmall, color = semantic.textSecondary)
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    val canEdit = tx.typeEnum() in listOf(
                        TransactionType.INCOME,
                        TransactionType.EXPENSE,
                        TransactionType.TRANSFER,
                        TransactionType.CREDIT_CARD_PAYMENT
                    )
                    TransactionRow(
                        tx = tx,
                        currencySymbol = settings.currencySymbol,
                        dateFormat = settings.dateFormat,
                        hideBalances = settings.hideBalances,
                        onClick = { if (canEdit) onSelectTransaction(tx) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFormDialog(
    editingAccount: Account?,
    currencySymbol: String,
    onSave: (Account) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalFinLedgerColors.current
    var name by remember { mutableStateOf(editingAccount?.name ?: "") }
    var accountType by remember { mutableStateOf(editingAccount?.typeEnum() ?: AccountType.CASH) }
    var openingBalanceText by remember { mutableStateOf(editingAccount?.openingBalance?.toString() ?: "0") }
    var creditLimitText by remember { mutableStateOf(editingAccount?.creditLimit?.toString() ?: "50000") }
    var bankName by remember { mutableStateOf(editingAccount?.bankName ?: "") }
    var accountNumber by remember { mutableStateOf(editingAccount?.accountNumber ?: "") }
    var billingDayText by remember { mutableStateOf((editingAccount?.billingCycleDay ?: 1).toString()) }
    var dueDayText by remember { mutableStateOf((editingAccount?.paymentDueDay ?: 15).toString()) }
    var minPayText by remember { mutableStateOf((editingAccount?.minimumPayment ?: 0.0).toString()) }
    var notes by remember { mutableStateOf(editingAccount?.notes ?: "") }
    var iconKey by remember { mutableStateOf(editingAccount?.iconKey ?: "wallet") }
    var colorHex by remember { mutableLongStateOf(editingAccount?.colorHex ?: 0xFF2563EB) }
    var defaultAccount by remember { mutableStateOf(editingAccount?.defaultAccount ?: false) }

    var typeExpanded by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingAccount == null) "Add Account" else "Edit Account") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text(if (accountType == AccountType.CREDIT_CARD) "Card Name" else "Account Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("account_name_input")
                )

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = accountType.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        AccountType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.label) },
                                onClick = {
                                    accountType = t
                                    iconKey = when (t) {
                                        AccountType.CASH -> "wallet"
                                        AccountType.BANK -> "account_balance"
                                        AccountType.MOBILE_WALLET -> "phone_android"
                                        AccountType.CREDIT_CARD -> "credit_card"
                                        AccountType.SAVINGS -> "savings"
                                        AccountType.OTHER -> "payments"
                                    }
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = openingBalanceText,
                    onValueChange = { openingBalanceText = it },
                    label = {
                        Text(
                            if (accountType == AccountType.CREDIT_CARD) "Initial Outstanding Balance ($currencySymbol)"
                            else "Opening Balance ($currencySymbol)"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (accountType == AccountType.CREDIT_CARD) {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = creditLimitText,
                        onValueChange = { creditLimitText = it },
                        label = { Text("Credit Limit ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = billingDayText,
                            onValueChange = { billingDayText = it },
                            label = { Text("Billing Day") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dueDayText,
                            onValueChange = { dueDayText = it },
                            label = { Text("Due Day") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = minPayText,
                        onValueChange = { minPayText = it },
                        label = { Text("Minimum Payment ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text("Account / Card Ref (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pick Icon & Color")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Set as Default Account", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = defaultAccount, onCheckedChange = { defaultAccount = it })
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = semantic.expense, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val openBal = openingBalanceText.toDoubleOrNull()
                    if (name.isBlank()) {
                        errorMsg = "Account name is required."
                    } else if (openBal == null) {
                        errorMsg = "Enter a valid numeric balance."
                    } else {
                        val acc = Account(
                            id = editingAccount?.id ?: "",
                            userId = editingAccount?.userId ?: "",
                            name = name.trim(),
                            type = accountType.name,
                            openingBalance = openBal,
                            currentBalance = editingAccount?.currentBalance ?: openBal,
                            creditLimit = if (accountType == AccountType.CREDIT_CARD) (creditLimitText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            bankName = bankName.trim().takeIf { it.isNotEmpty() },
                            accountNumber = accountNumber.trim().takeIf { it.isNotEmpty() },
                            billingCycleDay = (billingDayText.toIntOrNull() ?: 1).coerceIn(1, 31),
                            paymentDueDay = (dueDayText.toIntOrNull() ?: 15).coerceIn(1, 31),
                            minimumPayment = (minPayText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                            iconKey = iconKey,
                            colorHex = colorHex,
                            notes = notes.trim().takeIf { it.isNotEmpty() },
                            archived = editingAccount?.archived ?: false,
                            defaultAccount = defaultAccount
                        )
                        onSave(acc)
                    }
                },
                modifier = Modifier.testTag("save_account_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showPicker) {
        IconColorPickerDialog(
            selectedIconKey = iconKey,
            selectedColorHex = colorHex,
            onSelect = { k, c -> iconKey = k; colorHex = c },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    account: Account,
    hasTransactions: Boolean,
    migrationCandidates: List<Account>,
    onArchiveInstead: () -> Unit,
    onConfirmDelete: (Account?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTarget by remember { mutableStateOf(migrationCandidates.firstOrNull()) }
    var deleteHistoryToo by remember { mutableStateOf(migrationCandidates.isEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${account.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (hasTransactions) {
                        "This account has existing transactions. Archive it to keep history intact, or migrate its transactions to another account."
                    } else {
                        "Are you sure you want to permanently delete this account?"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (hasTransactions && migrationCandidates.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !deleteHistoryToo, onClick = { deleteHistoryToo = false })
                        Text("Migrate transactions to:", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!deleteHistoryToo) {
                        migrationCandidates.forEach { cand ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                                RadioButton(selected = selectedTarget?.id == cand.id, onClick = { selectedTarget = cand })
                                Text(cand.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = deleteHistoryToo, onClick = { deleteHistoryToo = true })
                        Text("Delete account & its transactions", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (hasTransactions && !account.archived) {
                    TextButton(onClick = onArchiveInstead) { Text("Archive Instead") }
                }
                TextButton(
                    onClick = {
                        val migrateTo = if (hasTransactions && !deleteHistoryToo) selectedTarget else null
                        onConfirmDelete(migrateTo)
                    }
                ) {
                    Text("Delete", color = LocalFinLedgerColors.current.expense, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
