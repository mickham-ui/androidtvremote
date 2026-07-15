package com.out.androidtvremote.ui.frags.cast.data

enum class MediaType(val type: String) {
    VIDEO("video"),
    AUDIO("audio"),
    IMAGE("image");

    companion object {
        fun fromString(type: String?): MediaType? {
            return values().find { it.type == type }
        }
    }
}
