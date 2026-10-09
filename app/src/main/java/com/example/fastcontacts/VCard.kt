package com.example.fastcontacts

// Small local vCard importer / exporter. Nothing is uploaded anywhere.
object VCard {

    private val BS = 92.toChar().toString()
    private val CRLF = "" + 13.toChar() + 10.toChar()

    private fun esc(s: String): String =
        s.replace(BS, BS + BS).replace(";", BS + ";").replace(",", BS + ",")
            .replace(10.toChar().toString(), BS + "n")

    private fun unescape(s: String): String {
        val bs = 92.toChar()
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == bs && i + 1 < s.length) {
                val n = s[i + 1]
                if (n == 'n' || n == 'N') sb.append(10.toChar()) else sb.append(n)
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    fun export(contacts: List<Contact>): String {
        val sb = StringBuilder()
        for (c in contacts) {
            sb.append("BEGIN:VCARD").append(CRLF)
            sb.append("VERSION:3.0").append(CRLF)
            sb.append("N:").append(esc(c.lastName)).append(";").append(esc(c.firstName)).append(";;;").append(CRLF)
            sb.append("FN:").append(esc(c.name)).append(CRLF)
            for (p in c.phones) {
                val l = p.type.lowercase()
                val t = when {
                    l.contains("mobile") -> "CELL"
                    l.contains("home") -> "HOME"
                    l.contains("work") -> "WORK"
                    else -> "VOICE"
                }
                sb.append("TEL;TYPE=").append(t).append(":").append(p.number).append(CRLF)
            }
            for (e in c.emails) sb.append("EMAIL;TYPE=INTERNET:").append(e.address).append(CRLF)
            if (c.company.isNotEmpty()) sb.append("ORG:").append(esc(c.company)).append(CRLF)
            if (c.jobTitle.isNotEmpty()) sb.append("TITLE:").append(esc(c.jobTitle)).append(CRLF)
            if (c.nickname.isNotEmpty()) sb.append("NICKNAME:").append(esc(c.nickname)).append(CRLF)
            for (a in c.addresses) sb.append("ADR:;;").append(esc(a.value)).append(";;;;").append(CRLF)
            for (w in c.websites) sb.append("URL:").append(w.value).append(CRLF)
            c.events.firstOrNull { it.type == 3 }?.let { sb.append("BDAY:").append(it.value).append(CRLF) }
            if (c.notes.isNotEmpty()) sb.append("NOTE:").append(esc(c.notes)).append(CRLF)
            sb.append("END:VCARD").append(CRLF)
        }
        return sb.toString()
    }

    fun parse(text: String): List<ContactForm> {
        val out = ArrayList<ContactForm>()
        val raw = text.replace(13.toChar().toString(), "").split(10.toChar())
        val lines = ArrayList<String>()
        val tab = 9.toChar().toString()
        for (l in raw) {
            if ((l.startsWith(" ") || l.startsWith(tab)) && lines.isNotEmpty()) {
                lines[lines.size - 1] = lines[lines.size - 1] + l.substring(1)
            } else {
                lines.add(l)
            }
        }
        var cur: ContactForm? = null
        var fn = ""
        val phones = ArrayList<Pair<Int, String>>()
        val emails = ArrayList<Pair<Int, String>>()
        for (line in lines) {
            val idx = line.indexOf(':')
            if (idx < 0) continue
            val head = line.substring(0, idx)
            val value = line.substring(idx + 1)
            val name = head.substringBefore(';').uppercase()
            val params = head.substringAfter(';', "").uppercase()
            when (name) {
                "BEGIN" -> {
                    if (value.trim().uppercase() == "VCARD") {
                        cur = ContactForm()
                        fn = ""
                        phones.clear()
                        emails.clear()
                    }
                }
                "END" -> {
                    val f = cur
                    if (f != null) {
                        if (f.given.isBlank() && f.family.isBlank() && fn.isNotBlank()) f.given = fn
                        f.phones = ArrayList(phones)
                        f.emails = ArrayList(emails)
                        out.add(f)
                    }
                    cur = null
                }
                "N" -> {
                    val p = value.split(';')
                    cur?.family = unescape(p.getOrElse(0) { "" })
                    cur?.given = unescape(p.getOrElse(1) { "" })
                }
                "FN" -> fn = unescape(value)
                "TEL" -> {
                    val t = when {
                        params.contains("CELL") -> 2
                        params.contains("HOME") -> 1
                        params.contains("WORK") -> 3
                        else -> 7
                    }
                    if (value.isNotBlank()) phones.add(Pair(t, value.trim()))
                }
                "EMAIL" -> {
                    val t = when {
                        params.contains("HOME") -> 1
                        params.contains("WORK") -> 2
                        else -> 3
                    }
                    if (value.isNotBlank()) emails.add(Pair(t, value.trim()))
                }
                "ORG" -> cur?.company = unescape(value.substringBefore(';'))
                "TITLE" -> cur?.jobTitle = unescape(value)
                "NICKNAME" -> cur?.nickname = unescape(value)
                "NOTE" -> cur?.notes = unescape(value)
                "URL" -> cur?.website = value.trim()
                "BDAY" -> {
                    val v = value.trim()
                    val digits = v.filter { it.isDigit() }
                    cur?.birthday =
                        if (!v.contains('-') && digits.length >= 8)
                            digits.substring(0, 4) + "-" + digits.substring(4, 6) + "-" + digits.substring(6, 8)
                        else v
                }
                "ADR" -> cur?.address = value.split(';').filter { it.isNotBlank() }
                    .joinToString(", ") { unescape(it) }
            }
        }
        return out
    }
}
