package com.example.application

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.runtime.produceState


@Composable
fun HomeScreen(
    isLoggedIn: Boolean,
    onQuickLog: () -> Unit = {},
    onGoHistory: () -> Unit = {},
    onGoInsights: () -> Unit = {},
    onGoSettings: () -> Unit = {}
) {
    val repo = remember { HomeRepositoryFirebase() }

    // Pull the raw entries for this week (there may be multiple entries for the same day)
    val week by produceState<List<HomeDay>>(
        initialValue = emptyList(),
        key1 = isLoggedIn
    ) {
        value = if (!isLoggedIn) {
            emptyList()
        } else {
            val uid = AuthRepository.currentUserId()
            if (uid == null) emptyList() else repo.loadWeek(uid)
        }
    }

    // Key: Merge multiple logs of the same day into "daily" data
    val daysForChart = remember(week) { aggregateWeek(week) }

    // Statistics and reminders are calculated based on the number of days recorded (the number of days recorded will be used as the calculation).
    val hasData = daysForChart.isNotEmpty()
    val avgMood  = if (hasData) daysForChart.map { it.mood }.average() else 0.0
    val totalMin = if (hasData) daysForChart.sumOf { it.minutes } else 0
    val avgTemp  = if (hasData) daysForChart.map { it.tempC }.average() else 0.0
    val suggestion: String =
        if (daysForChart.isNotEmpty()) buildHomeSuggestion(daysForChart)
        else "No data yet. Log to see insights."

    val today = remember {
        LocalDate.now().format(
            DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.getDefault())
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Home", style = MaterialTheme.typography.headlineSmall)
        Text(today, style = MaterialTheme.typography.labelLarge, color = Color.Gray)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard("Log today", Icons.Filled.Edit, onQuickLog, Modifier.weight(1f))
            QuickActionCard("History", Icons.Filled.History, onGoHistory, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard("Insights", Icons.Filled.Assessment, onGoInsights, Modifier.weight(1f))
            QuickActionCard("Settings", Icons.Filled.Settings, onGoSettings, Modifier.weight(1f))
        }

        // Summary card: three statistics + a weekly chart
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This Week at a Glance", style = MaterialTheme.typography.titleMedium)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeStat("Avg mood", String.format(Locale.getDefault(), "%.1f / 5", avgMood))
                    HomeStat("Exercise", "$totalMin min")
                    HomeStat("Avg temp", "${avgTemp.roundToInt()}°C")
                }

                HomeWeeklyChart(
                    days = daysForChart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFB39DDB).copy(alpha = 0.12f)),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tip for you", style = MaterialTheme.typography.titleMedium)
                Text(suggestion)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

/* ---------- UI bits ---------- */

@Composable
private fun QuickActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = title)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HomeStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ---------- Chart + helpers ---------- */

/**
 * Combine multiple logs from the same day into "daily data":
 * - minutes: sum
 * - tempC: average rounding
 * - mood: average rounding
 */
private fun aggregateWeek(raw: List<HomeDay>): List<HomeDay> {
    return raw
        .groupBy { it.date }
        .map { (date, items) ->
            val minutesSum = items.sumOf { it.minutes }
            val tempAvg    = items.map { it.tempC }.average().toInt()
            val moodAvg    = items.map { it.mood }.average().toInt()
            HomeDay(date = date, mood = moodAvg, minutes = minutesSum, tempC = tempAvg)
        }
        .sortedBy { it.date }
}


/**
 * Weekly chart:
 * - The vertical axis is fixed at 0–40°C, with only the 20° and 40° tick marks displayed (the bottom baseline is 0°).
 * - The bar height represents the temperature; the "minutes" are displayed above the top of the bar, followed by the "emoji" above.
 * - The day of the week is displayed below (MON/TUE/...).
 */
@Composable
private fun HomeWeeklyChart(
    days: List<HomeDay>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFFB39DDB),
    axisColor: Color = Color(0x33000000),
    textColor: Color = Color(0xFF424242),
    showEmoji: Boolean = true
) {
    if (days.isEmpty()) {
        Box(
            modifier = modifier.height(200.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) { Text("No data this week yet") }
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        /* ---- Layout constants ---- */
        val gutterLeft = 56f
        val gutterRight = 12f
        val gutterTop = 12f
        val gutterBottom = 56f
        val left = gutterLeft
        val right = size.width - gutterRight
        val top = gutterTop
        val bottom = size.height - gutterBottom
        val chartW = right - left
        val chartH = bottom - top


        fun yOfTemp(t: Int): Float {
            val v = t.coerceIn(0, 40)
            return bottom - (v / 40f) * chartH
        }


        val tickPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = textColor.copy(alpha = 0.70f).toArgb()
            textSize = 26f
        }
        // Draw only the 20°/40° lines and the 0° baseline
        val ticks = listOf(20, 40)
        // 0° baseline
        drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), 2f)
        drawContext.canvas.nativeCanvas.drawText("0°", left - 28f, bottom + 8f, tickPaint)
        // 20° / 40°
        for (deg in ticks) {
            val y = yOfTemp(deg)
            drawLine(axisColor, Offset(left, y), Offset(right, y), 1.5f)
            drawContext.canvas.nativeCanvas.drawText("${deg}°", left - 36f, y + 8f, tickPaint)
        }

        /* ---- X-axis distribution ---- */
        val n = days.size
        val step = if (n > 1) chartW / (n - 1) else chartW
        val inset = if (n > 1) step * 0.12f else 0f
        val startX = left + inset
        val endX = right - inset
        val effStep = if (n > 1) (endX - startX) / (n - 1) else 0f


        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true; color = textColor.toArgb(); textSize = 28f
        }
        val minutesPaint = android.graphics.Paint().apply {
            isAntiAlias = true; color = textColor.copy(alpha = 0.80f).toArgb(); textSize = 26f
        }
        val emojiPaint = android.graphics.Paint().apply {
            isAntiAlias = true; textSize = 30f
        }

        /* ---- Column width and spacing ---- */
        val barW = minOf(26f, if (n > 1) effStep * 0.38f else 24f)
        val barFloorGap = 3f
        val gapMinutesToBar = 12f
        val gapEmojiToMinutes = 20f
        val gapDayToAxis = 18f

        /* ---- draw each day ---- */
        days.forEachIndexed { i, d ->
            val cx = startX + i * effStep
            val barTop = yOfTemp(d.tempC)

            // 1) Temperature column (height determined by temperature)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(cx - barW / 2f, barTop + barFloorGap),
                size = androidx.compose.ui.geometry.Size(
                    barW,
                    (bottom - barFloorGap) - barTop
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
            )

            // 2) Exercise time above the top of the column (centered)
            val minText = "${d.minutes}min"
            val minW = minutesPaint.measureText(minText)
            drawContext.canvas.nativeCanvas.drawText(
                minText,
                cx - minW / 2f,
                barTop - gapMinutesToBar,
                minutesPaint
            )

            // 3) Minutes above emoji (centered)
            if (showEmoji) {
                val emoji = when (d.mood.coerceIn(1, 5)) {
                    5 -> "😄"; 4 -> "🙂"; 3 -> "😐"; 2 -> "🙁"; else -> "😖"
                }
                val emojiW = emojiPaint.measureText(emoji)
                drawContext.canvas.nativeCanvas.drawText(
                    emoji,
                    cx - emojiW / 2f,
                    barTop - gapMinutesToBar - gapEmojiToMinutes,
                    emojiPaint
                )
            }

            // 4) X-axis (English short name, centered)
            val dayLabel = d.date.dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                .uppercase(java.util.Locale.getDefault())
            val dayW = labelPaint.measureText(dayLabel)
            drawContext.canvas.nativeCanvas.drawText(
                dayLabel,
                cx - dayW / 2f,
                bottom + gapDayToAxis,
                labelPaint
            )
        }
    }
}





private fun emojiFromMood(mood: Int): String = when (mood.coerceIn(1, 5)) {
    5 -> "😄"   // very happy
    4 -> "🙂"   // happy
    3 -> "😐"   // normal
    2 -> "🙁"   // sad
    else -> "😖" // very sad
}

/* ---------- Suggestion ---------- */

private fun buildHomeSuggestion(week: List<HomeDay>): String {
    val avgTemp = week.map { it.tempC }.average()
    val totalMin = week.sumOf { it.minutes }
    val avgMood = week.map { it.mood }.average()

    return when {
        avgTemp >= 30 && totalMin < 150 ->
            "Hot week. Try an indoor stretch today and remember to hydrate."
        avgTemp <= 18 && avgMood <= 3 ->
            "Cooler temps and mood dipped. A 10–15 minute walk could help."
        totalMin < 90 ->
            "Exercise minutes were low. Aim for 15–20 minutes on three days this week."
        else ->
            "Nice consistency! Keep a steady routine and note which days felt best."
    }
}
