package io.nostr.ndk.compose.relay

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.nostr.ndk.relay.NDKRelayState

/**
 * Displays connection status indicator with color and optional label.
 *
 * Shows an animated pulsing effect for connecting/reconnecting states.
 *
 * Must be used within [RelayRoot].
 *
 * @param modifier Modifier for the indicator
 * @param showLabel Whether to show text label (default: false)
 * @param size Size of the status indicator dot (default: 12.dp)
 */
@Composable
fun RelayConnectionStatus(
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    size: Dp = 12.dp
) {
    val context = LocalRelayContext.current
        ?: error("RelayConnectionStatus must be used within RelayRoot")

    val state by context.relay.state.collectAsState()

    val (color, label, shouldPulse) = when (state) {
        NDKRelayState.CONNECTED, NDKRelayState.AUTHENTICATED ->
            Triple(Color(0xFF4CAF50), "Connected", false) // Green

        NDKRelayState.CONNECTING, NDKRelayState.AUTHENTICATING ->
            Triple(Color(0xFFFFEB3B), "Connecting", true) // Yellow

        NDKRelayState.RECONNECTING ->
            Triple(Color(0xFFFF9800), "Reconnecting", true) // Orange

        NDKRelayState.DISCONNECTED ->
            Triple(Color(0xFFF44336), "Disconnected", false) // Red

        NDKRelayState.AUTH_REQUIRED ->
            Triple(Color(0xFFFF9800), "Auth Required", false) // Orange

        NDKRelayState.FLAPPING ->
            Triple(Color(0xFFF44336), "Connection Issues", true) // Red pulsing
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(size)
        ) {
            // Base indicator
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, CircleShape)
            )

            // Pulsing indicator for connecting states
            if (shouldPulse) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color.copy(alpha = alpha), CircleShape)
                )
            }
        }

        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
