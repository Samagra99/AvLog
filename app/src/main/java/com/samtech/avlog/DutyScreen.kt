package com.samtech.avlog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DutyScreen(viewModel: DutyViewModel) {
    val dayRecord by viewModel.dayRecord.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsState()
    val utcClock by viewModel.currentUtcTime.collectAsState()

    val warning by viewModel.fdtlWarning.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(warning) {
        warning?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            viewModel.clearWarning() // Tell ViewModel we've shown it
        }
    }

    Scaffold(
        snackbarHost = {
            // This is the specific "anchor" for the snackbar
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
            Text(
                "Flight Log",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // THE UTC CLOCK
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = utcClock,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
            // BA CHECK CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (dayRecord.baCheckDone) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Pre-Flight BA Check", fontWeight = FontWeight.Bold)
                    if (!dayRecord.baCheckDone) {
                        Button(
                            onClick = { viewModel.confirmBACheck() },
                            Modifier.padding(top = 8.dp)
                        ) {
                            Text("CONFIRM CHECK")
                        }
                    } else {
                        Text(
                            "Passed at ${formatTime(dayRecord.baCheckTime!!)}",
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // TIMER / ACTION SECTION
            val activeSortie = dayRecord.sorties.find { it.blockOn == null }


            if (activeSortie != null) {
                // Airborne UI (The pulsing timer)
                AirborneDisplay(elapsed, onBlockOn = { viewModel.handleBlockOn() })
            } else if (dayRecord.baCheckDone) {
                // Block Off Button
                Button(
                    onClick = { viewModel.handleBlockOff() },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FlightTakeoff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("BLOCK OFF", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun AirborneDisplay(seconds: Long, onBlockOn: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AIRBORNE", color = Color.Red, fontWeight = FontWeight.Black)
            Text(
                text = formatDuration(seconds),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace
            )
            Button(
                onClick = onBlockOn,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("BLOCK ON")
            }
        }
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(timestamp))
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}
