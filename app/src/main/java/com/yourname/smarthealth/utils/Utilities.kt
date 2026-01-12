package com.yourname.smarthealth.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.yourname.smarthealth.adapter.DurationAdapter
import com.yourname.smarthealth.adapter.InstantAdapter
import com.yourname.smarthealth.adapter.LocalDateAdapter
import com.yourname.smarthealth.adapter.ZoneOffsetAdapter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object Utilities {

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Instant::class.java, InstantAdapter())
        .registerTypeAdapter(Duration::class.java, DurationAdapter())
        .registerTypeAdapter(ZoneOffset::class.java, ZoneOffsetAdapter())
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .create()
}