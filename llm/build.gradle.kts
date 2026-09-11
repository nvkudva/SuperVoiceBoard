// SPDX-License-Identifier: GPL-3.0-only
//
// The refiner process. Ported from VBoard's app/llm/ + ILlmRefiner.aidl with the
// process boundary intact: a 0.5B model OOM must not take down the keyboard.
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.vboard.app.llm"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines.android)
    // On-device LLM refinement (LiteRT-LM, the successor to MediaPipe's
    // LLM Inference API). Ships arm64-v8a and x86_64 only - see
    // `refinerAbiSupported` for what that costs on 32-bit installs.
    implementation(libs.litertlm.android)
}
