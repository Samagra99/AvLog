package com.samtech.avlog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LicenseScreen(viewModel: LicenseViewModel) {
    val licenses by viewModel.allLicenses.collectAsState()
    // State to track which license is being edited
    var editingLicense by remember { mutableStateOf<LicenseEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // Use your AddLicenseDialog logic, but pass initial values if editingLicense != null
    if (showDialog || editingLicense != null) {
        AddLicenseDialog(
            initialName = editingLicense?.name ?: "",
            onDismiss = {
                showDialog = false
                editingLicense = null
            },
            onConfirm = { name, date ->
                if (editingLicense != null) {
                    viewModel.updateLicense(editingLicense!!.id, name, date)
                } else {
                    viewModel.addLicense(name, date)
                }
                showDialog = false
                editingLicense = null
            }
        )
    }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Text(
                    "Certifications",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
            }

            items(licenses) { license ->
                val daysLeft = viewModel.calculateDaysRemaining(license.expiryDate)
                LicenseCard(
                    license = license,
                    daysLeft = viewModel.calculateDaysRemaining(license.expiryDate),
                    onEdit = { editingLicense = license },
                    onDelete = { viewModel.deleteLicense(license) }
                )
            }
        }
}

@Composable
fun LicenseCard(license: LicenseEntity, daysLeft: Long, onEdit: () -> Unit,
                onDelete: () -> Unit) {
    val statusColor = when {
        daysLeft <= 0 -> Color.Red
        daysLeft <= license.warningDays -> Color(0xFFFFA000) // Orange
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(license.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Expires: ${formatDate(license.expiryDate)}", style = MaterialTheme.typography.bodySmall)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (daysLeft <= 0) "EXPIRED" else "$daysLeft days",
                    color = statusColor,
                    fontWeight = FontWeight.Black
                )
                Text("remaining", style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun AddLicenseDialog(onDismiss: () -> Unit, onConfirm: (String, Long) -> Unit, initialName: String = "") {
    var name by remember { mutableStateOf(initialName) }
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }

    val selectedDateText = datePickerState.selectedDateMillis?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
    } ?: "Select Date"

    // THE MAIN DIALOG (Name + Date Trigger)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Certification") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("License/Rating Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Clicking this opens the actual Calendar
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Expiry: $selectedDateText")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    datePickerState.selectedDateMillis?.let { onConfirm(name, it) }
                },
                enabled = name.isNotBlank() && datePickerState.selectedDateMillis != null
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    // THE SEPARATE DATE PICKER (Triggered by the button above)
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = { Text("Select Expiry Date", modifier = Modifier.padding(16.dp)) }
            )
        }
    }
}