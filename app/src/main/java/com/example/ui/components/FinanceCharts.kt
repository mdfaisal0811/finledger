package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.export.Formatters
import com.example.domain.model.BudgetProgress
import com.example.domain.model.CategorySpending
import com.example.domain.model.MonthlyCashflow
import com.example.ui.theme.AmountTextStyle
import com.example.ui.theme.LocalFinLedgerColors
import kotlin.math.max

@Composable
fun IncomeExpenseBarChart(
    series: List<MonthlyCashflow>,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    val maxVal = max(1.0, series.maxOfOrNull { max(it.income, it.expense) } ?: 1.0)
    val gridColor = semantic.cardBorder

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(color = semantic.income, label = "Income")
            LegendDot(color = semantic.expense, label = "Expense")
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(top = 8.dp)
        ) {
            if (series.isEmpty()) return@Canvas
            val groupCount = series.size
            val groupWidth = size.width / groupCount
            val barWidth = (groupWidth * 0.28f).coerceIn(8.dp.toPx(), 24.dp.toPx())
            val gap = 4.dp.toPx()

            // Horizontal guide lines
            for (step in 0..3) {
                val y = size.height * (step / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            series.forEachIndexed { index, item ->
                val centerX = index * groupWidth + groupWidth / 2f
                val incHeight = ((item.income / maxVal).toFloat() * (size.height - 6.dp.toPx())).coerceAtLeast(if (item.income > 0) 4.dp.toPx() else 0f)
                val expHeight = ((item.expense / maxVal).toFloat() * (size.height - 6.dp.toPx())).coerceAtLeast(if (item.expense > 0) 4.dp.toPx() else 0f)

                if (incHeight > 0f) {
                    drawRoundRect(
                        color = semantic.income,
                        topLeft = Offset(centerX - barWidth - gap / 2f, size.height - incHeight),
                        size = Size(barWidth, incHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
                if (expHeight > 0f) {
                    drawRoundRect(
                        color = semantic.expense,
                        topLeft = Offset(centerX + gap / 2f, size.height - expHeight),
                        size = Size(barWidth, expHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            series.forEach { item ->
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.textSecondary
                )
            }
        }
    }
}

@Composable
fun CashflowLineChart(
    series: List<MonthlyCashflow>,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    val maxVal = max(1.0, series.maxOfOrNull { max(it.income, it.expense) } ?: 1.0)
    val gridColor = semantic.cardBorder

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(color = semantic.transfer, label = "Income Trend")
            LegendDot(color = semantic.expense, label = "Expense Trend")
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(vertical = 8.dp)
        ) {
            if (series.size < 2) return@Canvas
            val stepX = size.width / (series.size - 1)

            for (step in 0..3) {
                val y = size.height * (step / 3f)
                drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }

            val incomePath = Path()
            val expensePath = Path()

            series.forEachIndexed { i, item ->
                val x = i * stepX
                val incY = size.height - ((item.income / maxVal).toFloat() * size.height)
                val expY = size.height - ((item.expense / maxVal).toFloat() * size.height)
                if (i == 0) {
                    incomePath.moveTo(x, incY)
                    expensePath.moveTo(x, expY)
                } else {
                    incomePath.lineTo(x, incY)
                    expensePath.lineTo(x, expY)
                }
            }

            drawPath(path = incomePath, color = semantic.transfer, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path = expensePath, color = semantic.expense, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

            series.forEachIndexed { i, item ->
                val x = i * stepX
                val incY = size.height - ((item.income / maxVal).toFloat() * size.height)
                val expY = size.height - ((item.expense / maxVal).toFloat() * size.height)
                drawCircle(color = semantic.transfer, radius = 4.dp.toPx(), center = Offset(x, incY))
                drawCircle(color = semantic.expense, radius = 4.dp.toPx(), center = Offset(x, expY))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            series.forEach { item ->
                Text(item.label, style = MaterialTheme.typography.labelSmall, color = semantic.textSecondary)
            }
        }
    }
}

@Composable
fun CategoryDoughnutChart(
    items: List<CategorySpending>,
    currencySymbol: String,
    hideBalances: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    val total = items.sumOf { it.amount }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(124.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val strokeWidth = 18.dp.toPx()
                if (total <= 0.0 || items.isEmpty()) {
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    var startAngle = -90f
                    items.forEach { cat ->
                        val sweep = ((cat.amount / total) * 360.0).toFloat()
                        drawArc(
                            color = Color(cat.colorHex),
                            startAngle = startAngle,
                            sweepAngle = max(1f, sweep - 1.5f),
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Spent", style = MaterialTheme.typography.labelSmall, color = semantic.textSecondary)
                Text(
                    text = Formatters.formatMoney(total, currencySymbol, hideBalances),
                    style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    maxLines = 1
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.take(5).forEach { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(cat.colorHex))
                        )
                        Text(
                            text = cat.categoryName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${(cat.percentage * 100).toInt()}%",
                        style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                        color = semantic.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetProgressRow(
    progress: BudgetProgress,
    currencySymbol: String,
    hideBalances: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    val pct = (progress.progressRatio * 100).toInt()
    val barColor = when {
        pct >= 100 -> semantic.expense
        pct >= 75 -> semantic.warning
        else -> semantic.income
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(progress.budget.name, style = MaterialTheme.typography.titleSmall)
                if (pct >= 50) {
                    Surface(
                        color = barColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${pct}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = barColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "${Formatters.formatMoney(progress.spentAmount, currencySymbol, hideBalances)} / ${Formatters.formatMoney(progress.budget.limitAmount, currencySymbol, hideBalances)}",
                style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                color = semantic.textSecondary
            )
        }

        LinearProgressIndicator(
            progress = { progress.progressRatio.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = progress.budget.categoryName ?: "All Expenses",
                style = MaterialTheme.typography.labelSmall,
                color = semantic.textSecondary
            )
            val remText = if (progress.remainingAmount >= 0.0) {
                "Remaining: ${Formatters.formatMoney(progress.remainingAmount, currencySymbol, hideBalances)}"
            } else {
                "Over by ${Formatters.formatMoney(-progress.remainingAmount, currencySymbol, hideBalances)}"
            }
            Text(
                text = remText,
                style = MaterialTheme.typography.labelSmall,
                color = if (progress.remainingAmount < 0) semantic.expense else semantic.textSecondary
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalFinLedgerColors.current.textSecondary)
    }
}
