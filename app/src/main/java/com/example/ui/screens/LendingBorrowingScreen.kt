package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.export.Formatters
import com.example.data.media.PreparedReceiptImage
import com.example.domain.model.Account
import com.example.domain.model.BorrowingRecord
import com.example.domain.model.LendingRecord
import com.example.domain.model.LoanStatus
import com.example.domain.model.RepaymentRecord
import com.example.domain.model.TransactionImage
import com.example.domain.model.UserSettings
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinCard
import com.example.ui.components.ReceiptAttachmentSection
import com.example.ui.theme.AmountTextStyle
import com.example.ui.theme.LocalFinLedgerColors

@Composable
fun LendingBorrowingScreen(
    initialTab: Int = 0,
    lendingRecords: List<LendingRecord>,
    borrowingRecords: List<BorrowingRecord>,
    repayments: List<RepaymentRecord>,
    accounts: List<Account>,
    transactionImages: List<TransactionImage> = emptyList(),
    settings: UserSettings,
    onBack: () -> Unit,
    onCreateLending: (LendingRecord, List<PreparedReceiptImage>) -> Unit,
    onUpdateLending: (LendingRecord, LendingRecord, List<PreparedReceiptImage>) -> Unit,
    onDeleteLending: (LendingRecord) -> Unit,
    onAddLendingRepayment: (LendingRecord, Double, Account, Long, String?, List<PreparedReceiptImage>) -> Unit,
    onCreateBorrowing: (BorrowingRecord, List<PreparedReceiptImage>) -> Unit,
    onUpdateBorrowing: (BorrowingRecord, BorrowingRecord, List<PreparedReceiptImage>) -> Unit,
    onDeleteBorrowing: (BorrowingRecord) -> Unit,
    onAddBorrowingRepayment: (BorrowingRecord, Double, Account, Long, String?, List<PreparedReceiptImage>) -> Unit,
    onDeleteRepayment: (RepaymentRecord) -> Unit,
    onAttachImagesToTx: (String, List<PreparedReceiptImage>) -> Unit = { _, _ -> },
    onReplaceImage: (TransactionImage, PreparedReceiptImage) -> Unit = { _, _ -> },
    onDeleteImage: (TransactionImage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var showAddLending by remember { mutableStateOf(false) }
    var editingLending by remember { mutableStateOf<LendingRecord?>(null) }
    var repayLendingTarget by remember { mutableStateOf<LendingRecord?>(null) }
    var deleteLendingTarget by remember { mutableStateOf<LendingRecord?>(null) }

    var showAddBorrowing by remember { mutableStateOf(false) }
    var editingBorrowing by remember { mutableStateOf<BorrowingRecord?>(null) }
    var repayBorrowingTarget by remember { mutableStateOf<BorrowingRecord?>(null) }
    var deleteBorrowingTarget by remember { mutableStateOf<BorrowingRecord?>(null) }

    var deleteRepaymentTarget by remember { mutableStateOf<RepaymentRecord?>(null) }

    val activeAccounts = remember(accounts) { accounts.filter { !it.archived } }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Text("Lending & Borrowing", style = MaterialTheme.typography.titleLarge)
                }
                Button(
                    onClick = {
                        if (selectedTab == 0) {
                            editingLending = null
                            showAddLending = true
                        } else {
                            editingBorrowing = null
                            showAddBorrowing = true
                        }
                    },
                    modifier = Modifier.padding(end = 8.dp).testTag("add_loan_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (selectedTab == 0) "Lent" else "Borrowed")
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Money Lent") }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Money Borrowed") }
            }

            Spacer(Modifier.height(10.dp))

            if (selectedTab == 0) {
                val totalLent = lendingRecords.sumOf { it.principalAmount }
                val totalReturned = lendingRecords.sumOf { it.returnedAmount }
                val outstanding = lendingRecords.sumOf { it.outstandingAmount }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LoanSummaryRow(
                            label1 = "Total Lent", val1 = Formatters.formatMoney(totalLent, settings.currencySymbol, settings.hideBalances),
                            label2 = "Returned", val2 = Formatters.formatMoney(totalReturned, settings.currencySymbol, settings.hideBalances),
                            label3 = "Outstanding", val3 = Formatters.formatMoney(outstanding, settings.currencySymbol, settings.hideBalances),
                            accent3 = semantic.warning
                        )
                    }
                    if (lendingRecords.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.AutoMirrored.Outlined.CallMade,
                                title = "No lending records",
                                message = "Track money lent to friends or family with partial/full repayments.",
                                actionLabel = "Record Money Lent",
                                onAction = {
                                    editingLending = null
                                    showAddLending = true
                                }
                            )
                        }
                    } else {
                        items(lendingRecords, key = { it.id }) { loan ->
                            val history = repayments.filter { it.loanId == loan.id }
                            val loanImages = remember(transactionImages, loan.transactionId) {
                                transactionImages.filter { it.transactionId == loan.transactionId }
                            }
                            LoanRecordCard(
                                personName = loan.personName,
                                description = loan.description,
                                accountName = loan.accountName,
                                dateMillis = loan.lentDateMillis,
                                dueDateMillis = loan.expectedReturnDateMillis,
                                principal = loan.principalAmount,
                                settledAmount = loan.returnedAmount,
                                outstanding = loan.outstandingAmount,
                                status = loan.statusEnum(),
                                repayments = history,
                                loanImages = loanImages,
                                transactionImages = transactionImages,
                                settings = settings,
                                settleLabel = "Record Return",
                                onRepay = { repayLendingTarget = loan },
                                onEdit = {
                                    editingLending = loan
                                    showAddLending = true
                                },
                                onDelete = { deleteLendingTarget = loan },
                                onDeleteRepayment = { deleteRepaymentTarget = it },
                                onAttachImages = { imgs ->
                                    if (loan.transactionId.isNotBlank()) onAttachImagesToTx(loan.transactionId, imgs)
                                },
                                onReplaceImage = onReplaceImage,
                                onDeleteImage = onDeleteImage
                            )
                        }
                    }
                }
            } else {
                val totalBorrowed = borrowingRecords.sumOf { it.principalAmount }
                val totalRepaid = borrowingRecords.sumOf { it.repaidAmount }
                val outstandingDebt = borrowingRecords.sumOf { it.outstandingDebt }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LoanSummaryRow(
                            label1 = "Total Borrowed", val1 = Formatters.formatMoney(totalBorrowed, settings.currencySymbol, settings.hideBalances),
                            label2 = "Repaid", val2 = Formatters.formatMoney(totalRepaid, settings.currencySymbol, settings.hideBalances),
                            label3 = "Outstanding", val3 = Formatters.formatMoney(outstandingDebt, settings.currencySymbol, settings.hideBalances),
                            accent3 = semantic.expense
                        )
                    }
                    if (borrowingRecords.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.AutoMirrored.Outlined.CallReceived,
                                title = "No borrowing records",
                                message = "Track money borrowed from others with repayment schedules.",
                                actionLabel = "Record Money Borrowed",
                                onAction = {
                                    editingBorrowing = null
                                    showAddBorrowing = true
                                }
                            )
                        }
                    } else {
                        items(borrowingRecords, key = { it.id }) { loan ->
                            val history = repayments.filter { it.loanId == loan.id }
                            val loanImages = remember(transactionImages, loan.transactionId) {
                                transactionImages.filter { it.transactionId == loan.transactionId }
                            }
                            LoanRecordCard(
                                personName = loan.personName,
                                description = loan.description,
                                accountName = loan.accountName,
                                dateMillis = loan.borrowedDateMillis,
                                dueDateMillis = loan.expectedRepaymentDateMillis,
                                principal = loan.principalAmount,
                                settledAmount = loan.repaidAmount,
                                outstanding = loan.outstandingDebt,
                                status = loan.statusEnum(),
                                repayments = history,
                                loanImages = loanImages,
                                transactionImages = transactionImages,
                                settings = settings,
                                settleLabel = "Repay Debt",
                                onRepay = { repayBorrowingTarget = loan },
                                onEdit = {
                                    editingBorrowing = loan
                                    showAddBorrowing = true
                                },
                                onDelete = { deleteBorrowingTarget = loan },
                                onDeleteRepayment = { deleteRepaymentTarget = it },
                                onAttachImages = { imgs ->
                                    if (loan.transactionId.isNotBlank()) onAttachImagesToTx(loan.transactionId, imgs)
                                },
                                onReplaceImage = onReplaceImage,
                                onDeleteImage = onDeleteImage
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddLending) {
        val editImages = remember(transactionImages, editingLending) {
            editingLending?.transactionId?.let { txId ->
                transactionImages.filter { it.transactionId == txId }
            }.orEmpty()
        }
        LoanFormDialog(
            isLending = true,
            editingLending = editingLending,
            editingBorrowing = null,
            accounts = activeAccounts,
            savedImages = editImages,
            settings = settings,
            onDeleteSavedImage = onDeleteImage,
            onReplaceSavedImage = onReplaceImage,
            onSaveLending = { rec, pending ->
                if (editingLending == null) onCreateLending(rec, pending)
                else onUpdateLending(editingLending!!, rec, pending)
                showAddLending = false
            },
            onSaveBorrowing = { _, _ -> },
            onDismiss = { showAddLending = false }
        )
    }

    if (showAddBorrowing) {
        val editImages = remember(transactionImages, editingBorrowing) {
            editingBorrowing?.transactionId?.let { txId ->
                transactionImages.filter { it.transactionId == txId }
            }.orEmpty()
        }
        LoanFormDialog(
            isLending = false,
            editingLending = null,
            editingBorrowing = editingBorrowing,
            accounts = activeAccounts,
            savedImages = editImages,
            settings = settings,
            onDeleteSavedImage = onDeleteImage,
            onReplaceSavedImage = onReplaceImage,
            onSaveLending = { _, _ -> },
            onSaveBorrowing = { rec, pending ->
                if (editingBorrowing == null) onCreateBorrowing(rec, pending)
                else onUpdateBorrowing(editingBorrowing!!, rec, pending)
                showAddBorrowing = false
            },
            onDismiss = { showAddBorrowing = false }
        )
    }

    repayLendingTarget?.let { loan ->
        RepaymentDialog(
            title = "Return from ${loan.personName}",
            accountLabel = "Receiving Account",
            maxAmount = loan.outstandingAmount,
            accounts = activeAccounts,
            settings = settings,
            onConfirm = { amt, acc, millis, notes, pending ->
                onAddLendingRepayment(loan, amt, acc, millis, notes, pending)
                repayLendingTarget = null
            },
            onDismiss = { repayLendingTarget = null }
        )
    }

    repayBorrowingTarget?.let { loan ->
        RepaymentDialog(
            title = "Repay ${loan.personName}",
            accountLabel = "Payment Account",
            maxAmount = loan.outstandingDebt,
            accounts = activeAccounts,
            settings = settings,
            onConfirm = { amt, acc, millis, notes, pending ->
                onAddBorrowingRepayment(loan, amt, acc, millis, notes, pending)
                repayBorrowingTarget = null
            },
            onDismiss = { repayBorrowingTarget = null }
        )
    }

    deleteLendingTarget?.let { loan ->
        ConfirmationDialog(
            title = "Delete Lending Record?",
            message = "This will delete ${loan.personName}'s lending record, all repayments, and reverse their balance effects.",
            onConfirm = { onDeleteLending(loan); deleteLendingTarget = null },
            onDismiss = { deleteLendingTarget = null }
        )
    }

    deleteBorrowingTarget?.let { loan ->
        ConfirmationDialog(
            title = "Delete Borrowing Record?",
            message = "This will delete ${loan.personName}'s borrowing record, all repayments, and reverse their balance effects.",
            onConfirm = { onDeleteBorrowing(loan); deleteBorrowingTarget = null },
            onDismiss = { deleteBorrowingTarget = null }
        )
    }

    deleteRepaymentTarget?.let { rep ->
        ConfirmationDialog(
            title = "Delete Repayment?",
            message = "This will remove this repayment of ${Formatters.formatMoney(rep.amount, settings.currencySymbol)} and reverse the account balance.",
            onConfirm = { onDeleteRepayment(rep); deleteRepaymentTarget = null },
            onDismiss = { deleteRepaymentTarget = null }
        )
    }
}

@Composable
private fun LoanSummaryRow(
    label1: String, val1: String,
    label2: String, val2: String,
    label3: String, val3: String,
    accent3: androidx.compose.ui.graphics.Color
) {
    val semantic = LocalFinLedgerColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FinCard(modifier = Modifier.weight(1f)) {
            Text(label1, style = MaterialTheme.typography.labelSmall, color = semantic.textSecondary)
            Text(val1, style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize))
        }
        FinCard(modifier = Modifier.weight(1f)) {
            Text(label2, style = MaterialTheme.typography.labelSmall, color = semantic.textSecondary)
            Text(val2, style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize), color = semantic.income)
        }
        FinCard(modifier = Modifier.weight(1f)) {
            Text(label3, style = MaterialTheme.typography.labelSmall, color = semantic.textSecondary)
            Text(val3, style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize), color = accent3)
        }
    }
}

@Composable
private fun LoanRecordCard(
    personName: String,
    description: String,
    accountName: String,
    dateMillis: Long,
    dueDateMillis: Long?,
    principal: Double,
    settledAmount: Double,
    outstanding: Double,
    status: LoanStatus,
    repayments: List<RepaymentRecord>,
    loanImages: List<TransactionImage>,
    transactionImages: List<TransactionImage>,
    settings: UserSettings,
    settleLabel: String,
    onRepay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteRepayment: (RepaymentRecord) -> Unit,
    onAttachImages: (List<PreparedReceiptImage>) -> Unit,
    onReplaceImage: (TransactionImage, PreparedReceiptImage) -> Unit,
    onDeleteImage: (TransactionImage) -> Unit
) {
    val semantic = LocalFinLedgerColors.current
    val progress = if (principal > 0) (settledAmount / principal).toFloat().coerceIn(0f, 1f) else 0f
    var expanded by remember { mutableStateOf(false) }

    FinCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(personName, style = MaterialTheme.typography.titleMedium)
                        Surface(
                            color = when (status) {
                                LoanStatus.SETTLED -> semantic.incomeContainer
                                LoanStatus.PARTIAL -> semantic.transferContainer
                                LoanStatus.OPEN -> semantic.warningContainer
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = status.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (status) {
                                    LoanStatus.SETTLED -> semantic.income
                                    LoanStatus.PARTIAL -> semantic.transfer
                                    LoanStatus.OPEN -> semantic.warning
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "$accountName • ${Formatters.formatDate(dateMillis, settings.dateFormat)}" +
                            (dueDateMillis?.let { " • Due ${Formatters.formatDate(it, settings.dateFormat)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.textSecondary
                    )
                    if (description.isNotBlank()) {
                        Text(description, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatMoney(outstanding, settings.currencySymbol, settings.hideBalances),
                        style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                        color = if (outstanding > 0) semantic.warning else semantic.income
                    )
                    Text(
                        text = "of ${Formatters.formatMoney(principal, settings.currencySymbol, settings.hideBalances)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantic.textSecondary
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (status == LoanStatus.SETTLED) semantic.income else MaterialTheme.colorScheme.primary
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status != LoanStatus.SETTLED) {
                    Button(onClick = onRepay, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(settleLabel)
                    }
                }
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                }
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = semantic.expense, modifier = Modifier.size(16.dp))
                }
            }

            if (loanImages.isNotEmpty() || expanded) {
                ReceiptAttachmentSection(
                    savedImages = loanImages,
                    pendingImages = emptyList(),
                    onAddPendingImages = onAttachImages,
                    onRemovePendingImage = {},
                    onReplacePendingImage = { _, _ -> },
                    onDeleteSavedImage = onDeleteImage,
                    onReplaceSavedImage = onReplaceImage
                )
            }

            if (repayments.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide Repayment History (${repayments.size})" else "View Repayment History (${repayments.size})")
                }
                if (expanded) {
                    HorizontalDivider()
                    repayments.forEach { rep ->
                        val repImages = remember(transactionImages, rep.transactionId) {
                            transactionImages.filter { it.transactionId == rep.transactionId }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "${Formatters.formatMoney(rep.amount, settings.currencySymbol, settings.hideBalances)} • ${rep.accountName}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        Formatters.formatDate(rep.repaidAtMillis, settings.dateFormat),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = semantic.textSecondary
                                    )
                                }
                                IconButton(onClick = { onDeleteRepayment(rep) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete repayment", tint = semantic.expense, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (repImages.isNotEmpty()) {
                                ReceiptAttachmentSection(
                                    savedImages = repImages,
                                    pendingImages = emptyList(),
                                    onAddPendingImages = { imgs ->
                                        if (rep.transactionId.isNotBlank()) onAttachImages(imgs)
                                    },
                                    onRemovePendingImage = {},
                                    onReplacePendingImage = { _, _ -> },
                                    onDeleteSavedImage = onDeleteImage,
                                    onReplaceSavedImage = onReplaceImage
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanFormDialog(
    isLending: Boolean,
    editingLending: LendingRecord?,
    editingBorrowing: BorrowingRecord?,
    accounts: List<Account>,
    savedImages: List<TransactionImage> = emptyList(),
    settings: UserSettings,
    onDeleteSavedImage: (TransactionImage) -> Unit = {},
    onReplaceSavedImage: (TransactionImage, PreparedReceiptImage) -> Unit = { _, _ -> },
    onSaveLending: (LendingRecord, List<PreparedReceiptImage>) -> Unit,
    onSaveBorrowing: (BorrowingRecord, List<PreparedReceiptImage>) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalFinLedgerColors.current
    var personName by remember { mutableStateOf(editingLending?.personName ?: editingBorrowing?.personName ?: "") }
    var amountText by remember { mutableStateOf((editingLending?.principalAmount ?: editingBorrowing?.principalAmount)?.toString() ?: "") }
    var selectedAccount by remember {
        val accId = editingLending?.accountId ?: editingBorrowing?.accountId ?: settings.defaultAccountId
        mutableStateOf(accounts.firstOrNull { it.id == accId } ?: accounts.firstOrNull())
    }
    var startMillis by remember { mutableLongStateOf(editingLending?.lentDateMillis ?: editingBorrowing?.borrowedDateMillis ?: System.currentTimeMillis()) }
    var dueMillis by remember { mutableStateOf<Long?>(editingLending?.expectedReturnDateMillis ?: editingBorrowing?.expectedRepaymentDateMillis) }
    var description by remember { mutableStateOf(editingLending?.description ?: editingBorrowing?.description ?: "") }
    var notes by remember { mutableStateOf(editingLending?.notes ?: editingBorrowing?.notes ?: "") }
    var reminderEnabled by remember { mutableStateOf(editingLending?.reminderEnabled ?: editingBorrowing?.reminderEnabled ?: true) }
    var pendingImages by remember { mutableStateOf<List<PreparedReceiptImage>>(emptyList()) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isLending) "Money Lent" else "Money Borrowed") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it; errorMsg = null },
                    label = { Text("Person Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("loan_person_input")
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorMsg = null },
                    label = { Text("Amount (${settings.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("loan_amount_input")
                )
                AccountDropdown(
                    label = if (isLending) "Payment Account" else "Receiving Account",
                    accounts = accounts,
                    selected = selectedAccount,
                    currencySymbol = settings.currencySymbol,
                    onSelect = { selectedAccount = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Formatters.formatDate(startMillis, settings.dateFormat), maxLines = 1)
                    }
                    OutlinedButton(onClick = { showDueDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(dueMillis?.let { "Due ${Formatters.formatDate(it, "dd MMM")}" } ?: "Set Due Date", maxLines = 1)
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Due Date Reminder", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }
                ReceiptAttachmentSection(
                    savedImages = savedImages,
                    pendingImages = pendingImages,
                    onAddPendingImages = { added -> pendingImages = pendingImages + added },
                    onRemovePendingImage = { rem -> pendingImages = pendingImages.filterNot { it === rem } },
                    onReplacePendingImage = { old, rep ->
                        pendingImages = pendingImages.map { if (it === old) rep else it }
                    },
                    onDeleteSavedImage = onDeleteSavedImage,
                    onReplaceSavedImage = onReplaceSavedImage
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = semantic.expense, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    val acc = selectedAccount
                    when {
                        personName.isBlank() -> errorMsg = "Enter person's name."
                        amt == null || amt <= 0.0 -> errorMsg = "Enter a valid positive amount."
                        acc == null -> errorMsg = "Select an account."
                        isLending -> onSaveLending(
                            LendingRecord(
                                id = editingLending?.id ?: "",
                                userId = editingLending?.userId ?: "",
                                personName = personName.trim(),
                                principalAmount = amt,
                                returnedAmount = editingLending?.returnedAmount ?: 0.0,
                                accountId = acc.id,
                                accountName = acc.name,
                                transactionId = editingLending?.transactionId ?: "",
                                lentDateMillis = startMillis,
                                expectedReturnDateMillis = dueMillis,
                                description = description.trim(),
                                notes = notes.trim().takeIf { it.isNotEmpty() },
                                status = editingLending?.status ?: LoanStatus.OPEN.name,
                                reminderEnabled = reminderEnabled
                            ),
                            pendingImages
                        )
                        else -> onSaveBorrowing(
                            BorrowingRecord(
                                id = editingBorrowing?.id ?: "",
                                userId = editingBorrowing?.userId ?: "",
                                personName = personName.trim(),
                                principalAmount = amt,
                                repaidAmount = editingBorrowing?.repaidAmount ?: 0.0,
                                accountId = acc.id,
                                accountName = acc.name,
                                transactionId = editingBorrowing?.transactionId ?: "",
                                borrowedDateMillis = startMillis,
                                expectedRepaymentDateMillis = dueMillis,
                                description = description.trim(),
                                notes = notes.trim().takeIf { it.isNotEmpty() },
                                status = editingBorrowing?.status ?: LoanStatus.OPEN.name,
                                reminderEnabled = reminderEnabled
                            ),
                            pendingImages
                        )
                    }
                },
                modifier = Modifier.testTag("save_loan_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showStartDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startMillis)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { startMillis = it }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showDueDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueMillis ?: (System.currentTimeMillis() + 7 * 86_400_000L))
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueMillis = state.selectedDateMillis
                    showDueDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDueDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun RepaymentDialog(
    title: String,
    accountLabel: String,
    maxAmount: Double,
    accounts: List<Account>,
    settings: UserSettings,
    onConfirm: (Double, Account, Long, String?, List<PreparedReceiptImage>) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(maxAmount.toString()) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var notes by remember { mutableStateOf("") }
    var pendingImages by remember { mutableStateOf<List<PreparedReceiptImage>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Outstanding: ${Formatters.formatMoney(maxAmount, settings.currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalFinLedgerColors.current.textSecondary
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorMsg = null },
                    label = { Text("Repayment Amount (${settings.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AccountDropdown(
                    label = accountLabel,
                    accounts = accounts,
                    selected = selectedAccount,
                    currencySymbol = settings.currencySymbol,
                    onSelect = { selectedAccount = it }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ReceiptAttachmentSection(
                    savedImages = emptyList(),
                    pendingImages = pendingImages,
                    onAddPendingImages = { added -> pendingImages = pendingImages + added },
                    onRemovePendingImage = { rem -> pendingImages = pendingImages.filterNot { it === rem } },
                    onReplacePendingImage = { old, rep ->
                        pendingImages = pendingImages.map { if (it === old) rep else it }
                    },
                    onDeleteSavedImage = {},
                    onReplaceSavedImage = { _, _ -> }
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = LocalFinLedgerColors.current.expense, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    val acc = selectedAccount
                    when {
                        amt == null || amt <= 0.0 -> errorMsg = "Enter a positive amount."
                        amt > maxAmount + 0.01 -> errorMsg = "Cannot exceed outstanding balance."
                        acc == null -> errorMsg = "Select an account."
                        else -> onConfirm(amt, acc, System.currentTimeMillis(), notes.trim().takeIf { it.isNotEmpty() }, pendingImages)
                    }
                }
            ) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
