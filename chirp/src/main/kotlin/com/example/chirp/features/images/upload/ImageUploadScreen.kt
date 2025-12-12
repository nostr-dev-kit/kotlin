package com.example.chirp.features.images.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageUploadScreen(
    viewModel: ImageUploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    // Image picker launcher
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onImagesSelected(uris)
        }
    }

    LaunchedEffect(state.uploaded) {
        if (state.uploaded) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Images") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (state.selectedImages.isNotEmpty() && !state.isUploading) {
                        Button(
                            onClick = { viewModel.onPublish() },
                            enabled = !state.isUploading
                        ) {
                            Text("Upload")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (state.selectedImages.isEmpty() && !state.isProcessing) {
                // Image selection prompt
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { imagePicker.launch("image/*") }) {
                        Text("Select Images")
                    }
                }
            } else {
                // Selected images preview
                if (state.selectedImages.isNotEmpty()) {
                    Text(
                        text = "${state.selectedImages.size} image(s) selected",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.selectedImages) { img ->
                            Box(
                                modifier = Modifier.size(120.dp)
                            ) {
                                AsyncImage(
                                    model = img.file,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Add more images button
                    TextButton(onClick = { imagePicker.launch("image/*") }) {
                        Text("Add More Images")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Caption input
                OutlinedTextField(
                    value = state.caption,
                    onValueChange = { viewModel.onCaptionChanged(it) },
                    label = { Text("Caption (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isUploading,
                    minLines = 3,
                    maxLines = 5
                )

                // Processing/uploading state
                if (state.isProcessing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Processing images...")
                    }
                }

                if (state.isUploading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column {
                        LinearProgressIndicator(
                            progress = { state.uploadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Uploading... ${(state.uploadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Error message
                if (state.error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
