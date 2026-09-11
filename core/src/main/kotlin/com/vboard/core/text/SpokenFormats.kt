package com.vboard.core.text

/**
 * Turns spoken formats into their written form, before anything else touches
 * the transcript (W7.2).
 *
 * A recognizer hears "five dollars fifty" and writes exactly that. Nobody types
 * it that way, and no later stage can fix it: [ContentGuard] shields written
 * forms, and the tokenizer is prose-only, so by the time either of them runs the
 * words are already the wrong shape. This runs first, on the raw transcript.
 *
 * Every rule here is deliberately conservative, because the failure modes are
 * asymmetric: leaving "five dollars fifty" alone is a small annoyance, and
 * turning "the third of us" into "the 3rd of us" is the keyboard putting words
 * in the user's mouth. So each conversion needs an unambiguous trigger — a
 * currency word, a clock-shaped pair, an explicit "at"/"dot" chain — and
 * anything short of that is left exactly as spoken.
 *
 * The one thing this must never do is lose a word. Every rule either rewrites a
 * span into a strictly equivalent written form or leaves it untouched.
 */
object SpokenFormats {

    // Compiled once. These used to be built inside the rewrite functions, which
    // meant recompiling five patterns on every utterance — and two more per
    // address match. The three that interpolate NUMBER are lazy because it is
    // declared further down and object properties initialize in source order.
    private val ADDRESS_PATTERN = Regex(
        """\b([A-Za-z0-9]+(?:\s+dot\s+[A-Za-z0-9]+)*)\s+at\s+([A-Za-z0-9]+(?:\s+dot\s+[A-Za-z0-9]+)+)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val SPOKEN_DOT = Regex("""\s+dot\s+""", RegexOption.IGNORE_CASE)
    private val LEADING_OH = Regex("^oh\\s+", RegexOption.IGNORE_CASE)
    private val MONEY_PATTERN by lazy {
        Regex(
            """\b($NUMBER)\s+(dollars?|euros?|pounds?)(?:\s+(?:and\s+)?($NUMBER)(?:\s+cents?|\s+pence)?)?\b""",
            RegexOption.IGNORE_CASE,
        )
    }
    private val CLOCK_PATTERN by lazy {
        val minuteWord = """(?:$NUMBER|o'?clock|oh\s+$NUMBER)"""
        Regex(
            """\b($NUMBER)(?:\s+($minuteWord))?\s+([ap])[.\s]*m\b\.?""",
            RegexOption.IGNORE_CASE,
        )
    }

    /** Rewrites [text] in place of the recognizer's spoken forms. */
    fun apply(text: String): String {
        if (text.isBlank()) return text
        var out = text
        // Order matters: email/URL chains first, so "dot" and "at" inside an
        // address are consumed before the money and time rules can see them.
        out = spokenAddresses(out)
        out = webAddresses(out)
        out = filePaths(out)
        out = digitRuns(out)
        out = money(out)
        out = clockTimes(out)
        return out
    }

    // ------------------------------------------------------------- addresses

    /**
     * "john dot smith at gmail dot com" -> "john.smith@gmail.com".
     *
     * Requires the full shape: at least one "at" with a "dot" chain after it,
     * because "dot" alone is a word ("connect the dots") and "at" alone is a
     * preposition. Both sides must be single words, which is what an address
     * dictated word-by-word looks like.
     */
    private fun spokenAddresses(text: String): String {
        return ADDRESS_PATTERN.replace(text) { match ->
            val local = match.groupValues[1].replace(SPOKEN_DOT, ".")
            val domain = match.groupValues[2].replace(SPOKEN_DOT, ".")
            // A domain has to end in something that could be a TLD; "meet me at
            // building dot two" is not an address.
            if (!domain.substringAfterLast('.').all { it.isLetter() }) match.value
            else "$local@$domain"
        }
    }

    // ------------------------------------------------------- web and paths

    private val SCHEME_PATTERN = Regex(
        """\b(https?)\s+colon\s+(?:slash\s+slash|double\s+slash)\s+""",
        RegexOption.IGNORE_CASE,
    )

    /** Host with its "dot" chain, plus any "slash <word>" steps that follow it. */
    private val WEB_PATTERN = Regex(
        """\b([A-Za-z][A-Za-z0-9-]*(?:\s+dot\s+[A-Za-z0-9-]+)+)((?:\s+slash\s+[A-Za-z0-9._-]+)*)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Two steps minimum. One step is "pick red slash blue", which is a choice,
     * not a path; by the second step nobody means "or" any more.
     */
    private val ABSOLUTE_PATH = Regex(
        """(?<![A-Za-z0-9])slash\s+([A-Za-z0-9._-]+)((?:\s+slash\s+[A-Za-z0-9._-]+)+)""",
        RegexOption.IGNORE_CASE,
    )
    private val SLASH_STEP = Regex("""\s+slash\s+""", RegexOption.IGNORE_CASE)

    /** A last label that could be a top-level domain. */
    private val TLD_SHAPE = Regex("""^[A-Za-z]{2,24}$""")

    /**
     * "https colon slash slash example dot com slash docs" ->
     * "https://example.com/docs", and the bare "example dot com" form too.
     *
     * Runs after [spokenAddresses], so an email has already been consumed and
     * what is left with a "dot" chain is a host. The trailing "slash <word>"
     * steps are taken as the path, which is the part a model gets wrong most
     * often: asked for one it has not memorised, it invents a plausible one.
     */
    private fun webAddresses(text: String): String {
        val schemed = SCHEME_PATTERN.replace(text) { "${it.groupValues[1].lowercase()}://" }
        return WEB_PATTERN.replace(schemed) { match ->
            val host = match.groupValues[1].replace(SPOKEN_DOT, ".")
            if (!TLD_SHAPE.matches(host.substringAfterLast('.'))) return@replace match.value
            val path = match.groupValues[2]
                .replace(SLASH_STEP, "/")
                .let { if (it.isEmpty()) "" else "/" + it.trimStart('/') }
            host + path
        }
    }

    /**
     * "slash user slash local slash bin" -> "/user/local/bin".
     *
     * A leading "slash" is unambiguous: nobody says it unless they mean one.
     * Relative paths ("src slash main") are left alone, because "and/or" chains
     * and fractions are spoken with the same word.
     */
    private fun filePaths(text: String): String {
        return ABSOLUTE_PATH.replace(text) { match ->
            val tail = match.groupValues[2].replace(SLASH_STEP, "/")
            "/" + match.groupValues[1] + if (tail.isEmpty()) "" else "/" + tail.trimStart('/')
        }
    }

    // ------------------------------------------------------------ digit runs

    /**
     * Same threshold RefinementValidator uses to call a run a number rather
     * than counting, which is what keeps "two three apples" out of it.
     */
    private const val MIN_DIGIT_RUN = 4

    private val SPOKEN_DIGITS = mapOf(
        "zero" to '0', "oh" to '0', "nought" to '0',
        "one" to '1', "two" to '2', "three" to '3', "four" to '4', "five" to '5',
        "six" to '6', "seven" to '7', "eight" to '8', "nine" to '9',
    )

    private val WORD_OR_GAP = Regex("""[A-Za-z]+|[^A-Za-z]+""")

    /**
     * "five five five one two three four" -> "5551234".
     *
     * Done here rather than in the prompt because the model is bad at it in a
     * way that corrupts data: asked to write the digits itself, a 0.6B returned
     * 5:55:12, and on another run invented 555-123-4567.
     */
    private fun digitRuns(text: String): String {
        val out = StringBuilder(text.length)
        val digits = StringBuilder()
        val asSpoken = StringBuilder()
        // The gap after the last digit word belongs to the sentence, not to the
        // run: swallowing it is what glued "5551234" to the next word.
        var pendingGap = ""

        fun flush() {
            out.append(if (digits.length >= MIN_DIGIT_RUN) digits else asSpoken)
            digits.setLength(0)
            asSpoken.setLength(0)
        }

        for (token in WORD_OR_GAP.findAll(text).map { it.value }) {
            val digit = SPOKEN_DIGITS[token.lowercase()]
            when {
                // A single space holds a run together; anything else ends it.
                digit != null && (digits.isEmpty() || pendingGap == " ") -> {
                    asSpoken.append(pendingGap).append(token)
                    digits.append(digit)
                    pendingGap = ""
                }
                digits.isNotEmpty() && token.isNotEmpty() && !token[0].isLetter() -> {
                    pendingGap = token
                }
                else -> {
                    flush()
                    out.append(pendingGap).append(token)
                    pendingGap = ""
                }
            }
        }
        flush()
        out.append(pendingGap)
        return out.toString()
    }

    // ----------------------------------------------------------------- money

    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19,
    )

    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    private val CURRENCIES = mapOf(
        "dollars" to "$", "dollar" to "$",
        "euros" to "€", "euro" to "€",
        "pounds" to "£", "pound" to "£",
    )

    /**
     * "five dollars fifty" -> "$5.50", "twenty dollars" -> "$20",
     * "five dollars and ninety nine cents" -> "$5.99".
     *
     * The currency word is the trigger, and it must be preceded by a number, so
     * "dollars are down" and "pound of flour" are untouched. Amounts above 999
     * are left alone: "a hundred thousand dollars" is prose as often as it is a
     * figure, and getting it wrong is worse than leaving it.
     */
    private fun money(text: String): String {
        return MONEY_PATTERN.replace(text) { match ->
            val whole = parseNumber(match.groupValues[1]) ?: return@replace match.value
            val symbol = CURRENCIES[match.groupValues[2].lowercase()] ?: return@replace match.value
            val fractionWords = match.groupValues[3]
            if (fractionWords.isEmpty()) return@replace "$symbol$whole"
            val fraction = parseNumber(fractionWords) ?: return@replace "$symbol$whole"
            if (fraction !in 0..99) return@replace match.value
            "$symbol$whole.${fraction.toString().padStart(2, '0')}"
        }
    }

    /**
     * A number, spoken or written, as a regex fragment.
     *
     * Built from the vocabulary rather than written as `[a-z]+`, because a
     * generic word class made every rule greedy in the wrong direction: "it
     * costs twenty dollars" matched "costs twenty" as the amount, and "five
     * dollars and fifty cents" swallowed "fifty cents" as the fraction.
     */
    private val NUMBER: String = run {
        val words = (TENS.keys.flatMap { tens -> UNITS.keys.filter { UNITS[it] != 0 }.map { "$tens[- ]$it" } } +
            TENS.keys + UNITS.keys).sortedByDescending { it.length }
        "(?:\\d{1,3}|" + words.joinToString("|") + ")"
    }

    /** A spoken or written number 0..999, or null when it is not one. */
    private fun parseNumber(raw: String): Int? {
        val text = raw.trim().lowercase()
        text.toIntOrNull()?.let { return if (it in 0..999) it else null }
        val words = text.split(' ', '-').filter { it.isNotEmpty() }
        return when (words.size) {
            1 -> UNITS[words[0]] ?: TENS[words[0]]
            2 -> {
                val tens = TENS[words[0]] ?: return null
                val unit = UNITS[words[1]] ?: return null
                if (unit == 0) null else tens + unit
            }
            else -> null
        }
    }

    // ----------------------------------------------------------------- times

    /**
     * "three thirty p m" -> "3:30 PM", "seven a m" -> "7 AM".
     *
     * The meridiem is the trigger. Without it, "three thirty" stays as it is:
     * a bare pair of numbers is a time only in context this cannot see.
     */
    private fun clockTimes(text: String): String {
        return CLOCK_PATTERN.replace(text) { match ->
            val hour = parseNumber(match.groupValues[1]) ?: return@replace match.value
            if (hour !in 1..12) return@replace match.value
            val meridiem = match.groupValues[3].uppercase() + "M"
            val minuteRaw = match.groupValues[2]
            if (minuteRaw.isEmpty()) return@replace "$hour $meridiem"
            // "o'clock" and "oh five" are the two spoken zero-minute forms.
            val minute = when (minuteRaw.lowercase().replace("-", " ")) {
                "o'clock", "oclock" -> 0
                else -> parseNumber(minuteRaw.replace(LEADING_OH, ""))
            } ?: return@replace match.value
            if (minute !in 0..59) return@replace match.value
            "$hour:${minute.toString().padStart(2, '0')} $meridiem"
        }
    }
}
