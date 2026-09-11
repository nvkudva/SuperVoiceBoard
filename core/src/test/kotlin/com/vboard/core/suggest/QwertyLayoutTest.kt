package com.vboard.core.suggest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QwertyLayoutTest {

    private val letters = ('a'..'z').toList()

    @Test
    fun `keys sharing an edge are neighbors`() {
        assertTrue(QwertyLayout.adjacent('q', 'w'))
        assertTrue(QwertyLayout.adjacent('g', 'h'))
        assertTrue(QwertyLayout.adjacent('e', 'd'))
        assertTrue(QwertyLayout.adjacent('n', 'm'))
    }

    @Test
    fun `keys across the board are not neighbors`() {
        assertFalse(QwertyLayout.adjacent('q', 'p'))
        assertFalse(QwertyLayout.adjacent('a', 'l'))
        assertFalse(QwertyLayout.adjacent('z', 'o'))
    }

    @Test
    fun `adjacency is symmetric for every pair of letters`() {
        for (a in letters) {
            for (b in letters) {
                assertEquals(
                    QwertyLayout.adjacent(a, b),
                    QwertyLayout.adjacent(b, a),
                    "$a/$b disagree with $b/$a",
                )
            }
        }
    }

    @Test
    fun `a key is not its own neighbor`() {
        for (a in letters) assertFalse(QwertyLayout.adjacent(a, a), "$a neighbors itself")
    }

    @Test
    fun `the end of a row does not wrap to the start of the next`() {
        assertFalse(QwertyLayout.adjacent('p', 'a'))
        assertFalse(QwertyLayout.adjacent('l', 'z'))
    }

    @Test
    fun `every letter has at least two neighbors`() {
        for (a in letters) {
            assertTrue(letters.count { QwertyLayout.adjacent(a, it) } >= 2, "$a is nearly isolated")
        }
    }

    @Test
    fun `uppercase is not a letter this layout knows`() {
        assertFalse(QwertyLayout.adjacent('Q', 'W'))
        assertFalse(QwertyLayout.adjacent('q', 'W'))
    }

    @Test
    fun `digits, punctuation and accented letters are never adjacent`() {
        assertFalse(QwertyLayout.adjacent('1', '2'))
        assertFalse(QwertyLayout.adjacent('.', ','))
        assertFalse(QwertyLayout.adjacent('é', 'e'))
        assertFalse(QwertyLayout.adjacent(' ', 'a'))
    }
}
