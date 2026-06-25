plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization") version "2.2.10"
}

kotlin {
    // 1. Android Target (The SurfaceView Consumer)
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    // 2. iOS Targets (The Metal/AVAudioEngine Consumers)
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedEngine"
            isStatic = true
        }
    }
    
    // 3. Windows Bare-Metal Target (The 1MB EXE)
    mingwX64("win") {
        binaries.executable {
            entryPoint = "main"
            // C-interop definitions for Win32 and miniaudio will go here later
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // THE ONLY ALLOWED DEPENDENCIES: Pure Kotlin and Coroutines (for the Event Bus)
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
    }
}

android {
    namespace = "com.timebox.engine.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}