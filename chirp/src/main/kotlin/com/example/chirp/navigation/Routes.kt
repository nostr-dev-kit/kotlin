package com.example.chirp.navigation

sealed class Routes(val route: String) {
    data object Onboarding : Routes("onboarding")
    data object Home : Routes("home")
    data object Compose : Routes("compose?replyTo={replyTo}") {
        fun createRoute(replyTo: String? = null): String =
            if (replyTo != null) "compose?replyTo=$replyTo" else "compose"
    }
    data object Thread : Routes("thread/{eventId}") {
        fun createRoute(eventId: String): String = "thread/$eventId"
    }
    data object Profile : Routes("profile/{pubkey}") {
        fun createRoute(pubkey: String): String = "profile/$pubkey"
    }
    data object Search : Routes("search?hashtag={hashtag}") {
        fun createRoute(hashtag: String? = null): String =
            if (hashtag != null) "search?hashtag=$hashtag" else "search"
    }
    data object Settings : Routes("settings")
    data object Debug : Routes("debug")
    data object ContentRendererSettings : Routes("settings/content_renderer")
    data object RelaySettings : Routes("settings/relays")
    data object ImageDetail : Routes("image_detail/{galleryId}") {
        fun createRoute(galleryId: String): String = "image_detail/$galleryId"
    }
    data object ImageUpload : Routes("image_upload")
}
