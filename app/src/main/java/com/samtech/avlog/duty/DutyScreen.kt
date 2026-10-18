package com.samtech.avlog.duty

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samtech.avlog.R
import com.samtech.avlog.core.Formatters
import com.samtech.avlog.data.BaCheckPolicy
import com.samtech.avlog.fdtl.FdtlStatus
import com.samtech.avlog.ui.SortieEditDialog
import com.samtech.avlog.ui.editResultMessage
import com.samtech.avlog.ui.fdtlWarningMessage
import com.samtech.avlog.ui.theme.AvLogTheme
import com.samtech.avlog.ui.theme.severityColors

@Composable
fun DutyScreen(viewModel: DutyViewModel, snackbarHostState: SnackbarHostState) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    var editing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DutyEvent.FdtlWarning ->
                    snackbarHostState.showSnackbar(resources.fdtlWarningMessage(event.status), duration = SnackbarDuration.Long)
                is DutyEvent.EditFailed -> snackbarHostState.showSnackbar(resources.editResultMessage(event.result))
                DutyEvent.EditSaved -> { editing = false }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.flight_log_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = state.utcClock,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // P3.5: stays visible while any window is at or over the threshold.
        state.fdtlAlert?.let { FdtlAlertCard(it) }

        val open = state.openSortie
        if (open != null && (state.stale || state.clockBehind)) {
            StaleSortieBanner(
                text = stringResource(if (state.clockBehind) R.string.clock_behind_banner else R.string.stale_sortie_banner),
                onFix = { editing = true },
            )
        }

        BaCheckCard(validBaTime = state.validBaCheckTime, onConfirm = viewModel::confirmBaCheck)

        when {
            state.loading -> Unit
            open != null -> AirborneDisplay(state.elapsedSeconds, onBlockOn = viewModel::blockOn)
            state.validBaCheckTime != null -> Button(
                onClick = viewModel::blockOff,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FlightTakeoff, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.block_off), fontSize = 18.sp)
            }
            else -> Text(
                stringResource(R.string.ba_required_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val openSortie = state.openSortie
    if (editing && openSortie != null) {
        SortieEditDialog(
            sortie = openSortie,
            nowMillis = state.nowMillis.coerceAtLeast(openSortie.blockOff),
            onDismiss = { editing = false },
            onSave = { off, on -> viewModel.updateSortie(openSortie.id, off, on) },
        )
    }
}

@Composable
private fun BaCheckCard(validBaTime: Long?, onConfirm: () -> Unit) {
    val av = AvLogTheme.colors
    val done = validBaTime != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (done) av.successContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (done) av.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ba_check_title), fontWeight = FontWeight.Bold)
            if (validBaTime == null) {
                Button(onClick = onConfirm, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.ba_confirm))
                }
            } else {
                Text(
                    stringResource(
                        R.string.ba_passed_at,
                        Formatters.utcTime(validBaTime),
                        Formatters.utcTime(validBaTime + BaCheckPolicy.VALIDITY_MS),
                    )
                )
            }
        }
    }
}

@Composable
private fun FdtlAlertCard(status: FdtlStatus) {
    val colors = severityColors(status.severity)
    val resources = LocalResources.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.onContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(resources.fdtlWarningMessage(status), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StaleSortieBanner(text: String, onFix: () -> Unit) {
    val av = AvLogTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = av.warningContainer, contentColor = av.onWarningContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text)
            OutlinedButton(onClick = onFix, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.set_block_on_time))
            }
        }
    }
}

@Composable
fun AirborneDisplay(seconds: Long, onBlockOn: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer)
    ) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.airborne), fontWeight = FontWeight.Black)
            Text(
                text = Formatters.hms(seconds),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace
            )
            Button(
                onClick = onBlockOn,
                colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.block_on))
            }
        }
    }
}
