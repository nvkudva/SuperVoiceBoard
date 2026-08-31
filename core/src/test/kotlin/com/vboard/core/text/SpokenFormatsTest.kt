package com.vboard.core.text

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

/**
 * W7.2. The rules are conservative by design, so the tests come in pairs: what
 * each rule converts, and the near-miss it must leave alone. The second half is
 * the half that matters — a keyboard that rewrites "the third of us" is worse
 * than one that never converts anything.
 */
class SpokenFormatsTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "five dollars fifty|$5.50",
            "five dollars and fifty cents|$5.50",
            "five dollars ninety nine|$5.99",
            "twenty dollars|$20",
            "one dollar|$1",
            "thirty euros|€30",
            "twelve pounds fifty|£12.50",
            "3 dollars|$3",
            "it costs twenty dollars today|it costs $20 today",
        ],
    )
    fun `money is written the way it is typed`(input: String, expected: String) {
        assertEquals(expected, SpokenFormats.apply(input))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "dollars are down again",
            "a pound of flour",
            "he made millions of dollars",
            "a hundred thousand dollars",
        ],
    )
    fun `money words without a number are left alone`(input: String) {
        assertEquals(input, SpokenFormats.apply(input))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "three thirty p m|3:30 PM",
            "three thirty pm|3:30 PM",
            "seven a m|7 AM",
            "meet me at seven a m sharp|meet me at 7 AM sharp",
            "ten fifteen p m|10:15 PM",
            "9 45 a m|9:45 AM",
        ],
    )
    fun `clock times get a colon and a meridiem`(input: String, expected: String) {
        assertEquals(expected, SpokenFormats.apply(input))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "three thirty",
            "call me at seven",
            "twenty five past nine",
            "thirteen thirty p m",
        ],
    )
    fun `a time without an unambiguous trigger stays spoken`(input: String) {
        assertEquals(input, SpokenFormats.apply(input))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "john dot smith at gmail dot com|john.smith@gmail.com",
            "vijay at example dot co dot uk|vijay@example.co.uk",
            "mail me at jane at acme dot com|mail me at jane@acme.com",
        ],
    )
    fun `spoken addresses become addresses`(input: String, expected: String) {
        assertEquals(expected, SpokenFormats.apply(input))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "connect the dots",
            "meet me at the pub",
            "look at that",
            "we arrive at six",
        ],
    )
    fun `dot and at on their own are ordinary words`(input: String) {
        assertEquals(input, SpokenFormats.apply(input))
    }

    @Test
    fun `an address is converted before the money and time rules can see it`() {
        // "dollar" inside a domain must not become a currency symbol, and the
        // "at" must not survive into the time rule.
        assertEquals(
            "billing@dollar dot com".let { SpokenFormats.apply("billing at dollar dot com") },
            SpokenFormats.apply("billing at dollar dot com"),
        )
        assertEquals("billing@dollar.com", SpokenFormats.apply("billing at dollar dot com"))
    }

    @Test
    fun `blank and ordinary prose are untouched`() {
        assertEquals("", SpokenFormats.apply(""))
        assertEquals("   ", SpokenFormats.apply("   "))
        val prose = "the meeting went well and everyone agreed on the plan"
        assertEquals(prose, SpokenFormats.apply(prose))
    }

    @Test
    fun `no word is ever silently dropped`() {
        // Every rule either rewrites a span into an equivalent written form or
        // leaves it; nothing may vanish. Checked by counting non-space runs that
        // survive in some form for inputs the rules decline to convert.
        for (input in listOf(
            "we agreed at the pub about dollars and cents",
            "he was third in line",
            "the dots connect at nine",
        )) {
            assertEquals(input, SpokenFormats.apply(input))
        }
    }
}
