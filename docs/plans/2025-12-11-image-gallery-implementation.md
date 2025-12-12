# Image Gallery (NIP-68) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Instagram-quality image gallery support to Chirp with NIP-68/NIP-92 compliance and Blossom upload integration.

**Architecture:** Implement kind wrappers in ndk-core using Kotlin delegation pattern, create Blossom upload client with BUD-01 auth, build progressive image loading with blurhash placeholders, and create Instagram-style UI with grid feed and zoomable gallery viewer.

**Tech Stack:** Kotlin, Jetpack Compose, Coil 3.x, Ktor HTTP client, blurhash-kotlin, telephoto library

---

## Phase 1: ndk-core Library Foundation

### Task 1: Add NIP-92 Constants

**Files:**
- Create: `ndk-core/src/main/kotlin/io/nostr/ndk/nips/Nip92.kt`

**Step 1: Write the constants file**

```kotlin
package io.nostr.ndk.nips

/**
 * NIP-92: Media Attachments
 * https://github.com/nostr-protocol/nips/blob/master/92.md
 */
const val KIND_IMAGE = 20

/**
 * NIP-71: Video Events
 * https://github.com/nostr-protocol/nips/blob/master/71.md
 */
const val KIND_VIDEO = 34235

/**
 * Generic reply kind for replying to non-kind-1 events
 */
const val KIND_GENERIC_REPLY = 1111
```

**Step 2: Commit**

```bash
git add ndk-core/src/main/kotlin/io/nostr/ndk/nips/Nip92.kt
git commit -m "feat(ndk-core): add NIP-92 media attachment constants"
```

---

### Task 2: ImetaTag Data Class and Parser

**Files:**
- Create: `ndk-core/src/main/kotlin/io/nostr/ndk/models/ImetaTag.kt`
- Create: `ndk-core/src/test/kotlin/io/nostr/ndk/models/ImetaTagTest.kt`

**Step 1: Write failing test for basic imeta parsing**

```kotlin
package io.nostr.ndk.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ImetaTagTest {

    @Test
    fun `parse imeta tag with all fields`() {
        val tag = NDKTag(
            "imeta",
            listOf("url https://example.com/image.jpg blurhash LKO2?U%2Tw=w]~RBVZRi};RPxuwH dim 1920x1080 m image/jpeg x abc123 size 204800 alt Sunset")
        )

        val result = ImetaTag.parse(tag)

        assertNotNull(result)
        assertEquals("https://example.com/image.jpg", result?.url)
        assertEquals("LKO2?U%2Tw=w]~RBVZRi};RPxuwH", result?.blurhash)
        assertEquals(1920 to 1080, result?.dimensions)
        assertEquals("image/jpeg", result?.mimeType)
        assertEquals("abc123", result?.sha256)
        assertEquals(204800L, result?.size)
        assertEquals("Sunset", result?.alt)
    }

    @Test
    fun `parse imeta tag with only required url`() {
        val tag = NDKTag("imeta", listOf("url https://example.com/image.jpg"))

        val result = ImetaTag.parse(tag)

        assertNotNull(result)
        assertEquals("https://example.com/image.jpg", result?.url)
        assertEquals(null, result?.blurhash)
        assertEquals(null, result?.dimensions)
    }

    @Test
    fun `parse imeta tag with multiple fallback URLs`() {
        val tag = NDKTag(
            "imeta",
            listOf("url https://cdn1.com/img.jpg fallback https://cdn2.com/img.jpg fallback https://cdn3.com/img.jpg")
        )

        val result = ImetaTag.parse(tag)

        assertNotNull(result)
        assertEquals("https://cdn1.com/img.jpg", result?.url)
        assertEquals(2, result?.fallback?.size)
        assertEquals("https://cdn2.com/img.jpg", result?.fallback?.get(0))
        assertEquals("https://cdn3.com/img.jpg", result?.fallback?.get(1))
    }

    @Test
    fun `return null for non-imeta tags`() {
        val tag = NDKTag("e", listOf("event123"))

        val result = ImetaTag.parse(tag)

        assertEquals(null, result)
    }

    @Test
    fun `return null for imeta tag without url`() {
        val tag = NDKTag("imeta", listOf("blurhash LKO2?U%2Tw=w"))

        val result = ImetaTag.parse(tag)

        assertEquals(null, result)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :ndk-core:test --tests "io.nostr.ndk.models.ImetaTagTest" 2>&1 | grep -A5 "FAILED\|error"`
Expected: FAIL with compilation errors (ImetaTag class doesn't exist)

**Step 3: Implement ImetaTag data class**

```kotlin
package io.nostr.ndk.models

/**
 * Parsed NIP-92 media metadata from imeta tags.
 *
 * Format: ["imeta", "url <url> blurhash <hash> dim <w>x<h> m <mime> x <sha256> size <bytes> alt <text>"]
 */
data class ImetaTag(
    val url: String?,
    val blurhash: String? = null,
    val dimensions: Pair<Int, Int>? = null,
    val mimeType: String? = null,
    val sha256: String? = null,
    val size: Long? = null,
    val alt: String? = null,
    val fallback: List<String> = emptyList()
) {
    companion object {
        /**
         * Parse an imeta tag into structured metadata.
         *
         * @param tag NDKTag with name "imeta"
         * @return Parsed ImetaTag or null if invalid
         */
        fun parse(tag: NDKTag): ImetaTag? {
            if (tag.name != "imeta") return null

            val metadata = mutableMapOf<String, String>()
            val fallbacks = mutableListOf<String>()

            // Parse space-separated key-value pairs
            tag.values.forEach { value ->
                val parts = value.split(" ")
                var i = 0
                while (i < parts.size - 1) {
                    val key = parts[i]
                    val v = parts[i + 1]

                    if (key == "fallback") {
                        fallbacks.add(v)
                    } else {
                        metadata[key] = v
                    }
                    i += 2
                }
            }

            // URL is required
            val url = metadata["url"] ?: return null

            return ImetaTag(
                url = url,
                blurhash = metadata["blurhash"],
                dimensions = metadata["dim"]?.let { parseDimensions(it) },
                mimeType = metadata["m"],
                sha256 = metadata["x"],
                size = metadata["size"]?.toLongOrNull(),
                alt = metadata["alt"],
                fallback = fallbacks
            )
        }

        private fun parseDimensions(dim: String): Pair<Int, Int>? {
            val parts = dim.split("x")
            if (parts.size != 2) return null
            val width = parts[0].toIntOrNull() ?: return null
            val height = parts[1].toIntOrNull() ?: return null
            return width to height
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :ndk-core:test --tests "io.nostr.ndk.models.ImetaTagTest" 2>&1 | grep -E "BUILD|tests completed"`
Expected: PASS - "5 tests completed"

**Step 5: Commit**

```bash
git add ndk-core/src/main/kotlin/io/nostr/ndk/models/ImetaTag.kt
git add ndk-core/src/test/kotlin/io/nostr/ndk/models/ImetaTagTest.kt
git commit -m "feat(ndk-core): add ImetaTag parser for NIP-92 metadata"
```

---

### Task 3: NDKImage Kind Wrapper

**Files:**
- Create: `ndk-core/src/main/kotlin/io/nostr/ndk/kinds/NDKImage.kt`
- Create: `ndk-core/src/test/kotlin/io/nostr/ndk/kinds/NDKImageTest.kt`

**Step 1: Write failing test for NDKImage wrapper**

```kotlin
package io.nostr.ndk.kinds

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.nips.KIND_IMAGE
import org.junit.Assert.*
import org.junit.Test

class NDKImageTest {

    @Test
    fun `from creates NDKImage from kind 20 event`() {
        val event = NDKEvent(
            id = "img123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = KIND_IMAGE,
            tags = listOf(
                NDKTag("imeta", listOf("url https://example.com/img1.jpg blurhash LKO2 dim 1920x1080 m image/jpeg x abc123 size 204800")),
                NDKTag("imeta", listOf("url https://example.com/img2.jpg blurhash LPO3 dim 1080x1920 m image/jpeg x def456 size 187392"))
            ),
            content = "Beautiful sunset!",
            sig = "sig123"
        )

        val image = NDKImage.from(event)

        assertNotNull(image)
        assertEquals("img123", image?.id)
        assertEquals("author123", image?.pubkey)
        assertEquals("Beautiful sunset!", image?.caption)
        assertEquals(2, image?.images?.size)
        assertTrue(image?.isValid == true)
    }

    @Test
    fun `from returns null for non-kind-20 events`() {
        val event = NDKEvent(
            id = "note123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = 1,
            tags = emptyList(),
            content = "Just a note",
            sig = "sig123"
        )

        val image = NDKImage.from(event)

        assertNull(image)
    }

    @Test
    fun `coverImage returns first image`() {
        val event = NDKEvent(
            id = "img123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = KIND_IMAGE,
            tags = listOf(
                NDKTag("imeta", listOf("url https://example.com/img1.jpg")),
                NDKTag("imeta", listOf("url https://example.com/img2.jpg"))
            ),
            content = "Test",
            sig = "sig123"
        )

        val image = NDKImage.from(event)!!

        assertEquals("https://example.com/img1.jpg", image.coverImage?.url)
    }

    @Test
    fun `isValid returns false when no valid images`() {
        val event = NDKEvent(
            id = "img123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = KIND_IMAGE,
            tags = emptyList(),
            content = "No images",
            sig = "sig123"
        )

        val image = NDKImage.from(event)!!

        assertFalse(image.isValid)
    }

    @Test
    fun `lazy images property caches result`() {
        val event = NDKEvent(
            id = "img123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = KIND_IMAGE,
            tags = listOf(
                NDKTag("imeta", listOf("url https://example.com/img1.jpg"))
            ),
            content = "Test",
            sig = "sig123"
        )

        val image = NDKImage.from(event)!!

        val first = image.images
        val second = image.images

        assertSame(first, second)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :ndk-core:test --tests "io.nostr.ndk.kinds.NDKImageTest" 2>&1 | grep -A5 "FAILED\|error"`
Expected: FAIL with compilation errors (NDKImage class doesn't exist)

**Step 3: Implement NDKImage class with delegation**

```kotlin
package io.nostr.ndk.kinds

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.ImetaTag
import io.nostr.ndk.nips.KIND_IMAGE

/**
 * Represents an image gallery event (NIP-68/NIP-92).
 *
 * Uses delegation to extend NDKEvent functionality while maintaining
 * type safety and avoiding wrapper overhead.
 *
 * @kind 20
 */
class NDKImage private constructor(
    delegate: NDKEvent
) : NDKEvent by delegate {

    companion object {
        const val KIND = KIND_IMAGE

        /**
         * Create NDKImage from an existing NDKEvent.
         *
         * @param event Event to wrap (must be kind 20)
         * @return NDKImage instance or null if event is not kind 20
         */
        fun from(event: NDKEvent): NDKImage? {
            if (event.kind != KIND) return null
            return NDKImage(event)
        }
    }

    /**
     * Parsed image metadata from imeta tags.
     * Lazy evaluation - only parses once on first access.
     */
    val images: List<ImetaTag> by lazy {
        tags.filter { it.name == "imeta" }
            .mapNotNull { ImetaTag.parse(it) }
    }

    /**
     * Caption text for the gallery.
     */
    val caption: String get() = content

    /**
     * Whether this gallery has at least one valid image.
     */
    val isValid: Boolean get() = images.isNotEmpty()

    /**
     * First image for use as cover/thumbnail.
     */
    val coverImage: ImetaTag? get() = images.firstOrNull()
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :ndk-core:test --tests "io.nostr.ndk.kinds.NDKImageTest" 2>&1 | grep -E "BUILD|tests completed"`
Expected: PASS - "6 tests completed"

**Step 5: Commit**

```bash
git add ndk-core/src/main/kotlin/io/nostr/ndk/kinds/NDKImage.kt
git add ndk-core/src/test/kotlin/io/nostr/ndk/kinds/NDKImageTest.kt
git commit -m "feat(ndk-core): add NDKImage kind wrapper with delegation pattern"
```

---

### Task 4: BlossomClient for File Uploads

**Files:**
- Modify: `ndk-core/build.gradle.kts` (add Ktor dependency)
- Create: `ndk-core/src/main/kotlin/io/nostr/ndk/blossom/BlossomClient.kt`
- Create: `ndk-core/src/main/kotlin/io/nostr/ndk/blossom/BlossomUploadResult.kt`
- Create: `ndk-core/src/test/kotlin/io/nostr/ndk/blossom/BlossomClientTest.kt`

**Step 1: Add Ktor HTTP client dependency**

Modify `ndk-core/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies ...

    // Ktor for HTTP client (Blossom uploads)
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")

    // Testing
    testImplementation("io.ktor:ktor-client-mock:2.3.7")
}
```

**Step 2: Sync Gradle**

Run: `./gradlew :ndk-core:dependencies --configuration implementation 2>&1 | grep ktor`
Expected: Should show ktor dependencies resolved

**Step 3: Write BlossomUploadResult data class**

```kotlin
package io.nostr.ndk.blossom

import kotlinx.serialization.Serializable

/**
 * Result of a successful Blossom upload.
 */
@Serializable
data class BlossomUploadResult(
    val url: String,
    val sha256: String,
    val size: Long,
    val type: String
) {
    val mimeType: String get() = type
}
```

**Step 4: Write failing test for BlossomClient**

```kotlin
package io.nostr.ndk.blossom

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BlossomClientTest {

    @Test
    fun `upload file successfully returns result`() = runTest {
        // Mock HTTP client that returns success
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/upload", request.url.encodedPath)
            assertTrue(request.headers.contains("Authorization"))

            respond(
                content = """{"url":"https://cdn.example.com/abc123.jpg","sha256":"abc123","size":1024,"type":"image/jpeg"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = BlossomClient("https://blossom.example.com", httpClient)
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        // Create temporary test file
        val testFile = File.createTempFile("test", ".jpg")
        testFile.writeBytes(ByteArray(1024) { it.toByte() })

        try {
            val result = client.upload(testFile, signer, "image/jpeg").getOrThrow()

            assertEquals("https://cdn.example.com/abc123.jpg", result.url)
            assertEquals("abc123", result.sha256)
            assertEquals(1024L, result.size)
            assertEquals("image/jpeg", result.mimeType)
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `upload returns failure for non-2xx response`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error":"Upload failed"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = BlossomClient("https://blossom.example.com", httpClient)
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val testFile = File.createTempFile("test", ".jpg")
        testFile.writeBytes(ByteArray(1024))

        try {
            val result = client.upload(testFile, signer, "image/jpeg")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Upload failed") == true)
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `upload includes Authorization header with kind 24242 event`() = runTest {
        var capturedAuthHeader: String? = null

        val mockEngine = MockEngine { request ->
            capturedAuthHeader = request.headers["Authorization"]

            respond(
                content = """{"url":"https://cdn.example.com/abc123.jpg","sha256":"abc123","size":1024,"type":"image/jpeg"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = BlossomClient("https://blossom.example.com", httpClient)
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val testFile = File.createTempFile("test", ".jpg")
        testFile.writeBytes(ByteArray(1024))

        try {
            client.upload(testFile, signer, "image/jpeg").getOrThrow()

            assertNotNull(capturedAuthHeader)
            assertTrue(capturedAuthHeader?.startsWith("Nostr ") == true)
        } finally {
            testFile.delete()
        }
    }
}
```

**Step 5: Run test to verify it fails**

Run: `./gradlew :ndk-core:test --tests "io.nostr.ndk.blossom.BlossomClientTest" 2>&1 | grep -A5 "FAILED\|error"`
Expected: FAIL with compilation errors (BlossomClient class doesn't exist)

**Step 6: Implement BlossomClient**

```kotlin
package io.nostr.ndk.blossom

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Client for uploading files to Blossom servers (BUD-01 protocol).
 *
 * @param serverUrl Base URL of the Blossom server (e.g., "https://blossom.primal.net")
 * @param httpClient Ktor HTTP client instance
 */
class BlossomClient(
    private val serverUrl: String,
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Upload a file to the Blossom server.
     *
     * @param file File to upload
     * @param signer Signer for creating BUD-01 auth event
     * @param mimeType MIME type of the file
     * @return Upload result with URL and metadata
     */
    suspend fun upload(
        file: File,
        signer: NDKSigner,
        mimeType: String
    ): Result<BlossomUploadResult> = withContext(Dispatchers.IO) {
        try {
            // Calculate SHA-256 hash
            val hash = file.inputStream().use { calculateSHA256(it) }

            // Create BUD-01 auth event (kind 24242)
            val authEvent = createUploadAuthEvent(
                hash = hash,
                size = file.length(),
                mimeType = mimeType,
                signer = signer
            )

            // Upload with auth header
            val response: HttpResponse = httpClient.put("$serverUrl/upload") {
                headers {
                    append("Authorization", "Nostr ${authEvent.toJson()}")
                }
                setBody(file.readBytes())
                contentType(ContentType.parse(mimeType))
            }

            if (response.status.isSuccess()) {
                val result = json.decodeFromString<BlossomUploadResult>(response.bodyAsText())
                Result.success(result)
            } else {
                Result.failure(Exception("Upload failed: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createUploadAuthEvent(
        hash: String,
        size: Long,
        mimeType: String,
        signer: NDKSigner
    ): NDKEvent {
        val now = System.currentTimeMillis() / 1000
        val expiration = now + 600 // 10 minutes

        return signer.sign(
            kind = 24242,
            content = "",
            tags = listOf(
                NDKTag("t", listOf("upload")),
                NDKTag("x", listOf(hash)),
                NDKTag("size", listOf(size.toString())),
                NDKTag("m", listOf(mimeType)),
                NDKTag("expiration", listOf(expiration.toString()))
            ),
            createdAt = now
        )
    }

    private fun calculateSHA256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
```

**Step 7: Run tests to verify they pass**

Run: `./gradlew :ndk-core:test --tests "io.nostr.ndk.blossom.BlossomClientTest" 2>&1 | grep -E "BUILD|tests completed"`
Expected: PASS - "3 tests completed"

**Step 8: Commit**

```bash
git add ndk-core/build.gradle.kts
git add ndk-core/src/main/kotlin/io/nostr/ndk/blossom/BlossomClient.kt
git add ndk-core/src/main/kotlin/io/nostr/ndk/blossom/BlossomUploadResult.kt
git add ndk-core/src/test/kotlin/io/nostr/ndk/blossom/BlossomClientTest.kt
git commit -m "feat(ndk-core): add BlossomClient with BUD-01 authentication"
```

---

### Task 5: ImageBuilder

**Files:**
- Create: `ndk-core/src/main/kotlin/io/nostr/ndk/builders/ImageBuilder.kt`
- Create: `ndk-core/src/test/kotlin/io/nostr/ndk/builders/ImageBuilderTest.kt`

**Step 1: Write failing test for ImageBuilder**

```kotlin
package io.nostr.ndk.builders

import io.nostr.ndk.blossom.BlossomUploadResult
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.kinds.NDKImage
import io.nostr.ndk.nips.KIND_IMAGE
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ImageBuilderTest {

    @Test
    fun `build creates kind 20 event with imeta tags`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())
        val uploadResult = BlossomUploadResult(
            url = "https://cdn.example.com/image.jpg",
            sha256 = "abc123",
            size = 204800,
            type = "image/jpeg"
        )

        val image = ImageBuilder()
            .caption("Beautiful sunset!")
            .addImage(
                uploadResult = uploadResult,
                blurhash = "LKO2?U%2Tw=w",
                dimensions = 1920 to 1080,
                alt = "Sunset photo"
            )
            .build(signer)

        assertEquals(KIND_IMAGE, image.kind)
        assertEquals("Beautiful sunset!", image.content)
        assertEquals(1, image.images.size)

        val img = image.images[0]
        assertEquals("https://cdn.example.com/image.jpg", img.url)
        assertEquals("LKO2?U%2Tw=w", img.blurhash)
        assertEquals(1920 to 1080, img.dimensions)
        assertEquals("image/jpeg", img.mimeType)
        assertEquals("abc123", img.sha256)
        assertEquals(204800L, img.size)
        assertEquals("Sunset photo", img.alt)
    }

    @Test
    fun `build with multiple images creates multiple imeta tags`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val image = ImageBuilder()
            .caption("Gallery")
            .addImage(
                BlossomUploadResult("https://cdn.example.com/img1.jpg", "hash1", 100000, "image/jpeg"),
                blurhash = "LBLUE"
            )
            .addImage(
                BlossomUploadResult("https://cdn.example.com/img2.jpg", "hash2", 200000, "image/png"),
                blurhash = "LRED"
            )
            .build(signer)

        assertEquals(2, image.images.size)
        assertEquals("https://cdn.example.com/img1.jpg", image.images[0].url)
        assertEquals("https://cdn.example.com/img2.jpg", image.images[1].url)
    }

    @Test
    fun `build without caption creates empty content`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val image = ImageBuilder()
            .addImage(
                BlossomUploadResult("https://cdn.example.com/img.jpg", "hash", 100000, "image/jpeg")
            )
            .build(signer)

        assertEquals("", image.content)
    }

    @Test
    fun `build requires at least one image`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        try {
            ImageBuilder()
                .caption("No images")
                .build(signer)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("At least one image") == true)
        }
    }

    @Test
    fun `imeta tag format is space-separated key-value pairs`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val image = ImageBuilder()
            .addImage(
                BlossomUploadResult("https://cdn.example.com/img.jpg", "abc123", 204800, "image/jpeg"),
                blurhash = "LBLUE",
                dimensions = 1920 to 1080,
                alt = "Test image"
            )
            .build(signer)

        val imetaTag = image.tags.find { it.name == "imeta" }
        assertNotNull(imetaTag)

        val tagValue = imetaTag!!.values.first()
        assertTrue(tagValue.contains("url https://cdn.example.com/img.jpg"))
        assertTrue(tagValue.contains("blurhash LBLUE"))
        assertTrue(tagValue.contains("dim 1920x1080"))
        assertTrue(tagValue.contains("m image/jpeg"))
        assertTrue(tagValue.contains("x abc123"))
        assertTrue(tagValue.contains("size 204800"))
        assertTrue(tagValue.contains("alt Test image"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :ndk-core:test --tests "io.nostr.ndk.builders.ImageBuilderTest" 2>&1 | grep -A5 "FAILED\|error"`
Expected: FAIL with compilation errors (ImageBuilder class doesn't exist)

**Step 3: Implement ImageBuilder**

```kotlin
package io.nostr.ndk.builders

import io.nostr.ndk.blossom.BlossomUploadResult
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.kinds.NDKImage
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.nips.KIND_IMAGE

/**
 * Builder for creating image gallery events (kind 20).
 *
 * Example usage:
 * ```
 * val imageEvent = ImageBuilder()
 *     .caption("Beautiful sunset!")
 *     .addImage(uploadResult, blurhash = "LKO2...", dimensions = 1920 to 1080)
 *     .build(signer)
 * ```
 */
class ImageBuilder {
    private var content: String = ""
    private val images = mutableListOf<ImageMetadata>()

    private data class ImageMetadata(
        val url: String,
        val blurhash: String?,
        val dimensions: Pair<Int, Int>?,
        val mimeType: String,
        val sha256: String,
        val size: Long,
        val alt: String?
    )

    /**
     * Set the caption text for the gallery.
     */
    fun caption(text: String) = apply { content = text }

    /**
     * Add an image to the gallery.
     *
     * @param uploadResult Result from Blossom upload
     * @param blurhash Blurhash string for placeholder
     * @param dimensions Image dimensions (width x height)
     * @param alt Alt text for accessibility
     */
    fun addImage(
        uploadResult: BlossomUploadResult,
        blurhash: String? = null,
        dimensions: Pair<Int, Int>? = null,
        alt: String? = null
    ) = apply {
        images.add(
            ImageMetadata(
                url = uploadResult.url,
                blurhash = blurhash,
                dimensions = dimensions,
                mimeType = uploadResult.mimeType,
                sha256 = uploadResult.sha256,
                size = uploadResult.size,
                alt = alt
            )
        )
    }

    /**
     * Build and sign the image gallery event.
     */
    suspend fun build(signer: NDKSigner): NDKImage {
        require(images.isNotEmpty()) { "At least one image is required" }

        val tags = images.map { img ->
            val imetaParts = buildList {
                // Required fields
                add("url ${img.url}")

                // Optional fields
                img.blurhash?.let { add("blurhash $it") }
                img.dimensions?.let { (w, h) -> add("dim ${w}x${h}") }
                add("m ${img.mimeType}")
                add("x ${img.sha256}")
                add("size ${img.size}")
                img.alt?.let { add("alt $it") }
            }

            NDKTag("imeta", listOf(imetaParts.joinToString(" ")))
        }

        val signedEvent = signer.sign(
            kind = KIND_IMAGE,
            content = content,
            tags = tags,
            createdAt = System.currentTimeMillis() / 1000
        )

        return NDKImage.from(signedEvent)!!
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :ndk-core:test --tests "io.nostr.ndk.builders.ImageBuilderTest" 2>&1 | grep -E "BUILD|tests completed"`
Expected: PASS - "6 tests completed"

**Step 5: Commit**

```bash
git add ndk-core/src/main/kotlin/io/nostr/ndk/builders/ImageBuilder.kt
git add ndk-core/src/test/kotlin/io/nostr/ndk/builders/ImageBuilderTest.kt
git commit -m "feat(ndk-core): add ImageBuilder following existing builder patterns"
```

---

## Phase 2: Chirp App Dependencies

### Task 6: Add Image Loading Dependencies

**Files:**
- Modify: `chirp/build.gradle.kts`

**Step 1: Add Coil, Blurhash, and Telephoto dependencies**

```kotlin
dependencies {
    // ... existing dependencies ...

    // Coil 3.x for image loading
    implementation("io.coil-kt.coil3:coil:3.0.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")

    // Blurhash for placeholders
    implementation("com.github.woltapp:blurhash-kotlin:1.0.0")

    // Telephoto for zoomable images
    implementation("me.saket.telephoto:zoomable-image-coil:0.7.1")

    // Ktor for Blossom uploads (shared with ndk-core)
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
}
```

**Step 2: Sync Gradle**

Run: `./gradlew :chirp:dependencies --configuration implementation 2>&1 | grep -E "coil|blurhash|telephoto"`
Expected: Should show new dependencies resolved

**Step 3: Commit**

```bash
git add chirp/build.gradle.kts
git commit -m "feat(chirp): add image loading dependencies (Coil, Blurhash, Telephoto)"
```

---

## Phase 3: Chirp App Implementation

### Task 7: ContentType Navigation Model

**Files:**
- Create: `chirp/src/main/kotlin/com/example/chirp/models/ContentType.kt`
- Modify: `chirp/src/main/kotlin/com/example/chirp/features/main/MainViewModel.kt`
- Modify: `chirp/src/main/kotlin/com/example/chirp/features/main/MainState.kt`

**Step 1: Create ContentType sealed class**

```kotlin
package com.example.chirp.models

/**
 * Content types supported in the app.
 * Use sealed class for type-safe navigation.
 */
sealed class ContentType {
    data object TextNotes : ContentType()
    data object Images : ContentType()

    // Future content types:
    // data object Videos : ContentType()
    // data object Articles : ContentType()
    // data object Audio : ContentType()
}
```

**Step 2: Update MainState to include selectedContent**

Modify `chirp/src/main/kotlin/com/example/chirp/features/main/MainState.kt`:

```kotlin
package com.example.chirp.features.main

import com.example.chirp.models.ContentType
import io.nostr.ndk.models.NDKUser

data class MainState(
    val currentUser: NDKUser? = null,
    val isLoading: Boolean = false,
    val selectedContent: ContentType = ContentType.TextNotes
)
```

**Step 3: Add content type switching to MainViewModel**

Modify `chirp/src/main/kotlin/com/example/chirp/features/main/MainViewModel.kt`:

Add this method to MainViewModel:

```kotlin
fun selectContentType(contentType: ContentType) {
    _state.update { it.copy(selectedContent = contentType) }
}
```

**Step 4: Commit**

```bash
git add chirp/src/main/kotlin/com/example/chirp/models/ContentType.kt
git add chirp/src/main/kotlin/com/example/chirp/features/main/MainState.kt
git add chirp/src/main/kotlin/com/example/chirp/features/main/MainViewModel.kt
git commit -m "feat(chirp): add ContentType model for extensible navigation"
```

---

### Task 8: Image Feed State and ViewModel

**Files:**
- Create: `chirp/src/main/kotlin/com/example/chirp/features/images/ImageFeedState.kt`
- Create: `chirp/src/main/kotlin/com/example/chirp/features/images/ImageFeedViewModel.kt`

**Step 1: Create ImageFeedState**

```kotlin
package com.example.chirp.features.images

import io.nostr.ndk.kinds.NDKImage

data class ImageFeedState(
    val galleries: List<NDKImage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

**Step 2: Create ImageFeedViewModel with subscription**

```kotlin
package com.example.chirp.features.images

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.kinds.NDKImage
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.nips.KIND_IMAGE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImageFeedViewModel @Inject constructor(
    private val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(ImageFeedState())
    val state: StateFlow<ImageFeedState> = _state.asStateFlow()

    init {
        subscribeToImageGalleries()
    }

    private fun subscribeToImageGalleries() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val filter = NDKFilter(
                    kinds = setOf(KIND_IMAGE),
                    limit = 100
                )

                val subscription = ndk.subscribe(filter)

                subscription.events.collect { event ->
                    val image = NDKImage.from(event)
                    if (image != null && image.isValid) {
                        _state.update { state ->
                            val updated = (state.galleries + image)
                                .distinctBy { it.id }
                                .sortedByDescending { it.createdAt }
                            state.copy(
                                galleries = updated,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load galleries"
                    )
                }
            }
        }
    }
}
```

**Step 3: Commit**

```bash
git add chirp/src/main/kotlin/com/example/chirp/features/images/ImageFeedState.kt
git add chirp/src/main/kotlin/com/example/chirp/features/images/ImageFeedViewModel.kt
git commit -m "feat(chirp): add ImageFeedViewModel with kind 20 subscription"
```

---

### Task 9: Image Feed UI with Grid

**Files:**
- Create: `chirp/src/main/kotlin/com/example/chirp/features/images/ImageFeedScreen.kt`
- Create: `chirp/src/main/kotlin/com/example/chirp/components/BlurhashImage.kt`

**Step 1: Create BlurhashImage composable for placeholder support**

```kotlin
package com.example.chirp.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wolt.blurhashkt.BlurHashDecoder

@Composable
fun BlurhashImage(
    url: String?,
    blurhash: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cacheKey: String? = null
) {
    val placeholder = remember(blurhash) {
        blurhash?.let { hash ->
            try {
                val bitmap = BlurHashDecoder.decode(hash, 32, 32)
                bitmap?.let { BitmapPainter(it.asImageBitmap()) }
            } catch (e: Exception) {
                null
            }
        }
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .apply {
                cacheKey?.let {
                    memoryCacheKey(it)
                    diskCacheKey(it)
                }
            }
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholder
    )
}
```

**Step 2: Create ImageFeedScreen with 3-column grid**

```kotlin
package com.example.chirp.features.images

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chirp.components.BlurhashImage
import io.nostr.ndk.kinds.NDKImage

@Composable
fun ImageFeedScreen(
    viewModel: ImageFeedViewModel = hiltViewModel(),
    onImageClick: (NDKImage) -> Unit = {},
    onUploadClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onUploadClick) {
                Icon(Icons.Default.Add, "Upload images")
            }
        }
    ) { padding ->
        if (state.isLoading && state.galleries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.error != null && state.galleries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(1.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(state.galleries, key = { it.id }) { gallery ->
                    ImageGalleryCard(
                        gallery = gallery,
                        onClick = { onImageClick(gallery) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageGalleryCard(
    gallery: NDKImage,
    onClick: () -> Unit
) {
    val coverImage = gallery.coverImage

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        BlurhashImage(
            url = coverImage?.url,
            blurhash = coverImage?.blurhash,
            contentDescription = coverImage?.alt,
            cacheKey = coverImage?.sha256,
            modifier = Modifier.fillMaxSize()
        )

        // Multi-image indicator
        if (gallery.images.size > 1) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Text(
                        text = gallery.images.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}
```

**Step 3: Commit**

```bash
git add chirp/src/main/kotlin/com/example/chirp/components/BlurhashImage.kt
git add chirp/src/main/kotlin/com/example/chirp/features/images/ImageFeedScreen.kt
git commit -m "feat(chirp): add image feed screen with 3-column grid and blurhash"
```

---

### Task 10: Gallery Viewer with Zoom

**Files:**
- Create: `chirp/src/main/kotlin/com/example/chirp/features/images/ImageDetailScreen.kt`

**Step 1: Create ImageDetailScreen with zoomable pager**

```kotlin
package com.example.chirp.features.images

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.chirp.components.UserDisplayName
import io.nostr.ndk.NDK
import io.nostr.ndk.kinds.NDKImage
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableState

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ImageDetailScreen(
    gallery: NDKImage,
    ndk: NDK,
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { gallery.images.size })

    LaunchedEffect(pagerState.currentPage) {
        currentPage = pagerState.currentPage
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Horizontal pager for swiping between images
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val image = gallery.images[page]

            // Zoomable image with pinch-to-zoom
            ZoomableAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image.url)
                    .crossfade(true)
                    .apply {
                        image.sha256?.let {
                            memoryCacheKey(it)
                            diskCacheKey(it)
                        }
                    }
                    .build(),
                contentDescription = image.alt,
                modifier = Modifier.fillMaxSize(),
                state = rememberZoomableState()
            )
        }

        // Top bar with close button and page indicator
        TopAppBar(
            title = {
                Text(
                    "${currentPage + 1} / ${gallery.images.size}",
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Page indicators (dots)
        if (gallery.images.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(gallery.images.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentPage) 8.dp else 6.dp)
                            .background(
                                color = if (index == currentPage)
                                    Color.White
                                else
                                    Color.White.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        // Bottom caption overlay
        if (gallery.caption.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Author info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserDisplayName(
                            pubkey = gallery.pubkey,
                            ndk = ndk,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "•",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatTimestamp(gallery.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Caption text
                    Text(
                        text = gallery.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(unixTimestamp: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - unixTimestamp

    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        else -> "${diff / 604800}w ago"
    }
}
```

**Step 2: Commit**

```bash
git add chirp/src/main/kotlin/com/example/chirp/features/images/ImageDetailScreen.kt
git commit -m "feat(chirp): add zoomable gallery viewer with pager and captions"
```

---

### Task 11: Image Upload State and ViewModel

**Files:**
- Create: `chirp/src/main/kotlin/com/example/chirp/features/images/upload/ImageUploadState.kt`
- Create: `chirp/src/main/kotlin/com/example/chirp/features/images/upload/ImageUploadViewModel.kt`
- Create: `chirp/src/main/kotlin/com/example/chirp/di/BlossomModule.kt`

**Step 1: Create Hilt module for BlossomClient**

```kotlin
package com.example.chirp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.nostr.ndk.blossom.BlossomClient
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BlossomModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    @Provides
    @Singleton
    fun provideBlossomClient(httpClient: HttpClient): BlossomClient {
        // TODO: Make this configurable in settings
        return BlossomClient("https://blossom.primal.net", httpClient)
    }
}
```

**Step 2: Create ImageUploadState**

```kotlin
package com.example.chirp.features.images.upload

import java.io.File

data class ImageUploadState(
    val selectedImages: List<ProcessedImage> = emptyList(),
    val caption: String = "",
    val isProcessing: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val uploaded: Boolean = false,
    val error: String? = null
)

data class ProcessedImage(
    val file: File,
    val blurhash: String,
    val dimensions: Pair<Int, Int>,
    val mimeType: String,
    val alt: String? = null
)
```

**Step 3: Create ImageUploadViewModel**

```kotlin
package com.example.chirp.features.images.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolt.blurhashkt.BlurHashEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nostr.ndk.NDK
import io.nostr.ndk.blossom.BlossomClient
import io.nostr.ndk.builders.ImageBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ImageUploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ndk: NDK,
    private val blossomClient: BlossomClient
) : ViewModel() {

    private val _state = MutableStateFlow(ImageUploadState())
    val state: StateFlow<ImageUploadState> = _state.asStateFlow()

    fun onImagesSelected(uris: List<Uri>) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }

            try {
                val processed = uris.map { uri ->
                    async { processImage(uri) }
                }.awaitAll()

                _state.update {
                    it.copy(
                        selectedImages = processed,
                        isProcessing = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Failed to process images: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun processImage(uri: Uri): ProcessedImage = withContext(Dispatchers.IO) {
        // Load bitmap
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IOException("Failed to load image")

        // Resize if too large (max 2048x2048)
        val resized = if (bitmap.width > 2048 || bitmap.height > 2048) {
            val scale = minOf(2048f / bitmap.width, 2048f / bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                bitmap.recycle()
            }
        } else {
            bitmap
        }

        // Generate blurhash
        val blurhash = BlurHashEncoder.encode(resized, componentX = 4, componentY = 3)

        // Create temp file
        val file = File.createTempFile("upload_", ".jpg", context.cacheDir)
        file.outputStream().use { output ->
            resized.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }

        ProcessedImage(
            file = file,
            blurhash = blurhash,
            dimensions = resized.width to resized.height,
            mimeType = "image/jpeg"
        )
    }

    fun onCaptionChanged(caption: String) {
        _state.update { it.copy(caption = caption) }
    }

    fun onPublish() {
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadProgress = 0f, error = null) }

            try {
                val signer = ndk.currentUser.value?.signer
                    ?: throw Exception("No active user")

                val builder = ImageBuilder().caption(state.value.caption)

                // Upload each image with progress tracking
                state.value.selectedImages.forEachIndexed { index, img ->
                    val uploadResult = blossomClient.upload(
                        file = img.file,
                        signer = signer,
                        mimeType = img.mimeType
                    ).getOrThrow()

                    builder.addImage(
                        uploadResult = uploadResult,
                        blurhash = img.blurhash,
                        dimensions = img.dimensions,
                        alt = img.alt
                    )

                    // Update progress
                    val progress = (index + 1).toFloat() / state.value.selectedImages.size
                    _state.update { it.copy(uploadProgress = progress) }
                }

                // Build and publish event
                val imageEvent = builder.build(signer)
                ndk.publish(imageEvent)

                // Clean up temp files
                state.value.selectedImages.forEach { it.file.delete() }

                _state.update {
                    it.copy(
                        isUploading = false,
                        uploaded = true,
                        uploadProgress = 1f
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isUploading = false,
                        error = e.message ?: "Upload failed"
                    )
                }
            }
        }
    }

    fun reset() {
        _state.value = ImageUploadState()
    }
}
```

**Step 4: Commit**

```bash
git add chirp/src/main/kotlin/com/example/chirp/di/BlossomModule.kt
git add chirp/src/main/kotlin/com/example/chirp/features/images/upload/ImageUploadState.kt
git add chirp/src/main/kotlin/com/example/chirp/features/images/upload/ImageUploadViewModel.kt
git commit -m "feat(chirp): add image upload viewmodel with blurhash generation"
```

---

### Task 12: Image Upload UI

**Files:**
- Create: `chirp/src/main/kotlin/com/example/chirp/features/images/upload/ImageUploadScreen.kt`

**Step 1: Create ImageUploadScreen**

```kotlin
package com.example.chirp.features.images.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
```

**Step 2: Commit**

```bash
git add chirp/src/main/kotlin/com/example/chirp/features/images/upload/ImageUploadScreen.kt
git commit -m "feat(chirp): add image upload screen with multi-image picker"
```

---

### Task 13: Integrate Image Screens into Navigation

**Files:**
- Modify: `chirp/src/main/kotlin/com/example/chirp/features/main/MainScreen.kt`
- Modify: `chirp/src/main/kotlin/com/example/chirp/navigation/NavGraph.kt` (if exists)

**Step 1: Add Images tab to MainScreen**

Modify the bottom navigation in MainScreen to include Images tab:

```kotlin
// In MainScreen.kt, add to bottom navigation items:

NavigationBarItem(
    selected = state.selectedContent == ContentType.Images,
    onClick = { viewModel.selectContentType(ContentType.Images) },
    icon = { Icon(Icons.Default.Collections, "Images") },
    label = { Text("Images") }
)
```

**Step 2: Add content switching in MainScreen body**

```kotlin
// In MainScreen content area:

when (state.selectedContent) {
    ContentType.TextNotes -> {
        HomeScreen(
            onNavigateToThread = { eventId ->
                navController.navigate("thread/$eventId")
            },
            onNavigateToProfile = { pubkey ->
                navController.navigate("profile/$pubkey")
            }
        )
    }
    ContentType.Images -> {
        ImageFeedScreen(
            onImageClick = { gallery ->
                navController.navigate("image_detail/${gallery.id}")
            },
            onUploadClick = {
                navController.navigate("image_upload")
            }
        )
    }
}
```

**Step 3: Add routes for image screens**

Add to NavHost:

```kotlin
composable("image_detail/{galleryId}") { backStackEntry ->
    val galleryId = backStackEntry.arguments?.getString("galleryId")
    // Load gallery from viewmodel and pass to ImageDetailScreen
    // Placeholder - needs proper gallery loading:
    ImageDetailScreen(
        gallery = gallery, // Need to fetch this
        ndk = ndk,
        onDismiss = { navController.popBackStack() }
    )
}

composable("image_upload") {
    ImageUploadScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

**Step 4: Commit**

```bash
git add chirp/src/main/kotlin/com/example/chirp/features/main/MainScreen.kt
git add chirp/src/main/kotlin/com/example/chirp/navigation/NavGraph.kt
git commit -m "feat(chirp): integrate image screens into navigation"
```

---

## Phase 4: Testing and Polish

### Task 14: Manual Testing Checklist

**Manual Test Plan:**

1. **Feed Loading**
   - Launch app → Navigate to Images tab
   - Verify grid loads with 3 columns
   - Verify blurhash placeholders appear before images
   - Verify multi-image indicators show on galleries with >1 image

2. **Gallery Viewer**
   - Tap on gallery card
   - Verify full-screen viewer opens
   - Verify can swipe between images
   - Verify can pinch-to-zoom
   - Verify page indicators update
   - Verify caption shows at bottom with author info

3. **Image Upload**
   - Tap FAB → Select Images
   - Choose 1-3 images from gallery
   - Verify blurhash generation completes
   - Verify can add caption
   - Tap Upload
   - Verify progress indicator
   - Verify redirects to feed after upload
   - Verify uploaded gallery appears in feed

4. **Error Handling**
   - Try uploading without network
   - Verify error message shows
   - Verify can retry after fixing network

**Step 1: Perform manual tests**

Run through all test scenarios and note any issues.

**Step 2: Fix any bugs found**

Address issues discovered during testing.

**Step 3: Commit fixes**

```bash
git add <files>
git commit -m "fix: [description of bug fix]"
```

---

### Task 15: Update Design Document with Implementation Notes

**Files:**
- Modify: `docs/plans/2025-12-11-image-gallery-design.md`

**Step 1: Add "Implementation Notes" section**

Add at end of design document:

```markdown
## Implementation Notes

**Completed:** 2025-12-11

### Library Components (ndk-core)
- ✅ NDKImage kind wrapper with delegation pattern
- ✅ ImetaTag parser for NIP-92 metadata
- ✅ BlossomClient with BUD-01 authentication
- ✅ ImageBuilder following existing patterns

### App Components (Chirp)
- ✅ ImageFeedScreen with 3-column grid
- ✅ ImageDetailScreen with zoomable pager
- ✅ ImageUploadScreen with blurhash generation
- ✅ Navigation integration with ContentType model

### Dependencies Added
- Coil 3.0.0 for image loading
- blurhash-kotlin 1.0.0 for placeholders
- telephoto 0.7.1 for zoomable images
- Ktor 2.3.7 for HTTP client

### Known Limitations
- Blossom server URL hardcoded (needs settings integration)
- Gallery detail route needs proper data passing
- No offline support yet
- No image editing features

### Future Enhancements
See "Future Enhancements" section in original design.
```

**Step 2: Commit**

```bash
git add docs/plans/2025-12-11-image-gallery-design.md
git commit -m "docs: add implementation notes to design document"
```

---

## Final Steps

### Task 16: Create Pull Request

**Step 1: Push branch to remote**

```bash
git push -u origin feature/image-gallery
```

**Step 2: Create PR with description**

Use GitHub CLI or web interface:

```bash
gh pr create --title "feat: Add NIP-68 image gallery support" --body "$(cat <<EOF
## Summary
Adds Instagram-quality image gallery support to Chirp with NIP-68/NIP-92 compliance.

## Changes

### ndk-core Library
- NDKImage kind wrapper using delegation pattern
- ImetaTag parser for NIP-92 media metadata
- BlossomClient for file uploads with BUD-01 auth
- ImageBuilder following existing builder patterns

### Chirp App
- ImageFeedScreen with 3-column grid layout
- ImageDetailScreen with zoomable pager and captions
- ImageUploadScreen with blurhash generation
- ContentType navigation model for extensibility

## Testing
- Unit tests for ImetaTag parsing
- Unit tests for NDKImage wrapper
- Unit tests for ImageBuilder
- Mock tests for BlossomClient
- Manual UI testing completed

## Design Document
See docs/plans/2025-12-11-image-gallery-design.md

## Screenshots
[Add screenshots here]
EOF
)"
```

**Step 3: Request review**

Tag appropriate reviewers for the PR.

---

## Execution Summary

**Total Tasks:** 16
**Estimated Time:** 6-8 hours
**Dependencies:** Coil, Blurhash, Telephoto, Ktor

**Key Principles Applied:**
- ✅ TDD (test first, then implement)
- ✅ DRY (reused existing patterns)
- ✅ YAGNI (no premature abstractions)
- ✅ Frequent commits (after each task)
- ✅ Bite-sized steps (2-5 min each)

**Ready for Execution**
Use superpowers:executing-plans or superpowers:subagent-driven-development to implement.
