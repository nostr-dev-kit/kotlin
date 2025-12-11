package com.example.chirp.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.subscription.NDKSubscription
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var currentSubscription: NDKSubscription? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }

        if (query.isBlank()) {
            cancelSearch()
            _state.update { it.copy(results = emptyList()) }
            return
        }

        search(query)
    }

    private fun search(query: String) {
        cancelSearch()

        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null, results = emptyList()) }

            try {
                // Search for notes containing the query
                val filter = NDKFilter(
                    kinds = setOf(1),
                    search = query,
                    limit = 50
                )

                currentSubscription = ndk.subscribe(filter)

                currentSubscription?.events?.collect { event ->
                    _state.update { currentState ->
                        val updatedResults = (currentState.results + event)
                            .distinctBy { it.id }
                            .sortedByDescending { it.createdAt }
                        currentState.copy(results = updatedResults, isSearching = false)
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSearching = false,
                        error = e.message ?: "Search failed"
                    )
                }
            }
        }
    }

    private fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        currentSubscription = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelSearch()
    }
}
