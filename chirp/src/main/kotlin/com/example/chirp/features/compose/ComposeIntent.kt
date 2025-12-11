package com.example.chirp.features.compose

sealed class ComposeIntent {
    data class UpdateContent(val content: String) : ComposeIntent()
    data object Post : ComposeIntent()
    data object Cancel : ComposeIntent()
}
