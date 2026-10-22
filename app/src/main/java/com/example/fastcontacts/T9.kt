package com.example.fastcontacts

// Predictive dialpad search: typing 3-2-8 finds "David" (D=3, A=2, V=8).
// Uses the same first-name-first ranking as SearchEngine.
object T9 {

    fun encode(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            when (ch) {
                in 'a'..'c' -> sb.append('2')
                in 'd'..'f' -> sb.append('3')
                in 'g'..'i' -> sb.append('4')
                in 'j'..'l' -> sb.append('5')
                in 'm'..'o' -> sb.append('6')
                in 'p'..'s' -> sb.append('7')
                in 't'..'v' -> sb.append('8')
                in 'w'..'z' -> sb.append('9')
                in '0'..'9' -> sb.append(ch)
                else -> {}
            }
        }
        return sb.toString()
    }

    private fun tier(c: Contact, q: String): Int {
        val first = encode(c.firstName.ifEmpty { c.name })
        val last = encode(c.lastName)
        val full = encode(c.name)
        return when {
            first == q -> 0
            first.startsWith(q) -> 1
            first.contains(q) -> 2
            last.isNotEmpty() && last.startsWith(q) -> 3
            full.startsWith(q) -> 4
            full.contains(q) -> 5
            else -> -1
        }
    }

    fun search(contacts: List<Contact>, digits: String): List<Contact> {
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return emptyList()
        val scored = ArrayList<Pair<Int, Contact>>()
        for (c in contacts) {
            val t = tier(c, digits)
            if (t >= 0) scored.add(Pair(t, c))
        }
        return scored
            .sortedWith(
                compareBy<Pair<Int, Contact>>(
                    { it.first },
                    { it.second.firstName.lowercase() },
                    { it.second.name.lowercase() }
                )
            )
            .map { it.second }
    }
}
