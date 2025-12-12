package com.example.chirp.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chirp.ui.theme.AvatarSizes
import com.example.chirp.ui.theme.CornerRadius
import com.example.chirp.ui.theme.Spacing
import io.nostr.ndk.compose.content.ContentCallbacks
import io.nostr.ndk.compose.content.RenderedContent
import io.nostr.ndk.compose.user.UserAvatar
import io.nostr.ndk.compose.user.UserDisplayName
import io.nostr.ndk.models.NDKEvent
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToThread: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(ProfileTab.NOTES) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        val user = state.user
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.error != null -> {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.lg)
                    )
                }

                user != null -> {
                    val profile by user.profile.collectAsState()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Profile header
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
                            ) {
                                // Avatar, name, username, stats
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    UserAvatar(
                                        pubkey = user.pubkey,
                                        ndk = viewModel.ndk,
                                        size = AvatarSizes.lg
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        UserDisplayName(
                                            pubkey = user.pubkey,
                                            ndk = viewModel.ndk,
                                            style = MaterialTheme.typography.titleLarge
                                                .copy(fontWeight = FontWeight.Bold)
                                        )

                                        Text(
                                            text = "@${profile?.name ?: user.pubkey.take(8)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Stats row
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                                        ) {
                                            ProfileStat(label = "Posts", count = state.notes.size)
                                            ProfileStat(label = "Following", count = 0)
                                            ProfileStat(label = "Followers", count = 0)
                                        }
                                    }
                                }

                                // Bio
                                profile?.about?.let { about ->
                                    Spacer(modifier = Modifier.height(Spacing.md))
                                    Text(
                                        text = about,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 20.sp
                                    )
                                }

                                // Link pills
                                profile?.website?.let { website ->
                                    Spacer(modifier = Modifier.height(Spacing.md))
                                    LinkPill(url = website)
                                }

                                // Action buttons
                                Spacer(modifier = Modifier.height(Spacing.lg))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("Follow")
                                    }
                                    OutlinedButton(
                                        onClick = { },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Edit Profile")
                                    }
                                }
                            }
                        }

                        // Featured Articles Section
                        if (state.featuredArticles.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(vertical = Spacing.lg)
                                ) {
                                    Text(
                                        text = "Featured Articles",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm)
                                    )

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = Spacing.xl),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                    ) {
                                        items(
                                            items = state.featuredArticles,
                                            key = { it.id }
                                        ) { article ->
                                            ArticleCard(
                                                article = article,
                                                ndk = viewModel.ndk,
                                                onClick = { /* TODO: Navigate to article */ }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tabs
                        item {
                            ProfileTabs(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                state = state
                            )
                        }

                        // Content list based on selected tab
                        val contentItems = when (selectedTab) {
                            ProfileTab.NOTES -> state.notes
                            ProfileTab.ARTICLES -> state.articles
                            ProfileTab.IMAGES -> state.images
                            ProfileTab.VIDEOS -> state.videos
                        }

                        items(
                            items = contentItems,
                            key = { it.id }
                        ) { event ->
                            CompactNoteItem(
                                note = event,
                                ndk = viewModel.ndk,
                                onNoteClick = { onNavigateToThread(event.id) },
                                onProfileClick = { }
                            )
                        }

                    }
                }

                else -> {
                    Text(
                        text = "Profile not found",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.lg)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(label: String, count: Int) {
    Column {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LinkPill(url: String) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.xl),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.clip(RoundedCornerShape(CornerRadius.xl))
    ) {
        Text(
            text = url.removePrefix("https://").removePrefix("http://"),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
        )
    }
}

enum class ProfileTab(val label: String) {
    NOTES("Notes"),
    ARTICLES("Articles"),
    IMAGES("Images"),
    VIDEOS("Videos")
}

@Composable
private fun ProfileTabs(
    selectedTab: ProfileTab,
    onTabSelected: (ProfileTab) -> Unit,
    state: ProfileState
) {
    val availableTabs = buildList {
        add(ProfileTab.NOTES)
        if (state.hasArticles) add(ProfileTab.ARTICLES)
        if (state.hasImages) add(ProfileTab.IMAGES)
        if (state.hasVideos) add(ProfileTab.VIDEOS)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        availableTabs.forEach { tab ->
            val isSelected = selectedTab == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tab.label,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onBackground
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(3.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(CornerRadius.sm)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactNoteItem(
    note: NDKEvent,
    ndk: io.nostr.ndk.NDK,
    onNoteClick: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNoteClick),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.xl,
                vertical = 14.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            UserAvatar(
                pubkey = note.pubkey,
                ndk = ndk,
                size = AvatarSizes.sm
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserDisplayName(
                        pubkey = note.pubkey,
                        ndk = ndk,
                        style = MaterialTheme.typography.bodyMedium
                            .copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.clickable { onProfileClick(note.pubkey) }
                    )

                    Text(
                        text = formatTimestamp(note.createdAt),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RenderedContent(
                    ndk = ndk,
                    event = note,
                    callbacks = ContentCallbacks(
                        onUserClick = onProfileClick,
                        onHashtagClick = { },
                        onLinkClick = { },
                        onMediaClick = { _, _ -> }
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    IconButton(
                        onClick = { },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = Spacing.xl + AvatarSizes.sm + Spacing.md)
    )
}

@Composable
private fun ArticleCard(
    article: NDKEvent,
    ndk: io.nostr.ndk.NDK,
    onClick: () -> Unit
) {
    val title = article.tagValue("title") ?: "Untitled Article"
    val summary = article.tagValue("summary") ?: ""
    val image = article.tagValue("image")

    Surface(
        modifier = Modifier
            .width(280.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CornerRadius.lg),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Cover image or placeholder
            if (image != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clip(RoundedCornerShape(topStart = CornerRadius.lg, topEnd = CornerRadius.lg))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clip(RoundedCornerShape(topStart = CornerRadius.lg, topEnd = CornerRadius.lg)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.take(1).uppercase(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Article info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2
                )

                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                Text(
                    text = formatTimestamp(article.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestamp

    return when {
        diff < 60 -> "${diff}s"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        else -> {
            val date = Date(timestamp * 1000)
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }
}
