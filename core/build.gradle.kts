// SPDX-License-Identifier: GPL-3.0-only
//
// Ported from VBoard's :core module. The sources under src/ are unchanged;
// this build file is rewritten because WaveKey (inherited from
// HeliBoard) had no version catalog. It has one now, for the fork's own
// modules: gradle/libs.versions.toml.
plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.coroutines.core)

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.3")
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Lets tools/promptlab pipe utterances through the spoken-form rules the phone
// actually runs, rather than a Python copy of them.
tasks.register<JavaExec>("runSpokenFormats") {
    group = "verification"
    mainClass.set("com.vboard.core.text.SpokenFormatsCliKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}

// Same idea as runSpokenFormats: promptlab scores the shipped accept/reject
// decision, not the model's raw answer.
tasks.register<JavaExec>("runRefinementValidator") {
    group = "verification"
    mainClass.set("com.vboard.core.correct.RefinementValidatorCliKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}
