package com.samtech.avlog

import androidx.lifecycle.ViewModel

data class FDTLStatus(
    val period: String,
    val current: Double,
    val limit: Double,
    val label: String
)

