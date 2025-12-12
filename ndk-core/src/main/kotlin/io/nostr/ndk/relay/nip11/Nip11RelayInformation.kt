package io.nostr.ndk.relay.nip11

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * NIP-11 Relay Information Document
 * https://github.com/nostr-protocol/nips/blob/master/11.md
 *
 * Provides metadata about a relay including capabilities, limitations,
 * and contact information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Nip11RelayInformation(
    /** Optional relay identifier */
    val id: String? = null,

    /** Human-readable name of the relay */
    val name: String? = null,

    /** Description of the relay's purpose */
    val description: String? = null,

    /** URL to a relay icon image */
    val icon: String? = null,

    /** URL to a relay banner image */
    val banner: String? = null,

    /** Public key of the relay operator */
    val pubkey: String? = null,

    /** Contact information (email, URL, etc.) */
    val contact: String? = null,

    /** List of supported NIP numbers */
    @JsonProperty("supported_nips")
    val supportedNips: List<Int>? = null,

    /** List of supported NIP extensions */
    @JsonProperty("supported_nip_extensions")
    val supportedNipExtensions: List<String>? = null,

    /** Software name running the relay */
    val software: String? = null,

    /** Software version */
    val version: String? = null,

    /** Relay limitations and restrictions */
    val limitation: Nip11Limitation? = null,

    /** Countries where relay servers are located */
    @JsonProperty("relay_countries")
    val relayCountries: List<String>? = null,

    /** Relay's preferred languages */
    @JsonProperty("language_tags")
    val languageTags: List<String>? = null,

    /** Arbitrary tags for categorization */
    val tags: List<String>? = null,

    /** URL to the relay's posting policy */
    @JsonProperty("posting_policy")
    val postingPolicy: String? = null,

    /** URL for payment information */
    @JsonProperty("payments_url")
    val paymentsUrl: String? = null,

    /** Data retention policies */
    val retention: List<Nip11RetentionPolicy>? = null,

    /** Fee structure */
    val fees: Nip11Fees? = null,

    /** NIP-50 search capabilities */
    val nip50: List<String>? = null
) {
    companion object {
        private val mapper = jacksonObjectMapper()

        /**
         * Parse NIP-11 information from JSON string.
         */
        fun fromJson(json: String): Nip11RelayInformation {
            return mapper.readValue(json)
        }
    }

    /**
     * Check if relay supports a specific NIP.
     */
    fun supportsNip(nip: Int): Boolean {
        return supportedNips?.contains(nip) ?: false
    }
}

/**
 * Relay limitations and restrictions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Nip11Limitation(
    /** Maximum message length in bytes */
    @JsonProperty("max_message_length")
    val maxMessageLength: Int? = null,

    /** Maximum number of concurrent subscriptions */
    @JsonProperty("max_subscriptions")
    val maxSubscriptions: Int? = null,

    /** Maximum number of filters per subscription */
    @JsonProperty("max_filters")
    val maxFilters: Int? = null,

    /** Maximum limit value in filters */
    @JsonProperty("max_limit")
    val maxLimit: Int? = null,

    /** Maximum subscription ID length */
    @JsonProperty("max_subid_length")
    val maxSubidLength: Int? = null,

    /** Minimum prefix length for queries */
    @JsonProperty("min_prefix")
    val minPrefix: Int? = null,

    /** Maximum number of tags per event */
    @JsonProperty("max_event_tags")
    val maxEventTags: Int? = null,

    /** Maximum event content length */
    @JsonProperty("max_content_length")
    val maxContentLength: Int? = null,

    /** Minimum PoW difficulty required */
    @JsonProperty("min_pow_difficulty")
    val minPowDifficulty: Int? = null,

    /** Whether authentication is required */
    @JsonProperty("auth_required")
    val authRequired: Boolean? = null,

    /** Whether payment is required */
    @JsonProperty("payment_required")
    val paymentRequired: Boolean? = null,

    /** Whether writes are restricted */
    @JsonProperty("restricted_writes")
    val restrictedWrites: Boolean? = null,

    /** Lower limit for event created_at timestamp */
    @JsonProperty("created_at_lower_limit")
    val createdAtLowerLimit: Long? = null,

    /** Upper limit for event created_at timestamp */
    @JsonProperty("created_at_upper_limit")
    val createdAtUpperLimit: Long? = null
)

/**
 * Relay data retention policy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Nip11RetentionPolicy(
    /** Event kinds covered by this policy */
    val kinds: List<Int>? = null,

    /** Time in seconds to retain events */
    val time: Int? = null,

    /** Number of events to retain */
    val count: Int? = null
)

/**
 * Relay fee structure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Nip11Fees(
    /** Admission fees */
    val admission: List<Nip11Fee>? = null,

    /** Subscription fees */
    val subscription: List<Nip11Fee>? = null,

    /** Publication fees */
    val publication: List<Nip11Fee>? = null
)

/**
 * Individual fee specification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Nip11Fee(
    /** Fee amount */
    val amount: Int? = null,

    /** Fee unit (e.g., "msats") */
    val unit: String? = null,

    /** Billing period in seconds */
    val period: Int? = null,

    /** Event kinds this fee applies to */
    val kinds: List<Int>? = null
)
