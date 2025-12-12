/**
 * NostrDB JNI Bridge for Android
 *
 * Provides JNI bindings for nostrdb functions to be called from Kotlin.
 * This bridges the C nostrdb library to the NDK Android cacheadapter.
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <android/log.h>

#include "nostrdb.h"
#include "bindings/c/profile_reader.h"

#define LOG_TAG "NdbJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============== Database Lifecycle ==============

JNIEXPORT jlong JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeInit(
    JNIEnv *env,
    jobject thiz,
    jstring db_path,
    jlong map_size,
    jint ingester_threads
) {
    const char *path = (*env)->GetStringUTFChars(env, db_path, NULL);
    if (!path) {
        LOGE("Failed to get db_path string");
        return 0;
    }

    struct ndb *ndb = NULL;
    struct ndb_config config;
    ndb_default_config(&config);
    ndb_config_set_mapsize(&config, (size_t)map_size);
    ndb_config_set_ingest_threads(&config, ingester_threads);

    int result = ndb_init(&ndb, path, &config);
    (*env)->ReleaseStringUTFChars(env, db_path, path);

    if (result != 1) {
        LOGE("ndb_init failed with result: %d", result);
        return 0;
    }

    LOGI("NostrDB initialized successfully at %s", path);
    return (jlong)(intptr_t)ndb;
}

JNIEXPORT void JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeDestroy(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    if (ndb) {
        ndb_destroy(ndb);
        LOGI("NostrDB destroyed");
    }
}

// ============== Transaction Management ==============

JNIEXPORT jlong JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeBeginQuery(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    if (!ndb) return 0;

    struct ndb_txn *txn = malloc(sizeof(struct ndb_txn));
    if (!txn) return 0;

    if (ndb_begin_query(ndb, txn) != 1) {
        free(txn);
        return 0;
    }

    return (jlong)(intptr_t)txn;
}

JNIEXPORT void JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeEndQuery(
    JNIEnv *env,
    jobject thiz,
    jlong txn_ptr
) {
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (txn) {
        ndb_end_query(txn);
        free(txn);
    }
}

// ============== Event Ingestion ==============

JNIEXPORT jint JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeProcessEvent(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jstring json_event
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    if (!ndb) return 0;

    const char *json = (*env)->GetStringUTFChars(env, json_event, NULL);
    if (!json) return 0;

    int len = strlen(json);
    int result = ndb_process_event(ndb, json, len);

    (*env)->ReleaseStringUTFChars(env, json_event, json);
    return result;
}

// ============== Note Retrieval ==============

JNIEXPORT jbyteArray JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeGetNoteById(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong txn_ptr,
    jbyteArray note_id
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (!ndb || !txn) return NULL;

    jbyte *id_bytes = (*env)->GetByteArrayElements(env, note_id, NULL);
    if (!id_bytes) return NULL;

    size_t len = 0;
    uint64_t primkey = 0;
    struct ndb_note *note = ndb_get_note_by_id(txn, (unsigned char *)id_bytes, &len, &primkey);
    (*env)->ReleaseByteArrayElements(env, note_id, id_bytes, JNI_ABORT);

    if (!note || len == 0) return NULL;

    // Serialize note to JSON for return to Kotlin
    // For now, return raw bytes - Kotlin will need to parse
    jbyteArray result = (*env)->NewByteArray(env, len);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte *)note);
    }

    return result;
}

JNIEXPORT jlong JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeGetNoteKeyById(
    JNIEnv *env,
    jobject thiz,
    jlong txn_ptr,
    jbyteArray note_id
) {
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (!txn) return 0;

    jbyte *id_bytes = (*env)->GetByteArrayElements(env, note_id, NULL);
    if (!id_bytes) return 0;

    uint64_t key = ndb_get_notekey_by_id(txn, (unsigned char *)id_bytes);
    (*env)->ReleaseByteArrayElements(env, note_id, id_bytes, JNI_ABORT);

    return (jlong)key;
}

// ============== Profile Retrieval ==============

// Helper to escape JSON strings
static void json_escape_string(char *dest, size_t dest_size, const char *src) {
    if (!src) {
        dest[0] = '\0';
        return;
    }

    size_t di = 0;
    for (size_t si = 0; src[si] && di < dest_size - 2; si++) {
        char c = src[si];
        if (c == '"' || c == '\\') {
            if (di + 2 >= dest_size) break;
            dest[di++] = '\\';
            dest[di++] = c;
        } else if (c == '\n') {
            if (di + 2 >= dest_size) break;
            dest[di++] = '\\';
            dest[di++] = 'n';
        } else if (c == '\r') {
            if (di + 2 >= dest_size) break;
            dest[di++] = '\\';
            dest[di++] = 'r';
        } else if (c == '\t') {
            if (di + 2 >= dest_size) break;
            dest[di++] = '\\';
            dest[di++] = 't';
        } else if ((unsigned char)c < 0x20) {
            // Skip other control characters
        } else {
            dest[di++] = c;
        }
    }
    dest[di] = '\0';
}

JNIEXPORT jstring JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeGetProfileByPubkey(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong txn_ptr,
    jbyteArray pubkey
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (!ndb || !txn) return NULL;

    jbyte *pk_bytes = (*env)->GetByteArrayElements(env, pubkey, NULL);
    if (!pk_bytes) return NULL;

    size_t len = 0;
    uint64_t primkey = 0;
    void *profile_ptr = ndb_get_profile_by_pubkey(txn, (unsigned char *)pk_bytes, &len, &primkey);
    (*env)->ReleaseByteArrayElements(env, pubkey, pk_bytes, JNI_ABORT);

    if (!profile_ptr) return NULL;

    // Parse the flatbuffer as NdbProfileRecord
    NdbProfileRecord_table_t record = NdbProfileRecord_as_root(profile_ptr);
    if (!record) return NULL;

    NdbProfile_table_t profile = NdbProfileRecord_profile(record);
    if (!profile) return NULL;

    // Extract profile fields
    const char *name = NdbProfile_name(profile);
    const char *display_name = NdbProfile_display_name(profile);
    const char *about = NdbProfile_about(profile);
    const char *picture = NdbProfile_picture(profile);
    const char *banner = NdbProfile_banner(profile);
    const char *nip05 = NdbProfile_nip05(profile);
    const char *lud16 = NdbProfile_lud16(profile);
    const char *lud06 = NdbProfile_lud06(profile);
    const char *website = NdbProfile_website(profile);

    // Build JSON manually (escaped)
    // Allocate buffer for JSON - fields can be large
    size_t buf_size = 32768;  // 32KB should be enough for most profiles
    char *json = malloc(buf_size);
    if (!json) return NULL;

    char escaped[8192];  // Buffer for escaped strings

    strcpy(json, "{");
    int first = 1;

    #define ADD_FIELD(field_name, value) \
        if (value) { \
            json_escape_string(escaped, sizeof(escaped), value); \
            if (!first) strcat(json, ","); \
            strcat(json, "\"" field_name "\":\""); \
            strcat(json, escaped); \
            strcat(json, "\""); \
            first = 0; \
        }

    ADD_FIELD("name", name);
    ADD_FIELD("display_name", display_name);
    ADD_FIELD("about", about);
    ADD_FIELD("picture", picture);
    ADD_FIELD("banner", banner);
    ADD_FIELD("nip05", nip05);
    ADD_FIELD("lud16", lud16);
    ADD_FIELD("lud06", lud06);
    ADD_FIELD("website", website);

    #undef ADD_FIELD

    strcat(json, "}");

    jstring result = (*env)->NewStringUTF(env, json);
    free(json);
    return result;
}

// ============== Query/Filter API ==============

JNIEXPORT jlong JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeFilterCreate(
    JNIEnv *env,
    jobject thiz
) {
    struct ndb_filter *filter = malloc(sizeof(struct ndb_filter));
    if (!filter) return 0;

    if (ndb_filter_init(filter) != 1) {
        free(filter);
        return 0;
    }

    return (jlong)(intptr_t)filter;
}

JNIEXPORT void JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeFilterDestroy(
    JNIEnv *env,
    jobject thiz,
    jlong filter_ptr
) {
    struct ndb_filter *filter = (struct ndb_filter *)(intptr_t)filter_ptr;
    if (filter) {
        ndb_filter_destroy(filter);
        free(filter);
    }
}

JNIEXPORT jint JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeFilterStartField(
    JNIEnv *env,
    jobject thiz,
    jlong filter_ptr,
    jint field_type
) {
    struct ndb_filter *filter = (struct ndb_filter *)(intptr_t)filter_ptr;
    if (!filter) return 0;

    return ndb_filter_start_field(filter, (enum ndb_filter_fieldtype)field_type);
}

JNIEXPORT jint JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeFilterAddIdElement(
    JNIEnv *env,
    jobject thiz,
    jlong filter_ptr,
    jbyteArray id
) {
    struct ndb_filter *filter = (struct ndb_filter *)(intptr_t)filter_ptr;
    if (!filter) return 0;

    jbyte *id_bytes = (*env)->GetByteArrayElements(env, id, NULL);
    if (!id_bytes) return 0;

    int result = ndb_filter_add_id_element(filter, (unsigned char *)id_bytes);
    (*env)->ReleaseByteArrayElements(env, id, id_bytes, JNI_ABORT);

    return result;
}

JNIEXPORT jint JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeFilterAddIntElement(
    JNIEnv *env,
    jobject thiz,
    jlong filter_ptr,
    jlong value
) {
    struct ndb_filter *filter = (struct ndb_filter *)(intptr_t)filter_ptr;
    if (!filter) return 0;

    return ndb_filter_add_int_element(filter, (uint64_t)value);
}

JNIEXPORT void JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeFilterEndField(
    JNIEnv *env,
    jobject thiz,
    jlong filter_ptr
) {
    struct ndb_filter *filter = (struct ndb_filter *)(intptr_t)filter_ptr;
    if (filter) {
        ndb_filter_end_field(filter);
    }
}

JNIEXPORT jint JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeFilterEnd(
    JNIEnv *env,
    jobject thiz,
    jlong filter_ptr
) {
    struct ndb_filter *filter = (struct ndb_filter *)(intptr_t)filter_ptr;
    if (!filter) return 0;

    return ndb_filter_end(filter);
}

// ============== Query Execution ==============

JNIEXPORT jlongArray JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeQuery(
    JNIEnv *env,
    jobject thiz,
    jlong txn_ptr,
    jlong filter_ptr,
    jint limit
) {
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    struct ndb_filter *filter = (struct ndb_filter *)(intptr_t)filter_ptr;
    if (!txn || !filter) return NULL;

    // Allocate result buffer
    struct ndb_query_result *results = malloc(sizeof(struct ndb_query_result) * limit);
    if (!results) return NULL;

    int count = 0;
    int result = ndb_query(txn, filter, 1, results, limit, &count);

    if (result != 1 || count == 0) {
        free(results);
        return NULL;
    }

    // Return array of note keys
    jlongArray keys = (*env)->NewLongArray(env, count);
    if (keys) {
        jlong *key_values = malloc(sizeof(jlong) * count);
        for (int i = 0; i < count; i++) {
            key_values[i] = (jlong)results[i].note_id;
        }
        (*env)->SetLongArrayRegion(env, keys, 0, count, key_values);
        free(key_values);
    }

    free(results);
    return keys;
}

// ============== Subscription API ==============

JNIEXPORT jlong JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeSubscribe(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong filter_ptr
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    struct ndb_filter *filter = (struct ndb_filter *)(intptr_t)filter_ptr;
    if (!ndb || !filter) return 0;

    return (jlong)ndb_subscribe(ndb, filter, 1);
}

JNIEXPORT jlongArray JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativePollForNotes(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong sub_id,
    jint max_notes
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    if (!ndb) return NULL;

    uint64_t *note_keys = malloc(sizeof(uint64_t) * max_notes);
    if (!note_keys) return NULL;

    int count = ndb_poll_for_notes(ndb, (uint64_t)sub_id, note_keys, max_notes);

    if (count <= 0) {
        free(note_keys);
        return NULL;
    }

    jlongArray keys = (*env)->NewLongArray(env, count);
    if (keys) {
        jlong *long_keys = malloc(sizeof(jlong) * count);
        for (int i = 0; i < count; i++) {
            long_keys[i] = (jlong)note_keys[i];
        }
        (*env)->SetLongArrayRegion(env, keys, 0, count, long_keys);
        free(long_keys);
    }

    free(note_keys);
    return keys;
}

JNIEXPORT jint JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeUnsubscribe(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong sub_id
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    if (!ndb) return 0;

    return ndb_unsubscribe(ndb, (uint64_t)sub_id);
}

// ============== Note Data Access ==============

// Helper to get note field as string
JNIEXPORT jstring JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeNoteContent(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong txn_ptr,
    jlong note_key
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (!ndb || !txn) return NULL;

    size_t len = 0;
    struct ndb_note *note = ndb_get_note_by_key(txn, (uint64_t)note_key, &len);
    if (!note) return NULL;

    const char *content = ndb_note_content(note);
    if (!content) return NULL;

    return (*env)->NewStringUTF(env, content);
}

JNIEXPORT jbyteArray JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeNoteId(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong txn_ptr,
    jlong note_key
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (!ndb || !txn) return NULL;

    size_t len = 0;
    struct ndb_note *note = ndb_get_note_by_key(txn, (uint64_t)note_key, &len);
    if (!note) return NULL;

    unsigned char *id = ndb_note_id(note);
    if (!id) return NULL;

    jbyteArray result = (*env)->NewByteArray(env, 32);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, 32, (jbyte *)id);
    }

    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeNotePubkey(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong txn_ptr,
    jlong note_key
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (!ndb || !txn) return NULL;

    size_t len = 0;
    struct ndb_note *note = ndb_get_note_by_key(txn, (uint64_t)note_key, &len);
    if (!note) return NULL;

    unsigned char *pubkey = ndb_note_pubkey(note);
    if (!pubkey) return NULL;

    jbyteArray result = (*env)->NewByteArray(env, 32);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, 32, (jbyte *)pubkey);
    }

    return result;
}

JNIEXPORT jlong JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeNoteCreatedAt(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong txn_ptr,
    jlong note_key
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (!ndb || !txn) return 0;

    size_t len = 0;
    struct ndb_note *note = ndb_get_note_by_key(txn, (uint64_t)note_key, &len);
    if (!note) return 0;

    return (jlong)ndb_note_created_at(note);
}

JNIEXPORT jint JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeNoteKind(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong txn_ptr,
    jlong note_key
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (!ndb || !txn) return 0;

    size_t len = 0;
    struct ndb_note *note = ndb_get_note_by_key(txn, (uint64_t)note_key, &len);
    if (!note) return 0;

    return (jint)ndb_note_kind(note);
}

// ============== Tag Iteration ==============

/**
 * Get all tags from a note as a 2D array of strings.
 * Returns jobjectArray of String[] where each String[] is one tag.
 * Tag elements are returned as strings; binary IDs are hex-encoded.
 */
JNIEXPORT jobjectArray JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeNoteTags(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong txn_ptr,
    jlong note_key
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (!ndb || !txn) return NULL;

    size_t len = 0;
    struct ndb_note *note = ndb_get_note_by_key(txn, (uint64_t)note_key, &len);
    if (!note) return NULL;

    // Count tags first
    struct ndb_tags *tags = ndb_note_tags(note);
    if (!tags) return NULL;

    uint16_t tag_count = ndb_tags_count(tags);
    if (tag_count == 0) return NULL;

    // Get String class for array creation
    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    if (!stringClass) return NULL;

    // Create outer array (array of String[])
    jclass stringArrayClass = (*env)->FindClass(env, "[Ljava/lang/String;");
    if (!stringArrayClass) return NULL;

    jobjectArray result = (*env)->NewObjectArray(env, tag_count, stringArrayClass, NULL);
    if (!result) return NULL;

    // Iterate through tags
    struct ndb_iterator iter;
    ndb_tags_iterate_start(note, &iter);

    int tag_index = 0;
    while (ndb_tags_iterate_next(&iter) && tag_index < tag_count) {
        struct ndb_tag *tag = iter.tag;
        uint16_t elem_count = ndb_tag_count(tag);

        // Create inner array for this tag's elements
        jobjectArray tag_array = (*env)->NewObjectArray(env, elem_count, stringClass, NULL);
        if (!tag_array) continue;

        // Get each element of the tag
        for (int i = 0; i < elem_count; i++) {
            struct ndb_str str = ndb_iter_tag_str(&iter, i);

            if (str.flag == NDB_PACKED_STR && str.str) {
                // Regular string
                jstring elem = (*env)->NewStringUTF(env, str.str);
                if (elem) {
                    (*env)->SetObjectArrayElement(env, tag_array, i, elem);
                    (*env)->DeleteLocalRef(env, elem);
                }
            } else if (str.flag == NDB_PACKED_ID && str.id) {
                // Binary ID - convert to hex string
                char hex[65];
                for (int j = 0; j < 32; j++) {
                    sprintf(hex + j * 2, "%02x", str.id[j]);
                }
                hex[64] = '\0';
                jstring elem = (*env)->NewStringUTF(env, hex);
                if (elem) {
                    (*env)->SetObjectArrayElement(env, tag_array, i, elem);
                    (*env)->DeleteLocalRef(env, elem);
                }
            }
        }

        (*env)->SetObjectArrayElement(env, result, tag_index, tag_array);
        (*env)->DeleteLocalRef(env, tag_array);
        tag_index++;
    }

    return result;
}

/**
 * Get the signature from a note.
 * Returns 64-byte signature as byte array.
 */
JNIEXPORT jbyteArray JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeNoteSig(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr,
    jlong txn_ptr,
    jlong note_key
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    struct ndb_txn *txn = (struct ndb_txn *)(intptr_t)txn_ptr;
    if (!ndb || !txn) return NULL;

    size_t len = 0;
    struct ndb_note *note = ndb_get_note_by_key(txn, (uint64_t)note_key, &len);
    if (!note) return NULL;

    unsigned char *sig = ndb_note_sig(note);
    if (!sig) return NULL;

    jbyteArray result = (*env)->NewByteArray(env, 64);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, 64, (jbyte *)sig);
    }

    return result;
}

// ============== Statistics API ==============

/**
 * Get database statistics.
 * Returns a long array with the following structure:
 * [0-47]: Database stats (16 DBs * 3 values: count, key_size, value_size)
 * [48-92]: Common kind stats (15 kinds * 3 values)
 * [93-95]: Other kinds stats (3 values)
 * Total: 96 longs
 */
JNIEXPORT jlongArray JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeGetStats(
    JNIEnv *env,
    jobject thiz,
    jlong ndb_ptr
) {
    struct ndb *ndb = (struct ndb *)(intptr_t)ndb_ptr;
    if (!ndb) return NULL;

    struct ndb_stat stat;
    if (ndb_stat(ndb, &stat) != 1) {
        LOGE("ndb_stat failed");
        return NULL;
    }

    // Calculate total size: 16 DBs + 15 common kinds + 1 other = 32 entries * 3 values = 96
    // But NDB_DBS = 16, NDB_CKIND_COUNT = 15
    int total_values = (NDB_DBS * 3) + (NDB_CKIND_COUNT * 3) + 3;

    jlongArray result = (*env)->NewLongArray(env, total_values);
    if (!result) return NULL;

    jlong *values = malloc(sizeof(jlong) * total_values);
    if (!values) {
        return NULL;
    }

    int idx = 0;

    // Database stats (16 DBs)
    for (int i = 0; i < NDB_DBS; i++) {
        values[idx++] = (jlong)stat.dbs[i].count;
        values[idx++] = (jlong)stat.dbs[i].key_size;
        values[idx++] = (jlong)stat.dbs[i].value_size;
    }

    // Common kind stats (15 kinds)
    for (int i = 0; i < NDB_CKIND_COUNT; i++) {
        values[idx++] = (jlong)stat.common_kinds[i].count;
        values[idx++] = (jlong)stat.common_kinds[i].key_size;
        values[idx++] = (jlong)stat.common_kinds[i].value_size;
    }

    // Other kinds stats
    values[idx++] = (jlong)stat.other_kinds.count;
    values[idx++] = (jlong)stat.other_kinds.key_size;
    values[idx++] = (jlong)stat.other_kinds.value_size;

    (*env)->SetLongArrayRegion(env, result, 0, total_values, values);
    free(values);

    return result;
}

/**
 * Get database name by index.
 */
JNIEXPORT jstring JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeGetDbName(
    JNIEnv *env,
    jobject thiz,
    jint db_index
) {
    if (db_index < 0 || db_index >= NDB_DBS) return NULL;

    const char *name = ndb_db_name((enum ndb_dbs)db_index);
    if (!name) return NULL;

    return (*env)->NewStringUTF(env, name);
}

/**
 * Get common kind name by index.
 */
JNIEXPORT jstring JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeGetCommonKindName(
    JNIEnv *env,
    jobject thiz,
    jint kind_index
) {
    // Map kind index to human-readable name
    const char *names[] = {
        "Profile",      // NDB_CKIND_PROFILE
        "Text",         // NDB_CKIND_TEXT
        "Contacts",     // NDB_CKIND_CONTACTS
        "DM",           // NDB_CKIND_DM
        "Delete",       // NDB_CKIND_DELETE
        "Repost",       // NDB_CKIND_REPOST
        "Reaction",     // NDB_CKIND_REACTION
        "Zap",          // NDB_CKIND_ZAP
        "Zap Request",  // NDB_CKIND_ZAP_REQUEST
        "NWC Request",  // NDB_CKIND_NWC_REQUEST
        "NWC Response", // NDB_CKIND_NWC_RESPONSE
        "HTTP Auth",    // NDB_CKIND_HTTP_AUTH
        "List",         // NDB_CKIND_LIST
        "Long-form",    // NDB_CKIND_LONGFORM
        "Status"        // NDB_CKIND_STATUS
    };

    if (kind_index < 0 || kind_index >= NDB_CKIND_COUNT) return NULL;

    return (*env)->NewStringUTF(env, names[kind_index]);
}

/**
 * Get the number of databases.
 */
JNIEXPORT jint JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeGetDbCount(
    JNIEnv *env,
    jobject thiz
) {
    return NDB_DBS;
}

/**
 * Get the number of common kinds.
 */
JNIEXPORT jint JNICALL
Java_io_nostr_ndk_cache_nostrdb_NostrDB_nativeGetCommonKindCount(
    JNIEnv *env,
    jobject thiz
) {
    return NDB_CKIND_COUNT;
}
