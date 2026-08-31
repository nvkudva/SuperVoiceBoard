// SPDX-License-Identifier: GPL-3.0-only
//
// The voice layer: ASR engines, the dictation session controller, and model
// download/storage. IME-agnostic on purpose — nothing here references a View or
// any HeliBoard class, so the strip UI (W3) is the only thing that binds it to
// this particular keyboard.
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    // com.vboard.app so the ported sources' `com.vboard.app.R` resolves here
    // rather than needing an edit in every file (see PLAN.md R6).
    namespace = "com.vboard.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
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
    implementation(project(":llm"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Model downloads: WorkManager owns the retry/constraint/process-death story.
    api("androidx.work:work-runtime-ktx:2.10.0")
    // On-device speech recognition (streaming Zipformer + Parakeet TDT).
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.6")
    // tar.bz2 extraction for downloaded ASR model archives.
    implementation("org.apache.commons:commons-compress:1.27.1")
}
