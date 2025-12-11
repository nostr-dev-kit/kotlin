package io.nostr.ndk.nips

/**
 * NIP-18: Reposts
 *
 * https://github.com/nostr-protocol/nips/blob/master/18.md
 */

/**
 * Kind constant for reposts (kind 6).
 * Used to repost kind 1 events.
 */
const val KIND_REPOST = 6

/**
 * Kind constant for generic reposts (kind 16).
 * Used to repost any event kind.
 */
const val KIND_GENERIC_REPOST = 16
