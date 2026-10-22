package com.example.fastcontacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import java.util.Date
import java.util.Locale

// One row of the system call log.
data class CallEntry(val number: String, val type: Int, val date: Long, val duration: Long)

// Consecutive calls with the same number and the same type are shown as one row, like Google Phone.
class CallGroup(
    val number: String,
    val type: Int,
    private val cachedName: String?,
    val entries: MutableList<CallEntry>
) {
    var contact: Contact? = null
    var expanded = false
    val name: String?
        get() = contact?.name ?: cachedName?.takeIf { it.isNotBlank() }
}

object CallLogRepository {

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    // Last 9 digits: lets "+8801712345678" and "01712345678" match the same contact.
    fun key(n: String): String = n.filter { it.isDigit() }.takeLast(9)

    fun load(ctx: Context, contacts: List<Contact>): List<CallGroup> {
        if (!hasPermission(ctx)) return emptyList()
        val byKey = HashMap<String, Contact>()
        for (c in contacts) for (p in c.phones) {
            val k = key(p.number)
            if (k.isNotEmpty() && !byKey.containsKey(k)) byKey[k] = c
        }
        val out = ArrayList<CallGroup>()
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE,
                    CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME
                ),
                null, null, CallLog.Calls.DATE + " DESC"
            )?.use { cur ->
                var n = 0
                while (cur.moveToNext() && n < 500) {
                    n++
                    val e = CallEntry(cur.getString(0) ?: "", cur.getInt(1), cur.getLong(2), cur.getLong(3))
                    val k = key(e.number)
                    val last = out.lastOrNull()
                    if (last != null && key(last.number) == k && last.type == e.type) {
                        last.entries.add(e)
                    } else {
                        val g = CallGroup(e.number, e.type, cur.getString(4), mutableListOf(e))
                        g.contact = if (k.isEmpty()) null else byKey[k]
                        out.add(g)
                    }
                }
            }
        } catch (e: SecurityException) {
            // permission revoked while loading: show an empty list
        }
        return out
    }

    // CallLog types: 1 incoming, 2 outgoing, 3 missed, 4 voicemail, 5 rejected, 6 blocked
    fun isMissed(type: Int) = type == 3 || type == 5 || type == 6

    fun typeLabel(type: Int) = when (type) {
        1 -> "Incoming"
        2 -> "Outgoing"
        3 -> "Missed"
        4 -> "Voicemail"
        5 -> "Declined"
        6 -> "Blocked"
        else -> "Call"
    }

    fun typeIcon(type: Int) = when (type) {
        2 -> R.drawable.ic_call_made
        3, 5, 6 -> R.drawable.ic_call_missed
        else -> R.drawable.ic_call_received
    }

    fun duration(s: Long): String = when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${s % 60}s"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }

    private fun dayNum(ms: Long): Long =
        (ms + java.util.TimeZone.getDefault().getOffset(ms)) / 86_400_000L

    // Time today, "Yesterday", weekday this week, otherwise "Oct 12".
    fun shortWhen(ctx: Context, ms: Long): String {
        val diff = dayNum(System.currentTimeMillis()) - dayNum(ms)
        return when {
            diff <= 0L -> DateFormat.getTimeFormat(ctx).format(Date(ms))
            diff == 1L -> "Yesterday"
            diff in 2L..6L -> DateFormat.format("EEE", ms).toString()
            else -> DateFormat.format("MMM d", ms).toString()
        }
    }

    fun fullWhen(ctx: Context, ms: Long): String =
        DateFormat.format("EEE, MMM d", ms).toString() + ", " + DateFormat.getTimeFormat(ctx).format(Date(ms))

    // Pretty number for display only. The dialed number itself is never changed.
    fun pretty(n: String): String {
        if (n.isEmpty()) return "Unknown"
        return try {
            PhoneNumberUtils.formatNumber(n, Locale.getDefault().country) ?: n
        } catch (e: Exception) {
            n
        }
    }

    // Live formatting while typing on the dialpad.
    fun live(raw: String): String {
        if (raw.length < 5 || raw.any { it == '*' || it == '#' }) return raw
        return pretty(raw)
    }
}
