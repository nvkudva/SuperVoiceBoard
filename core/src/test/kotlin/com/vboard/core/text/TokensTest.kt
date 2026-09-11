package com.vboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokensTest {

    private fun roundTrip(text: String) =
        assertEquals(text, Tokenizer.render(Tokenizer.tokenize(text)), "round trip changed the text")

    // ------------------------------------------------------------ round trip

    @Test
    fun `plain dictated prose survives a round trip`() {
        roundTrip("the meeting is on tuesday")
        roundTrip("Call me back, please.")
        roundTrip("Are you there? I think so!")
    }

    @Test
    fun `contractions and possessives stay one word`() {
        roundTrip("I'll ask Sam's team what they're doing")
        assertEquals<List<Tok>>(listOf(Tok.Word("I'll")), Tokenizer.tokenize("I'll"))
    }

    @Test
    fun `hyphenated words and addresses stay one word`() {
        roundTrip("a well-known state-of-the-art result")
        assertEquals<List<Tok>>(listOf(Tok.Word("sam@vboard.dev")), Tokenizer.tokenize("sam@vboard.dev"))
    }

    @Test
    fun `combining marks and non-ASCII letters are carried through untouched`() {
        roundTrip("café naïve résumé")
        roundTrip("café Ω 5 € 3 × 4")
        roundTrip("emoji 🎉 stays")
    }

    @Test
    fun `currency and math symbols are not deleted`() {
        val words = Tokenizer.tokenize("£5 + 3 = 8").filterIsInstance<Tok.Word>().map { it.text }
        assertEquals(listOf("£5", "+", "3", "=", "8"), words)
    }

    @Test
    fun `hard breaks survive a round trip`() {
        roundTrip("first line\nsecond line")
        roundTrip("a paragraph\n\nand another")
    }

    // ------------------------------------------------------------- artifacts

    @Test
    fun `a control character is dropped without splitting the word`() {
        assertEquals<List<Tok>>(listOf(Tok.Word("hello")), Tokenizer.tokenize("hel\u0007lo"))
    }

    @Test
    fun `a byte order mark and a replacement character are dropped`() {
        assertEquals<List<Tok>>(listOf(Tok.Word("hello")), Tokenizer.tokenize("\ufeffhe\ufffdllo"))
    }

    @Test
    fun `an unpaired surrogate never reaches the field`() {
        assertEquals<List<Tok>>(listOf(Tok.Word("ab")), Tokenizer.tokenize("a\ud800b"))
    }

    // ------------------------------------------------------------ typography

    @Test
    fun `curly quotes and dashes are folded to their ASCII form`() {
        assertEquals("I'll wait", Tokenizer.render(Tokenizer.tokenize("I’ll wait")))
        assertEquals("\"yes\"", Tokenizer.render(Tokenizer.tokenize("“yes”")))
        assertEquals("a - b", Tokenizer.render(Tokenizer.tokenize("a — b")))
    }

    @Test
    fun `an ellipsis is one token however it was written`() {
        assertEquals(listOf(Tok.Word("wait"), Tok.Punct("...")), Tokenizer.tokenize("wait..."))
        assertEquals(listOf(Tok.Word("wait"), Tok.Punct("...")), Tokenizer.tokenize("wait…"))
    }

    // ------------------------------------------------------------ boundaries

    @Test
    fun `punctuation between two words is its own token`() {
        assertEquals(
            listOf(Tok.Word("yes"), Tok.Punct(","), Tok.Word("please")),
            Tokenizer.tokenize("yes, please"),
        )
    }

    @Test
    fun `repeated punctuation makes one token each`() {
        assertEquals(
            listOf(Tok.Word("what"), Tok.Punct("?"), Tok.Punct("?"), Tok.Punct("?")),
            Tokenizer.tokenize("what???"),
        )
    }

    @Test
    fun `runs of whitespace are a single boundary`() {
        assertEquals<List<Tok>>(listOf(Tok.Word("a"), Tok.Word("b")), Tokenizer.tokenize("a \t  b"))
    }

    @Test
    fun `a non-breaking space separates words just like a space`() {
        assertEquals<List<Tok>>(listOf(Tok.Word("a"), Tok.Word("b")), Tokenizer.tokenize("a\u00a0b"))
    }

    @Test
    fun `many blank lines collapse to one paragraph break`() {
        assertEquals(
            listOf(Tok.Word("a"), Tok.Break("\n\n"), Tok.Word("b")),
            Tokenizer.tokenize("a\n\n\n\nb"),
        )
    }

    @Test
    fun `a windows line ending is a single break`() {
        assertEquals(
            listOf(Tok.Word("a"), Tok.Break("\n"), Tok.Word("b")),
            Tokenizer.tokenize("a\r\nb"),
        )
    }

    // ----------------------------------------------------------- empty input

    @Test
    fun `nothing dictated makes no tokens`() {
        assertTrue(Tokenizer.tokenize("").isEmpty())
        assertTrue(Tokenizer.tokenize("   ").isEmpty())
        assertEquals("", Tokenizer.render(emptyList()))
    }

    // ---------------------------------------------------------------- render

    @Test
    fun `render normalizes stray spacing before punctuation`() {
        assertEquals("yes, please.", Tokenizer.render(Tokenizer.tokenize("yes , please .")))
    }

    @Test
    fun `quotes alternate open and close`() {
        assertEquals("he said \"stop\" then", Tokenizer.render(Tokenizer.tokenize("he said \"stop\" then")))
    }
}
