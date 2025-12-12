package com.example.chirp.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.nostr.ndk.compose.content.ContentRendererSettings
import io.nostr.ndk.compose.content.RendererStyle

/**
 * Settings screen for content renderer customization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentRenderSettingsScreen(
    settings: ContentRendererSettings,
    onSettingsChanged: (ContentRendererSettings) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentSettings by remember { mutableStateOf(settings) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Content Renderer Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Section: Event Mentions
            Text(
                text = "Event Mentions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    RendererStyleOption(
                        title = "Default Preview",
                        description = "Show event content with author",
                        selected = currentSettings.eventMentionStyle == RendererStyle.DEFAULT,
                        onClick = {
                            currentSettings = currentSettings.copy(eventMentionStyle = RendererStyle.DEFAULT)
                            onSettingsChanged(currentSettings)
                        }
                    )

                    RendererStyleOption(
                        title = "Compact",
                        description = "Show only event ID",
                        selected = currentSettings.eventMentionStyle == RendererStyle.COMPACT,
                        onClick = {
                            currentSettings = currentSettings.copy(eventMentionStyle = RendererStyle.COMPACT)
                            onSettingsChanged(currentSettings)
                        }
                    )

                    RendererStyleOption(
                        title = "Card",
                        description = "Full card with metadata",
                        selected = currentSettings.eventMentionStyle == RendererStyle.CARD,
                        onClick = {
                            currentSettings = currentSettings.copy(eventMentionStyle = RendererStyle.CARD)
                            onSettingsChanged(currentSettings)
                        }
                    )
                }
            }

            // Section: Articles (Kind 30023)
            Text(
                text = "Articles (Kind 30023)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    RendererStyleOption(
                        title = "Card with Image",
                        description = "Show title, summary, and image",
                        selected = currentSettings.articleStyle == RendererStyle.CARD,
                        onClick = {
                            currentSettings = currentSettings.copy(articleStyle = RendererStyle.CARD)
                            onSettingsChanged(currentSettings)
                        }
                    )

                    RendererStyleOption(
                        title = "Compact",
                        description = "Title and author only",
                        selected = currentSettings.articleStyle == RendererStyle.COMPACT,
                        onClick = {
                            currentSettings = currentSettings.copy(articleStyle = RendererStyle.COMPACT)
                            onSettingsChanged(currentSettings)
                        }
                    )

                    RendererStyleOption(
                        title = "Minimal",
                        description = "Just the title",
                        selected = currentSettings.articleStyle == RendererStyle.MINIMAL,
                        onClick = {
                            currentSettings = currentSettings.copy(articleStyle = RendererStyle.MINIMAL)
                            onSettingsChanged(currentSettings)
                        }
                    )
                }
            }

            // Section: User Mentions
            Text(
                text = "User Mentions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    RendererStyleOption(
                        title = "Avatar + Name",
                        description = "Show user avatar and display name",
                        selected = currentSettings.mentionStyle == RendererStyle.DEFAULT,
                        onClick = {
                            currentSettings = currentSettings.copy(mentionStyle = RendererStyle.DEFAULT)
                            onSettingsChanged(currentSettings)
                        }
                    )

                    RendererStyleOption(
                        title = "Name Only",
                        description = "Show only display name",
                        selected = currentSettings.mentionStyle == RendererStyle.COMPACT,
                        onClick = {
                            currentSettings = currentSettings.copy(mentionStyle = RendererStyle.COMPACT)
                            onSettingsChanged(currentSettings)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RendererStyleOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}
