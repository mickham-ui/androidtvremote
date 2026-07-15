package com.out.androidtvremote.utils

object QuickAccessApps {
    const val PREF_QUICK_ACCESS = "quick_access_apps"
    const val PREF_ORDER = "quick_access_apps_order"
    const val PREF_ENABLED = "quick_access_apps_enabled"

    val DEFAULT_ORDER: List<String> = listOf(
        "Netflix",
        "YouTube",
        "Prime Video",
        "Hotstar",
        "Spotify",
        "YouTube Music",
        "Apple TV",
        "HBO Max",
        "Hulu"
    )

    fun parseOrder(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return DEFAULT_ORDER
        val known = DEFAULT_ORDER.toSet()
        val parsed = raw.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it in known }
        if (parsed.isEmpty()) return DEFAULT_ORDER
        val missing = DEFAULT_ORDER.filter { it !in parsed }
        return parsed + missing
    }

    fun serializeOrder(order: List<String>): String {
        return order.joinToString("|")
    }

    fun defaultEnabled(): Set<String> = DEFAULT_ORDER.toSet()
}
