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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.export.Formatters
import com.example.data.export.IconRegistry
import com.example.domain.model.AppNotification
import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.BudgetProgress
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.FinancialEngine
import com.example.domain.model.DashboardDateRange
import com.example.domain.model.UserSettings
import com.example.ui.components.BudgetProgressRow
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinCard
import com.example.ui.components.IconColorPickerDialog
import com.example.ui.theme.LocalFinLedgerColors

@Composable
fun BudgetsScreen(
    budgetProgress: List<BudgetProgress>,
    categories: List<Category>,
    settings: UserSettings,
    onBack: () -> Unit,
    onSaveBudget: (Budget) -> Unit,
    onDeleteBudget: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 960.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Text("Budgets & Alerts", style = MaterialTheme.typography.titleLarge)
                }
                Button(
                    onClick = {
                        editingBudget = null
                        showBudgetDialog = true
                    },
                    modifier = Modifier.padding(end = 8.dp).testTag("add_budget_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Budget")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (budgetProgress.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.PieChart,
                            title = "No budgets yet",
                            message = "Create overall monthly/weekly budgets or category-wise spending caps with threshold warnings.",
                            actionLabel = "Create Budget",
                            onAction = { editingBudget = null; showBudgetDialog = true }
                        )
                    }
                } else {
                    items(budgetProgress, key = { it.budget.id }) { bp ->
                        FinCard(modifier = Modifier.fillMaxWidth()) {
                            BudgetProgressRow(
                                progress = bp,
                                currencySymbol = settings.currencySymbol,
                                hideBalances = settings.hideBalances
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${bp.budget.periodTypeEnum().label} • Alerts: ${bp.budget.alertThresholds.joinToString("%/")}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.textSecondary
                                )
                                Row {
                                    IconButton(onClick = { editingBudget = bp.budget; showBudgetDialog = true }) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Edit budget", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { pendingDeleteId = bp.budget.id }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete budget", tint = semantic.expense, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        BudgetFormDialog(
            editingBudget = editingBudget,
            expenseCategories = categories.filter { it.typeEnum() == CategoryType.EXPENSE },
            settings = settings,
            onSave = { bud ->
                onSaveBudget(bud)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false }
        )
    }

    pendingDeleteId?.let { id ->
        ConfirmationDialog(
            title = "Delete Budget?",
            message = "Are you sure you want to delete this budget?",
            onConfirm = { onDeleteBudget(id); pendingDeleteId = null },
            onDismiss = { pendingDeleteId = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetFormDialog(
    editingBudget: Budget?,
    expenseCategories: List<Category>,
    settings: UserSettings,
    onSave: (Budget) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(editingBudget?.name ?: "") }
    var limitText by remember { mutableStateOf(editingBudget?.limitAmount?.toString() ?: "") }
    var periodType by remember { mutableStateOf(editingBudget?.periodTypeEnum() ?: BudgetPeriodType.MONTHLY) }
    var selectedCategory by remember {
        mutableStateOf(editingBudget?.categoryId?.let { id -> expenseCategories.firstOrNull { it.id == id } })
    }
    val defaultBounds = FinancialEngine.getDateRangeBounds(DashboardDateRange.THIS_MONTH)
    var customStart by remember { mutableLongStateOf(editingBudget?.startDateMillis?.takeIf { it > 0 } ?: defaultBounds.first) }
    var customEnd by remember { mutableLongStateOf(editingBudget?.endDateMillis?.takeIf { it > 0 } ?: defaultBounds.second) }
    var thresholds by remember { mutableStateOf(editingBudget?.alertThresholds ?: settings.budgetAlertThresholds) }

    var catExpanded by remember { mutableStateOf(false) }
    var showCustomPicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingBudget == null) "Create Budget" else "Edit Budget") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("Budget Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("budget_name_input")
                )
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it; errorMsg = null },
                    label = { Text("Limit Amount (${settings.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("budget_limit_input")
                )

                Text("Period", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BudgetPeriodType.entries.forEachIndexed { idx, p ->
                        SegmentedButton(
                            selected = periodType == p,
                            onClick = { periodType = p },
                            shape = SegmentedButtonDefaults.itemShape(idx, BudgetPeriodType.entries.size)
                        ) { Text(p.label) }
                    }
                }

                if (periodType == BudgetPeriodType.CUSTOM) {
                    OutlinedButton(onClick = { showCustomPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${Formatters.formatDate(customStart, settings.dateFormat)} - ${Formatters.formatDate(customEnd, settings.dateFormat)}")
                    }
                }

                ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "Overall (All Expenses)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Scope") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Overall (All Expenses)") },
                            onClick = { selectedCategory = null; catExpanded = false }
                        )
                        expenseCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { selectedCategory = cat; catExpanded = false }
                            )
                        }
                    }
                }

                Text("Alert Thresholds", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50, 75, 90, 100).forEach { pct ->
                        val active = pct in thresholds
                        FilterChip(
                            selected = active,
                            onClick = {
                                thresholds = if (active) (thresholds - pct).sorted() else (thresholds + pct).sorted()
                            },
                            label = { Text("$pct%") }
                        )
                    }
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = LocalFinLedgerColors.current.expense, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = limitText.toDoubleOrNull()
                    if (name.isBlank()) {
                        errorMsg = "Enter a budget name."
                    } else if (limit == null || limit <= 0.0) {
                        errorMsg = "Enter a positive budget limit."
                    } else {
                        val (s, e) = when (periodType) {
                            BudgetPeriodType.WEEKLY -> FinancialEngine.getDateRangeBounds(DashboardDateRange.THIS_WEEK)
                            BudgetPeriodType.MONTHLY -> FinancialEngine.getDateRangeBounds(DashboardDateRange.THIS_MONTH)
                            BudgetPeriodType.CUSTOM -> customStart to customEnd
                        }
                        onSave(
                            Budget(
                                id = editingBudget?.id ?: "",
                                userId = editingBudget?.userId ?: "",
                                name = name.trim(),
                                periodType = periodType.name,
                                categoryId = selectedCategory?.id,
                                categoryName = selectedCategory?.name,
                                limitAmount = limit,
                                startDateMillis = s,
                                endDateMillis = e,
                                alertThresholds = thresholds,
                                lastNotifiedThreshold = editingBudget?.lastNotifiedThreshold ?: 0,
                                active = true
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("save_budget_button")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showCustomPicker) {
        val state = rememberDateRangePickerState(initialSelectedStartDateMillis = customStart, initialSelectedEndDateMillis = customEnd)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showCustomPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedStartDateMillis?.let { customStart = it }
                    state.selectedEndDateMillis?.let { customEnd = it + 86_399_999L }
                    showCustomPicker = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showCustomPicker = false }) { Text("Cancel") } }
        ) { DateRangePicker(state = state, modifier = Modifier.height(420.dp)) }
    }
}

@Composable
fun CategoriesManagerScreen(
    categories: List<Category>,
    onBack: () -> Unit,
    onSaveCategory: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showForm by remember { mutableStateOf(false) }
    var editingCat by remember { mutableStateOf<Category?>(null) }
    var deleteCat by remember { mutableStateOf<Category?>(null) }

    val filtered = categories.filter {
        it.typeEnum() == if (selectedTab == 0) CategoryType.EXPENSE else CategoryType.INCOME
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 960.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Text("Categories", style = MaterialTheme.typography.titleLarge)
                }
                Button(
                    onClick = { editingCat = null; showForm = true },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Expense Categories") }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Income Categories") }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.Category,
                            title = "No categories",
                            message = "Create custom categories with icons and colors.",
                            actionLabel = "Add Category",
                            onAction = { editingCat = null; showForm = true }
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { cat ->
                        FinCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(cat.colorHex).copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        IconRegistry.resolve(cat.iconKey),
                                        contentDescription = cat.name,
                                        tint = Color(cat.colorHex),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(cat.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { editingCat = cat; showForm = true }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit category", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { deleteCat = cat }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete category", tint = semantic.expense, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        var catName by remember { mutableStateOf(editingCat?.name ?: "") }
        var iconKey by remember { mutableStateOf(editingCat?.iconKey ?: "category") }
        var colorHex by remember { mutableLongStateOf(editingCat?.colorHex ?: 0xFF2563EB) }
        var showPicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text(if (editingCat == null) "New Category" else "Edit Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Icon & Color")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.isNotBlank()) {
                            onSaveCategory(
                                Category(
                                    id = editingCat?.id ?: "",
                                    userId = editingCat?.userId ?: "",
                                    name = catName.trim(),
                                    type = if (selectedTab == 0) CategoryType.EXPENSE.name else CategoryType.INCOME.name,
                                    iconKey = iconKey,
                                    colorHex = colorHex,
                                    systemDefault = editingCat?.systemDefault ?: false
                                )
                            )
                            showForm = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showForm = false }) { Text("Cancel") } }
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

    deleteCat?.let { cat ->
        ConfirmationDialog(
            title = "Delete Category?",
            message = "Delete '${cat.name}'? Existing transactions will keep their category label.",
            onConfirm = { onDeleteCategory(cat.id); deleteCat = null },
            onDismiss = { deleteCat = null }
        )
    }
}

@Composable
fun NotificationsScreen(
    notifications: List<AppNotification>,
    dateFormat: String,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 960.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Text("Notifications", style = MaterialTheme.typography.titleLarge)
                }
                if (notifications.any { !it.read }) {
                    TextButton(onClick = onMarkAllRead) {
                        Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mark all read")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (notifications.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.NotificationsNone,
                            title = "All caught up",
                            message = "Budget threshold warnings and loan due reminders will appear here."
                        )
                    }
                } else {
                    items(notifications, key = { it.id }) { notif ->
                        FinCard(
                            onClick = { if (!notif.read) onMarkRead(notif.id) },
                            containerColor = if (notif.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(notif.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    Formatters.formatDate(notif.triggeredAtMillis, dateFormat),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.textSecondary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(notif.message, style = MaterialTheme.typography.bodySmall, color = semantic.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
