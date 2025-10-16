package com.example.application

import java.time.LocalDate

data class HomeDay(
    val date: LocalDate,
    val mood: Int,
    val minutes: Int,
    val tempC: Int
)