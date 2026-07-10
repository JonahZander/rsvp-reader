package com.jonahzander.rsvpreader.presentation

enum class ReadingMode(val key: String, val label: String) {
    Rsvp("rsvp", "RSVP"),
    Line("line", "Line"),
    Paged("paged", "Pages"),
    Scroll("scroll", "Scroll");

    companion object {
        fun fromKey(key: String?): ReadingMode = entries.firstOrNull { it.key == key } ?: Rsvp
    }
}
