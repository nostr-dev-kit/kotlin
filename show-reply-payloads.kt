import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.nostr.ndk.builders.reply
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val mapper = jacksonObjectMapper()
    val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

    // Test 1: Reply to root note
    println("\n=== REPLY TO ROOT NOTE ===")
    val originalNote = NDKEvent(
        id = "original123",
        pubkey = "authorpubkey123",
        createdAt = 1234567890,
        kind = 1,
        tags = emptyList(),
        content = "This is the original note",
        sig = "sig123"
    )

    val reply = originalNote.reply()
        .content("This is my reply!")
        .build(signer)

    println("Original note ID: ${originalNote.id}")
    println("\nReply event JSON:")
    println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
        mapOf(
            "id" to reply.id,
            "pubkey" to reply.pubkey,
            "created_at" to reply.createdAt,
            "kind" to reply.kind,
            "tags" to reply.tags.map { mapOf("name" to it.name, "values" to it.values) },
            "content" to reply.content,
            "sig" to reply.sig
        )
    ))

    // Test 2: Reply in thread
    println("\n\n=== REPLY IN THREAD ===")
    val firstReply = NDKEvent(
        id = "reply1",
        pubkey = "replier1",
        createdAt = 1234567900,
        kind = 1,
        tags = listOf(
            NDKTag("e", listOf("root123", "", "root")),
            NDKTag("p", listOf("rootauthor"))
        ),
        content = "First reply",
        sig = "sig2"
    )

    val nestedReply = firstReply.reply()
        .content("Reply to the reply!")
        .build(signer)

    println("Nested reply event JSON:")
    println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
        mapOf(
            "id" to nestedReply.id,
            "pubkey" to nestedReply.pubkey,
            "created_at" to nestedReply.createdAt,
            "kind" to nestedReply.kind,
            "tags" to nestedReply.tags.map { mapOf("name" to it.name, "values" to it.values) },
            "content" to nestedReply.content,
            "sig" to nestedReply.sig
        )
    ))

    // Test 3: Reply to article (NIP-22)
    println("\n\n=== REPLY TO ARTICLE (NIP-22) ===")
    val article = NDKEvent(
        id = "article123",
        pubkey = "author",
        createdAt = 1234567890,
        kind = 30023,
        tags = listOf(
            NDKTag("d", listOf("my-article-identifier")),
            NDKTag("title", listOf("My Article"))
        ),
        content = "Article content...",
        sig = "sig1"
    )

    val articleReply = article.reply()
        .content("Great article!")
        .build(signer)

    println("Article kind: ${article.kind}")
    println("\nReply event JSON:")
    println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
        mapOf(
            "id" to articleReply.id,
            "pubkey" to articleReply.pubkey,
            "created_at" to articleReply.createdAt,
            "kind" to articleReply.kind,
            "tags" to articleReply.tags.map { mapOf("name" to it.name, "values" to it.values) },
            "content" to articleReply.content,
            "sig" to articleReply.sig
        )
    ))
}
