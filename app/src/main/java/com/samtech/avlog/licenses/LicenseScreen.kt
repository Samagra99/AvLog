package com.samtech.avlog.licenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samtech.avlog.R
import com.samtech.avlog.core.Formatters
import com.samtech.avlog.data.LicenseEntity
import com.samtech.avlog.fdtl.Severity
import com.samtech.avlog.ui.theme.severityColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun LicenseScreen(
    viewModel: LicenseViewModel,
    snackbarHostState: SnackbarHostState,
    onLicenseSaved: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    // null = closed, "" = add, otherwise the id being edited
    var dialogFor by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LicenseEvent.Saved -> onLicenseSaved()
                is LicenseEvent.Deleted -> {
                    val r = snackbarHostState.showSnackbar(
                        resources.getString(R.string.license_deleted, event.license.name),
                        actionLabel = resources.getString(R.string.undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (r == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.license)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.licenses_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
            }
            when {
                state.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.items.isEmpty() -> item {
                    Text(stringResource(R.string.no_licenses), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> items(state.items, key = { it.license.id }) { item ->
                    LicenseCard(
                        item = item,
                        onEdit = { dialogFor = item.license.id },
                        onDelete = { viewModel.delete(item.license.id) },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { dialogFor = "" },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_license))
        }
    }

    dialogFor?.let { key ->
        val editing = state.items.firstOrNull { it.license.id == key }?.license
        if (key.isEmpty() || editing != null) {
            LicenseDialog(
                initial = editing,
                defaultDate = state.today,
                onDismiss = { dialogFor = null },
                onConfirm = { name, date, warn ->
                    viewModel.save(editing?.id, name, date, warn)
                    dialogFor = null
                },
            )
        }
    }
}

@Composable
fun LicenseCard(item: LicenseItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    val license = item.license
    val daysLeft = item.daysLeft
    val severity = when {
        daysLeft < 0 -> Severity.EXCEEDED
        daysLeft <= license.warningDays -> Severity.APPROACHING
        else -> Severity.OK
    }
    val colors = severityColors(severity)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.onContainer),
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(license.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.expires_on, Formatters.displayDate(license.expiryDate)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = when {
                        daysLeft < 0 -> stringResource(R.string.license_expired)
                        daysLeft == 0L -> stringResource(R.string.license_expires_today)
                        else -> pluralStringResource(R.plurals.days_remaining, daysLeft.toInt(), daysLeft.toInt())
                    },
                    color = colors.accent,
                    fontWeight = FontWeight.Black,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_license_cd, license.name))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_license_cd, license.name),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** P5.1: prefilled for edit, keeps warning days, survives rotation. DatePicker millis are UTC both ways (P5.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseDialog(
    initial: LicenseEntity?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (name: String, expiry: LocalDate, warningDays: Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var expiryEpochDay by rememberSaveable { mutableStateOf(initial?.expiryDate?.toEpochDay()) }
    var warningText by rememberSaveable {
        mutableStateOf((initial?.warningDays ?: LicenseEntity.DEFAULT_WARNING_DAYS).toString())
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val expiry = expiryEpochDay?.let(LocalDate::ofEpochDay)
    val warningDays = warningText.toIntOrNull()?.takeIf { it in 0..3650 }
    val valid = name.isNotBlank() && expiry != null && warningDays != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_license else R.string.edit_license)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.license_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            R.string.expiry_button,
                            expiry?.let { Formatters.displayDate(it) } ?: stringResource(R.string.select_date),
                        )
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = warningText,
                    onValueChange = { v -> warningText = v.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.warning_days)) },
                    isError = warningDays == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), expiry!!, warningDays!!) },
                enabled = valid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (expiry ?: defaultDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        expiryEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(
                state = pickerState,
                title = { Text(stringResource(R.string.select_expiry_date), modifier = Modifier.padding(16.dp)) }
            )
        }
    }
}
