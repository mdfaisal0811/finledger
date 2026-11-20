package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.export.Formatters
import com.example.data.media.PreparedReceiptImage
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.TransactionImage
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.ui.components.ReceiptAttachmentSection
import com.example.ui.theme.AmountTextStyle
import com.example.ui.theme.LocalFinLedgerColors
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormDialog(
    initialType: TransactionType,
    editingTransaction: TransactionRecord? = null,
    preselectedToCard: Account? = null,
    accounts: List<Account>,
    categories: List<Category>,
    savedImages: List<TransactionImage> = emptyList(),
    currencySymbol: String,
    dateFormat: String,
    defaultAccountId: String?,
    onCreateCategory: (Category) -> Unit,
    onDeleteSavedImage: (TransactionImage) -> Unit = {},
    onReplaceSavedImage: (TransactionImage, PreparedReceiptImage) -> Unit = { _, _ -> },
    onSave: (TransactionRecord, TransactionRecord?, List<PreparedReceiptImage>) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalFinLedgerColors.current
    val activeAccounts = remember(accounts) { accounts.filter { !it.archived } }
    val nonCardAccounts = remember(activeAccounts) {
        activeAccounts.filter { it.typeEnum() != AccountType.CREDIT_CARD }
    }
    val creditCards = remember(activeAccounts) {
        activeAccounts.filter { it.typeEnum() == AccountType.CREDIT_CARD }
    }

    var txType by remember {
        mutableStateOf(editingTransaction?.typeEnum() ?: initialType)
    }

    val isTransferMode = txType == TransactionType.TRANSFER || txType == TransactionType.CREDIT_CARD_PAYMENT

    var amountText by remember {
        mutableStateOf(editingTransaction?.amount?.let { if (it > 0) it.toString() else "" } ?: "")
    }
    var chargeText by remember {
        mutableStateOf(editingTransaction?.chargeAmount?.let { if (it > 0) it.toString() else "0" } ?: "0")
    }

    val initialSource = remember(editingTransaction, activeAccounts, defaultAccountId, txType) {
        editingTransaction?.let { tx -> activeAccounts.firstOrNull { it.id == tx.accountId } }
            ?: if (txType == TransactionType.CREDIT_CARD_PAYMENT) {
                nonCardAccounts.firstOrNull { it.id == defaultAccountId } ?: nonCardAccounts.firstOrNull()
            } else {
                activeAccounts.firstOrNull { it.id == defaultAccountId } ?: activeAccounts.firstOrNull()
            }
    }
    var selectedSourceAccount by remember { mutableStateOf(initialSource) }

    val initialDest = remember(editingTransaction, activeAccounts, preselectedToCard, txType) {
        preselectedToCard
            ?: editingTransaction?.toAccountId?.let { id -> activeAccounts.firstOrNull { it.id == id } }
            ?: if (txType == TransactionType.CREDIT_CARD_PAYMENT) {
                creditCards.firstOrNull()
            } else {
                activeAccounts.firstOrNull { it.id != initialSource?.id }
            }
    }
    var selectedDestAccount by remember { mutableStateOf(initialDest) }

    val matchingCategories = remember(categories, txType) {
        val catType = if (txType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        categories.filter { it.typeEnum() == catType }
    }
    var selectedCategory by remember(txType, categories) {
        mutableStateOf(
            editingTransaction?.categoryId?.let { id -> matchingCategories.firstOrNull { it.id == id } }
                ?: matchingCategories.firstOrNull()
        )
    }

    var description by remember { mutableStateOf(editingTransaction?.description ?: "") }
    var notes by remember { mutableStateOf(editingTransaction?.notes ?: "") }
    var receiptUri by remember { mutableStateOf(editingTransaction?.receiptUri) }
    var pendingImages by remember { mutableStateOf<List<PreparedReceiptImage>>(emptyList()) }
    var occurredAtMillis by remember { mutableLongStateOf(editingTransaction?.occurredAtMillis ?: System.currentTimeMillis()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val amountVal = amountText.toDoubleOrNull() ?: 0.0
    val chargeVal = chargeText.toDoubleOrNull() ?: 0.0

    val showInsufficientWarning = remember(selectedSourceAccount, amountVal, chargeVal, txType) {
        val src = selectedSourceAccount ?: return@remember false
        if (src.typeEnum() == AccountType.CREDIT_CARD) {
            if (txType == TransactionType.EXPENSE) amountVal + chargeVal > src.availableCredit + 0.001 else false
        } else {
            when (txType) {
                TransactionType.EXPENSE, TransactionType.TRANSFER, TransactionType.CREDIT_CARD_PAYMENT ->
                    amountVal + chargeVal > src.currentBalance + 0.001
                else -> false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    editingTransaction != null -> "Edit ${txType.label}"
                    txType == TransactionType.TRANSFER -> "Transfer Money"
                    txType == TransactionType.CREDIT_CARD_PAYMENT -> "Pay Credit Card Bill"
                    else -> "Add ${txType.label}"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (txType == TransactionType.INCOME || txType == TransactionType.EXPENSE) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = txType == TransactionType.EXPENSE,
                            onClick = { txType = TransactionType.EXPENSE },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text("Expense") }
                        SegmentedButton(
                            selected = txType == TransactionType.INCOME,
                            onClick = { txType = TransactionType.INCOME },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text("Income") }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; validationError = null },
                    label = {
                        Text(if (txType == TransactionType.TRANSFER) "Transfer Amount ($currencySymbol)" else "Amount ($currencySymbol)")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_amount_input")
                )

                // Credit card quick payment chips
                if (txType == TransactionType.CREDIT_CARD_PAYMENT && selectedDestAccount != null) {
                    val card = selectedDestAccount!!
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (card.currentBalance > 0) {
                            OutlinedButton(
                                onClick = { amountText = card.currentBalance.toString() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Full ${Formatters.formatMoney(card.currentBalance, currencySymbol)}", maxLines = 1)
                            }
                        }
                        if (card.minimumPayment > 0) {
                            OutlinedButton(
                                onClick = { amountText = card.minimumPayment.toString() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Min ${Formatters.formatMoney(card.minimumPayment, currencySymbol)}", maxLines = 1)
                            }
                        }
                    }
                }

                // Source account dropdown
                val sourceList = if (txType == TransactionType.CREDIT_CARD_PAYMENT) nonCardAccounts else activeAccounts
                AccountDropdown(
                    label = when (txType) {
                        TransactionType.TRANSFER -> "From Account"
                        TransactionType.CREDIT_CARD_PAYMENT -> "Pay From Account"
                        else -> "Account"
                    },
                    accounts = sourceList,
                    selected = selectedSourceAccount,
                    currencySymbol = currencySymbol,
                    onSelect = {
                        selectedSourceAccount = it
                        if (isTransferMode && selectedDestAccount?.id == it.id) {
                            selectedDestAccount = activeAccounts.firstOrNull { acc -> acc.id != it.id }
                        }
                    }
                )

                // Destination account dropdown for Transfer or Credit Card Payment
                if (isTransferMode) {
                    val destCandidates = if (txType == TransactionType.CREDIT_CARD_PAYMENT) {
                        creditCards
                    } else {
                        activeAccounts.filter { it.id != selectedSourceAccount?.id }
                    }
                    AccountDropdown(
                        label = if (txType == TransactionType.CREDIT_CARD_PAYMENT) "To Credit Card" else "To Account",
                        accounts = destCandidates,
                        selected = selectedDestAccount,
                        currencySymbol = currencySymbol,
                        onSelect = { selectedDestAccount = it }
                    )
                }

                // Insufficient balance warning
                if (showInsufficientWarning) {
                    Surface(
                        color = semantic.warningContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = semantic.warning, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (selectedSourceAccount?.typeEnum() == AccountType.CREDIT_CARD) {
                                    "Exceeds available credit limit."
                                } else {
                                    "Warning: Amount exceeds current account balance."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.warning
                            )
                        }
                    }
                }

                // Transfer Charge / Optional Additional Charge
                OutlinedTextField(
                    value = chargeText,
                    onValueChange = { chargeText = it; validationError = null },
                    label = {
                        Text(
                            if (txType == TransactionType.TRANSFER) "Transfer Charge ($currencySymbol, optional)"
                            else "Additional Charge / Fee ($currencySymbol, optional)"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_charge_input")
                )

                // Live transfer summary breakdown
                if (txType == TransactionType.TRANSFER && amountVal > 0.0) {
                    Surface(
                        color = semantic.transferContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Linked Transfer Summary",
                                style = MaterialTheme.typography.labelMedium,
                                color = semantic.transfer
                            )
                            Text(
                                text = "${selectedSourceAccount?.name ?: "Source"} Deduction: ${Formatters.formatMoney(amountVal + chargeVal, currencySymbol)}",
                                style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            )
                            Text(
                                text = "${selectedDestAccount?.name ?: "Destination"} Addition: ${Formatters.formatMoney(amountVal, currencySymbol)}",
                                style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            )
                            if (chargeVal > 0.0) {
                                Text(
                                    text = "Transfer Charge (counted as expense): ${Formatters.formatMoney(chargeVal, currencySymbol)}",
                                    style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                    color = semantic.expense
                                )
                            }
                        }
                    }
                }

                // Category selector for Income / Expense
                if (txType == TransactionType.INCOME || txType == TransactionType.EXPENSE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryDropdown(
                            categories = matchingCategories,
                            selected = selectedCategory,
                            onSelect = { selectedCategory = it },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showNewCategoryDialog = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Create category")
                        }
                    }
                }

                // Date & Time row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(Formatters.formatDate(occurredAtMillis, dateFormat), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(Formatters.formatTime(occurredAtMillis), maxLines = 1)
                    }
                }

                // Description & Notes
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_description_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Receipt & Bill Attachments (All transaction types: Expense, Income, Transfer, Credit Card Purchase & Payment)
                ReceiptAttachmentSection(
                    savedImages = savedImages,
                    pendingImages = pendingImages,
                    legacyReceiptUri = receiptUri,
                    onAddPendingImages = { added -> pendingImages = pendingImages + added },
                    onRemovePendingImage = { rem -> pendingImages = pendingImages.filterNot { it === rem } },
                    onReplacePendingImage = { old, rep ->
                        pendingImages = pendingImages.map { if (it === old) rep else it }
                    },
                    onDeleteSavedImage = onDeleteSavedImage,
                    onReplaceSavedImage = onReplaceSavedImage,
                    onClearLegacyReceipt = { receiptUri = null }
                )

                if (!validationError.isNullOrBlank()) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.expense
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    val chg = chargeText.toDoubleOrNull() ?: 0.0
                    val src = selectedSourceAccount
                    val dst = selectedDestAccount

                    when {
                        amt == null || amt <= 0.0 -> validationError = "Enter a valid positive amount."
                        chg < 0.0 -> validationError = "Charge cannot be negative."
                        txType == TransactionType.INCOME && chg >= amt -> validationError = "Fee must be less than income amount."
                        src == null -> validationError = "Please select an account."
                        isTransferMode && dst == null -> validationError = "Please select a destination account."
                        isTransferMode && dst?.id == src.id -> validationError = "Source and destination accounts cannot be the same."
                        else -> {
                            val record = TransactionRecord(
                                id = editingTransaction?.id ?: "",
                                userId = editingTransaction?.userId ?: "",
                                type = txType.name,
                                amount = amt,
                                chargeAmount = chg,
                                accountId = src.id,
                                accountName = src.name,
                                toAccountId = if (isTransferMode) dst?.id else null,
                                toAccountName = if (isTransferMode) dst?.name else null,
                                categoryId = if (!isTransferMode) selectedCategory?.id else null,
                                categoryName = if (!isTransferMode) selectedCategory?.name else null,
                                description = description.trim().ifBlank {
                                    when (txType) {
                                        TransactionType.TRANSFER -> "Transfer to ${dst?.name ?: "Account"}"
                                        TransactionType.CREDIT_CARD_PAYMENT -> "Card bill payment"
                                        else -> selectedCategory?.name ?: txType.label
                                    }
                                },
                                notes = notes.trim().takeIf { it.isNotEmpty() },
                                receiptUri = receiptUri,
                                loanId = editingTransaction?.loanId,
                                repaymentId = editingTransaction?.repaymentId,
                                counterpartyName = editingTransaction?.counterpartyName,
                                occurredAtMillis = occurredAtMillis
                            )
                            onSave(record, editingTransaction, pendingImages)
                        }
                    }
                },
                modifier = Modifier.testTag("tx_save_button")
            ) {
                Text(if (editingTransaction == null) "Save" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = occurredAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { pickedDate ->
                            val oldCal = Calendar.getInstance().apply { timeInMillis = occurredAtMillis }
                            val newCal = Calendar.getInstance().apply {
                                timeInMillis = pickedDate
                                set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                                set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                            }
                            occurredAtMillis = newCal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = occurredAtMillis }
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedCal = Calendar.getInstance().apply {
                            timeInMillis = occurredAtMillis
                            set(Calendar.HOUR_OF_DAY, timeState.hour)
                            set(Calendar.MINUTE, timeState.minute)
                        }
                        occurredAtMillis = updatedCal.timeInMillis
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showNewCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = { Text("New ${if (txType == TransactionType.INCOME) "Income" else "Expense"} Category") },
            text = {
                OutlinedTextField(
                    value = catName,
                    onValueChange = { catName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (catName.isNotBlank()) {
                            onCreateCategory(
                                Category(
                                    name = catName.trim(),
                                    type = if (txType == TransactionType.INCOME) CategoryType.INCOME.name else CategoryType.EXPENSE.name,
                                    iconKey = "category",
                                    colorHex = 0xFF2563EB,
                                    systemDefault = false
                                )
                            )
                            showNewCategoryDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDropdown(
    label: String,
    accounts: List<Account>,
    selected: Account?,
    currencySymbol: String,
    onSelect: (Account) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected?.let {
                val balLabel = if (it.typeEnum() == AccountType.CREDIT_CARD) {
                    "Avail ${Formatters.formatMoney(it.availableCredit, currencySymbol)}"
                } else {
                    Formatters.formatMoney(it.currentBalance, currencySymbol)
                }
                "${it.name} ($balLabel)"
            } ?: "Select account",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { acc ->
                val balText = if (acc.typeEnum() == AccountType.CREDIT_CARD) {
                    "Debt ${Formatters.formatMoney(acc.currentBalance, currencySymbol)}"
                } else {
                    Formatters.formatMoney(acc.currentBalance, currencySymbol)
                }
                DropdownMenuItem(
                    text = { Text("${acc.name} (${acc.typeEnum().label}) • $balText") },
                    onClick = {
                        onSelect(acc)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selected: Category?,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.name ?: "Select category",
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = {
                        onSelect(cat)
                        expanded = false
                    }
                )
            }
        }
    }
}
