package com.out.androidtvremote.ui.frags.cast.data

sealed class CastingState {
    object Idle : CastingState()
    data class Casting(val title: String, val description: String) : CastingState()
}