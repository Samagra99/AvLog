package com.samtech.avlog

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.samtech.avlog.duty.DutyViewModel
import com.samtech.avlog.licenses.LicenseViewModel
import com.samtech.avlog.records.RecordsViewModel

/** One factory for every view model, backed by the [AppContainer]. */
object AvLogViewModels {
    private val CreationExtras.container: AppContainer
        get() = (this[APPLICATION_KEY] as AvLogApp).container

    val Factory = viewModelFactory {
        initializer { DutyViewModel(container.dutyRepository, container.clock) }
        initializer {
            RecordsViewModel(container.dutyRepository, container.csvExporter, container.backupManager, container.clock)
        }
        initializer { LicenseViewModel(container.licenseRepository, container.clock) }
    }
}
