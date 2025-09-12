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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * HomeScreen
 *
 * Landing page that summarizes the current week and offers quick navigation:
 * - Quick action cards: Log, History, Insights, Settings.
 * - "This Week at a Glance": three key stats + a small mood sparkline.
 * - A context-aware tip derived from recent mood/exercise/temp.
 *
 * @param onQuickLog   navigate to the Log screen quickly
 * @param onGoHistory  navigate to the History screen
 * @param onGoInsights navigate to the Insights screen
 * @param onGoSettings navigate to the Settings screen
 */
@Composable
fun HomeScreen(
    onQuickLog: () -> Unit = {},
    onGoHistory: () -> Unit = {},
    onGoInsights: () -> Unit = {},
    onGoSettings: () -> Unit = {}
) {
    // Demo weekly data (replace with Room/Retrofit later).
    val week = remember { generateHomeWeek() }

    // Aggregate metrics for the header stats.
    val avgMood = week.map { it.mood }.average()
    val totalMin = week.sumOf { it.minutes }
    val avgTemp = week.map { it.tempC }.average()

    // Simple rule-based suggestion based on week context.
    val suggestion = remember(week) { buildHomeSuggestion(week) }

    // Display-friendly "today" string.
    val today = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.getDefault()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title + date
        Text("Home", style = MaterialTheme.typography.headlineSmall)
        Text(today, style = MaterialTheme.typography.labelLarge, color = Color.Gray)

        // Quick actions (two rows of cards).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard("Log today", Icons.Filled.Edit, onQuickLog, Modifier.weight(1f))
            QuickActionCard("History", Icons.Filled.History, onGoHistory, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard("Insights", Icons.Filled.Assessment, onGoInsights, Modifier.weight(1f))
            QuickActionCard("Settings", Icons.Filled.Settings, onGoSettings, Modifier.weight(1f))
        }

        // Summary card with stats + sparkline.
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This Week at a Glance", style = MaterialTheme.typography.titleMedium)

                // Three compact stats.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeStat("Avg mood", String.format(Locale.getDefault(), "%.1f / 5", avgMood))
                    HomeStat("Exercise", "$totalMin min")
                    HomeStat("Avg temp", "${avgTemp.roundToInt()}°C")
                }

                // Minimal sparkline of mood across the week.
                MiniMoodSparkline(
                    values = week.map { it.mood.toFloat() },
                    labels = week.map { it.date.dayOfWeek.name.take(3) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }

        // Context-aware tip for the user.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFB39DDB).copy(alpha = 0.12f)
            ),
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

/* ---------- UI Bits ---------- */

/**
 * Small tappable card used for the four quick actions on Home.
 *
 * @param title label shown next to the icon
 * @param icon  material icon to display
 * @param onClick invoked when the card is tapped
 * @param modifier optional layout modifier
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
 * Compact stat tile used inside the "Week at a Glance" row.
 *
 * @param label short label of the metric
 * @param value main value to emphasize
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
 * Minimal line chart (sparkline) for mood values across the week.
 * Draws a baseline, connects points with a rounded stroke, and renders x-axis labels.
 *
 * @param values mood values mapped to 1..5
 * @param labels short day-of-week labels aligned with values
 */
@Composable
private fun MiniMoodSparkline(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    // Fixed mood range.
    val minY = 1f
    val maxY = 5f
    Canvas(modifier) {
        val left = 36f
        val right = size.width - 8f
        val top = 8f
        val bottom = size.height - 28f

        // X-axis baseline
        drawLine(Color.LightGray, Offset(left, bottom), Offset(right, bottom), 2f)

        // Compute x-step and draw connected points.
        val stepX = (right - left) / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = left + i * stepX
            val y = bottom - (v - minY) / (maxY - minY) * (bottom - top)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(Color(0xFF7E57C2), radius = 5f, center = Offset(x, y))
        }
        drawPath(path, Color(0xFF7E57C2), style = Stroke(width = 4f, cap = StrokeCap.Round))

        // X-axis text labels (nativeCanvas used for simple text draw).
        labels.forEachIndexed { i, s ->
            val x = left + i * stepX
            drawContext.canvas.nativeCanvas.drawText(
                s,
                x - 18f,
                bottom + 18f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 28f
                    isAntiAlias = true
                }
            )
        }
    }
}

/* ---------- Demo data + suggestion ---------- */

/** Simple daily data model for the Home weekly summary. */
private data class HomeDay(val date: LocalDate, val mood: Int, val minutes: Int, val tempC: Int)

/** Generate a seven-day window ending today (demo-only). */
private fun generateHomeWeek(): List<HomeDay> {
    val end = LocalDate.now()
    val start = end.minusDays(6)
    val temps = listOf(18, 22, 26, 31, 33, 29, 21)
    val moods = listOf(3, 4, 3, 2, 4, 5, 3)
    val minutes = listOf(20, 35, 15, 10, 40, 50, 25)
    return (0..6).map { i ->
        HomeDay(start.plusDays(i.toLong()), moods[i], minutes[i], temps[i])
    }
}

/**
 * Build a human-readable tip based on average temperature, total exercise,
 * and average mood for the week.
 */
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
