package com.example.fastcontacts

// Smart relevance search. Names come from Android's StructuredName (given / family),
// never from splitting the display name.
class SearchEngine(private val contacts: List<Contact>) {

    fun search(query: String): List<Contact> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return contacts
        val phoneLike = q.all { it.isDigit() || it == '+' || it == '-' || it == '(' || it == ')' || it == ' ' }
        val qDigits = if (phoneLike) q.filter { it.isDigit() } else ""

        val scored = ArrayList<Pair<Int, Contact>>()
        for (c in contacts) {
            val t = tier(c, q, qDigits)
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

    private fun tier(c: Contact, q: String, qDigits: String): Int {
        val full = c.name.lowercase().trim()
        val first = c.firstName.lowercase()
        val last = c.lastName.lowercase()
        return when {
            first == q -> 0
            first.startsWith(q) -> 1
            first.contains(q) -> 2
            last.isNotEmpty() && last.startsWith(q) -> 3
            full.startsWith(q) -> 4
            full.contains(q) -> 5
            qDigits.isNotEmpty() && c.phones.any { phoneMatches(it.number, qDigits) } -> 6
            c.emails.any { it.address.lowercase().contains(q) } -> 7
            c.company.lowercase().contains(q) || c.nickname.lowercase().contains(q) ||
                c.jobTitle.lowercase().contains(q) -> 8
            else -> -1
        }
    }

    companion object {
        // +880 1712-345678, 01712345678 and 01712 345678 all match "017123" / "1712".
        fun phoneMatches(number: String, qDigits: String): Boolean {
            val nd = number.filter { it.isDigit() }
            if (nd.contains(qDigits)) return true
            val stripped = qDigits.trimStart('0')
            return stripped.length >= 3 && nd.contains(stripped)
        }

        private fun key(c: Contact, byLast: Boolean): String {
            val k = if (byLast) {
                (if (c.lastName.isNotEmpty()) c.lastName else c.firstName) + " " + c.firstName
            } else {
                (if (c.firstName.isNotEmpty()) c.firstName else c.name) + " " + c.lastName
            }.trim().lowercase()
            // names that do not start with a letter go to the end ("#" section)
            return (if (k.firstOrNull()?.isLetter() == true) "a" else "b") + k
        }

        fun sorted(list: List<Contact>, byLast: Boolean): List<Contact> =
            list.sortedBy { key(it, byLast) }

        fun letterOf(c: Contact, byLast: Boolean): String {
            val k = key(c, byLast).substring(1).trim()
            val ch = k.firstOrNull() ?: return "#"
            return if (ch.isLetter()) ch.uppercase() else "#"
        }
    }
}
