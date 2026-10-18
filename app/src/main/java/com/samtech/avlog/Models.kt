package com.samtech.avlog

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import java.time.LocalDateTime

data class Sortie(
    val id: String = UUID.randomUUID().toString(),
    val blockOff: Long = System.currentTimeMillis(),
    val blockOn: Long? = null
)

data class DayRecord(
    val date: String, // e.g. "2024-05-20"
    val baCheckDone: Boolean = false,
    val baCheckTime: Long? = null,
    val sorties: List<Sortie> = emptyList()
)
