package dev.coys.calendar.data

/**
 * Places a starting XI on a pitch.
 *
 * ESPN position codes don't line up with formation rows (in a 4-2-3-1 the two
 * holding midfielders come back as "LM"/"RM"), so rows are built by sorting
 * outfielders from deepest to most advanced and cutting that list by the
 * formation's row sizes. Left/right order inside a row comes from the code's
 * side marker (R..., ...-R).
 */
object FormationLayout {

    /** x: 0 = left touchline, 1 = right. y: 0 = own goal line, 1 = opponent's. */
    data class Slot(val player: LineupPlayer, val x: Float, val y: Float)

    fun layout(formation: String?, starters: List<LineupPlayer>): List<Slot>? {
        if (starters.size != 11) return null
        val rows = formation?.split("-")?.mapNotNull { it.trim().toIntOrNull() }
        if (rows.isNullOrEmpty() || rows.sum() != 10) return null

        val keeper = starters.firstOrNull { it.formationPlace == 1 }
            ?: starters.firstOrNull { it.position == "G" || it.position == "GK" }
            ?: return null
        val outfield = starters.filter { it !== keeper }
            .sortedWith(compareBy({ depth(it.position) }, { it.formationPlace }))

        // Keeper sits high enough that the name label clears the goal line.
        val slots = mutableListOf(Slot(keeper, 0.5f, 0.09f))
        var index = 0
        rows.forEachIndexed { rowIndex, size ->
            val row = outfield.subList(index, index + size)
                .sortedWith(compareBy({ side(it.position) }, { it.formationPlace }))
            index += size
            // Spread rows evenly from just above the box to just short of the far box.
            val y = if (rows.size == 1) 0.5f else 0.22f + rowIndex * (0.66f / (rows.size - 1))
            row.forEachIndexed { i, p -> slots += Slot(p, (i + 1f) / (size + 1f), y) }
        }
        return slots
    }

    /** Smaller = deeper. Unknown codes sit in midfield. */
    internal fun depth(code: String): Int {
        val c = code.uppercase()
        return when {
            c == "G" || c == "GK" -> 0
            c.startsWith("CD") || c == "RB" || c == "LB" || c == "SW" || c == "D" || c == "CB" -> 1
            c.endsWith("WB") -> 2
            c.startsWith("DM") || c == "CDM" -> 3
            c.startsWith("CM") || c == "RM" || c == "LM" || c == "M" -> 4
            c.startsWith("AM") || c == "CAM" -> 5
            c.startsWith("CF-") || c == "RF" || c == "LF" || c == "RW" || c == "LW" -> 6
            c == "F" || c == "CF" || c == "ST" -> 7
            else -> 4
        }
    }

    /** -2 wide left … 0 centre … +2 wide right. */
    internal fun side(code: String): Int {
        val c = code.uppercase()
        return when {
            c.endsWith("-L") -> -1
            c.endsWith("-R") -> 1
            c.length in 2..3 && c.startsWith("L") -> -2
            c.length in 2..3 && c.startsWith("R") -> 2
            else -> 0
        }
    }
}
