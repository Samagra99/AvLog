package com.samtech.avlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.samtech.avlog.R
import com.samtech.avlog.core.Formatters
import com.samtech.avlog.data.SortieEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * P4.5: edit Block Off / Block On in UTC. Block Off stays on the sortie's UTC date; a Block On earlier than
 * Block Off is taken as the next UTC day. The DAO re-validates (order, overlap, not in the future).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortieEditDialog(
    sortie: SortieEntity,
    nowMillis: Long,
    onDismiss: () -> Unit,
    onSave: (blockOff: Long, blockOn: Long?) -> Unit,
) {
    val offTime = Instant.ofEpochMilli(sortie.blockOff).atZone(ZoneOffset.UTC).toLocalTime()
    val onTime = Instant.ofEpochMilli(sortie.blockOn ?: nowMillis).atZone(ZoneOffset.UTC).toLocalTime()
    val offState = rememberTimePickerState(offTime.hour, offTime.minute, is24Hour = true)
    val onState = rememberTimePickerState(onTime.hour, onTime.minute, is24Hour = true)
    var stillAirborne by rememberSaveable { mutableStateOf(false) }

    val offMillis = sortie.dateUtc.atTime(LocalTime.of(offState.hour, offState.minute))
        .toInstant(ZoneOffset.UTC).toEpochMilli()
    val sameDayOn = sortie.dateUtc.atTime(LocalTime.of(onState.hour, onState.minute))
        .toInstant(ZoneOffset.UTC).toEpochMilli()
    val nextDay = sameDayOn < offMillis
    val onMillis = if (nextDay) sameDayOn + 24 * 3600 * 1000L else sameDayOn

    val error = when {
        offMillis > nowMillis -> stringResource(R.string.edit_error_future)
        !stillAirborne && onMillis > nowMillis -> stringResource(R.string.edit_error_future)
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_sortie_title, Formatters.isoDate(sortie.dateUtc))) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.block_off_utc), style = MaterialTheme.typography.labelLarge)
                TimeInput(state = offState)
                if (sortie.blockOn == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = stillAirborne, onCheckedChange = { stillAirborne = it })
                        Text(stringResource(R.string.edit_still_airborne))
                    }
                }
                if (!stillAirborne) {
                    Text(
                        stringResource(R.string.block_on_utc) + if (nextDay) " " + stringResource(R.string.edit_next_day) else "",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TimeInput(state = onState)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = { onSave(offMillis, if (stillAirborne) null else onMillis) },
                modifier = Modifier.fillMaxWidth(0.4f),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
