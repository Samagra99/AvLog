package com.samtech.avlog.records

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samtech.avlog.R
import com.samtech.avlog.core.Formatters
import com.samtech.avlog.data.DayLog
import com.samtech.avlog.data.ImportMode
import com.samtech.avlog.data.SortieEntity
import com.samtech.avlog.fdtl.FdtlCalculator
import com.samtech.avlog.fdtl.FdtlStatus
import com.samtech.avlog.ui.SortieEditDialog
import com.samtech.avlog.ui.editResultMessage
import com.samtech.avlog.ui.theme.AvLogTheme
import com.samtech.avlog.ui.theme.severityColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(viewModel: RecordsViewModel, snackbarHostState: SnackbarHostState) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = context.resources

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::writeBackup)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readBackup)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordsEvent.Share -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.export_subject))
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, resources.getString(R.string.export_chooser)))
                }
                RecordsEvent.NoRecords -> snackbarHostState.showSnackbar(resources.getString(R.string.export_no_records))
                RecordsEvent.ExportFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.export_failed))
                is RecordsEvent.SortieDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.sortie_deleted),
                        actionLabel = resources.getString(R.string.undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.sortie)
                }
                is RecordsEvent.EditFailed -> snackbarHostState.showSnackbar(resources.editResultMessage(event.result))
                RecordsEvent.EditSaved -> editingId = null
                is RecordsEvent.BackupSaved -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.backup_saved, event.sorties, event.licenses)
                )
                is RecordsEvent.RestoreDone -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.restore_done, event.sorties, event.licenses)
                )
                is RecordsEvent.Failed -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.operation_failed, event.message ?: "")
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.records_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_csv)) }, onClick = {
                        menuOpen = false; viewModel.exportCsv()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.backup_json)) }, onClick = {
                        menuOpen = false; createBackup.launch(viewModel.suggestedBackupName())
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.restore_json)) }, onClick = {
                        menuOpen = false; openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    })
                }
            }
        }

        // --- FDTL SECTION ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.planningMode) {
                    stringResource(R.string.planning_for, Formatters.isoDate(state.planningDate))
                } else {
                    stringResource(R.string.cumulative_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.planningMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.plan), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Switch(checked = state.planningMode, onCheckedChange = viewModel::setPlanningMode)
            }
        }

        if (state.planningMode) {
            Text(
                stringResource(R.string.planning_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Icon(Icons.Default.DateRange, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.change_planning_date))
            }
        }

        FdtlDashboard(state.stats, planning = state.planningMode)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.days.isEmpty() -> EmptyState()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.days, key = { it.date.toString() }) { day ->
                    RecordCard(
                        day = day,
                        initiallyExpanded = day.date == state.today,
                        onEdit = { editingId = it.id },
                        onDelete = { viewModel.deleteSortie(it.id) },
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.planningDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.setPlanningDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }

    val editing = editingId?.let { id -> state.days.firstNotNullOfOrNull { d -> d.sorties.find { it.id == id } } }
    if (editing != null) {
        SortieEditDialog(
            sortie = editing,
            nowMillis = viewModel.nowMillis(),
            onDismiss = { editingId = null },
            onSave = { off, on -> viewModel.updateSortie(editing.id, off, on) },
        )
    }

    state.pendingRestore?.let { data ->
        if (!confirmReplace) {
            AlertDialog(
                onDismissRequest = viewModel::cancelRestore,
                title = { Text(stringResource(R.string.restore_title)) },
                text = { Text(stringResource(R.string.restore_summary, data.sorties.size, data.licenses.size)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmRestore(ImportMode.MERGE) }) { Text(stringResource(R.string.restore_merge)) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = { confirmReplace = true }) {
                            Text(stringResource(R.string.restore_replace), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { confirmReplace = false },
                title = { Text(stringResource(R.string.restore_replace_confirm_title)) },
                text = { Text(stringResource(R.string.restore_replace_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmReplace = false
                        viewModel.confirmRestore(ImportMode.REPLACE)
                    }) { Text(stringResource(R.string.restore_replace), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

/**
 * P3.4: severity always comes from the consumed fraction. Live mode shows consumed (rounded up);
 * planning mode shows what is left for the planned day (rounded down) plus what was used before it.
 */
@Composable
fun FdtlDashboard(stats: List<FdtlStatus>, planning: Boolean) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(stats, key = { it.window.days }) { stat ->
            val colors = severityColors(stat.severity)
            Card(
                modifier = Modifier.width(116.dp),
                colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.onContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.fdtl_window_label, stat.window.days), style = MaterialTheme.typography.labelMedium)
                    CircularProgressIndicator(
                        progress = { stat.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.size(40.dp).padding(vertical = 4.dp),
                        color = colors.accent,
                        strokeWidth = 4.dp,
                    )
                    if (planning) {
                        Text(Formatters.hMm(stat.remainingSeconds, roundUp = false), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.fdtl_left), style = MaterialTheme.typography.labelSmall)
                        Text(
                            stringResource(R.string.fdtl_used_before, Formatters.hMm(stat.consumedSeconds, roundUp = true)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        Text(Formatters.hMm(stat.consumedSeconds, roundUp = true), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.fdtl_of_limit, Formatters.hMm(stat.limitSeconds, roundUp = false)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordCard(
    day: DayLog,
    initiallyExpanded: Boolean,
    onEdit: (SortieEntity) -> Unit,
    onDelete: (SortieEntity) -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val dateText = Formatters.isoDate(day.date)
    val toggleLabel = stringResource(if (isExpanded) R.string.collapse_day else R.string.expand_day, dateText)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = toggleLabel) { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(dateText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        stringResource(R.string.day_total, Formatters.hms(day.totalSeconds)),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    day.baCheckTime?.let {
                        Text(
                            stringResource(R.string.ba_at, Formatters.utcTime(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = toggleLabel)
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    day.sorties.forEachIndexed { index, sortie ->
                        SortieRow(index + 1, dateText, sortie, onEdit = { onEdit(sortie) }, onDelete = { onDelete(sortie) })
                    }
                }
            }
        }
    }
}

@Composable
fun SortieRow(index: Int, dateText: String, sortie: SortieEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val duration = FdtlCalculator.durationSeconds(sortie.blockOff, sortie.blockOn)
    val needsCheck = sortie.blockOn != null && sortie.blockOn < sortie.blockOff

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sortie_n, index),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sortie.editedAt != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.edited_marker), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (needsCheck) {
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.needs_check_marker), style = MaterialTheme.typography.labelSmall, color = AvLogTheme.colors.warning)
                }
            }
            Text(
                text = "${Formatters.utcTime(sortie.blockOff)} – ${sortie.blockOn?.let { Formatters.utcTime(it) } ?: "---"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = Formatters.hms(duration),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_sortie_cd, index, dateText))
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = stringResource(R.string.delete_sortie_cd, index, dateText),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.no_records),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

