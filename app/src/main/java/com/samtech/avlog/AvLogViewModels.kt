package com.samtech.avlog

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** One factory for every view model, backed by the [AppContainer]. */
object AvLogViewModels {
    private val CreationExtras.container: AppContainer
        get() = (this[APPLICATION_KEY] as AvLogApp).container

    val Factory = viewModelFactory {
        initializer { DutyViewModel(container.database.dutyDao(), container.app, container.clock) }
        initializer { RecordsViewModel(container.database.dutyDao(), container.clock) }
        initializer { LicenseViewModel(container.database.licenseDao()) }
    }
}
