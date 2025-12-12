package com.example.chirp.features.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

@Composable
fun OnboardingScreen(
    onLoginSuccess: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // NIP-55 launcher
    val nip55Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onIntent(OnboardingIntent.HandleNip55Result(result.resultCode, result.data))
    }

    // Launch NIP-55 intent when available
    LaunchedEffect(state.nip55Intent) {
        state.nip55Intent?.let { intent ->
            nip55Launcher.launch(intent)
        }
    }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Chirp",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "A Twitter-like Nostr client",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { viewModel.onIntent(OnboardingIntent.CreateAccount) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        ) {
            Text("Create New Account")
        }

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(24.dp))

        // NIP-55 Login (Amber)
        if (state.isNip55Available) {
            Button(
                onClick = { viewModel.onIntent(OnboardingIntent.LoginWithNip55) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Text("Login with Amber")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // NIP-46 Login (Bunker)
        var bunkerInput by remember { mutableStateOf("") }

        OutlinedTextField(
            value = bunkerInput,
            onValueChange = { bunkerInput = it },
            label = { Text("Bunker URL") },
            placeholder = { Text("bunker://...") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.onIntent(OnboardingIntent.LoginWithBunker(bunkerInput)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading && !state.isWaitingForNostrConnect && bunkerInput.isNotBlank()
        ) {
            Text("Login with Bunker")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // NostrConnect (client-initiated NIP-46)
        val context = LocalContext.current

        val nostrConnectUrl = state.nostrConnectUrl
        if (state.isWaitingForNostrConnect && nostrConnectUrl != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scan with your signer app:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // QR Code
                    val qrPainter = rememberQrCodePainter(data = nostrConnectUrl)
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = qrPainter,
                            contentDescription = "QR Code for nostrconnect",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.onIntent(OnboardingIntent.CancelNostrConnect) }
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(nostrConnectUrl))
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Open Signer")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = "Waiting for connection...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            OutlinedButton(
                onClick = { viewModel.onIntent(OnboardingIntent.StartNostrConnect) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && !state.isWaitingForNostrConnect
            ) {
                Text("Connect with Signer App")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(24.dp))

        // nsec Login
        var nsecInput by remember { mutableStateOf("") }

        OutlinedTextField(
            value = nsecInput,
            onValueChange = { nsecInput = it },
            label = { Text("nsec key") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.onIntent(OnboardingIntent.LoginWithNsec(nsecInput)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading && nsecInput.isNotBlank()
        ) {
            Text("Login with nsec")
        }

        if (state.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        state.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        state.generatedNsec?.let { nsec ->
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Save your private key!",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = nsec,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
