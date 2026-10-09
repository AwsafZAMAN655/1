package com.example.fastcontacts

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email as CdkEmail
import android.provider.ContactsContract.CommonDataKinds.Event as CdkEvent
import android.provider.ContactsContract.CommonDataKinds.GroupMembership as CdkGroup
import android.provider.ContactsContract.CommonDataKinds.Nickname as CdkNick
import android.provider.ContactsContract.CommonDataKinds.Note as CdkNote
import android.provider.ContactsContract.CommonDataKinds.Organization as CdkOrg
import android.provider.ContactsContract.CommonDataKinds.Phone as CdkPhone
import android.provider.ContactsContract.CommonDataKinds.Photo as CdkPhoto
import android.provider.ContactsContract.CommonDataKinds.StructuredName as CdkName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal as CdkAddr
import android.provider.ContactsContract.CommonDataKinds.Website as CdkWeb

class ContactForm(
    var given: String = "",
    var family: String = "",
    var company: String = "",
    var jobTitle: String = "",
    var nickname: String = "",
    var notes: String = "",
    var address: String = "",
    var website: String = "",
    var birthday: String = "",
    var anniversary: String = "",
    var phones: List<Pair<Int, String>> = emptyList(),
    var emails: List<Pair<Int, String>> = emptyList(),
    var photo: ByteArray? = null
)

// Every change goes straight into Android's Contacts Provider.
// Google sync (if the contact lives in a Google account) is done by Android itself.
object ContactWriter {

    fun defaultAccount(ctx: Context): Pair<String, String>? {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val a = ContactsContract.Settings.getDefaultAccount(ctx.contentResolver)
                if (a != null) return Pair(a.name, a.type)
            } catch (e: Exception) {
                // ignore, fall back to device-only contact
            }
        }
        return null
    }

    private fun firstAccount(ctx: Context): Pair<String, String>? {
        try {
            ctx.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
                ContactsContract.RawContacts.DELETED + "=0", null, null
            )?.use {
                var fallback: Pair<String, String>? = null
                while (it.moveToNext()) {
                    val n = it.getString(0)
                    val t = it.getString(1)
                    if (n != null && t != null) {
                        if (t == "com.google") return Pair(n, t)
                        if (fallback == null) fallback = Pair(n, t)
                    }
                }
                return fallback
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    private fun addData(ops: ArrayList<ContentProviderOperation>, f: ContactForm, rawId: Long?) {
        fun ins(mime: String): ContentProviderOperation.Builder {
            val b = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            if (rawId != null) b.withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
            else b.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            b.withValue(ContactsContract.Data.MIMETYPE, mime)
            return b
        }
        if (f.given.isNotBlank() || f.family.isNotBlank()) {
            ops.add(
                ins(CdkName.CONTENT_ITEM_TYPE)
                    .withValue(CdkName.GIVEN_NAME, f.given.trim())
                    .withValue(CdkName.FAMILY_NAME, f.family.trim()).build()
            )
        }
        for (p in f.phones) {
            if (p.second.isNotBlank()) {
                ops.add(
                    ins(CdkPhone.CONTENT_ITEM_TYPE)
                        .withValue(CdkPhone.NUMBER, p.second.trim())
                        .withValue(CdkPhone.TYPE, p.first).build()
                )
            }
        }
        for (e in f.emails) {
            if (e.second.isNotBlank()) {
                ops.add(
                    ins(CdkEmail.CONTENT_ITEM_TYPE)
                        .withValue(CdkEmail.ADDRESS, e.second.trim())
                        .withValue(CdkEmail.TYPE, e.first).build()
                )
            }
        }
        if (f.company.isNotBlank() || f.jobTitle.isNotBlank()) {
            ops.add(
                ins(CdkOrg.CONTENT_ITEM_TYPE)
                    .withValue(CdkOrg.COMPANY, f.company.trim())
                    .withValue(CdkOrg.TITLE, f.jobTitle.trim())
                    .withValue(CdkOrg.TYPE, CdkOrg.TYPE_WORK).build()
            )
        }
        if (f.address.isNotBlank()) {
            ops.add(
                ins(CdkAddr.CONTENT_ITEM_TYPE)
                    .withValue(CdkAddr.FORMATTED_ADDRESS, f.address.trim())
                    .withValue(CdkAddr.TYPE, CdkAddr.TYPE_HOME).build()
            )
        }
        if (f.website.isNotBlank()) {
            ops.add(
                ins(CdkWeb.CONTENT_ITEM_TYPE)
                    .withValue(CdkWeb.URL, f.website.trim())
                    .withValue(CdkWeb.TYPE, CdkWeb.TYPE_HOMEPAGE).build()
            )
        }
        if (f.birthday.isNotBlank()) {
            ops.add(
                ins(CdkEvent.CONTENT_ITEM_TYPE)
                    .withValue(CdkEvent.START_DATE, f.birthday.trim())
                    .withValue(CdkEvent.TYPE, CdkEvent.TYPE_BIRTHDAY).build()
            )
        }
        if (f.anniversary.isNotBlank()) {
            ops.add(
                ins(CdkEvent.CONTENT_ITEM_TYPE)
                    .withValue(CdkEvent.START_DATE, f.anniversary.trim())
                    .withValue(CdkEvent.TYPE, CdkEvent.TYPE_ANNIVERSARY).build()
            )
        }
        if (f.notes.isNotBlank()) {
            ops.add(ins(CdkNote.CONTENT_ITEM_TYPE).withValue(CdkNote.NOTE, f.notes.trim()).build())
        }
        if (f.nickname.isNotBlank()) {
            ops.add(
                ins(CdkNick.CONTENT_ITEM_TYPE)
                    .withValue(CdkNick.NAME, f.nickname.trim())
                    .withValue(CdkNick.TYPE, CdkNick.TYPE_DEFAULT).build()
            )
        }
        val photo = f.photo
        if (photo != null) {
            ops.add(ins(CdkPhoto.CONTENT_ITEM_TYPE).withValue(CdkPhoto.PHOTO, photo).build())
        }
    }

    fun insert(ctx: Context, f: ContactForm): Boolean {
        val acct = defaultAccount(ctx) ?: firstAccount(ctx)
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, acct?.second)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, acct?.first).build()
        )
        addData(ops, f, null)
        return try {
            ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun update(ctx: Context, rawId: Long, f: ContactForm): Boolean {
        val ops = ArrayList<ContentProviderOperation>()
        val mimes = listOf(
            CdkName.CONTENT_ITEM_TYPE, CdkPhone.CONTENT_ITEM_TYPE, CdkEmail.CONTENT_ITEM_TYPE,
            CdkOrg.CONTENT_ITEM_TYPE, CdkAddr.CONTENT_ITEM_TYPE, CdkWeb.CONTENT_ITEM_TYPE,
            CdkNote.CONTENT_ITEM_TYPE, CdkNick.CONTENT_ITEM_TYPE
        )
        val sel = ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE +
            " IN (" + mimes.joinToString(",") { "?" } + ")"
        val args = ArrayList<String>()
        args.add(rawId.toString())
        args.addAll(mimes)
        ops.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(sel, args.toTypedArray()).build()
        )
        ops.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE +
                        "=? AND " + ContactsContract.Data.DATA2 + " IN (1,3)",
                    arrayOf(rawId.toString(), CdkEvent.CONTENT_ITEM_TYPE)
                ).build()
        )
        if (f.photo != null) {
            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                        arrayOf(rawId.toString(), CdkPhoto.CONTENT_ITEM_TYPE)
                    ).build()
            )
        }
        addData(ops, f, rawId)
        return try {
            ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun toggleStar(ctx: Context, c: Contact) {
        val v = ContentValues()
        v.put(ContactsContract.Contacts.STARRED, if (c.starred) 0 else 1)
        try {
            ctx.contentResolver.update(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.id), v, null, null
            )
        } catch (e: Exception) {
            Actions.toast(ctx, "Could not update favorite")
        }
    }

    fun delete(ctx: Context, c: Contact): Boolean = try {
        ctx.contentResolver.delete(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.id), null, null
        )
        true
    } catch (e: Exception) {
        false
    }

    // ---- labels (Android contact groups) ----

    fun createGroup(ctx: Context, title: String): Boolean {
        val acct = defaultAccount(ctx) ?: firstAccount(ctx)
        val v = ContentValues()
        v.put(ContactsContract.Groups.TITLE, title)
        v.put(ContactsContract.Groups.GROUP_VISIBLE, 1)
        if (acct != null) {
            v.put(ContactsContract.Groups.ACCOUNT_NAME, acct.first)
            v.put(ContactsContract.Groups.ACCOUNT_TYPE, acct.second)
        }
        return try {
            ctx.contentResolver.insert(ContactsContract.Groups.CONTENT_URI, v) != null
        } catch (e: Exception) {
            false
        }
    }

    fun renameGroups(ctx: Context, groups: List<ContactGroup>, newTitle: String): Boolean {
        return try {
            for (g in groups) {
                val v = ContentValues()
                v.put(ContactsContract.Groups.TITLE, newTitle)
                ctx.contentResolver.update(
                    ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI, g.id), v, null, null
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteGroups(ctx: Context, groups: List<ContactGroup>): Boolean {
        return try {
            for (g in groups) {
                ctx.contentResolver.delete(
                    ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI, g.id), null, null
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setMembership(ctx: Context, c: Contact, groups: List<ContactGroup>, enable: Boolean): Boolean {
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            if (enable) {
                var any = false
                for (g in groups) {
                    if (g.id in c.groupIds) {
                        any = true
                        continue
                    }
                    val raw = c.raws.firstOrNull {
                        it.accountName == g.accountName && it.accountType == g.accountType
                    } ?: continue
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, raw.id)
                            .withValue(ContactsContract.Data.MIMETYPE, CdkGroup.CONTENT_ITEM_TYPE)
                            .withValue(CdkGroup.GROUP_ROW_ID, g.id).build()
                    )
                    any = true
                }
                if (!any) return false
            } else {
                for (g in groups) {
                    for (raw in c.raws) {
                        ops.add(
                            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                                .withSelection(
                                    ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
                                        ContactsContract.Data.MIMETYPE + "=? AND " +
                                        ContactsContract.Data.DATA1 + "=?",
                                    arrayOf(raw.id.toString(), CdkGroup.CONTENT_ITEM_TYPE, g.id.toString())
                                ).build()
                        )
                    }
                }
            }
            if (ops.isNotEmpty()) ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- duplicates ----

    fun findDuplicates(contacts: List<Contact>): List<Pair<String, List<Contact>>> {
        val out = ArrayList<Pair<String, List<Contact>>>()
        val seen = HashSet<Set<Long>>()
        val byName = LinkedHashMap<String, MutableList<Contact>>()
        for (c in contacts) {
            val k = c.name.trim().lowercase()
            if (k.isNotEmpty()) byName.getOrPut(k) { mutableListOf() }.add(c)
        }
        for ((_, v) in byName) {
            if (v.size > 1 && seen.add(v.map { it.id }.toSet())) out.add(Pair(v.first().name, v))
        }
        val byPhone = LinkedHashMap<String, MutableList<Contact>>()
        for (c in contacts) {
            for (p in c.phones) {
                val d = p.number.filter { it.isDigit() }
                if (d.length >= 7) {
                    val l = byPhone.getOrPut(d.takeLast(9)) { mutableListOf() }
                    if (l.none { it.id == c.id }) l.add(c)
                }
            }
        }
        for ((k, v) in byPhone) {
            if (v.size > 1 && seen.add(v.map { it.id }.toSet())) {
                out.add(Pair("Shared number ending " + k.takeLast(4), v))
            }
        }
        return out
    }

    // Uses Android's own aggregation: the contacts become one person in the provider.
    fun merge(ctx: Context, list: List<Contact>): Boolean {
        val raws = list.flatMap { it.raws }.map { it.id }.distinct()
        if (raws.size < 2) return false
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            for (i in 1 until raws.size) {
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                        .withValue(ContactsContract.AggregationExceptions.TYPE,
                            ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, raws[0])
                        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, raws[i]).build()
                )
            }
            ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            false
        }
    }
}
