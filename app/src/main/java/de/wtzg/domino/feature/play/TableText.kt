package de.wtzg.domino.feature.play

/** Names around the table, clockwise from you. */
val SEAT_NAMES = listOf("You", "Rosa", "Hank", "Mabel")

/** Numbers as players say them about ends: "5s", "3s or 6s", "1s, 2s or 4s". */
fun numbersText(values: List<Int>): String {
    val d = values.distinct()
    return when (d.size) {
        1 -> "${d[0]}s"
        else -> d.dropLast(1).joinToString(", ") { "${it}s" } + " or ${d.last()}s"
    }
}
