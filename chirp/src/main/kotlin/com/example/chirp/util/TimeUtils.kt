package com.example.chirp.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats a Unix timestamp into a human-readable relative time string.
 * Shows relative time for recent posts, date for older ones.
 */
fun formatRelativeTime(unixTimestamp: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - unixTimestamp

    return when {
        diff < 0 -> "now"
        diff < 60 -> "now"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        diff < 604800 -> "${diff / 86400}d"
        else -> {
            val date = Date(unixTimestamp * 1000)
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }
}
