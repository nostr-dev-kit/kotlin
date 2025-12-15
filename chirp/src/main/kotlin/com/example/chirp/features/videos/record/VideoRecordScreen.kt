package com.example.chirp.features.videos.record

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import coil3.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VideoRecordScreen(
    viewModel: VideoRecordViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    LaunchedEffect(state.uploaded) {
        if (state.uploaded) {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            !permissionsState.allPermissionsGranted -> {
                PermissionRequest(
                    onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() },
                    onBack = onNavigateBack
                )
            }
            state.recordedVideo != null && state.recordingState == RecordingState.Stopped -> {
                ReviewScreen(
                    state = state,
                    onCaptionChanged = viewModel::onCaptionChanged,
                    onPublish = viewModel::onPublish,
                    onRetry = viewModel::onRetry,
                    onBack = onNavigateBack
                )
            }
            else -> {
                CameraScreen(
                    state = state,
                    onRecordingStarted = viewModel::onRecordingStarted,
                    onRecordingComplete = viewModel::onRecordingComplete,
                    onRecordingStopped = viewModel::onRecordingStopped,
                    onBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun PermissionRequest(
    onRequestPermissions: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera & Microphone Access Required",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "To record videos, please grant camera and microphone permissions.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermissions) {
            Text("Grant Permissions")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text("Go Back", color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun CameraScreen(
    state: VideoRecordState,
    onRecordingStarted: () -> Unit,
    onRecordingComplete: (Uri) -> Unit,
    onRecordingStopped: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var cameraKey by remember { mutableStateOf(0) }
    val isRecording = state.recordingState is RecordingState.Recording

    // Auto-stop at 6 seconds
    LaunchedEffect(state.recordingState) {
        if (state.recordingState is RecordingState.Recording &&
            state.recordingState.elapsedSeconds >= MAX_VIDEO_DURATION_SECONDS) {
            recording?.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview - keyed to force rebuild on camera switch
        key(cameraKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        setupCamera(ctx, lifecycleOwner, previewView, useFrontCamera) { capture ->
                            videoCapture = capture
                        }
                    }
                }
            )
        }

        // Top bar - respects safe area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    recording?.stop()
                    onBack()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Timer display
            Surface(
                color = if (isRecording) Color.Red else Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            ) {
                val seconds = (state.recordingState as? RecordingState.Recording)?.elapsedSeconds ?: 0
                Text(
                    text = "${seconds}s / ${MAX_VIDEO_DURATION_SECONDS}s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Camera switch button
            IconButton(
                onClick = {
                    if (!isRecording) {
                        useFrontCamera = !useFrontCamera
                        cameraKey++
                    }
                },
                enabled = !isRecording
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = if (isRecording) Color.White.copy(alpha = 0.3f) else Color.White
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress ring around record button
            val progress by animateFloatAsState(
                targetValue = if (state.recordingState is RecordingState.Recording) {
                    state.recordingState.elapsedSeconds.toFloat() / MAX_VIDEO_DURATION_SECONDS
                } else 0f,
                label = "progress"
            )

            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Progress circle
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Red,
                    strokeWidth = 4.dp,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )

                // Record button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color.Red else Color.White)
                        .border(3.dp, Color.White, CircleShape)
                        .clickable {
                            if (isRecording) {
                                recording?.stop()
                            } else {
                                videoCapture?.let { capture ->
                                    recording = startRecording(
                                        context = context,
                                        videoCapture = capture,
                                        onStarted = onRecordingStarted,
                                        onComplete = { uri ->
                                            recording = null
                                            onRecordingComplete(uri)
                                        },
                                        onStopped = onRecordingStopped
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.White, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isRecording) "Tap to stop" else "Hold to record (max 6s)",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ReviewScreen(
    state: VideoRecordState,
    onCaptionChanged: (String) -> Unit,
    onPublish: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Review Video",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            if (!state.isUploading) {
                IconButton(onClick = onPublish) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Publish",
                        tint = Color.Green
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Video preview (thumbnail)
        state.recordedVideo?.thumbnailFile?.let { thumbnail ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Duration badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${state.recordedVideo.duration}s",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${state.recordedVideo?.duration ?: 0}s video ready",
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Caption input
        OutlinedTextField(
            value = state.caption,
            onValueChange = onCaptionChanged,
            label = { Text("Caption (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isUploading,
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.White.copy(alpha = 0.7f),
                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
            )
        )

        // Upload progress
        if (state.isUploading) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { state.uploadProgress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Uploading... ${(state.uploadProgress * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Error
        state.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                enabled = !state.isUploading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retake")
            }

            Button(
                onClick = onPublish,
                modifier = Modifier.weight(1f),
                enabled = !state.isUploading
            ) {
                Text("Publish")
            }
        }
    }
}

private fun setupCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    useFrontCamera: Boolean,
    onVideoCaptureReady: (VideoCapture<Recorder>) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()

        val videoCapture = VideoCapture.withOutput(recorder)

        val cameraSelector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )
            onVideoCaptureReady(videoCapture)
        } catch (e: Exception) {
            Log.e("VideoRecord", "Camera setup failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun startRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    onStarted: () -> Unit,
    onComplete: (Uri) -> Unit,
    onStopped: () -> Unit
): Recording {
    val videoFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.mp4")
    val outputOptions = FileOutputOptions.Builder(videoFile).build()

    return videoCapture.output
        .prepareRecording(context, outputOptions)
        .withAudioEnabled()
        .start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onStarted()
                is VideoRecordEvent.Finalize -> {
                    if (event.hasError()) {
                        Log.e("VideoRecord", "Recording error: ${event.error}")
                        onStopped()
                    } else {
                        onComplete(Uri.fromFile(videoFile))
                    }
                }
            }
        }
}
