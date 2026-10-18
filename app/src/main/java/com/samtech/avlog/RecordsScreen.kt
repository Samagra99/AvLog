package com.samtech.avlog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samtech.avlog.ui.theme.Converters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(viewModel: RecordsViewModel) {
    val records by viewModel.allRecords.collectAsState(initial = emptyList())
    val fdtlStats by viewModel.fdtlStats.collectAsState()
    val context = LocalContext.current

    // Planner State
    var isPlanningMode by remember { mutableStateOf(false) }
    var planningDate by remember { mutableStateOf(LocalDate.now(ZoneOffset.UTC)) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    // Calculate dynamic stats based on mode
    val displayStats = if (isPlanningMode) {
        // Logic we built in the previous step
        viewModel.calculateRemainingHours(records, planningDate)
    } else {
        fdtlStats
    }

    Scaffold(
        topBar = {
            // Your custom Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Flight Records", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.exportToCsv(context, records) }) {
                    Icon(Icons.Default.Share, "Export", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // --- FDTL SECTION ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPlanningMode) "Planning: $planningDate" else "Cumulative Flight Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlanningMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Plan", style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = isPlanningMode,
                        onCheckedChange = { isPlanningMode = it },
                        modifier = Modifier.scale(0.7f) // Make it a bit smaller
                    )
                }
            }

            if (isPlanningMode) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.DateRange, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Change Planning Date")
                }
            }

            // The Dashboard Row
            FDTLDashboard(displayStats)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- RECORDS LIST ---
            if (records.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(records) { record ->
                        RecordCard(
                            record = record,
                            onDeleteSortie = { date, id -> viewModel.deleteSortie(date, id) }
                        )
                    }
                }
            }
        }
    }

    // Date Picker Dialog Logic
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        planningDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun FDTLDashboard(stats: List<FDTLStatus>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(stats) { stat ->
            val progress = (stat.current / stat.limit).toFloat()
            val color = when {
                progress >= 1.0f -> Color.Red
                progress >= 0.9f -> Color(0xFFFFA500) // Orange warning
                else -> MaterialTheme.colorScheme.primary
            }

            Card(
                modifier = Modifier.width(110.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stat.label, style = MaterialTheme.typography.labelSmall)
                    CircularProgressIndicator(
                        progress = { progress.coerceAtMost(1f) },
                        modifier = Modifier.size(40.dp).padding(vertical = 4.dp),
                        color = color,
                        strokeWidth = 4.dp,
                    )
                    Text(
                        "${stat.current.toInt()}h",
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text("/${stat.limit.toInt()}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun RecordCard(record: DayRecordEntity, onDeleteSortie: (String, String) -> Unit) {
    val today = remember { LocalDate.now(ZoneOffset.UTC).toString() }
    // Initialize expanded as true if the record date matches today
    var isExpanded by rememberSaveable { mutableStateOf(record.date == today) }

    val sorties = remember(record.sortiesJson) {
        Converters().toSortieList(record.sortiesJson)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(record.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Total: ${formatDuration(record.dayTotalSeconds)}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    sorties.forEachIndexed { index, sortie ->
                        SortieRow(index + 1, sortie) { onDeleteSortie(record.date, sortie.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun SortieRow(index: Int, sortie: Sortie, onDelete: () -> Unit) {
    val duration = if (sortie.blockOn != null) (sortie.blockOn - sortie.blockOff) / 1000 else 0L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Sortie $index", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(
                text = "${formatTime(sortie.blockOff)} - ${sortie.blockOn?.let { formatTime(it) } ?: "---"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = formatDuration(duration),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun StatusBadge(isDone: Boolean) {
    Surface(
        color = if (isDone) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = if (isDone) "BA OK" else "No BA",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isDone) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No records yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
