package com.samtech.avlog.ui.theme

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.samtech.avlog.Sortie

class Converters {
    @TypeConverter
    fun fromSortieList(value: List<Sortie>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toSortieList(value: String): List<Sortie> {
        val listType = object : TypeToken<List<Sortie>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }
}