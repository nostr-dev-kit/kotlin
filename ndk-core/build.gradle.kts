plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "io.nostr.ndk"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // WebSocket
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("com.squareup.okhttp3:okhttp-coroutines:5.0.0-alpha.14")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Crypto - secp256k1 for Schnorr signatures
    implementation("fr.acinq.secp256k1:secp256k1-kmp-jvm:0.21.0")
    // Crypto - NIP-44 encryption (ChaCha20-Poly1305)
    implementation("com.goterl:lazysodium-android:5.2.0")
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    // Compose runtime (for @Immutable annotation only, no UI dependencies)
    implementation("androidx.compose.runtime:runtime:1.7.6")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // secp256k1 JNI bindings for unit tests (includes all platforms)
    testImplementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm:0.21.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
