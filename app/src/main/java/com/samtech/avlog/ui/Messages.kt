package com.samtech.avlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import android.content.res.Resources
import com.samtech.avlog.R
import com.samtech.avlog.core.Formatters
import com.samtech.avlog.data.EditResult
import com.samtech.avlog.fdtl.FdtlStatus
import com.samtech.avlog.fdtl.FdtlWindow
import com.samtech.avlog.fdtl.Severity

fun Resources.editResultMessage(result: EditResult): String = getString(
    when (result) {
        EditResult.Ok -> R.string.edit_saved
        EditResult.NotFound -> R.string.edit_error_not_found
        EditResult.EndBeforeStart -> R.string.edit_error_order
        EditResult.InFuture -> R.string.edit_error_future
        EditResult.Overlaps -> R.string.edit_error_overlap
        EditResult.AnotherSortieOpen -> R.string.edit_error_another_open
    }
)

/** Structured FDTL result formatted as H:MM for display; no raw doubles. */
fun Resources.fdtlWarningMessage(status: FdtlStatus): String {
    val consumed = Formatters.hMm(status.consumedSeconds, roundUp = true)
    val limit = Formatters.hMm(status.limitSeconds, roundUp = false)
    return if (status.severity == Severity.EXCEEDED) {
        getString(R.string.fdtl_exceeded, windowDescription(status.window), consumed, limit)
    } else {
        getString(R.string.fdtl_approaching, windowDescription(status.window), consumed, limit,
            Formatters.hMm(status.remainingSeconds, roundUp = false))
    }
}

/** "24-hour" / "7-day", for sentences. */
fun Resources.windowDescription(w: FdtlWindow): String =
    if (w.isRolling) getString(R.string.fdtl_desc_hours, Formatters.num(w.rollingHours))
    else getString(R.string.fdtl_desc_days, Formatters.num(w.days))

/** "24H" / "7D", for dashboard tiles. */
@Composable
@ReadOnlyComposable
fun windowLabel(w: FdtlWindow): String =
    if (w.isRolling) stringResource(R.string.fdtl_label_hours, Formatters.num(w.rollingHours))
    else stringResource(R.string.fdtl_label_days, Formatters.num(w.days))
