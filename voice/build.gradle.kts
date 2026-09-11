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
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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
        getByName("test") {
            kotlin.srcDir("src/test/kotlin")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":llm"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.android)
    // Model downloads: WorkManager owns the retry/constraint/process-death story.
    api("androidx.work:work-runtime-ktx:2.10.0")
    // On-device speech recognition (streaming Zipformer + Parakeet TDT).
    implementation(libs.sherpa.onnx)
    // tar.bz2 extraction for downloaded ASR model archives.
    implementation(libs.commons.compress)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
