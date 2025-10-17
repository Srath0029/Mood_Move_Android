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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.delay
import java.util.Date
import androidx.compose.foundation.pager.HorizontalPager

/**
 * HomeScreen
 *
 * Dashboard for quick navigation and weekly overview.
 *
 * Shows:
 * - Quick actions (Log, History, Insights, Settings).
 * - A public activity carousel when signed in.
 * - Weekly stats (avg mood, total minutes, avg temp) and a compact chart.
 *
 * Data
 * - Loads user-week data and public activities only when [isLoggedIn] is true.
 * - Aggregation is performed in-memory via [aggregateWeek].
 *
 * Callbacks
 * - [onQuickLog], [onGoHistory], [onGoInsights], [onGoSettings] drive navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    isLoggedIn: Boolean,
    onQuickLog: () -> Unit = {},
    onGoHistory: () -> Unit = {},
    onGoInsights: () -> Unit = {},
    onGoSettings: () -> Unit = {}
) {
    val repo = remember { HomeRepositoryFirebase() }

    // User week data (reloaded when login state changes).
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

    // Public feed (visible only when logged in).
    val publicActivities by produceState<List<PublicActivity>>(
        initialValue = emptyList(),
        key1 = isLoggedIn
    ) {
        value = if (isLoggedIn) repo.loadPublicActivities() else emptyList()
    }

    val daysForChart = remember(week) { aggregateWeek(week) }

    val hasData = daysForChart.isNotEmpty()
    val avgMood  = if (hasData) daysForChart.map { it.mood }.average() else 0.0
    val totalMin = if (hasData) daysForChart.sumOf { it.minutes } else 0
    val avgTemp  = if (hasData) daysForChart.map { it.tempC }.average() else 0.0

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

        if (publicActivities.isNotEmpty()) {
            ActivityCarousel(activities = publicActivities)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard("Log today", Icons.Filled.Edit, onQuickLog, Modifier.weight(1f))
            QuickActionCard("History", Icons.Filled.History, onGoHistory, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard("Insights", Icons.Filled.Assessment, onGoInsights, Modifier.weight(1f))
            QuickActionCard("Settings", Icons.Filled.Settings, onGoSettings, Modifier.weight(1f))
        }

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

        Spacer(Modifier.height(4.dp))
    }
}

/**
 * ActivityCarousel
 *
 * Auto-advancing (5s) pager showing recent public activities.
 * Displays "user did a <type> <relative time>" per page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityCarousel(activities: List<PublicActivity>, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { activities.size })

    LaunchedEffect(pagerState.pageCount) {
        if (pagerState.pageCount > 1) {
            while (true) {
                delay(5000)
                val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val activity = activities[page]
            val relativeTime = remember(activity.submittedAt) {
                formatRelativeTime(activity.submittedAt)
            }
            Text(
                text = "${activity.userName} did a ${activity.activityType} ${relativeTime}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

/**
 * Returns a short relative label for a past [Date] ("just now", "12 minutes ago", etc.).
 */
private fun formatRelativeTime(past: Date): String {
    val now = Date()
    val seconds = (now.time - past.time) / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "just now"
        minutes < 60 -> "$minutes minutes ago"
        hours < 24 -> "$hours hours ago"
        else -> "$days days ago"
    }
}

/**
 * QuickActionCard
 *
 * Compact card used for primary navigation shortcuts on the Home screen.
 */
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

/**
 * HomeStat
 *
 * Small metric card for a (label, value) pair.
 */
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

/**
 * Aggregates multiple [HomeDay] rows per date:
 * - sums minutes, averages temperature and mood.
 * Returns a date-sorted list for the chart.
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
 * HomeWeeklyChart
 *
 * Canvas-based weekly bar chart showing temperature per day with:
 * - a minutes label above each bar, and
 * - an optional mood emoji above the label.
 * Axes and labels use the native canvas for text drawing.
 *
 * @param days       Aggregated week data in chronological order.
 * @param barColor   Bar fill color.
 * @param axisColor  Axis and tick color.
 * @param textColor  Label color.
 * @param showEmoji  Whether to render mood emoji above minutes.
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
        val ticks = listOf(20, 40)
        drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), 2f)
        drawContext.canvas.nativeCanvas.drawText("0°", left - 28f, bottom + 8f, tickPaint)
        for (deg in ticks) {
            val y = yOfTemp(deg)
            drawLine(axisColor, Offset(left, y), Offset(right, y), 1.5f)
            drawContext.canvas.nativeCanvas.drawText("${deg}°", left - 36f, y + 8f, tickPaint)
        }

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

        val barW = minOf(26f, if (n > 1) effStep * 0.38f else 24f).toFloat()
        val barFloorGap = 3f
        val gapMinutesToBar = 12f
        val gapEmojiToMinutes = 20f
        val gapDayToAxis = 18f

        days.forEachIndexed { i, d ->
            val cx = startX + i * effStep
            val barTop = yOfTemp(d.tempC)

            drawRoundRect(
                color = barColor,
                topLeft = Offset(cx - barW / 2f, barTop + barFloorGap),
                size = androidx.compose.ui.geometry.Size(
                    barW,
                    (bottom - barFloorGap) - barTop
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
            )

            val minText = "${d.minutes}min"
            val minW = minutesPaint.measureText(minText)
            drawContext.canvas.nativeCanvas.drawText(
                minText,
                cx - minW / 2f,
                barTop - gapMinutesToBar,
                minutesPaint
            )

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
