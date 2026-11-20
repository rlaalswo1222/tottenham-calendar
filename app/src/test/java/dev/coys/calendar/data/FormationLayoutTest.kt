package dev.coys.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormationLayoutTest {

    private fun xi(vararg codes: Pair<Int, String>) = codes.map { (place, code) ->
        LineupPlayer(code, code, "$place", code, place, starter = true, subbedIn = false, subbedOut = false)
    }

    /** Rows from back to front, each left→right, as position codes. */
    private fun rows(formation: String, players: List<LineupPlayer>): List<List<String>> {
        val slots = FormationLayout.layout(formation, players)!!
        return slots.groupBy { it.y }.toSortedMap().values
            .map { row -> row.sortedBy { it.x }.map { it.player.position } }
    }

    @Test fun `4-2-3-1 puts ESPN's LM and RM in the holding pair`() {
        val players = xi(1 to "G", 2 to "RB", 3 to "LB", 4 to "LM", 5 to "CD-R", 6 to "CD-L",
            7 to "AM-R", 8 to "RM", 9 to "F", 10 to "AM", 11 to "AM-L")
        assertEquals(
            listOf(listOf("G"), listOf("LB", "CD-L", "CD-R", "RB"), listOf("LM", "RM"),
                listOf("AM-L", "AM", "AM-R"), listOf("F")),
            rows("4-2-3-1", players),
        )
    }

    @Test fun `4-3-3`() {
        val players = xi(1 to "G", 2 to "RB", 3 to "LB", 4 to "CM", 5 to "CD-R", 6 to "CD-L",
            7 to "RM", 8 to "LM", 9 to "F", 10 to "RF", 11 to "LF")
        assertEquals(
            listOf(listOf("G"), listOf("LB", "CD-L", "CD-R", "RB"), listOf("LM", "CM", "RM"),
                listOf("LF", "F", "RF")),
            rows("4-3-3", players),
        )
    }

    @Test fun `3-4-2-1`() {
        val players = xi(1 to "G", 2 to "RM", 3 to "LM", 4 to "CD-L", 5 to "CD", 6 to "CD-R",
            7 to "CM-R", 8 to "CM-L", 9 to "F", 10 to "CF-R", 11 to "CF-L")
        assertEquals(
            listOf(listOf("G"), listOf("CD-L", "CD", "CD-R"), listOf("LM", "CM-L", "CM-R", "RM"),
                listOf("CF-L", "CF-R"), listOf("F")),
            rows("3-4-2-1", players),
        )
    }

    @Test fun `unusable input gives null so the UI falls back to a list`() {
        val ten = xi(1 to "G", 2 to "RB", 3 to "LB", 4 to "CM", 5 to "CD-R", 6 to "CD-L",
            7 to "RM", 8 to "LM", 9 to "F", 10 to "RF")
        assertNull(FormationLayout.layout("4-3-3", ten))
        val eleven = ten + xi(11 to "LF")
        assertNull(FormationLayout.layout(null, eleven))
        assertNull(FormationLayout.layout("4-4-3", eleven))
    }
}
