package com.example.chirp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nostr.ndk.compose.content.ContentRendererSettings
import io.nostr.ndk.compose.content.RendererStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * Repository for persisting app settings using DataStore.
 */
@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val EVENT_MENTION_STYLE_KEY = stringPreferencesKey("event_mention_style")
    private val ARTICLE_STYLE_KEY = stringPreferencesKey("article_style")
    private val MENTION_STYLE_KEY = stringPreferencesKey("mention_style")
    private val LINK_STYLE_KEY = stringPreferencesKey("link_style")
    private val MEDIA_STYLE_KEY = stringPreferencesKey("media_style")
    private val SHOW_AVATARS_IN_MENTIONS_KEY = booleanPreferencesKey("show_avatars_in_mentions")
    private val ENABLE_LINK_PREVIEWS_KEY = booleanPreferencesKey("enable_link_previews")

    /**
     * Flow of content renderer settings.
     */
    val contentRendererSettings: Flow<ContentRendererSettings> = context.appSettingsDataStore.data.map { prefs ->
        ContentRendererSettings(
            eventMentionStyle = prefs[EVENT_MENTION_STYLE_KEY]?.toRendererStyle() ?: RendererStyle.DEFAULT,
            articleStyle = prefs[ARTICLE_STYLE_KEY]?.toRendererStyle() ?: RendererStyle.CARD,
            mentionStyle = prefs[MENTION_STYLE_KEY]?.toRendererStyle() ?: RendererStyle.DEFAULT,
            linkStyle = prefs[LINK_STYLE_KEY]?.toRendererStyle() ?: RendererStyle.DEFAULT,
            mediaStyle = prefs[MEDIA_STYLE_KEY]?.toRendererStyle() ?: RendererStyle.DEFAULT,
            showAvatarsInMentions = prefs[SHOW_AVATARS_IN_MENTIONS_KEY] ?: true,
            enableLinkPreviews = prefs[ENABLE_LINK_PREVIEWS_KEY] ?: true
        )
    }

    /**
     * Updates content renderer settings.
     */
    suspend fun updateContentRendererSettings(settings: ContentRendererSettings) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[EVENT_MENTION_STYLE_KEY] = settings.eventMentionStyle.name
            prefs[ARTICLE_STYLE_KEY] = settings.articleStyle.name
            prefs[MENTION_STYLE_KEY] = settings.mentionStyle.name
            prefs[LINK_STYLE_KEY] = settings.linkStyle.name
            prefs[MEDIA_STYLE_KEY] = settings.mediaStyle.name
            prefs[SHOW_AVATARS_IN_MENTIONS_KEY] = settings.showAvatarsInMentions
            prefs[ENABLE_LINK_PREVIEWS_KEY] = settings.enableLinkPreviews
        }
    }

    private fun String.toRendererStyle(): RendererStyle {
        return try {
            RendererStyle.valueOf(this)
        } catch (e: IllegalArgumentException) {
            RendererStyle.DEFAULT
        }
    }
}
