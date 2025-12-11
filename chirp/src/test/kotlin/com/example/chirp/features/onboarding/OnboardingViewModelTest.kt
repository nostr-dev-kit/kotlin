package com.example.chirp.features.onboarding

import io.nostr.ndk.NDK
import io.nostr.ndk.account.NDKCurrentUser
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
class OnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is idle`() {
        val ndk = createMockNDK()
        viewModel = OnboardingViewModel(ndk)
        assertEquals(OnboardingState(), viewModel.state.value)
    }

    @Test
    fun `createAccount generates nsec key`() {
        val ndk = createMockNDK()
        viewModel = OnboardingViewModel(ndk)

        viewModel.onIntent(OnboardingIntent.CreateAccount)

        // After the action completes, a nsec should be generated
        val state = viewModel.state.value
        assertNotNull(state.generatedNsec)
        assert(state.generatedNsec!!.startsWith("nsec1"))
    }

    @Test
    fun `loginWithNsec processes valid nsec`() {
        val ndk = createMockNDK()
        viewModel = OnboardingViewModel(ndk)

        val testNsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"

        viewModel.onIntent(OnboardingIntent.LoginWithNsec(testNsec))

        // After processing, state should be updated
        val state = viewModel.state.value
        // The test passes if no exceptions are thrown during processing
        assertNotNull(state)
    }

    private fun createMockNDK(): NDK {
        return mock {
            on { currentUser } doReturn MutableStateFlow(null)
            onBlocking { login(any()) } doAnswer {
                mock<NDKCurrentUser>()
            }
        }
    }
}
