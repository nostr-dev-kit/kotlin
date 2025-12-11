package com.example.chirp.features.home

import io.nostr.ndk.NDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty notes`() {
        val ndk = createMockNDK()
        viewModel = HomeViewModel(ndk)
        assertEquals(FeedTab.FOLLOWING, viewModel.state.value.selectedTab)
        assertEquals(0, viewModel.state.value.notes.size)
    }

    @Test
    fun `switchTab updates selected tab`() {
        val ndk = createMockNDK()
        viewModel = HomeViewModel(ndk)

        viewModel.onIntent(HomeIntent.SwitchTab(FeedTab.GLOBAL))

        assertEquals(FeedTab.GLOBAL, viewModel.state.value.selectedTab)
    }

    private fun createMockNDK(): NDK {
        return mock {
            on { currentUser } doReturn MutableStateFlow(null)
        }
    }
}
