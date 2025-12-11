buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.54")
    }
}

plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("android") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }

    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:2.1.0")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.0")
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
