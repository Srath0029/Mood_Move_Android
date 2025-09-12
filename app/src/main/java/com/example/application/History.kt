package com.example.application

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Model of a single historical log item shown in the list. */
data class LogEntry(
    val id: Long,
    val dateMillis: Long,
    val mood: Int,       // 1..5 scale (Very sad .. Very happy)
    val type: String,    // Activity type (Walk / Run / Cycling / ...)
    val minutes: Int     // Activity duration in minutes
)

/** Sorting options for the history list. */
private enum class SortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    DURATION("Longest")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onEdit: (LogEntry) -> Unit = {},
    onView: (LogEntry) -> Unit = {}
) {
    // Demo list kept in memory (replace with Room in the future).
    val initial = remember { mutableStateListOf<LogEntry>().apply { addAll(sampleLogs()) } }

    // Search and filter state.
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortOption.NEWEST) }

    // Date-range pickers (From / To).
    var fromOpen by remember { mutableStateOf(false) }
    var toOpen by remember { mutableStateOf(false) }
    var fromMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var toMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val fromState = rememberDatePickerState(initialSelectedDateMillis = fromMillis)
    val toState = rememberDatePickerState(initialSelectedDateMillis = toMillis)

    // Dialog state for view and delete confirmations.
    var viewing by remember { mutableStateOf<LogEntry?>(null) }
    var deleting by remember { mutableStateOf<LogEntry?>(null) }

    // Helper to format epoch millis to yyyy-MM-dd.
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()) }
    fun formatDate(ms: Long?): String =
        ms?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
        } ?: "Any"

    // Apply query filter, date-range filter, and sort order.
    val filteredSorted = remember(query, fromMillis, toMillis, sort, initial) {
        initial
            .asSequence()
            .filter { e ->
                val matchesQuery =
                    query.isBlank() ||
                            e.type.contains(query, ignoreCase = true) ||
                            formatDate(e.dateMillis).contains(query, ignoreCase = true)
                val afterFrom = fromMillis?.let { e.dateMillis >= it } ?: true
                val beforeTo = toMillis?.let { e.dateMillis <= it } ?: true
                matchesQuery && afterFrom && beforeTo
            }
            .sortedWith(
                when (sort) {
                    SortOption.NEWEST -> compareByDescending<LogEntry> { it.dateMillis }
                    SortOption.OLDEST -> compareBy { it.dateMillis }
                    SortOption.DURATION -> compareByDescending { it.minutes }
                }
            )
            .toList()
    }

    // Screen header, search box, and filter toolbar.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text("History", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // Free-text search by activity type or date string.
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search (type/date)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Keep all filter controls equal height for visual consistency.
        val controlHeight = 48.dp

        // Filters row: From / To / Sort (each uses weight(1f) to be same width).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // From date picker trigger.
            Column(Modifier.weight(1f)) {
                Text("From", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                OutlinedButton(
                    onClick = { fromOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(controlHeight)
                ) { Text(formatDate(fromMillis), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }

            // To date picker trigger.
            Column(Modifier.weight(1f)) {
                Text("To", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                OutlinedButton(
                    onClick = { toOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(controlHeight)
                ) { Text(formatDate(toMillis), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }

            // Sort dropdown button.
            SortMenu(
                current = sort,
                onChange = { sort = it },
                modifier = Modifier.weight(1f),
                height = controlHeight
            )
        }

        Spacer(Modifier.height(12.dp))

        // Scrollable list of entries.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredSorted, key = { it.id }) { entry ->
                HistoryRow(
                    entry = entry,
                    onView = { viewing = it; onView(it) },
                    onEdit = onEdit,
                    onDelete = { deleting = it }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // From date dialog.
    if (fromOpen) {
        DatePickerDialog(
            onDismissRequest = { fromOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    fromMillis = fromState.selectedDateMillis
                    fromOpen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { fromOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = fromState, showModeToggle = true) }
    }

    // To date dialog.
    if (toOpen) {
        DatePickerDialog(
            onDismissRequest = { toOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    toMillis = toState.selectedDateMillis
                    toOpen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { toOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = toState, showModeToggle = true) }
    }

    // View-details dialog for a selected entry.
    viewing?.let { e ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            confirmButton = {
                TextButton(onClick = { viewing = null }) { Text("Close") }
            },
            title = { Text("Log details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Date: ${formatDate(e.dateMillis)}")
                    Text("Mood: ${e.mood} / 5")
                    Text("Exercise: ${e.type}")
                    Text("Duration: ${e.minutes} min")
                }
            }
        )
    }

    // Delete confirmation dialog for a selected entry.
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            confirmButton = {
                TextButton(onClick = {
                    val idx = initial.indexOfFirst { it.id == e.id }
                    if (idx >= 0) initial.removeAt(idx)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            title = { Text("Delete log?") },
            text = { Text("This will permanently remove ${formatDate(e.dateMillis)} (${e.type}, ${e.minutes} min).") }
        )
    }
}

/**
 * Card row for a single log entry with date, mood badge, activity summary,
 * and quick actions (View/Edit/Delete).
 */
@Composable
private fun HistoryRow(
    entry: LogEntry,
    onView: (LogEntry) -> Unit,
    onEdit: (LogEntry) -> Unit,
    onDelete: (LogEntry) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDateOnly(entry.dateMillis),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                MoodBadge(entry.mood)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${entry.type} • ${entry.minutes} min",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onView(entry) }) { Text("View") }
                TextButton(onClick = { onEdit(entry) }) { Text("Edit") }
                TextButton(onClick = { onDelete(entry) }) { Text("Delete") }
            }
        }
    }
}

/** Small colored label showing the mood category based on the 1..5 mood score. */
@Composable
private fun MoodBadge(mood: Int) {
    val label = when (mood) {
        1 -> "Very sad"
        2 -> "Sad"
        3 -> "Normal"
        4 -> "Happy"
        else -> "Very happy"
    }
    val color = when (mood) {
        1 -> Color(0xFFB71C1C)
        2 -> Color(0xFFF57F17)
        3 -> Color(0xFF616161)
        4 -> Color(0xFF2E7D32)
        else -> Color(0xFF1B5E20)
    }
    Text(
        text = label,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(color, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Sort menu button + dropdown list of [SortOption] choices. */
@Composable
private fun SortMenu(
    current: SortOption,
    onChange: (SortOption) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text("Sort", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) { Text(current.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOption.values().forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = { onChange(opt); expanded = false }
                )
            }
        }
    }
}

// --- Helpers & sample data ---

/** Format an epoch millis value to a short yyyy-MM-dd string. */
private fun formatDateOnly(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))

/** Generate simple demo logs for preview/testing the history UI. */
private fun sampleLogs(): List<LogEntry> {
    val today = LocalDate.now()
    val types = listOf("Walk", "Run", "Cycling", "Yoga", "Strength", "Stretching")
    val moods = listOf(3, 4, 2, 5, 3, 4, 1, 5, 2, 3)
    val mins = listOf(15, 30, 10, 45, 25, 35, 20, 50, 12, 40)

    return (0 until 10).map { i ->
        val date = today.minusDays(i.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant()
        LogEntry(
            id = i.toLong(),
            dateMillis = date.toEpochMilli(),
            mood = moods[i % moods.size],
            type = types[i % types.size],
            minutes = mins[i % mins.size]
        )
    }
}
