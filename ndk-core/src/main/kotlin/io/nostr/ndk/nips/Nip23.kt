package io.nostr.ndk.nips

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.Timestamp

/**
 * NIP-23: Long-form Content (kind 30023)
 *
 * Long-form content events are parameterized replaceable events for
 * publishing articles, blog posts, and other long-form text.
 *
 * Required tags:
 * - d: unique identifier for the article (used for replaceable dedup)
 *
 * Optional tags:
 * - title: article title
 * - summary: short summary/description
 * - image: cover image URL
 * - published_at: original publication timestamp
 * - t: hashtags/topics
 */

/**
 * Kind constant for long-form content.
 */
const val KIND_LONG_FORM = 30023

/**
 * Returns true if this event is a long-form article (kind 30023).
 */
val NDKEvent.isLongFormContent: Boolean
    get() = kind == KIND_LONG_FORM

/**
 * Gets the article identifier (d tag).
 */
val NDKEvent.articleId: String?
    get() = tagValue("d")

/**
 * Gets the article title from the title tag.
 */
val NDKEvent.articleTitle: String?
    get() = tagValue("title")

/**
 * Gets the article summary from the summary tag.
 */
val NDKEvent.articleSummary: String?
    get() = tagValue("summary")

/**
 * Gets the article cover image URL from the image tag.
 */
val NDKEvent.articleImage: String?
    get() = tagValue("image")

/**
 * Gets the original publication timestamp from the published_at tag.
 * Falls back to createdAt if not present.
 */
val NDKEvent.articlePublishedAt: Timestamp
    get() = tagValue("published_at")?.toLongOrNull() ?: createdAt

/**
 * Gets all topics/hashtags for the article from t tags.
 */
val NDKEvent.articleTopics: List<String>
    get() = hashtags
