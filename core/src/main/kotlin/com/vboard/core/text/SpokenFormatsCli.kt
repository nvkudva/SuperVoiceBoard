package com.vboard.core.text

/**
 * Reads one utterance per line on stdin and writes the rewritten line back,
 * prefixed with "SF: " so Gradle's own chatter can be filtered out. Exists so
 * tools/promptlab can measure the rules the phone actually runs, instead of a
 * Python reimplementation that would drift from them within a week.
 */
fun main() {
    generateSequence(::readLine).forEach { line ->
        println("SF: " + SpokenFormats.apply(line))
    }
}
