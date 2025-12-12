package com.example.chirp.features.images.upload

import android.net.Uri
import io.nostr.ndk.NDK
import io.nostr.ndk.blossom.BlossomClient
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.account.NDKCurrentUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImageUploadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ImageUploadViewModel
    private lateinit var mockNdk: NDK
    private lateinit var mockBlossomClient: BlossomClient
    private lateinit var mockImageLoader: ImageLoader
    private lateinit var mockUser: NDKCurrentUser
    private lateinit var mockSigner: NDKSigner

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockNdk = mock()
        mockBlossomClient = mock()
        mockImageLoader = mock()
        mockUser = mock()
        mockSigner = mock()

        // Setup User with Signer
        whenever(mockUser.signer).thenReturn(mockSigner)
        whenever(mockNdk.currentUser).thenReturn(MutableStateFlow(mockUser))

        viewModel = ImageUploadViewModel(mockNdk, mockBlossomClient, mockImageLoader)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is clean`() {
        val state = viewModel.state.value
        assertTrue(state.selectedImages.isEmpty())
        assertFalse(state.isProcessing)
        assertFalse(state.isUploading)
        assertFalse(state.uploaded)
        assertEquals("", state.caption)
    }

    @Test
    fun `onCaptionChanged updates state`() {
        viewModel.onCaptionChanged("My new caption")
        assertEquals("My new caption", viewModel.state.value.caption)
    }

    @Test
    fun `onImagesSelected success`() = runTest(testDispatcher) {
        val uri = mock<Uri>()
        val processedImage = ProcessedImage(
            file = File("temp"),
            blurhash = "hash",
            dimensions = 100 to 100,
            mimeType = "image/jpeg"
        )

        whenever(mockImageLoader.loadAndResizeImage(uri)).thenReturn(processedImage)

        viewModel.onImagesSelected(listOf(uri))

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isProcessing)
        assertEquals(1, state.selectedImages.size)
        assertEquals(processedImage, state.selectedImages[0])
    }

    @Test
    fun `onImagesSelected handles failure`() = runTest(testDispatcher) {
        val uri = mock<Uri>()
        whenever(mockImageLoader.loadAndResizeImage(uri)).thenThrow(RuntimeException("Open failed"))

        viewModel.onImagesSelected(listOf(uri))

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse("Should not be processing", state.isProcessing)
        assertTrue("Error should be set", state.error?.contains("Failed to process images") == true)
    }
}
