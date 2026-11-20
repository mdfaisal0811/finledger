package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.domain.model.Category
import com.example.domain.model.TransactionImage
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionSortOrder
import com.example.domain.model.TransactionType
import com.example.domain.model.UserSettings
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ReceiptAttachmentSection
import com.example.ui.components.TransactionRow
import com.example.ui.theme.AmountTextStyle
import com.example.ui.theme.LocalFinLedgerColors
import com.example.ui.viewmodel.TransactionFilterState

@Composable
fun TransactionsScreen(
    transactions: List<TransactionRecord>,
    filterState: TransactionFilterState,
    accounts: List<Account>,
    categories: List<Category>,
    transactionImages: List<TransactionImage> = emptyList(),
    settings: UserSettings,
    onUpdateFilter: ((TransactionFilterState) -> TransactionFilterState) -> Unit,
    onResetFilter: () -> Unit,
    onEditTransaction: (TransactionRecord) -> Unit,
    onDeleteTransaction: (TransactionRecord) -> Unit,
    onAttachImages: (String, List<PreparedReceiptImage>) -> Unit = { _, _ -> },
    onReplaceImage: (TransactionImage, PreparedReceiptImage) -> Unit = { _, _ -> },
    onDeleteImage: (TransactionImage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    var showSortMenu by remember { mutableStateOf(false) }
    var showAdvancedFilter by remember { mutableStateOf(false) }
    var selectedTxForDetail by remember { mutableStateOf<TransactionRecord?>(null) }
    var pendingDeleteTx by remember { mutableStateOf<TransactionRecord?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp)
        ) {
            // Header + Search + Sort/Filter buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transactions", style = MaterialTheme.typography.headlineMedium)
                    Row {
                        IconButton(
                            onClick = { showAdvancedFilter = true },
                            modifier = Modifier.testTag("tx_filter_button")
                        ) {
                            Icon(Icons.Outlined.FilterList, contentDescription = "Filter transactions")
                        }
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("tx_sort_button")
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort transactions")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                TransactionSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        onClick = {
                                            onUpdateFilter { it.copy(sortOrder = order) }
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = filterState.searchQuery,
                    onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
                    placeholder = { Text("Search description, account, category, person...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filterState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onUpdateFilter { it.copy(searchQuery = "") } }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_search_input")
                )

                // Quick type filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isAll = filterState.selectedType == null && !filterState.onlyTransferCharges && !filterState.onlyRepayments
                    FilterChip(
                        selected = isAll,
                        onClick = { onUpdateFilter { it.copy(selectedType = null, onlyTransferCharges = false, onlyRepayments = false) } },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = filterState.selectedType == TransactionType.INCOME,
                        onClick = { onUpdateFilter { it.copy(selectedType = TransactionType.INCOME, onlyTransferCharges = false, onlyRepayments = false) } },
                        label = { Text("Income") }
                    )
                    FilterChip(
                        selected = filterState.selectedType == TransactionType.EXPENSE,
                        onClick = { onUpdateFilter { it.copy(selectedType = TransactionType.EXPENSE, onlyTransferCharges = false, onlyRepayments = false) } },
                        label = { Text("Expenses") }
                    )
                    FilterChip(
                        selected = filterState.selectedType == TransactionType.TRANSFER,
                        onClick = { onUpdateFilter { it.copy(selectedType = TransactionType.TRANSFER, onlyTransferCharges = false, onlyRepayments = false) } },
                        label = { Text("Transfers") }
                    )
                    FilterChip(
                        selected = filterState.onlyTransferCharges,
                        onClick = { onUpdateFilter { it.copy(selectedType = null, onlyTransferCharges = true, onlyRepayments = false) } },
                        label = { Text("Transfer Charges") }
                    )
                    FilterChip(
                        selected = filterState.selectedType == TransactionType.CREDIT_CARD_PAYMENT,
                        onClick = { onUpdateFilter { it.copy(selectedType = TransactionType.CREDIT_CARD_PAYMENT, onlyTransferCharges = false, onlyRepayments = false) } },
                        label = { Text("Card Payments") }
                    )
                    FilterChip(
                        selected = filterState.selectedType == TransactionType.LENDING,
                        onClick = { onUpdateFilter { it.copy(selectedType = TransactionType.LENDING, onlyTransferCharges = false, onlyRepayments = false) } },
                        label = { Text("Lending") }
                    )
                    FilterChip(
                        selected = filterState.selectedType == TransactionType.BORROWING,
                        onClick = { onUpdateFilter { it.copy(selectedType = TransactionType.BORROWING, onlyTransferCharges = false, onlyRepayments = false) } },
                        label = { Text("Borrowing") }
                    )
                    FilterChip(
                        selected = filterState.onlyRepayments,
                        onClick = { onUpdateFilter { it.copy(selectedType = null, onlyTransferCharges = false, onlyRepayments = true) } },
                        label = { Text("Repayments") }
                    )
                }
            }

            if (transactions.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = "No matching transactions",
                        message = "Adjust your filters or add a new transaction using the + button.",
                        actionLabel = if (filterState != TransactionFilterState()) "Reset Filters" else null,
                        onAction = if (filterState != TransactionFilterState()) onResetFilter else null
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "${transactions.size} record(s) • ${filterState.sortOrder.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.textSecondary
                        )
                    }
                    items(transactions, key = { it.id }) { tx ->
                        TransactionRow(
                            tx = tx,
                            currencySymbol = settings.currencySymbol,
                            dateFormat = settings.dateFormat,
                            hideBalances = settings.hideBalances,
                            onClick = { selectedTxForDetail = tx }
                        )
                    }
                }
            }
        }
    }

    // Transaction Detail Bottom Sheet
    selectedTxForDetail?.let { tx ->
        val txImages = remember(transactionImages, tx.id) {
            transactionImages.filter { it.transactionId == tx.id }
        }
        TransactionDetailSheet(
            tx = tx,
            images = txImages,
            currencySymbol = settings.currencySymbol,
            dateFormat = settings.dateFormat,
            hideBalances = settings.hideBalances,
            onAttachImages = { added -> onAttachImages(tx.id, added) },
            onReplaceImage = onReplaceImage,
            onDeleteImage = onDeleteImage,
            onEdit = {
                selectedTxForDetail = null
                onEditTransaction(tx)
            },
            onDelete = {
                selectedTxForDetail = null
                pendingDeleteTx = tx
            },
            onDismiss = { selectedTxForDetail = null }
        )
    }

    pendingDeleteTx?.let { tx ->
        ConfirmationDialog(
            title = "Delete Transaction?",
            message = "This will permanently delete this ${tx.typeEnum().label} and reverse its effect on account balances.",
            onConfirm = {
                onDeleteTransaction(tx)
                pendingDeleteTx = null
            },
            onDismiss = { pendingDeleteTx = null }
        )
    }

    if (showAdvancedFilter) {
        AdvancedFilterDialog(
            filterState = filterState,
            accounts = accounts,
            categories = categories,
            currencySymbol = settings.currencySymbol,
            onApply = { updated ->
                onUpdateFilter { updated }
                showAdvancedFilter = false
            },
            onReset = {
                onResetFilter()
                showAdvancedFilter = false
            },
            onDismiss = { showAdvancedFilter = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    tx: TransactionRecord,
    images: List<TransactionImage> = emptyList(),
    currencySymbol: String,
    dateFormat: String,
    hideBalances: Boolean,
    onAttachImages: (List<PreparedReceiptImage>) -> Unit = {},
    onReplaceImage: (TransactionImage, PreparedReceiptImage) -> Unit = { _, _ -> },
    onDeleteImage: (TransactionImage) -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalFinLedgerColors.current
    val canDirectEdit = tx.typeEnum() in listOf(
        TransactionType.INCOME,
        TransactionType.EXPENSE,
        TransactionType.TRANSFER,
        TransactionType.CREDIT_CARD_PAYMENT
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(tx.typeEnum().label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                text = Formatters.formatMoney(tx.amount, currencySymbol, hideBalances),
                style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize)
            )
            if (tx.chargeAmount > 0.0) {
                Text(
                    text = "Additional Charge: ${Formatters.formatMoney(tx.chargeAmount, currencySymbol, hideBalances)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantic.expense
                )
            }
            HorizontalDivider()
            DetailRow("Description", tx.description.ifBlank { "—" })
            DetailRow("Account", tx.accountName)
            if (!tx.toAccountName.isNullOrBlank()) {
                DetailRow("Destination Account", tx.toAccountName)
            }
            if (!tx.categoryName.isNullOrBlank()) {
                DetailRow("Category", tx.categoryName)
            }
            if (!tx.counterpartyName.isNullOrBlank()) {
                DetailRow("Person", tx.counterpartyName)
            }
            DetailRow("Date & Time", Formatters.formatDateTime(tx.occurredAtMillis, dateFormat))
            if (!tx.notes.isNullOrBlank()) {
                DetailRow("Notes", tx.notes)
            }

            ReceiptAttachmentSection(
                savedImages = images,
                pendingImages = emptyList(),
                legacyReceiptUri = tx.receiptUri,
                onAddPendingImages = onAttachImages,
                onRemovePendingImage = {},
                onReplacePendingImage = { _, _ -> },
                onDeleteSavedImage = onDeleteImage,
                onReplaceSavedImage = onReplaceImage
            )

            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canDirectEdit) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = semantic.expense, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = semantic.expense)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalFinLedgerColors.current.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AdvancedFilterDialog(
    filterState: TransactionFilterState,
    accounts: List<Account>,
    categories: List<Category>,
    currencySymbol: String,
    onApply: (TransactionFilterState) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAccId by remember { mutableStateOf(filterState.selectedAccountId) }
    var selectedCatId by remember { mutableStateOf(filterState.selectedCategoryId) }
    var minAmt by remember { mutableStateOf(filterState.minAmount?.toString() ?: "") }
    var maxAmt by remember { mutableStateOf(filterState.maxAmount?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Transactions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Account", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedAccId == null,
                        onClick = { selectedAccId = null },
                        label = { Text("All Accounts") }
                    )
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = selectedAccId == acc.id,
                            onClick = { selectedAccId = acc.id },
                            label = { Text(acc.name) }
                        )
                    }
                }

                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCatId == null,
                        onClick = { selectedCatId = null },
                        label = { Text("All Categories") }
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCatId == cat.id,
                            onClick = { selectedCatId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minAmt,
                        onValueChange = { minAmt = it },
                        label = { Text("Min ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxAmt,
                        onValueChange = { maxAmt = it },
                        label = { Text("Max ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        filterState.copy(
                            selectedAccountId = selectedAccId,
                            selectedCategoryId = selectedCatId,
                            minAmount = minAmt.toDoubleOrNull(),
                            maxAmount = maxAmt.toDoubleOrNull()
                        )
                    )
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
