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
    compileSdk = 36

    defaultConfig {
        minSdk = 21
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // On-device LLM refinement (MediaPipe LLM Inference).
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
}
