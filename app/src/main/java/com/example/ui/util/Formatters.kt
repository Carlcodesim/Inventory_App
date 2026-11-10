package com.example.ui.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    fun currency(value: Double): String {
        return currencyFormat.format(value)
    }

    fun percent(value: Double): String {
        return String.format(Locale.US, "%.1f%%", value)
    }

    fun dateTime(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    fun shortDateTime(timestamp: Long): String {
        return shortDateFormat.format(Date(timestamp))
    }
}
