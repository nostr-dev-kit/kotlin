package io.nostr.ndk.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Initialize NDK with relays
    private val ndk = NDK(
        explicitRelayUrls = setOf(
            "wss://relay.damus.io",
            "wss://relay.nostr.band",
            "wss://nos.lol"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NostrFeedScreen(ndk = ndk, lifecycleScope = lifecycleScope)
                }
            }
        }
    }
}

@Composable
fun NostrFeedScreen(
    ndk: NDK,
    lifecycleScope: kotlinx.coroutines.CoroutineScope
) {
    var events by remember { mutableStateOf<List<NDKEvent>>(emptyList()) }
    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "NDK Android Sample",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Status
        Text(
            text = when {
                isConnecting -> "Connecting to relays..."
                isConnected -> "Connected - Subscribed to kind 1 events"
                else -> "Not connected"
            },
            color = when {
                isConnecting -> MaterialTheme.colorScheme.tertiary
                isConnected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Error message
        errorMessage?.let { error ->
            Text(
                text = "Error: $error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Connect button
        Button(
            onClick = {
                isConnecting = true
                errorMessage = null

                lifecycleScope.launch {
                    try {
                        // Connect to relays
                        ndk.connect()
                        isConnected = true
                        isConnecting = false

                        // Subscribe to kind 1 (text notes) events
                        val filter = NDKFilter(
                            kinds = setOf(1),
                            limit = 50
                        )

                        val subscription = ndk.subscribe(filter)

                        // Collect events as they arrive
                        subscription.events.collectLatest { event ->
                            events = (listOf(event) + events).take(100)
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message
                        isConnecting = false
                    }
                }
            },
            enabled = !isConnecting && !isConnected,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(if (isConnecting) "Connecting..." else "Connect & Subscribe")
        }

        // Events count
        Text(
            text = "Events received: ${events.size}",
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Events list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(events) { event ->
                EventCard(event = event)
            }
        }
    }
}

@Composable
fun EventCard(event: NDKEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Pubkey (truncated)
            Text(
                text = "From: ${event.pubkey.take(16)}...",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Content
            Text(
                text = event.content.take(200) + if (event.content.length > 200) "..." else "",
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Event ID (truncated)
            Text(
                text = "ID: ${event.id.take(12)}...",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
