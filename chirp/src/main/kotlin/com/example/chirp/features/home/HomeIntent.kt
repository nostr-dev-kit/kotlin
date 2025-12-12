package com.example.chirp.features.home

sealed interface HomeIntent {
    data object LoadFeed : HomeIntent
    data object RefreshFeed : HomeIntent
}
