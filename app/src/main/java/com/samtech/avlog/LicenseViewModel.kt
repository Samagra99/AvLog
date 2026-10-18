package com.samtech.avlog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class LicenseViewModel(private val licenseDao: LicenseDao) : ViewModel() {
    val allLicenses = licenseDao.getAllLicenses().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun addLicense(name: String, expiryDate: Long) {
        // This MUST be inside viewModelScope
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newLicense = LicenseEntity(
                    name = name,
                    expiryDate = expiryDate
                )
                licenseDao.insertLicense(newLicense)
            } catch (e: Exception) {
                Log.e("LicenseCrash", "Error saving license", e)
            }
        }
    }

    fun updateLicense(id: String, newName: String, newExpiry: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedLicense = LicenseEntity(
                id = id, // Keeping the same ID triggers the REPLACE strategy
                name = newName,
                expiryDate = newExpiry
            )
            licenseDao.insertLicense(updatedLicense)
        }
    }

    fun deleteLicense(license: LicenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            licenseDao.deleteLicense(license)
        }
    }

    fun calculateDaysRemaining(expiryMillis: Long): Long {
        val expiry = Instant.ofEpochMilli(expiryMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return ChronoUnit.DAYS.between(today, expiry)
    }
}
