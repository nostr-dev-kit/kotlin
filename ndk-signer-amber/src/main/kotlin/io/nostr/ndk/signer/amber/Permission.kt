package io.nostr.ndk.signer.amber

/**
 * Permissions that can be requested from the Amber signer app.
 * These permissions control what operations the app can perform via Amber.
 */
enum class Permission(val value: String) {
    /**
     * Permission to sign Nostr events
     */
    SIGN_EVENT("sign_event"),

    /**
     * Permission to perform NIP-04 encryption
     */
    NIP04_ENCRYPT("nip04_encrypt"),

    /**
     * Permission to perform NIP-04 decryption
     */
    NIP04_DECRYPT("nip04_decrypt"),

    /**
     * Permission to perform NIP-44 encryption
     */
    NIP44_ENCRYPT("nip44_encrypt"),

    /**
     * Permission to perform NIP-44 decryption
     */
    NIP44_DECRYPT("nip44_decrypt"),

    /**
     * Permission to get the public key
     */
    GET_PUBLIC_KEY("get_public_key"),

    /**
     * Permission to decrypt zap events (NIP-57)
     */
    DECRYPT_ZAP_EVENT("decrypt_zap_event");

    companion object {
        fun fromValue(value: String): Permission? {
            return entries.find { it.value == value }
        }
    }
}
