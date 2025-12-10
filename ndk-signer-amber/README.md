# NDK Signer Amber

NIP-55 Android Signer Application (Amber) integration for NDK-Android.

## Overview

This module provides integration with [Amber](https://github.com/greenart7c3/Amber), an Android key management app that implements NIP-55. Amber securely stores Nostr private keys and signs events without exposing the key to requesting applications.

## Features

- ✅ Event signing via Android Intents (NIP-55)
- ✅ Public key retrieval
- ✅ Permission-based access control
- ✅ Support for NIP-04 and NIP-44 encryption/decryption
- ✅ Automatic Amber app detection
- ✅ Activity result launcher pattern for modern Android

## Installation

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":ndk-signer-amber"))
}
```

## Usage

### 1. Set up Activity Result Launcher

In your Activity or Fragment:

```kotlin
import androidx.activity.result.contract.ActivityResultContracts
import io.nostr.ndk.signer.amber.AmberSigner

class MainActivity : ComponentActivity() {
    private lateinit var amberSigner: AmberSigner

    private val amberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        amberSigner.handleAmberResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create AmberSigner
        amberSigner = AmberSigner(this)
        amberSigner.setActivityLauncher(amberLauncher)
    }
}
```

### 2. Check if Amber is installed

```kotlin
if (!AmberSigner.isAmberInstalled(context)) {
    // Prompt user to install Amber
    AmberSigner.openAmberInPlayStore(context)
    return
}
```

### 3. Initialize the signer

```kotlin
lifecycleScope.launch {
    try {
        amberSigner.initialize()
        println("Public key: ${amberSigner.pubkey}")
    } catch (e: Exception) {
        // Handle error (Amber not installed, user denied, etc.)
    }
}
```

### 4. Sign events

```kotlin
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKTag

lifecycleScope.launch {
    val unsignedEvent = UnsignedEvent(
        pubkey = amberSigner.pubkey,
        createdAt = System.currentTimeMillis() / 1000,
        kind = 1,
        tags = listOf(NDKTag("t", listOf("nostr"))),
        content = "Hello from NDK with Amber!"
    )

    try {
        val signedEvent = amberSigner.sign(unsignedEvent)
        println("Signed event ID: ${signedEvent.id}")
        println("Signature: ${signedEvent.sig}")
    } catch (e: Exception) {
        // Handle signing error
    }
}
```

## Permissions

You can specify which permissions to request from Amber:

```kotlin
import io.nostr.ndk.signer.amber.Permission

val amberSigner = AmberSigner(
    context = this,
    permissions = listOf(
        Permission.SIGN_EVENT,
        Permission.GET_PUBLIC_KEY,
        Permission.NIP04_ENCRYPT,
        Permission.NIP04_DECRYPT,
        Permission.NIP44_ENCRYPT,
        Permission.NIP44_DECRYPT
    )
)
```

Available permissions:
- `SIGN_EVENT` - Sign Nostr events
- `GET_PUBLIC_KEY` - Get the public key
- `NIP04_ENCRYPT` - Encrypt using NIP-04
- `NIP04_DECRYPT` - Decrypt using NIP-04
- `NIP44_ENCRYPT` - Encrypt using NIP-44
- `NIP44_DECRYPT` - Decrypt using NIP-44
- `DECRYPT_ZAP_EVENT` - Decrypt zap events (NIP-57)

## Error Handling

The signer will throw exceptions in the following cases:

- `IllegalStateException` if Amber is not installed
- `IllegalStateException` if `ActivityResultLauncher` is not set
- `IllegalStateException` if user cancels the signing request
- `IllegalStateException` if Amber returns an invalid response

Always wrap signer calls in try-catch blocks:

```kotlin
try {
    val signedEvent = amberSigner.sign(unsignedEvent)
    // Use signed event
} catch (e: IllegalStateException) {
    when {
        e.message?.contains("not installed") == true -> {
            // Prompt user to install Amber
            AmberSigner.openAmberInPlayStore(context)
        }
        e.message?.contains("cancelled") == true -> {
            // User cancelled - show message
        }
        else -> {
            // Other error
        }
    }
}
```

## Architecture

The AmberSigner uses Android's modern Activity Result API for communication with Amber:

1. **Intent Creation**: Creates a `nostrsigner:` URI intent with event data
2. **Activity Launch**: Launches Amber app using `ActivityResultLauncher`
3. **User Interaction**: User approves/denies signing in Amber
4. **Result Handling**: Receives signed event via `handleAmberResult()`
5. **Completion**: Returns signed `NDKEvent` to caller

The implementation uses coroutines with `suspendCancellableCoroutine` to provide a clean async API while handling the activity result callback pattern.

## NIP-55 Specification

This implementation follows [NIP-55](https://github.com/nostr-protocol/nips/blob/master/55.md) - Android Signer Application:

- Intent action: `android.intent.action.VIEW`
- URI scheme: `nostrsigner:`
- Package name: `com.greenart7c3.nostrsigner`
- Result format: JSON event with `id`, `sig` fields

## Example: Full Integration

```kotlin
class NostrActivity : ComponentActivity() {
    private lateinit var amberSigner: AmberSigner
    private lateinit var ndk: NDK

    private val amberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        amberSigner.handleAmberResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if Amber is installed
        if (!AmberSigner.isAmberInstalled(this)) {
            showInstallAmberDialog()
            return
        }

        // Initialize AmberSigner
        amberSigner = AmberSigner(this)
        amberSigner.setActivityLauncher(amberLauncher)

        lifecycleScope.launch {
            try {
                // Initialize and get public key
                amberSigner.initialize()

                // Create NDK instance with AmberSigner
                ndk = NDK(
                    relays = listOf("wss://relay.damus.io"),
                    signer = amberSigner
                )

                // Publish an event
                publishNote("Hello Nostr!")
            } catch (e: Exception) {
                showError("Failed to initialize: ${e.message}")
            }
        }
    }

    private suspend fun publishNote(content: String) {
        val unsignedEvent = UnsignedEvent(
            pubkey = amberSigner.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = emptyList(),
            content = content
        )

        try {
            val signedEvent = amberSigner.sign(unsignedEvent)
            ndk.publish(signedEvent)
            showSuccess("Note published!")
        } catch (e: Exception) {
            showError("Failed to publish: ${e.message}")
        }
    }

    private fun showInstallAmberDialog() {
        AlertDialog.Builder(this)
            .setTitle("Amber Required")
            .setMessage("This app requires Amber for secure key management.")
            .setPositiveButton("Install") { _, _ ->
                AmberSigner.openAmberInPlayStore(this)
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .show()
    }
}
```

## Security Considerations

- Private keys never leave the Amber app
- Each signing request requires user approval in Amber
- Permissions can be granted on a per-app basis
- Users can revoke permissions at any time in Amber settings

## Requirements

- Android API level 26 (Android 8.0) or higher
- Amber app installed on the device
- Activity or Fragment context for launching intents

## Links

- [Amber GitHub](https://github.com/greenart7c3/Amber)
- [NIP-55 Specification](https://github.com/nostr-protocol/nips/blob/master/55.md)
- [NDK Android](https://github.com/planetary-social/ndk-android)
