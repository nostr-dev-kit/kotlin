# NDK-Android

Nostr Development Kit for Android - A production-quality Kotlin library for building Nostr applications.

## Project Structure

```
ndk-android/
├── ndk-core/              # Core NDK library (Android library module)
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/io/nostr/ndk/
│           └── NDK.kt     # Main NDK class
│
├── sample-app/            # Sample Android application
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/io/nostr/ndk/sample/
│       │   └── MainActivity.kt
│       └── res/           # Android resources
│
├── build.gradle.kts       # Root build configuration
├── settings.gradle.kts    # Module configuration
└── gradle.properties      # Gradle properties
```

## Build Configuration

- **Kotlin**: 2.1.0
- **Android Gradle Plugin**: 8.7.2
- **Compile SDK**: 34
- **Min SDK**: 26
- **Java Target**: 17

## Dependencies

### Core Libraries
- kotlinx-coroutines-core: 1.10.2
- OkHttp: 5.0.0-alpha.14
- Jackson: 2.17.0 (JSON processing)
- secp256k1-kmp: 0.21.0 (Cryptography)

### Testing
- JUnit 4.13.2
- kotlinx-coroutines-test: 1.10.2

## Building

```bash
# Build all modules
./gradlew build

# Build only ndk-core library
./gradlew :ndk-core:build

# Build sample app
./gradlew :sample-app:build
```

## Artifacts

After building, you'll find:
- **Library AAR**: `ndk-core/build/outputs/aar/ndk-core-release.aar`
- **Sample APK**: `sample-app/build/outputs/apk/debug/sample-app-debug.apk`

## Development Status

This is the initial project setup. The NDK class is currently a placeholder.

See `/Users/pablofernandez/10x/NDK-ANDROID-PLAN.md` for the complete implementation plan.

## Next Steps

Proceed with Milestone 1: Core Event Model & Cryptography
- Implement NDKEvent data class
- Add type-safe tag system
- Implement NDKFilter
- Add cryptography support (signing, verification, encryption)
