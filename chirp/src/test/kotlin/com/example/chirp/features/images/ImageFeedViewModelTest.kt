package com.example.chirp.features.images

import io.nostr.ndk.NDK
import io.nostr.ndk.kinds.NDKImage
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.subscription.NDKSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ImageFeedViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ImageFeedViewModel
    private lateinit var mockNdk: NDK
    private lateinit var mockSubscription: NDKSubscription
    private val eventFlow = MutableSharedFlow<NDKEvent>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockNdk = mock()
        mockSubscription = mock()

        whenever(mockNdk.subscribe(any<io.nostr.ndk.models.NDKFilter>())).thenReturn(mockSubscription)
        whenever(mockSubscription.events).thenReturn(eventFlow)

        viewModel = ImageFeedViewModel(mockNdk)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() = runTest {
        val state = viewModel.state.value
        assertTrue(state.galleries.isEmpty())
        // Loading starts immediately in init block
        assertTrue(state.isLoading)
    }

    @Test
    fun `subscribe loads valid image events`() = runTest {
        // Prepare a valid NDKImage event (kind 20) with imeta tag
        val validEvent = mock<NDKEvent> {
            on { kind } doReturn NDKImage.KIND
            on { id } doReturn "event1"
            on { pubkey } doReturn "pubkey1"
            on { content } doReturn "Caption"
            on { createdAt } doReturn 1000L
            on { tags } doReturn list(
                NDKTag("imeta", listOf("url https://example.com/image.jpg", "dim 1024x768"))
            )
        }

        // Emit the event
        eventFlow.emit(validEvent)

        // Verify state update
        val state = viewModel.state.value
        assertEquals(1, state.galleries.size)
        assertEquals("event1", state.galleries[0].id)
        assertEquals("Caption", state.galleries[0].content)
    }

    @Test
    fun `subscribe ignores invalid events`() = runTest {
         // Prepare an invalid event (wrong kind)
        val invalidKindEvent = mock<NDKEvent> {
            on { kind } doReturn 1 // Not Kind 20
        }

        // Prepare an invalid event (no images)
        val noImageEvent = mock<NDKEvent> {
            on { kind } doReturn NDKImage.KIND
            on { tags } doReturn emptyList()
        }

        eventFlow.emit(invalidKindEvent)
        eventFlow.emit(noImageEvent)

        val state = viewModel.state.value
        assertTrue(state.galleries.isEmpty())
    }

    @Test
    fun `getGalleryById returns correct gallery`() = runTest {
        val event1 = mock<NDKEvent> {
            on { kind } doReturn NDKImage.KIND
            on { id } doReturn "id1"
            on { createdAt } doReturn 1000L
            on { tags } doReturn list(NDKTag("imeta", listOf("url http://a.com")))
        }
        val event2 = mock<NDKEvent> {
            on { kind } doReturn NDKImage.KIND
            on { id } doReturn "id2"
             on { createdAt } doReturn 2000L
            on { tags } doReturn list(NDKTag("imeta", listOf("url http://b.com")))
        }

        eventFlow.emit(event1)
        eventFlow.emit(event2)

        val gallery = viewModel.getGalleryById("id1")
        assertNotNull(gallery)
        assertEquals("id1", gallery?.id)

        val missing = viewModel.getGalleryById("id3")
        assertEquals(null, missing)
    }

    @Test
    fun `galleries are sorted by creation time descending`() = runTest {
         val oldEvent = mock<NDKEvent> {
            on { kind } doReturn NDKImage.KIND
            on { id } doReturn "old"
            on { createdAt } doReturn 1000L
            on { tags } doReturn list(NDKTag("imeta", listOf("url http://a.com")))
        }
        val newEvent = mock<NDKEvent> {
            on { kind } doReturn NDKImage.KIND
            on { id } doReturn "new"
             on { createdAt } doReturn 2000L
            on { tags } doReturn list(NDKTag("imeta", listOf("url http://b.com")))
        }

        // Emit old then new
        eventFlow.emit(oldEvent)
        eventFlow.emit(newEvent)

        val state = viewModel.state.value
        assertEquals(2, state.galleries.size)
        assertEquals("new", state.galleries[0].id)
        assertEquals("old", state.galleries[1].id)
    }

    // Helper to create list of tags
    private fun list(vararg tags: NDKTag): List<NDKTag> = tags.toList()
}
