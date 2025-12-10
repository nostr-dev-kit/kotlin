package io.nostr.ndk.signer.amber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.Timestamp
import kotlinx.coroutines.launch

/**
 * Example Activity demonstrating how to use AmberSigner.
 *
 * This is a reference implementation showing the complete integration pattern.
 * Copy this code into your app and modify as needed.
 */
class AmberSignerExample : ComponentActivity() {

    private lateinit var amberSigner: AmberSigner

    // Register activity result launcher for Amber
    private val amberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Forward result to AmberSigner
        amberSigner.handleAmberResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if Amber is installed
        if (!AmberSigner.isAmberInstalled(this)) {
            // Show dialog or prompt to install Amber
            promptInstallAmber()
            return
        }

        // Create AmberSigner with desired permissions
        amberSigner = AmberSigner(
            context = this,
            permissions = listOf(
                Permission.SIGN_EVENT,
                Permission.GET_PUBLIC_KEY
            )
        )

        // Set the activity launcher
        amberSigner.setActivityLauncher(amberLauncher)

        // Initialize and use the signer
        initializeAmber()
    }

    private fun initializeAmber() {
        lifecycleScope.launch {
            try {
                // Initialize to fetch public key
                amberSigner.initialize()

                // Now we have the public key
                val pubkey = amberSigner.pubkey
                println("Initialized with public key: $pubkey")

                // Example: Sign an event
                signExampleEvent()

            } catch (e: Exception) {
                println("Error initializing Amber: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun signExampleEvent() {
        // Create an unsigned event
        val unsignedEvent = UnsignedEvent(
            pubkey = amberSigner.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1, // Text note
            tags = listOf(
                NDKTag("t", listOf("nostr")),
                NDKTag("t", listOf("amber"))
            ),
            content = "Hello from NDK with Amber signer!"
        )

        try {
            // Sign the event - this will launch Amber app
            val signedEvent = amberSigner.sign(unsignedEvent)

            println("Successfully signed event!")
            println("Event ID: ${signedEvent.id}")
            println("Signature: ${signedEvent.sig}")

            // Now you can publish the signed event to relays
            // ndk.publish(signedEvent)

        } catch (e: Exception) {
            println("Error signing event: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun promptInstallAmber() {
        // In a real app, show a dialog here
        println("Amber is not installed. Please install it from the Play Store.")

        // Optionally open Play Store directly
        // AmberSigner.openAmberInPlayStore(this)
    }
}
