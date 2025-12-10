package io.nostr.ndk.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.nostr.ndk.NDK
import io.nostr.ndk.cache.InMemoryCacheAdapter
import io.nostr.ndk.cache.nostrdb.NostrDBCacheAdapter
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.utils.Nip19
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TestAppHub(lifecycleScope = lifecycleScope)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestAppHub(lifecycleScope: kotlinx.coroutines.CoroutineScope) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Feed", "Profile", "Publish", "NIP-19", "Filters", "NostrDB")

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Tab row
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        // Content
        when (selectedTab) {
            0 -> FeedTestScreen(lifecycleScope)
            1 -> ProfileTestScreen(lifecycleScope)
            2 -> PublishTestScreen(lifecycleScope)
            3 -> Nip19TestScreen()
            4 -> MultiFilterTestScreen(lifecycleScope)
            5 -> NostrDBTestScreen(lifecycleScope)
        }
    }
}

// ============== TEST 1: FEED (Kind 1 events with Cache) ==============
@Composable
fun FeedTestScreen(lifecycleScope: kotlinx.coroutines.CoroutineScope) {
    // Create a shared cache that persists events in memory
    val cache = remember { InMemoryCacheAdapter() }

    val ndk = remember {
        NDK(
            explicitRelayUrls = setOf(
                "wss://relay.damus.io",
                "wss://relay.nostr.band",
                "wss://nos.lol"
            ),
            cacheAdapter = cache // Enable caching!
        )
    }

    var events by remember { mutableStateOf<List<NDKEvent>>(emptyList()) }
    var isConnected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Not connected") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Feed Test (Kind 1)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(status, color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)

        Button(
            onClick = {
                status = "Connecting..."
                lifecycleScope.launch {
                    try {
                        ndk.connect()
                        isConnected = true
                        status = "Connected - Subscribing to kind 1"

                        val subscription = ndk.subscribe(NDKFilter(kinds = setOf(1), limit = 50))
                        subscription.events.collectLatest { event ->
                            events = (listOf(event) + events).take(100)
                        }
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    }
                }
            },
            enabled = !isConnected,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Connect & Subscribe")
        }

        Text("Events: ${events.size}", modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(events) { event ->
                EventCard(event)
            }
        }
    }
}

// ============== TEST 2: PROFILE (Kind 0 metadata) ==============
@Composable
fun ProfileTestScreen(lifecycleScope: kotlinx.coroutines.CoroutineScope) {
    val ndk = remember {
        NDK(explicitRelayUrls = setOf("wss://relay.damus.io", "wss://purplepag.es"))
    }

    var profiles by remember { mutableStateOf<List<NDKEvent>>(emptyList()) }
    var status by remember { mutableStateOf("Ready") }
    var searchPubkey by remember { mutableStateOf("") }

    // Jack Dorsey's pubkey for testing
    val testPubkeys = listOf(
        "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2", // jack
        "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d", // fiatjaf
        "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"  // pablo
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Profile Test (Kind 0)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(status, color = MaterialTheme.colorScheme.secondary)

        OutlinedTextField(
            value = searchPubkey,
            onValueChange = { searchPubkey = it },
            label = { Text("Pubkey or npub") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                status = "Fetching profiles..."
                profiles = emptyList()
                lifecycleScope.launch {
                    try {
                        ndk.connect()

                        // Decode npub if provided
                        val pubkeys = if (searchPubkey.isNotBlank()) {
                            val pk = if (searchPubkey.startsWith("npub")) {
                                val decoded = Nip19.decode(searchPubkey)
                                (decoded as? Nip19.Decoded.Npub)?.pubkey ?: searchPubkey
                            } else searchPubkey
                            setOf(pk)
                        } else {
                            testPubkeys.map {
                                if (it.startsWith("npub")) {
                                    (Nip19.decode(it) as? Nip19.Decoded.Npub)?.pubkey ?: it
                                } else it
                            }.toSet()
                        }

                        val filter = NDKFilter(kinds = setOf(0), authors = pubkeys, limit = 10)
                        val subscription = ndk.subscribe(filter)

                        status = "Connected - Fetching ${pubkeys.size} profile(s)"

                        subscription.events.collectLatest { event ->
                            profiles = (profiles + event).distinctBy { it.pubkey }
                            status = "Found ${profiles.size} profile(s)"
                        }
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    }
                }
            }) {
                Text("Fetch Profiles")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(profiles) { profile ->
                ProfileCard(profile)
            }
        }
    }
}

@Composable
fun ProfileCard(event: NDKEvent) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Pubkey: ${event.pubkey.take(16)}...", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary)

            // Parse JSON content for profile metadata
            val content = event.content
            Text("Raw metadata:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                text = content.take(300) + if (content.length > 300) "..." else "",
                fontSize = 12.sp
            )
        }
    }
}

// ============== TEST 3: PUBLISH (Sign & send events) ==============
@Composable
fun PublishTestScreen(lifecycleScope: kotlinx.coroutines.CoroutineScope) {
    // Generate a test keypair
    val keyPair = remember { NDKKeyPair.generate() }
    val signer = remember { NDKPrivateKeySigner(keyPair) }

    val ndk = remember {
        NDK(
            explicitRelayUrls = setOf("wss://relay.damus.io"),
            signer = signer
        )
    }

    var content by remember { mutableStateOf("Hello from NDK-Android! #nostr #test") }
    var status by remember { mutableStateOf("Ready") }
    var signedEvent by remember { mutableStateOf<NDKEvent?>(null) }
    var npub by remember { mutableStateOf("") }
    var nsec by remember { mutableStateOf("") }

    LaunchedEffect(keyPair) {
        npub = Nip19.encodeNpub(keyPair.pubkeyHex)
        nsec = Nip19.encodeNsec(keyPair.privateKeyHex!!)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Publish Test (Signing)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(status, color = MaterialTheme.colorScheme.secondary)

        Spacer(modifier = Modifier.height(8.dp))

        // Show generated keys
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Generated Test Keys:", fontWeight = FontWeight.Bold)
                Text("npub: ${npub.take(30)}...", fontSize = 10.sp)
                Text("nsec: ${nsec.take(30)}...", fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Event content") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Button(onClick = {
                lifecycleScope.launch {
                    status = "Creating unsigned event..."
                    try {
                        val unsigned = UnsignedEvent(
                            pubkey = keyPair.pubkeyHex,
                            createdAt = System.currentTimeMillis() / 1000,
                            kind = 1,
                            tags = listOf(
                                NDKTag.hashtag("nostr"),
                                NDKTag.hashtag("test"),
                                NDKTag.hashtag("ndk-android")
                            ),
                            content = content
                        )

                        status = "Signing event..."
                        val signed = signer.sign(unsigned)
                        signedEvent = signed

                        status = "Event signed! ID: ${signed.id.take(16)}..."
                    } catch (e: Exception) {
                        status = "Sign error: ${e.message}"
                    }
                }
            }) {
                Text("Sign Event")
            }

            Button(
                onClick = {
                    lifecycleScope.launch {
                        status = "Connecting to relay..."
                        try {
                            ndk.connect()
                            status = "Publishing event..."

                            val event = signedEvent
                            if (event != null) {
                                // Publish would go here - for now just validate
                                status = "Event ready to publish! (Publishing disabled for test)"
                            } else {
                                status = "Sign an event first!"
                            }
                        } catch (e: Exception) {
                            status = "Publish error: ${e.message}"
                        }
                    }
                },
                enabled = signedEvent != null
            ) {
                Text("Publish")
            }
        }

        // Show signed event details
        signedEvent?.let { event ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Signed Event:", fontWeight = FontWeight.Bold)
                    Text("ID: ${event.id}", fontSize = 10.sp)
                    Text("Pubkey: ${event.pubkey}", fontSize = 10.sp)
                    Text("Created: ${event.createdAt}", fontSize = 10.sp)
                    Text("Kind: ${event.kind}", fontSize = 10.sp)
                    Text("Sig: ${event.sig?.take(32)}...", fontSize = 10.sp)
                    Text("Content: ${event.content}", fontSize = 12.sp)
                    Text("ID Valid: ${event.isIdValid()}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============== TEST 4: NIP-19 Encoding/Decoding ==============
@Composable
fun Nip19TestScreen() {
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Enter a value to encode/decode") }
    var encodeType by remember { mutableStateOf("npub") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("NIP-19 Test", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Hex or Bech32 value") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                result = try {
                    val decoded = Nip19.decode(input)
                    when (decoded) {
                        is Nip19.Decoded.Npub -> "npub → Pubkey: ${decoded.pubkey}"
                        is Nip19.Decoded.Nsec -> "nsec → Private key: ${decoded.privateKey.take(16)}..."
                        is Nip19.Decoded.Note -> "note → Event ID: ${decoded.eventId}"
                        is Nip19.Decoded.Nevent -> "nevent → Event: ${decoded.eventId}, Relays: ${decoded.relays}"
                        is Nip19.Decoded.Nprofile -> "nprofile → Pubkey: ${decoded.pubkey}, Relays: ${decoded.relays}"
                        is Nip19.Decoded.Naddr -> "naddr → Kind: ${decoded.kind}, Pubkey: ${decoded.pubkey.take(16)}..."
                        else -> "Unknown type"
                    }
                } catch (e: Exception) {
                    "Decode error: ${e.message}"
                }
            }) {
                Text("Decode")
            }

            Button(onClick = {
                result = try {
                    when {
                        input.length == 64 -> {
                            val npub = Nip19.encodeNpub(input)
                            val note = Nip19.encodeNote(input)
                            "Hex → npub: $npub\nHex → note: $note"
                        }
                        else -> "Enter 64-char hex to encode"
                    }
                } catch (e: Exception) {
                    "Encode error: ${e.message}"
                }
            }) {
                Text("Encode")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Test values
        Text("Test Values:", fontWeight = FontWeight.Bold)
        val testValues = listOf(
            "npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m" to "fiatjaf npub",
            "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2" to "jack hex pubkey"
        )

        testValues.forEach { (value, desc) ->
            TextButton(onClick = { input = value }) {
                Text("$desc: ${value.take(20)}...", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = result,
                modifier = Modifier.padding(16.dp),
                fontSize = 12.sp
            )
        }

        // Generate random keypair test
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            val kp = NDKKeyPair.generate()
            val npub = Nip19.encodeNpub(kp.pubkeyHex)
            val nsec = Nip19.encodeNsec(kp.privateKeyHex!!)
            result = "Generated keypair:\nnpub: $npub\nnsec: $nsec"
        }) {
            Text("Generate Keypair")
        }
    }
}

// ============== TEST 5: Multiple Filters ==============
@Composable
fun MultiFilterTestScreen(lifecycleScope: kotlinx.coroutines.CoroutineScope) {
    val ndk = remember {
        NDK(explicitRelayUrls = setOf("wss://relay.damus.io", "wss://nos.lol"))
    }

    var events by remember { mutableStateOf<List<NDKEvent>>(emptyList()) }
    var status by remember { mutableStateOf("Ready") }
    var kindStats by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Multi-Filter Test", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(status, color = MaterialTheme.colorScheme.secondary)

        Button(
            onClick = {
                status = "Connecting..."
                events = emptyList()
                kindStats = emptyMap()

                lifecycleScope.launch {
                    try {
                        ndk.connect()
                        status = "Subscribing to kinds 0, 1, 3, 7..."

                        // Subscribe to multiple event kinds
                        val filters = listOf(
                            NDKFilter(kinds = setOf(0), limit = 10),  // Metadata
                            NDKFilter(kinds = setOf(1), limit = 20),  // Text notes
                            NDKFilter(kinds = setOf(3), limit = 5),   // Follows
                            NDKFilter(kinds = setOf(7), limit = 10)   // Reactions
                        )

                        val subscription = ndk.subscribe(filters)

                        subscription.events.collectLatest { event ->
                            events = (listOf(event) + events).take(100)
                            kindStats = events.groupBy { it.kind }.mapValues { it.value.size }
                            status = "Events: ${events.size} | Kinds: ${kindStats.entries.joinToString { "${it.key}:${it.value}" }}"
                        }
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    }
                }
            },
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Subscribe to Multiple Kinds")
        }

        // Kind breakdown
        if (kindStats.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Event Kind Breakdown:", fontWeight = FontWeight.Bold)
                    kindStats.entries.sortedBy { it.key }.forEach { (kind, count) ->
                        val kindName = when (kind) {
                            0 -> "Metadata"
                            1 -> "Text Note"
                            3 -> "Follows"
                            7 -> "Reaction"
                            else -> "Kind $kind"
                        }
                        Text("$kindName (kind $kind): $count events", fontSize = 12.sp)
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(events) { event ->
                MultiKindEventCard(event)
            }
        }
    }
}

@Composable
fun MultiKindEventCard(event: NDKEvent) {
    val kindName = when (event.kind) {
        0 -> "Metadata"
        1 -> "Text Note"
        3 -> "Follows"
        7 -> "Reaction"
        else -> "Kind ${event.kind}"
    }

    val kindColor = when (event.kind) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer
        3 -> MaterialTheme.colorScheme.tertiaryContainer
        7 -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = kindColor)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("[$kindName]", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("From: ${event.pubkey.take(12)}...", fontSize = 10.sp)
            Text(
                event.content.take(100) + if (event.content.length > 100) "..." else "",
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun EventCard(event: NDKEvent) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("From: ${event.pubkey.take(16)}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(event.content.take(200) + if (event.content.length > 200) "..." else "", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("ID: ${event.id.take(12)}...", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

// ============== TEST 6: NostrDB Cache ==============
@Composable
fun NostrDBTestScreen(lifecycleScope: kotlinx.coroutines.CoroutineScope) {
    val context = LocalContext.current

    // Create NostrDB cache adapter
    val cache = remember {
        NostrDBCacheAdapter.create(context)
    }

    val ndk = remember {
        NDK(
            explicitRelayUrls = setOf(
                "wss://relay.damus.io",
                "wss://relay.nostr.band",
                "wss://nos.lol"
            ),
            cacheAdapter = cache
        )
    }

    var events by remember { mutableStateOf<List<NDKEvent>>(emptyList()) }
    var cachedCount by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Not connected") }
    var isConnected by remember { mutableStateOf(false) }
    var dbStatus by remember { mutableStateOf("NostrDB initializing...") }

    // Check DB status
    LaunchedEffect(Unit) {
        dbStatus = "NostrDB ready at: ${context.filesDir}/nostrdb-test"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("NostrDB Cache Test", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // DB Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Database Status", fontWeight = FontWeight.Bold)
                Text(dbStatus, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Connection status
        Text("Status: $status", fontSize = 14.sp)
        Text("Events received: ${events.size}", fontSize = 14.sp)
        Text("Events in cache: $cachedCount", fontSize = 14.sp)

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    lifecycleScope.launch {
                        status = "Connecting..."
                        try {
                            ndk.connect()
                            isConnected = true
                            status = "Connected! Subscribing to kind 1..."

                            val filter = NDKFilter(
                                kinds = setOf(1),
                                limit = 30
                            )

                            val subscription = ndk.subscribe(filter)
                            status = "Subscribed! Waiting for events..."

                            subscription.events.collectLatest { event ->
                                events = (listOf(event) + events).take(50)
                                cachedCount = events.size // Events auto-cached by NostrDB
                            }
                        } catch (e: Exception) {
                            status = "Error: ${e.message}"
                        }
                    }
                },
                enabled = !isConnected
            ) {
                Text("Connect & Subscribe")
            }

            Button(
                onClick = {
                    lifecycleScope.launch {
                        // Query cached events using Flow
                        val filter = NDKFilter(kinds = setOf(1), limit = 10)
                        var count = 0
                        cache.query(filter).collect { count++ }
                        status = "Queried cache: $count events found"
                    }
                },
                enabled = isConnected
            ) {
                Text("Query Cache")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Event list
        LazyColumn {
            items(events) { event ->
                NostrDBEventCard(event)
            }
        }
    }
}

@Composable
fun NostrDBEventCard(event: NDKEvent) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("From: ${event.pubkey.take(12)}...", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                Text("Tags: ${event.tags.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                event.content.take(150) + if (event.content.length > 150) "..." else "",
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Sig: ${event.sig?.take(16) ?: "null"}...",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
