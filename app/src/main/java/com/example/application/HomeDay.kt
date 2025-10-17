package com.example.application

import java.time.LocalDate

/**
 * HomeDay
 *
 * Aggregated daily metrics used by the Home dashboard and weekly chart.
 * Values are derived from one or more exercise logs for the same day.
 *
 * Conventions
 * - [date]: calendar day (local time zone), no time component.
 * - [mood]: 1..5 scale (higher is better).
 * - [minutes]: total exercise duration for the day (≥ 0).
 * - [tempC]: average ambient temperature in °C (rounded to int).
 *
 * @property date    The day these metrics represent.
 * @property mood    Daily mood score (1..5).
 * @property minutes Sum of exercise minutes for the day.
 * @property tempC   Average temperature in degrees Celsius.
 */
data class HomeDay(
    val date: LocalDate,
    val mood: Int,
    val minutes: Int,
    val tempC: Int
)
