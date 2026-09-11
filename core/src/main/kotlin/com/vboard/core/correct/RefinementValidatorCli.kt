package com.vboard.core.correct

/**
 * Reads "original<TAB>candidate" per line and writes what the pipeline would
 * actually type: the candidate when the validator accepts it, the original
 * when it does not. Lets tools/promptlab score the shipped decision rather
 * than the model's raw answer, which is not what anyone sees.
 */
fun main() {
    generateSequence(::readLine).forEach { line ->
        val (original, candidate) = line.split('\t', limit = 2).let {
            it[0] to it.getOrElse(1) { "" }
        }
        val verdict = RefinementValidator.validate(original, candidate)
        val text = if (verdict.accepted) verdict.text() ?: original else original
        println("RV: " + text.replace("\n", " "))
    }
}
