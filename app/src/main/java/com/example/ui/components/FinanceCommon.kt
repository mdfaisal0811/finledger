package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.export.Formatters
import com.example.data.export.IconRegistry
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.ui.theme.AmountTextStyle
import com.example.ui.theme.LocalFinLedgerColors

@Composable
fun FinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val semantic = LocalFinLedgerColors.current
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, semantic.cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun TransactionRow(
    tx: TransactionRecord,
    currencySymbol: String,
    dateFormat: String,
    hideBalances: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    val type = tx.typeEnum()

    val (badgeColor, badgeBg, signPrefix, icon) = when (type) {
        TransactionType.INCOME -> Quad(semantic.income, semantic.incomeContainer, "+", Icons.Outlined.ArrowDownward)
        TransactionType.EXPENSE -> Quad(semantic.expense, semantic.expenseContainer, "-", Icons.Outlined.ArrowUpward)
        TransactionType.TRANSFER -> Quad(semantic.transfer, semantic.transferContainer, "", Icons.Outlined.SwapHoriz)
        TransactionType.CREDIT_CARD_PAYMENT -> Quad(semantic.transfer, semantic.transferContainer, "", Icons.Outlined.CreditCard)
        TransactionType.LENDING -> Quad(semantic.warning, semantic.warningContainer, "-", Icons.AutoMirrored.Outlined.CallMade)
        TransactionType.BORROWING -> Quad(semantic.income, semantic.incomeContainer, "+", Icons.AutoMirrored.Outlined.CallReceived)
        TransactionType.LENT_REPAYMENT -> Quad(semantic.income, semantic.incomeContainer, "+", Icons.AutoMirrored.Outlined.CallReceived)
        TransactionType.BORROWED_REPAYMENT -> Quad(semantic.expense, semantic.expenseContainer, "-", Icons.AutoMirrored.Outlined.CallMade)
    }

    val title = tx.description.ifBlank {
        when (type) {
            TransactionType.TRANSFER -> "${tx.accountName} -> ${tx.toAccountName ?: "Account"}"
            TransactionType.CREDIT_CARD_PAYMENT -> "Card Bill: ${tx.toAccountName ?: "Card"}"
            else -> tx.categoryName ?: tx.counterpartyName ?: type.label
        }
    }

    val subtitle = buildString {
        if (type == TransactionType.TRANSFER || type == TransactionType.CREDIT_CARD_PAYMENT) {
            append("${tx.accountName} → ${tx.toAccountName ?: ""}")
        } else {
            append(tx.accountName)
            if (!tx.categoryName.isNullOrBlank()) append(" • ${tx.categoryName}")
            if (!tx.counterpartyName.isNullOrBlank()) append(" • ${tx.counterpartyName}")
        }
        append(" • ${Formatters.formatDate(tx.occurredAtMillis, dateFormat)}")
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = type.label,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val formatted = Formatters.formatMoney(tx.amount, currencySymbol, hideBalances)
                Text(
                    text = if (hideBalances) formatted else "$signPrefix$formatted",
                    style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                    color = badgeColor
                )
                if (tx.chargeAmount > 0.0) {
                    Text(
                        text = "Fee ${Formatters.formatMoney(tx.chargeAmount, currencySymbol, hideBalances)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantic.expense
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    FinCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = semantic.textSecondary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = semantic.textSecondary
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAction) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalFinLedgerColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (isDestructive) semantic.expense else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class QuickActionType(val title: String, val subtitle: String, val testTag: String) {
    ADD_EXPENSE("Add Expense", "Log spending from any account or card", "qa_expense"),
    ADD_INCOME("Add Income", "Record salary, freelance, or refund", "qa_income"),
    TRANSFER("Transfer Money", "Move funds between accounts with fee support", "qa_transfer"),
    MONEY_LENT("Money Lent", "Record money given to someone", "qa_lent"),
    MONEY_BORROWED("Money Borrowed", "Record money received as a loan", "qa_borrowed")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionSheet(
    onSelect: (QuickActionType) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalFinLedgerColors.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Quick Action",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            QuickActionType.entries.forEach { action ->
                val (icon, tint, bg) = when (action) {
                    QuickActionType.ADD_EXPENSE -> Triple(Icons.Outlined.ArrowUpward, semantic.expense, semantic.expenseContainer)
                    QuickActionType.ADD_INCOME -> Triple(Icons.Outlined.ArrowDownward, semantic.income, semantic.incomeContainer)
                    QuickActionType.TRANSFER -> Triple(Icons.Outlined.SwapHoriz, semantic.transfer, semantic.transferContainer)
                    QuickActionType.MONEY_LENT -> Triple(Icons.AutoMirrored.Outlined.CallMade, semantic.warning, semantic.warningContainer)
                    QuickActionType.MONEY_BORROWED -> Triple(Icons.AutoMirrored.Outlined.CallReceived, semantic.income, semantic.incomeContainer)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(action) }
                        .testTag(action.testTag),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, semantic.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = action.title, tint = tint, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(action.title, style = MaterialTheme.typography.titleSmall)
                            Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = semantic.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconColorPickerDialog(
    selectedIconKey: String,
    selectedColorHex: Long,
    onSelect: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Icon & Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconRegistry.presetColors.forEach { hex ->
                        val isSelected = hex == selectedColorHex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { onSelect(selectedIconKey, hex) }
                        )
                    }
                }

                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconRegistry.allIcons.forEach { (key, vec) ->
                        val isSelected = key == selectedIconKey
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { onSelect(key, selectedColorHex) },
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) Color(selectedColorHex).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) BorderStroke(1.5.dp, Color(selectedColorHex)) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = vec,
                                    contentDescription = key,
                                    tint = if (isSelected) Color(selectedColorHex) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
