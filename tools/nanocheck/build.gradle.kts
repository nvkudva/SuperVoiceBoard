plugins {
    id("com.android.application") version "8.13.2"
    kotlin("android") version "2.3.20"
}

android {
    namespace = "com.vboard.nanocheck"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vboard.nanocheck"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

dependencies {
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
