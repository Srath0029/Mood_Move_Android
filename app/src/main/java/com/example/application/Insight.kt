package com.example.application

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// --- Sample weekly data model (replace with Room+Retrofit later) ---

/**
 * Immutable model used by the Insights screen to render a single day.
 * @param date calendar day
 * @param mood mood score (1..5)
 * @param minutes exercise minutes
 * @param tempC daily average temperature in °C
 */
data class DayInsight(
    val date: LocalDate,
    val mood: Int,        // 1..5 (Very sad .. Very happy)
    val minutes: Int,     // exercise minutes
    val tempC: Int        // weather (°C)
)

/**
 * Returns seven demo rows (ending today) for previewing charts.
 * In production this will be replaced by Room (logs) + Retrofit (weather).
 */
private fun demoWeek(): List<DayInsight> {
    val end = LocalDate.now()
    val start = end.minusDays(6)
    val temps = listOf(18, 21, 23, 27, 31, 33, 29)
    val moods = listOf(3, 4, 3, 2, 4, 5, 3)
    val minutes = listOf(20, 35, 15, 10, 40, 50, 25)
    return (0..6).map { i ->
        DayInsight(start.plusDays(i.toLong()), moods[i], minutes[i], temps[i])
    }
}

/**
 * Top-level Insights screen:
 * - Simulates a short "loading" state.
 * - Crossfades to the insights content with charts and tips.
 */
@Composable
fun InsightsScreen() {
    var loading by remember { mutableStateOf(true) }
    val week by remember { mutableStateOf(demoWeek()) }

    // Simulate I/O to demonstrate a progress indicator.
    LaunchedEffect(Unit) { delay(600); loading = false }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Crossfade provides a subtle transition between loading and content.
        Crossfade(targetState = loading, label = "insights-load") { isLoading ->
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                InsightsContent(week)
            }
        }
    }
}

/**
 * Insights body: renders KPI cards, two charts, and a context-aware tip.
 */
@Composable
private fun InsightsContent(week: List<DayInsight>) {
    // Aggregate metrics used in summary cards.
    val avgMood = week.map { it.mood }.average()
    val totalMin = week.sumOf { it.minutes }
    val avgTemp = week.map { it.tempC }.average()

    // Rule-based recommendation based on week context.
    val suggestion = remember(week) { buildSuggestion(week) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // allow scrolling on smaller screens
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Weekly Insights", style = MaterialTheme.typography.headlineSmall)

        // Summary KPI row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                "Avg mood",
                String.format(Locale.getDefault(), "%.1f / 5", avgMood),
                modifier = Modifier.weight(1f)
            )
            StatCard("Exercise", "$totalMin min", modifier = Modifier.weight(1f))
            StatCard("Avg temp", "${avgTemp.roundToInt()}°C", modifier = Modifier.weight(1f))
        }

        // Line chart for mood (1..5)
        ChartCard(title = "Mood trend (1–5)") {
            MoodLineChart(
                data = week.map { it.mood },
                labels = week.map { it.date.dayOfWeek.shortName() }
            )
        }

        // Bars for exercise minutes with weather dots overlaid
        ChartCard(title = "Exercise vs Weather") {
            ExerciseBarWithWeather(
                minutes = week.map { it.minutes },
                temps = week.map { it.tempC },
                labels = week.map { it.date.dayOfWeek.shortName() }
            )
        }

        // Tip panel (light brand tint)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFB39DDB).copy(alpha = 0.12f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Tip for you", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(suggestion)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Small heuristic that turns weekly context into a single actionable sentence.
 */
private fun buildSuggestion(week: List<DayInsight>): String {
    val avgTemp = week.map { it.tempC }.average()
    val totalMin = week.sumOf { it.minutes }
    val avgMood = week.map { it.mood }.average()

    return when {
        avgTemp >= 30 && totalMin < 150 ->
            "It’s been quite hot. Try a short indoor stretching session and keep up hydration."
        avgTemp <= 18 && avgMood <= 3 ->
            "Cooler week and mood dipped a bit. A 10–15 minute outdoor walk could help."
        totalMin < 90 ->
            "Exercise minutes were low. Aim for 15–20 minutes on three days next week."
        else ->
            "Nice consistency! Keep a steady routine and review which days felt best."
    }
}

// ---------- UI pieces ----------

/**
 * Compact statistic tile used at the top of the screen.
 */
@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,   // weight is supplied by the caller
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Generic chart container with a title and fixed plot height.
 */
@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.height(180.dp).fillMaxWidth()) { content() }
        }
    }
}

/**
 * Simple line chart for mood (1..5).
 * Draws Y gridlines, a baseline, points, and a rounded polyline.
 */
@Composable
private fun MoodLineChart(data: List<Int>, labels: List<String>) {
    val minY = 1f
    val maxY = 5f
    val points = data.map { it.toFloat() }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val left = 40f
        val right = w - 8f
        val top = 12f
        val bottom = h - 28f

        // Axes
        drawLine(Color.LightGray, Offset(left, top), Offset(left, bottom), 2f)
        drawLine(Color.LightGray, Offset(left, bottom), Offset(right, bottom), 2f)

        // Horizontal gridlines (1..5)
        (1..5).forEach { y ->
            val yy = bottom - (y - minY) / (maxY - minY) * (bottom - top)
            drawLine(Color(0xFFE0E0E0), Offset(left, yy), Offset(right, yy), 1f)
        }

        // X spacing between points
        val stepX = (right - left) / (points.size - 1).coerceAtLeast(1)

        // Connected line + points
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = left + i * stepX
            val y = bottom - (v - minY) / (maxY - minY) * (bottom - top)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(Color(0xFF7E57C2), radius = 5f, center = Offset(x, y))
        }
        drawPath(path, Color(0xFF7E57C2), style = Stroke(width = 4f, cap = StrokeCap.Round))

        // X labels
        val labelStep = stepX
        labels.forEachIndexed { i, s ->
            drawContext.canvas.nativeCanvas.apply {
                drawText(
                    s,
                    left + i * labelStep - 12f,
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
}

/**
 * Combined chart: exercise minutes as bars, temperature as dots overlaid.
 */
@Composable
private fun ExerciseBarWithWeather(
    minutes: List<Int>,
    temps: List<Int>,
    labels: List<String>,
    barWidth: Dp = 18.dp
) {
    val maxMin = (minutes.maxOrNull() ?: 0).coerceAtLeast(60).toFloat()  // Ensures a visible scale
    val maxTemp = (temps.maxOrNull() ?: 0).coerceAtLeast(30).toFloat()

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val left = 40f
        val right = w - 8f
        val top = 12f
        val bottom = h - 28f

        // Axes
        drawLine(Color.LightGray, Offset(left, top), Offset(left, bottom), 2f)
        drawLine(Color.LightGray, Offset(left, bottom), Offset(right, bottom), 2f)

        val barSpace = (right - left) / minutes.size
        val bw = barWidth.toPx()

        minutes.forEachIndexed { i, m ->
            val x = left + i * barSpace + barSpace / 2f
            val barTop = bottom - (m / maxMin) * (bottom - top)

            // Exercise bar for day i
            drawRect(
                color = Color(0xFFB39DDB),
                topLeft = Offset(x - bw / 2f, barTop),
                size = androidx.compose.ui.geometry.Size(bw, bottom - barTop)
            )

            // Temperature dot for day i
            val t = temps[i]
            val ty = bottom - (t / maxTemp) * (bottom - top)
            drawCircle(Color(0xFF455A64), radius = 5f, center = Offset(x, ty))

            // X labels under each bar/dot
            drawContext.canvas.nativeCanvas.apply {
                drawText(
                    labels[i],
                    x - 12f,
                    bottom + 18f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        textSize = 28f
                        isAntiAlias = true
                    }
                )
            }

            // Small temperature label above the dot
            drawContext.canvas.nativeCanvas.apply {
                drawText(
                    "${t}°",
                    x - 12f,
                    ty - 8f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 24f
                        isAntiAlias = true
                    }
                )
            }
        }
    }
}

/** Short day-of-week label for chart x-axis (e.g., Mon, Tue). */
private fun DayOfWeek.shortName(): String =
    getDisplayName(TextStyle.SHORT, Locale.getDefault())
