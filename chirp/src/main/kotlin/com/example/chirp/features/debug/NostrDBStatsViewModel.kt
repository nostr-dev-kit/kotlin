package com.example.chirp.features.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.cache.nostrdb.NdbStats
import io.nostr.ndk.cache.nostrdb.NostrDBCacheAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NostrDBStatsState(
    val stats: NdbStats? = null,
    val databaseSize: Long = 0,
    val databasePath: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class NostrDBStatsViewModel @Inject constructor(
    private val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(NostrDBStatsState())
    val state: StateFlow<NostrDBStatsState> = _state.asStateFlow()

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                refreshStats()
                delay(3000) // Refresh every 3 seconds
            }
        }
    }

    private fun refreshStats() {
        val cache = ndk.cacheAdapter
        if (cache !is NostrDBCacheAdapter) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = "NostrDB cache not available. Using different cache backend."
                )
            }
            return
        }

        val stats = cache.getStats()
        val databaseSize = cache.getDatabaseSize()
        val databasePath = cache.getDatabasePath()

        _state.update {
            it.copy(
                stats = stats,
                databaseSize = databaseSize,
                databasePath = databasePath,
                isLoading = false,
                error = if (stats == null) "Failed to load statistics" else null
            )
        }
    }
}
