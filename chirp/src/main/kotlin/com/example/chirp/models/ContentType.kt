package com.example.chirp.models

/**
 * Content types supported in the app.
 * Use sealed class for type-safe navigation.
 */
sealed class ContentType {
    data object TextNotes : ContentType()
    data object Images : ContentType()

    // Future content types:
    // data object Videos : ContentType()
    // data object Articles : ContentType()
    // data object Audio : ContentType()
}
