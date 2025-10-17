package com.example.application

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.LocalTime
import androidx.compose.ui.platform.LocalContext
import android.app.TimePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.TextFieldDefaults

/**
 * LogEntry
 *
 * Row model for the History list and edit/view dialogs.
 *
 * @property id         Stable identifier.
 * @property dateMillis UTC epoch millis representing the activity day.
 * @property mood       1..5 scale rendered by [MoodBadge].
 * @property type       Activity label (e.g., "Run", "Yoga").
 * @property minutes    Duration in minutes, used for sorting.
 * @property startTime  "HH:mm" start time string.
 * @property endTime    "HH:mm" end time string.
 */
data class LogEntry(
    val id: String,
    val dateMillis: Long,
    val mood: Int,
    val type: String,
    val minutes: Int,
    val startTime: String,
    val endTime: String
)

/**
 * SortOption
 *
 * Available sort orders for the History list.
 */
private enum class SortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    DURATION("Longest")
}

private val activityTypes = listOf("Walk", "Run", "Cycling", "Yoga", "Strength", "Stretching")

/**
 * Disallows future dates in the date pickers.
 */
@OptIn(ExperimentalMaterial3Api::class)
private object NotInTheFutureSelectableDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        return utcTimeMillis <= Instant.now().toEpochMilli()
    }
}

/**
 * Allows selecting dates from [fromMillis] up to today.
 * If [fromMillis] is null, selection is disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
private class ToDateSelectableDates(private val fromMillis: Long?) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        if (fromMillis == null) {
            return false
        }
        val isNotInFuture = NotInTheFutureSelectableDates.isSelectableDate(utcTimeMillis)
        val isAfterOrOnFrom = utcTimeMillis >= fromMillis
        return isNotInFuture && isAfterOrOnFrom
    }
}

/**
 * HistoryScreen
 *
 * Displays the user's exercise logs with:
 * - Text search (activity type/date),
 * - Date range filtering (From/To),
 * - Sorting (Newest/Oldest/Longest),
 * - Row actions (View/Edit/Delete).
 *
 * State & effects
 * - Observes [HistoryViewModel.uiState] with lifecycle awareness.
 * - Shows loading spinner, empty state, or a list based on data.
 * - Opens dialogs for view, edit, and delete confirmations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(),
    onView: (LogEntry) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortOption.NEWEST) }
    var fromOpen by remember { mutableStateOf(false) }
    var toOpen by remember { mutableStateOf(false) }
    var fromMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var toMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewing by remember { mutableStateOf<LogEntry?>(null) }
    var deleting by remember { mutableStateOf<LogEntry?>(null) }
    var editing by remember { mutableStateOf<LogEntry?>(null) }

    val fromState = rememberDatePickerState(
        initialSelectedDateMillis = fromMillis,
        selectableDates = NotInTheFutureSelectableDates
    )

    val toState = rememberDatePickerState(
        initialSelectedDateMillis = toMillis,
        selectableDates = remember(fromMillis) { ToDateSelectableDates(fromMillis) }
    )

    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()) }
    fun formatDate(ms: Long?): String =
        ms?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
        } ?: "Any"

    // Derived list based on search, date range, and sort.
    val filteredSorted = remember(query, fromMillis, toMillis, sort, uiState.logs) {
        uiState.logs
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text("History", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search (type/date)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        val controlHeight = 48.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("From", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                OutlinedButton(
                    onClick = { fromOpen = true },
                    modifier = Modifier.fillMaxWidth().height(controlHeight)
                ) { Text(formatDate(fromMillis), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Column(Modifier.weight(1f)) {
                Text("To", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                OutlinedButton(
                    onClick = { toOpen = true },
                    enabled = fromMillis != null,
                    modifier = Modifier.fillMaxWidth().height(controlHeight)
                ) { Text(formatDate(toMillis), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            SortMenu(
                current = sort,
                onChange = { sort = it },
                modifier = Modifier.weight(1f),
                height = controlHeight
            )
        }
        Spacer(Modifier.height(12.dp))
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredSorted.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (uiState.logs.isEmpty()) "No logs yet." else "No logs match your filters.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredSorted, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onView = { viewing = it },
                        onEdit = { editing = it },
                        onDelete = { deleting = it }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    // From date picker
    if (fromOpen) {
        DatePickerDialog(
            onDismissRequest = { fromOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    fromMillis = fromState.selectedDateMillis
                    if (toMillis != null && fromMillis != null && fromMillis!! > toMillis!!) {
                        toMillis = null
                    }
                    fromOpen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { fromOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = fromState, showModeToggle = true) }
    }

    // To date picker
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

    // View dialog
    viewing?.let { e ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } },
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

    // Delete confirmation
    deleting?.let { entryToDelete ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete Log?") },
            text = { Text("Are you sure you want to permanently delete the log for ${formatDate(entryToDelete.dateMillis)}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLog(entryToDelete.id)
                        deleting = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleting = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit dialog
    editing?.let { entryToEdit ->
        EditLogDialog(
            logEntry = entryToEdit,
            onDismiss = { editing = null },
            onSave = { date, mood, type, start, end ->
                viewModel.updateLog(entryToEdit.id, date, mood, type, start, end)
                editing = null
            }
        )
    }
}

/**
 * EditLogDialog
 *
 * In-place editing of a single log entry with validation:
 * - mood must be 1..5
 * - end time cannot be before start time and is capped at +1 hour
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLogDialog(
    logEntry: LogEntry,
    onDismiss: () -> Unit,
    onSave: (date: Long, mood: Int, type: String, start: String, end: String) -> Unit
) {
    var currentType by remember { mutableStateOf(logEntry.type) }
    var currentMood by remember { mutableStateOf(logEntry.mood.toString()) }
    var currentDateMillis by remember { mutableStateOf(logEntry.dateMillis) }
    var currentStartTime by remember { mutableStateOf(logEntry.startTime) }
    var currentEndTime by remember { mutableStateOf(logEntry.endTime) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var activityMenuExpanded by remember { mutableStateOf(false) }
    val isEndTimeEnabled = currentStartTime.isNotBlank()

    val isMoodValid = currentMood.toIntOrNull() in 1..5
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val areTimesValid = try {
        val start = LocalTime.parse(currentStartTime, timeFormatter)
        val end = LocalTime.parse(currentEndTime, timeFormatter)
        !end.isBefore(start)
    } catch (e: Exception) {
        false
    }
    val isSaveEnabled = currentType.isNotBlank() && isMoodValid && areTimesValid

    val editDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDateMillis,
        selectableDates = NotInTheFutureSelectableDates
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit log") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedTextField(
                        value = currentType,
                        onValueChange = {},
                        label = { Text("Activity type") },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activityMenuExpanded = true },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Select activity",
                                modifier = Modifier.clickable { activityMenuExpanded = true }
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = activityMenuExpanded,
                        onDismissRequest = { activityMenuExpanded = false }
                    ) {
                        activityTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    currentType = type
                                    activityMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showStartTimePicker = true }
                    ) {
                        OutlinedTextField(
                            value = currentStartTime,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Start time") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            colors = TextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor = Color.Transparent,
                                disabledIndicatorColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = isEndTimeEnabled) { showEndTimePicker = true }
                    ) {
                        OutlinedTextField(
                            value = currentEndTime,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("End time") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            colors = TextFieldDefaults.colors(
                                disabledTextColor = if (isEndTimeEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                disabledContainerColor = Color.Transparent,
                                disabledIndicatorColor = if (isEndTimeEnabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                disabledLabelColor = if (isEndTimeEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = currentMood,
                    onValueChange = { if (it.length <= 1) currentMood = it.filter { c -> c.isDigit() } },
                    label = { Text("Mood (1..5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isMoodValid && currentMood.isNotEmpty()
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Date: ${formatDateOnly(currentDateMillis)}")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(currentDateMillis, currentMood.toInt(), currentType, currentStartTime, currentEndTime) },
                enabled = isSaveEnabled
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    editDatePickerState.selectedDateMillis?.let { currentDateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = editDatePickerState, showModeToggle = true) }
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = currentStartTime,
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = { newTime ->
                currentStartTime = newTime
                try {
                    val startTime = LocalTime.parse(newTime, timeFormatter)
                    val endTime = LocalTime.parse(currentEndTime, timeFormatter)
                    if (startTime.isAfter(endTime)) {
                        currentEndTime = newTime
                    }
                } catch (e: Exception) {
                    currentEndTime = newTime
                }
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = currentEndTime,
            onDismiss = { showEndTimePicker = false },
            onTimeSelected = { newTime ->
                try {
                    val startTime = LocalTime.parse(currentStartTime, timeFormatter)
                    var endTime = LocalTime.parse(newTime, timeFormatter)
                    val maxEndTime = startTime.plusHours(1)

                    if (endTime.isBefore(startTime)) {
                        endTime = startTime
                    }
                    if (endTime.isAfter(maxEndTime)) {
                        endTime = maxEndTime
                    }
                    currentEndTime = endTime.format(timeFormatter)
                } catch (e: Exception) {
                    currentEndTime = newTime
                }
                showEndTimePicker = false
            }
        )
    }
}

/**
 * TimePickerDialog
 *
 * Wrapper around the platform dialog that returns a "HH:mm" time string.
 * Dismissal is wired via `setOnDismissListener`.
 */
@Composable
private fun TimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val initialLocalTime = try {
        LocalTime.parse(initialTime, formatter)
    } catch (e: Exception) {
        LocalTime.now()
    }

    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val selectedTime = LocalTime.of(hourOfDay, minute).format(formatter)
                onTimeSelected(selectedTime)
            },
            initialLocalTime.hour,
            initialLocalTime.minute,
            true
        ).apply {
            setOnDismissListener { onDismiss() }
        }
    }

    LaunchedEffect(Unit) {
        timePickerDialog.show()
    }
}

/**
 * HistoryRow
 *
 * Card row for a single log entry with quick actions (view, edit, delete).
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

/**
 * MoodBadge
 *
 * Displays a mood label (1..5) with a color-coded background.
 */
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

/**
 * SortMenu
 *
 * Compact dropdown for selecting a [SortOption].
 *
 * @param current  Currently selected option.
 * @param onChange Emits the selected option.
 * @param modifier Placement of the control in parent layouts.
 * @param height   Button height to align with sibling controls.
 */
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

/**
 * Formats epoch millis as "yyyy-MM-dd" in the device time zone, or "Any" if null.
 */
private fun formatDateOnly(ms: Long?): String {
    if (ms == null) return "Any"
    return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))
}
