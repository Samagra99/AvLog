package com.samtech.avlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import android.content.res.Resources
import com.samtech.avlog.R
import com.samtech.avlog.core.Formatters
import com.samtech.avlog.data.EditResult
import com.samtech.avlog.fdtl.FdtlStatus
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
        getString(R.string.fdtl_exceeded, Formatters.num(status.window.days), consumed, limit)
    } else {
        getString(R.string.fdtl_approaching, Formatters.num(status.window.days), consumed, limit,
            Formatters.hMm(status.remainingSeconds, roundUp = false))
    }
}

@Composable
@ReadOnlyComposable
fun windowLabel(days: Int): String = stringResource(R.string.fdtl_window_label, Formatters.num(days))
