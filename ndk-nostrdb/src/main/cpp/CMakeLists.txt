cmake_minimum_required(VERSION 3.22.1)
project(ndk_nostrdb C)

# Enable position independent code for shared library
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_C_STANDARD 11)

# Define Android-specific settings and macros
add_definitions(
    -DANDROID
    -DHAVE_MEMMEM=1
    -DCCAN_TAL_NEVER_RETURN_NULL=1
)

# secp256k1 configuration - enable schnorrsig and extrakeys modules
set(SECP256K1_ENABLE_MODULE_SCHNORRSIG ON CACHE BOOL "Enable schnorrsig module")
set(SECP256K1_ENABLE_MODULE_EXTRAKEYS ON CACHE BOOL "Enable extrakeys module")
set(SECP256K1_BUILD_TESTS OFF CACHE BOOL "Disable tests")
set(SECP256K1_BUILD_EXHAUSTIVE_TESTS OFF CACHE BOOL "Disable exhaustive tests")
set(SECP256K1_BUILD_BENCHMARK OFF CACHE BOOL "Disable benchmarks")
set(SECP256K1_BUILD_EXAMPLES OFF CACHE BOOL "Disable examples")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static library")

# Add secp256k1 as subdirectory
add_subdirectory(deps/secp256k1)

# LMDB source files
set(LMDB_SOURCES
    deps/lmdb/mdb.c
    deps/lmdb/midl.c
)

# CCAN helper libraries - matches Makefile CCAN_SRCS
set(CCAN_SOURCES
    ccan/ccan/utf8/utf8.c
    ccan/ccan/tal/tal.c
    ccan/ccan/tal/str/str.c
    ccan/ccan/list/list.c
    ccan/ccan/mem/mem.c
    ccan/ccan/crypto/sha256/sha256.c
    ccan/ccan/take/take.c
)

# FlatCC runtime sources - matches Makefile FLATCC_SRCS
set(FLATCC_SOURCES
    deps/flatcc/src/runtime/json_parser.c
    deps/flatcc/src/runtime/verifier.c
    deps/flatcc/src/runtime/builder.c
    deps/flatcc/src/runtime/emitter.c
    deps/flatcc/src/runtime/refmap.c
)

# Bolt11 (Lightning invoice parsing) sources - matches Makefile BOLT11_SRCS
set(BOLT11_SOURCES
    nostrdb/bolt11/bolt11.c
    nostrdb/bolt11/bech32.c
    nostrdb/bolt11/amount.c
    nostrdb/bolt11/hash_u5.c
    nostrdb/bolt11/bech32_util.c
    nostrdb/bolt11/error.c
    nostrdb/bolt11/node_id.c
)

# nostrdb source files - matches Makefile SRCS
set(NOSTRDB_SOURCES
    nostrdb/nostrdb.c
    nostrdb/block.c
    nostrdb/content_parser.c
    nostrdb/base64.c
    nostrdb/invoice.c
    nostrdb/nostr_bech32.c
    nostrdb/binmoji.c
    nostrdb/metadata.c
)

# JNI bridge source
set(JNI_SOURCES
    ndb_jni.c
)

# Include directories - matches Makefile CFLAGS includes
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/nostrdb
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/lmdb
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/secp256k1/include
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/flatcc/include
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/libsodium/src/libsodium/include
    ${CMAKE_CURRENT_SOURCE_DIR}/ccan
)

# Build nostrdb as static library first
add_library(nostrdb_static STATIC
    ${LMDB_SOURCES}
    ${CCAN_SOURCES}
    ${FLATCC_SOURCES}
    ${BOLT11_SOURCES}
    ${NOSTRDB_SOURCES}
)

target_compile_options(nostrdb_static PRIVATE
    -Wall
    -Wno-unused-function
    -Wno-unused-variable
    -Wno-misleading-indentation
    -O2
)

# Build JNI shared library
add_library(ndk_nostrdb SHARED
    ${JNI_SOURCES}
)

target_link_libraries(ndk_nostrdb
    nostrdb_static
    secp256k1
    log
)

target_compile_options(ndk_nostrdb PRIVATE
    -Wall
    -O2
)
