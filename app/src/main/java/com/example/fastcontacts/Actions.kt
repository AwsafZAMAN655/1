package com.example.fastcontacts

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

data class SocialApp(
    val label: String,
    val icon: Int,
    val pkg: String,
    val link: ((String) -> String)? = null
)

// Brand icons are PLACEHOLDER drawables (ic_<name>_placeholder). Replace the files, not the code.
object Social {
    val whatsapp = SocialApp("WhatsApp", R.drawable.ic_whatsapp_placeholder, "com.whatsapp") { n -> "https://wa.me/" + n }
    val meet = SocialApp("Meet", R.drawable.ic_meet_placeholder, "com.google.android.apps.tachyon")

    val apps: List<SocialApp> = listOf(
        whatsapp,
        SocialApp("WA Business", R.drawable.ic_whatsapp_business_placeholder, "com.whatsapp.w4b") { n -> "https://wa.me/" + n },
        SocialApp("imo", R.drawable.ic_imo_placeholder, "com.imo.android.imoim"),
        SocialApp("Telegram", R.drawable.ic_telegram_placeholder, "org.telegram.messenger") { n -> "https://t.me/+" + n },
        SocialApp("Viber", R.drawable.ic_viber_placeholder, "com.viber.voip") { n -> "viber://chat?number=%2B" + n },
        SocialApp("Signal", R.drawable.ic_signal_placeholder, "org.thoughtcrime.securesms") { n -> "sgnl://signal.me/#p/+" + n },
        SocialApp("Messenger", R.drawable.ic_messenger_placeholder, "com.facebook.orca"),
        SocialApp("Facebook", R.drawable.ic_facebook_placeholder, "com.facebook.katana"),
        SocialApp("Instagram", R.drawable.ic_instagram_placeholder, "com.instagram.android"),
        SocialApp("Skype", R.drawable.ic_skype_placeholder, "com.skype.raider") { n -> "skype:" + n + "?chat" },
        SocialApp("Snapchat", R.drawable.ic_snapchat_placeholder, "com.snapchat.android"),
        meet
    )
}

object Actions {

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    fun safeStart(ctx: Context, intent: Intent) {
        try {
            if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            toast(ctx, "No app found to do this")
        }
    }

    private fun startRaw(ctx: Context, intent: Intent) {
        if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    fun call(ctx: Context, number: String) {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        safeStart(ctx, Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
    }

    fun sms(ctx: Context, number: String) {
        safeStart(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))))
    }

    fun email(ctx: Context, address: String) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + address))
        safeStart(ctx, Intent.createChooser(i, "Send email"))
    }

    fun directions(ctx: Context, address: String) {
        safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))))
    }

    fun openUrl(ctx: Context, url: String) {
        val u = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://" + url
        safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    }

    // International digits for chat apps, using the default country code from Settings.
    fun international(ctx: Context, number: String): String {
        val trimmed = number.trim()
        val d = trimmed.filter { it.isDigit() }
        if (trimmed.startsWith("+")) return d
        if (d.startsWith("00")) return d.substring(2)
        val cc = Prefs.countryCode(ctx).filter { it.isDigit() }
        if (cc.isEmpty()) return d
        if (d.startsWith("0")) return cc + d.substring(1)
        if (d.startsWith(cc)) return d
        return cc + d
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("text", text))
    }

    fun isInstalled(ctx: Context, app: SocialApp): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(app.pkg) != null

    fun openSocial(ctx: Context, app: SocialApp, number: String) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(app.pkg)
        if (launch == null) {
            toast(ctx, app.label + " is not installed")
            try {
                startRaw(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + app.pkg)))
            } catch (e: Exception) {
                safeStart(ctx, Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + app.pkg)))
            }
            return
        }
        val link = app.link
        if (link != null) {
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(link(international(ctx, number))))
                i.setPackage(app.pkg)
                startRaw(ctx, i)
                return
            } catch (e: Exception) {
                // fall through: just open the app
            }
        }
        copy(ctx, number)
        toast(ctx, "Number copied - paste it in " + app.label + " search")
        safeStart(ctx, launch)
    }

    fun videoCall(ctx: Context, number: String) {
        if (!isInstalled(ctx, Social.meet)) {
            openSocial(ctx, Social.meet, number)
            return
        }
        try {
            val i = Intent("com.google.android.apps.tachyon.action.CALL")
            i.setPackage(Social.meet.pkg)
            i.data = Uri.parse("tel:" + Uri.encode(number))
            startRaw(ctx, i)
        } catch (e: Exception) {
            openSocial(ctx, Social.meet, number)
        }
    }

    fun withNumber(ctx: Context, c: Contact, block: (String) -> Unit) {
        when (c.phones.size) {
            0 -> toast(ctx, "No phone number")
            1 -> block(c.phones[0].number)
            else -> {
                val labels = c.phones.map { it.number + "  (" + it.type + ")" }.toTypedArray()
                AlertDialog.Builder(ctx).setTitle(c.name)
                    .setItems(labels) { _, i -> block(c.phones[i].number) }.show()
            }
        }
    }

    fun withEmail(ctx: Context, c: Contact, block: (String) -> Unit) {
        when (c.emails.size) {
            0 -> toast(ctx, "No email address")
            1 -> block(c.emails[0].address)
            else -> {
                val labels = c.emails.map { it.address + "  (" + it.type + ")" }.toTypedArray()
                AlertDialog.Builder(ctx).setTitle(c.name)
                    .setItems(labels) { _, i -> block(c.emails[i].address) }.show()
            }
        }
    }

    fun copyNumber(ctx: Context, c: Contact) {
        withNumber(ctx, c) { n ->
            copy(ctx, n)
            toast(ctx, "Number copied")
        }
    }

    fun toggleStar(ctx: Context, c: Contact) = ContactWriter.toggleStar(ctx, c)

    fun share(ctx: Context, c: Contact) {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/x-vcard"
        i.putExtra(Intent.EXTRA_STREAM, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, c.lookupKey))
        val lines = ArrayList<String>()
        lines.add(c.name)
        for (p in c.phones) lines.add(p.number)
        for (e in c.emails) lines.add(e.address)
        i.putExtra(Intent.EXTRA_TEXT, lines.joinToString(System.lineSeparator()))
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        safeStart(ctx, Intent.createChooser(i, "Share contact"))
    }

    // Our own editor is used when it can edit the contact without losing any data.
    fun canEditInApp(c: Contact): Boolean =
        c.raws.size == 1 && !c.hasMultiRows &&
            c.addresses.size <= 1 && c.websites.size <= 1 &&
            c.events.all { it.type == 3 || it.type == 1 } &&
            c.events.count { it.type == 3 } <= 1 && c.events.count { it.type == 1 } <= 1

    fun edit(ctx: Context, c: Contact) {
        if (canEditInApp(c)) {
            val i = Intent(ctx, EditActivity::class.java)
            i.putExtra("contact_id", c.id)
            safeStart(ctx, i)
        } else {
            systemEdit(ctx, c)
        }
    }

    fun systemEdit(ctx: Context, c: Contact) {
        val uri = ContactsContract.Contacts.getLookupUri(c.id, c.lookupKey)
        val i = Intent(Intent.ACTION_EDIT)
        i.setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
        i.putExtra("finishActivityOnSaveCompleted", true)
        safeStart(ctx, i)
    }

    fun create(ctx: Context, phone: String? = null) {
        val i = Intent(ctx, EditActivity::class.java)
        if (phone != null) i.putExtra("phone", phone)
        safeStart(ctx, i)
    }

    fun confirmDelete(ctx: Context, c: Contact, onDone: () -> Unit) {
        AlertDialog.Builder(ctx)
            .setTitle("Delete contact")
            .setMessage("Delete " + c.name + "? This removes it from your phone and from any account it syncs with.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (ContactWriter.delete(ctx, c)) {
                    toast(ctx, "Deleted")
                    onDone()
                } else {
                    toast(ctx, "Could not delete contact")
                }
            }
            .show()
    }

    fun sourceLabel(c: Contact): String {
        val a = c.raws.firstOrNull { it.accountName != null }
        return when {
            a == null -> "Saved on this device"
            a.accountType == "com.google" -> "Saved to Google (" + a.accountName + ")"
            else -> "Saved to " + a.accountName
        }
    }
}
