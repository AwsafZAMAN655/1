package com.example.fastcontacts

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email as CdkEmail
import android.provider.ContactsContract.CommonDataKinds.Event as CdkEvent
import android.provider.ContactsContract.CommonDataKinds.GroupMembership as CdkGroup
import android.provider.ContactsContract.CommonDataKinds.Nickname as CdkNick
import android.provider.ContactsContract.CommonDataKinds.Note as CdkNote
import android.provider.ContactsContract.CommonDataKinds.Organization as CdkOrg
import android.provider.ContactsContract.CommonDataKinds.Phone as CdkPhone
import android.provider.ContactsContract.CommonDataKinds.Relation as CdkRel
import android.provider.ContactsContract.CommonDataKinds.StructuredName as CdkName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal as CdkAddr
import android.provider.ContactsContract.CommonDataKinds.Website as CdkWeb
import java.text.SimpleDateFormat
import java.util.Locale

// The Android Contacts Provider is the single source of truth. No private database.
object ContactsRepository {

    fun loadAll(context: Context): List<Contact> = load(context, null)

    fun loadById(context: Context, id: Long): Contact? = load(context, id).firstOrNull()

    fun loadGroups(context: Context): List<ContactGroup> {
        val out = ArrayList<ContactGroup>()
        try {
            val c = context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(
                    ContactsContract.Groups._ID,
                    ContactsContract.Groups.TITLE,
                    ContactsContract.Groups.ACCOUNT_NAME,
                    ContactsContract.Groups.ACCOUNT_TYPE,
                    ContactsContract.Groups.SYSTEM_ID
                ),
                ContactsContract.Groups.DELETED + "=0", null,
                ContactsContract.Groups.TITLE + " ASC"
            )
            c?.use {
                while (it.moveToNext()) {
                    val sys = it.getString(4)
                    val title = it.getString(1) ?: ""
                    if (sys.isNullOrEmpty() && title.isNotBlank()) {
                        out.add(ContactGroup(it.getLong(0), title, it.getString(2), it.getString(3)))
                    }
                }
            }
        } catch (e: SecurityException) {
            // no permission yet
        }
        return out
    }

    fun prettyDate(s: String): String {
        return try {
            if (s.startsWith("--")) {
                val d = SimpleDateFormat("--MM-dd", Locale.US).parse(s)
                SimpleDateFormat("MMMM d", Locale.getDefault()).format(d!!)
            } else {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s)
                SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(d!!)
            }
        } catch (e: Exception) {
            s
        }
    }

    private class B(val id: Long, val name: String, val starred: Boolean, val lookup: String,
                    val thumb: String?, val full: String?) {
        var given = ""
        var family = ""
        var company = ""
        var job = ""
        var dept = ""
        var nick = ""
        var notes = ""
        var orgRows = 0
        var noteRows = 0
        var nickRows = 0
        val phones = ArrayList<Phone>()
        val emails = ArrayList<Email>()
        val addrs = ArrayList<LabeledValue>()
        val webs = ArrayList<LabeledValue>()
        val events = ArrayList<LabeledValue>()
        val rels = ArrayList<LabeledValue>()
        val groups = HashSet<Long>()
        val raws = ArrayList<RawInfo>()

        fun addPhone(p: Phone) {
            val d = p.number.filter { it.isDigit() }
            if (phones.none { x -> x.number.filter { it.isDigit() } == d }) phones.add(p)
        }

        fun addEmail(e: Email) {
            if (emails.none { it.address.equals(e.address, ignoreCase = true) }) emails.add(e)
        }

        fun build(): Contact {
            // firstName comes from StructuredName.GIVEN_NAME. Only when the contact has no
            // structured name at all, the whole display name is used (never split on spaces).
            val first = if (given.isEmpty() && family.isEmpty()) name else given
            return Contact(
                id = id, name = name, firstName = first, lastName = family,
                phones = phones, emails = emails, starred = starred, lookupKey = lookup,
                company = company, jobTitle = job, department = dept, nickname = nick,
                notes = notes, addresses = addrs, websites = webs, events = events,
                relations = rels, groupIds = groups, raws = raws,
                photoThumb = thumb, photoFull = full,
                hasMultiRows = orgRows > 1 || noteRows > 1 || nickRows > 1
            )
        }
    }

    private fun webLabel(type: Int, label: String): String {
        if (label.isNotBlank()) return label
        return when (type) {
            1 -> "Homepage"
            2 -> "Blog"
            3 -> "Profile"
            4 -> "Home"
            5 -> "Work"
            6 -> "FTP"
            else -> "Website"
        }
    }

    private fun load(context: Context, onlyId: Long?): List<Contact> {
        val map = LinkedHashMap<Long, B>()
        val cr = context.contentResolver
        val res = context.resources
        try {
            // 1) contacts (aggregated people)
            val cSel = if (onlyId != null) ContactsContract.Contacts._ID + "=?" else null
            val cArgs = if (onlyId != null) arrayOf(onlyId.toString()) else null
            cr.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                    ContactsContract.Contacts.PHOTO_URI
                ),
                cSel, cArgs, null
            )?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    map[id] = B(
                        id, it.getString(1) ?: "Unknown", it.getInt(2) == 1,
                        it.getString(3) ?: "", it.getString(4), it.getString(5)
                    )
                }
            }

            // 2) raw contacts (account / source information)
            val rSel = ContactsContract.RawContacts.DELETED + "=0" +
                (if (onlyId != null) " AND " + ContactsContract.RawContacts.CONTACT_ID + "=?" else "")
            cr.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts._ID,
                    ContactsContract.RawContacts.CONTACT_ID,
                    ContactsContract.RawContacts.ACCOUNT_NAME,
                    ContactsContract.RawContacts.ACCOUNT_TYPE
                ),
                rSel, cArgs, null
            )?.use {
                while (it.moveToNext()) {
                    map[it.getLong(1)]?.raws?.add(RawInfo(it.getLong(0), it.getString(2), it.getString(3)))
                }
            }

            // 3) structured data rows, one query for everything
            val mimes = arrayOf(
                CdkName.CONTENT_ITEM_TYPE, CdkPhone.CONTENT_ITEM_TYPE, CdkEmail.CONTENT_ITEM_TYPE,
                CdkOrg.CONTENT_ITEM_TYPE, CdkAddr.CONTENT_ITEM_TYPE, CdkWeb.CONTENT_ITEM_TYPE,
                CdkNote.CONTENT_ITEM_TYPE, CdkEvent.CONTENT_ITEM_TYPE, CdkNick.CONTENT_ITEM_TYPE,
                CdkRel.CONTENT_ITEM_TYPE, CdkGroup.CONTENT_ITEM_TYPE
            )
            var sel = ContactsContract.Data.MIMETYPE + " IN (" + mimes.joinToString(",") { "?" } + ")"
            val args = ArrayList<String>(mimes.toList())
            if (onlyId != null) {
                sel += " AND " + ContactsContract.Data.CONTACT_ID + "=?"
                args.add(onlyId.toString())
            }
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA2,
                    ContactsContract.Data.DATA3,
                    ContactsContract.Data.DATA4,
                    ContactsContract.Data.DATA5
                ),
                sel, args.toTypedArray(), null
            )?.use {
                while (it.moveToNext()) {
                    val b = map[it.getLong(0)] ?: continue
                    val mime = it.getString(1) ?: continue
                    val d1 = it.getString(2) ?: ""
                    val d2 = it.getString(3) ?: ""
                    val d3 = it.getString(4) ?: ""
                    val d4 = it.getString(5) ?: ""
                    val d5 = it.getString(6) ?: ""
                    val t = it.getInt(3)
                    when (mime) {
                        CdkName.CONTENT_ITEM_TYPE -> {
                            if (b.given.isEmpty() && b.family.isEmpty()) {
                                b.given = d2      // GIVEN_NAME
                                b.family = d3     // FAMILY_NAME
                            }
                        }
                        CdkPhone.CONTENT_ITEM_TYPE ->
                            if (d1.isNotBlank()) b.addPhone(
                                Phone(d1, CdkPhone.getTypeLabel(res, t, d3).toString(), t)
                            )
                        CdkEmail.CONTENT_ITEM_TYPE ->
                            if (d1.isNotBlank()) b.addEmail(
                                Email(d1, CdkEmail.getTypeLabel(res, t, d3).toString(), t)
                            )
                        CdkOrg.CONTENT_ITEM_TYPE -> {
                            b.orgRows++
                            if (b.company.isEmpty()) b.company = d1
                            if (b.job.isEmpty()) b.job = d4
                            if (b.dept.isEmpty()) b.dept = d5
                        }
                        CdkAddr.CONTENT_ITEM_TYPE ->
                            if (d1.isNotBlank()) b.addrs.add(
                                LabeledValue(d1, CdkAddr.getTypeLabel(res, t, d3).toString(), t)
                            )
                        CdkWeb.CONTENT_ITEM_TYPE ->
                            if (d1.isNotBlank()) b.webs.add(LabeledValue(d1, webLabel(t, d3), t))
                        CdkNote.CONTENT_ITEM_TYPE -> {
                            b.noteRows++
                            if (b.notes.isEmpty()) b.notes = d1
                        }
                        CdkEvent.CONTENT_ITEM_TYPE ->
                            if (d1.isNotBlank()) b.events.add(
                                LabeledValue(d1, CdkEvent.getTypeLabel(res, t, d3).toString(), t)
                            )
                        CdkNick.CONTENT_ITEM_TYPE -> {
                            b.nickRows++
                            if (b.nick.isEmpty()) b.nick = d1
                        }
                        CdkRel.CONTENT_ITEM_TYPE ->
                            if (d1.isNotBlank()) b.rels.add(
                                LabeledValue(d1, CdkRel.getTypeLabel(res, t, d3).toString(), t)
                            )
                        CdkGroup.CONTENT_ITEM_TYPE -> d1.toLongOrNull()?.let { g -> b.groups.add(g) }
                    }
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return map.values.map { it.build() }
    }
}
