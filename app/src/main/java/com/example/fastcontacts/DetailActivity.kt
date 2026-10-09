package com.example.fastcontacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class DetailActivity : AppCompatActivity() {

    private var contactId = -1L
    private var contact: Contact? = null
    private var groups: List<ContactGroup> = emptyList()
    private lateinit var content: LinearLayout
    private lateinit var btnFav: ImageButton
    private var moreOpen = false

    // WhatsApp UI Elements
    private var whatsappSection: LinearLayout? = null
    private var whatsappHeader: LinearLayout? = null
    private var whatsappActionsContainer: LinearLayout? = null
    private var whatsappExpandIcon: ImageView? = null
    private var isWhatsappExpanded = false

    private class Pill(val label: String, val icon: Int, val tinted: Boolean, val run: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        Ui.systemBars(this)
        contactId = intent.getLongExtra("contact_id", -1L)
        content = findViewById(R.id.detailContent)
        btnFav = findViewById(R.id.btnFav)

        // Find static WhatsApp views from activity_detail.xml
        whatsappSection = findViewById(R.id.whatsappSection)
        whatsappHeader = findViewById(R.id.whatsappHeader)
        whatsappActionsContainer = findViewById(R.id.whatsappActionsContainer)
        whatsappExpandIcon = findViewById(R.id.whatsappExpandIcon)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnFav.setOnClickListener {
            contact?.let { c ->
                Ui.pop(btnFav)
                Actions.toggleStar(this, c)
                load()
            }
        }
        findViewById<ImageButton>(R.id.btnEdit).setOnClickListener { contact?.let { Actions.edit(this, it) } }
        findViewById<ImageButton>(R.id.btnMore).setOnClickListener { contact?.let { showMore(it) } }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in_soft, R.anim.slide_down_out)
    }

    private fun dp(v: Int): Int = Ui.dp(this, v)

    private fun load() {
        Thread {
            val c = ContactsRepository.loadById(this, contactId)
            val g = ContactsRepository.loadGroups(this)
            runOnUiThread {
                if (c == null) {
                    finish()
                } else {
                    contact = c
                    groups = g
                    bind(c)
                }
            }
        }.start()
    }

    private fun bind(c: Contact) {
        content.removeAllViews()

        btnFav.setImageResource(if (c.starred) R.drawable.ic_star else R.drawable.ic_star_border)
        btnFav.imageTintList = Ui.tint(this, if (c.starred) R.color.app_star else R.color.app_text_secondary)
        btnFav.contentDescription = if (c.starred) "Remove from favorites" else "Add to favorites"

        // ----- header -----
        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.gravity = Gravity.CENTER_HORIZONTAL
        header.setPadding(dp(16), dp(4), dp(16), dp(16))
        val av = AvatarView(this)
        av.layoutParams = LinearLayout.LayoutParams(dp(104), dp(104))
        av.bind(c, true)
        header.addView(av)
        val name = TextView(this)
        name.text = c.name
        name.textSize = 26f
        name.gravity = Gravity.CENTER
        name.setTextColor(Ui.color(this, R.color.app_text))
        name.setPadding(0, dp(12), 0, 0)
        header.addView(name)
        val sub = when {
            c.jobTitle.isNotEmpty() && c.company.isNotEmpty() -> c.jobTitle + " at " + c.company
            c.company.isNotEmpty() -> c.company
            c.jobTitle.isNotEmpty() -> c.jobTitle
            else -> ""
        }
        if (sub.isNotEmpty()) {
            val s = TextView(this)
            s.text = sub
            s.textSize = 15f
            s.gravity = Gravity.CENTER
            s.setTextColor(Ui.color(this, R.color.app_text_secondary))
            header.addView(s)
        }
        content.addView(header)

        // ----- quick actions -----
        val pills = ArrayList<Pill>()
        if (c.phones.isNotEmpty()) {
            pills.add(Pill("Call", R.drawable.ic_call, true) {
                Actions.withNumber(this, c) { n -> Actions.call(this, n) }
            })
            pills.add(Pill("Message", R.drawable.ic_sms, true) {
                Actions.withNumber(this, c) { n -> Actions.sms(this, n) }
            })
            if (Actions.isInstalled(this, Social.whatsapp)) {
                pills.add(Pill("WhatsApp", R.drawable.ic_whatsapp_placeholder, false) {
                    Actions.withNumber(this, c) { n -> Actions.openSocial(this, Social.whatsapp, n) }
                })
            }
            if (Actions.isInstalled(this, Social.meet)) {
                pills.add(Pill("Video", R.drawable.ic_video, true) {
                    Actions.withNumber(this, c) { n -> Actions.videoCall(this, n) }
                })
            }
        }
        if (c.emails.isNotEmpty()) {
            pills.add(Pill("Email", R.drawable.ic_email, true) {
                Actions.withEmail(this, c) { e -> Actions.email(this, e) }
            })
        }
        if (c.addresses.isNotEmpty()) {
            pills.add(Pill("Directions", R.drawable.ic_location, true) {
                Actions.directions(this, c.addresses[0].value)
            })
        }
        pills.add(Pill("Share", R.drawable.ic_share, true) { Actions.share(this, c) })

        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        val pillRow = LinearLayout(this)
        pillRow.orientation = LinearLayout.HORIZONTAL
        pillRow.setPadding(dp(12), 0, dp(12), dp(8))
        for (p in pills) {
            val v = LayoutInflater.from(this).inflate(R.layout.view_action_pill, pillRow, false)
            val icon = v.findViewById<ImageView>(R.id.pillIcon)
            icon.setImageResource(p.icon)
            if (p.tinted) icon.imageTintList = Ui.tint(this, R.color.app_on_accent_container)
            v.findViewById<TextView>(R.id.pillLabel).text = p.label
            v.setOnClickListener { p.run() }
            val lp = LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), 0, dp(4), 0)
            pillRow.addView(v, lp)
        }
        scroll.addView(pillRow)
        content.addView(scroll)

        // ----- phone numbers -----
        if (c.phones.isNotEmpty()) {
            val card = Ui.card(this, content, "Phone")
            val wa = Actions.isInstalled(this, Social.whatsapp)
            for ((i, p) in c.phones.withIndex()) {
                val trailing = ArrayList<Ui.Trail>()
                trailing.add(Ui.Trail(R.drawable.ic_sms, "Message " + p.number) { Actions.sms(this, p.number) })
                if (wa) {
                    trailing.add(Ui.Trail(R.drawable.ic_whatsapp_placeholder, "WhatsApp " + p.number, false) {
                        Actions.openSocial(this, Social.whatsapp, p.number)
                    })
                }
                Ui.row(
                    this, card, if (i == 0) R.drawable.ic_call else null, p.number, p.type,
                    onClick = { Actions.call(this, p.number) },
                    onLong = {
                        Actions.copy(this, p.number)
                        Actions.toast(this, "Number copied")
                    },
                    trailing = trailing
                )
            }
        }

        // ----- WhatsApp expandable section integration -----
        if (c.phones.isNotEmpty()) {
            setupWhatsAppSection(c.phones[0].number)
        }

        // ----- email -----
        if (c.emails.isNotEmpty()) {
            val card = Ui.card(this, content, "Email")
            for ((i, e) in c.emails.withIndex()) {
                Ui.row(
                    this, card, if (i == 0) R.drawable.ic_email else null, e.address, e.type,
                    onClick = { Actions.email(this, e.address) },
                    onLong = {
                        Actions.copy(this, e.address)
                        Actions.toast(this, "Email copied")
                    }
                )
            }
        }

        // ----- chat and social apps -----
        if (c.phones.isNotEmpty()) buildSocialGrid(c)

        // ----- labels -----
        run {
            val card = Ui.card(this, content, "Labels")
            val names = groups.filter { it.id in c.groupIds }.map { it.title }.distinct().sorted()
            Ui.row(
                this, card, R.drawable.ic_label,
                if (names.isEmpty()) "Add to a label" else names.joinToString(", "),
                if (names.isEmpty()) "Group people like Family, Friends or Work" else "Tap to change",
                onClick = { manageLabels(c) }
            )
        }

        // ----- more details -----
        val hasMore = c.department.isNotEmpty() || c.nickname.isNotEmpty() || c.addresses.isNotEmpty() ||
            c.websites.isNotEmpty() || c.events.isNotEmpty() || c.relations.isNotEmpty() ||
            c.notes.isNotEmpty() || (c.company.isNotEmpty() && c.jobTitle.isNotEmpty())
        if (hasMore) {
            val card = Ui.card(this, content, null)
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.visibility = if (moreOpen) View.VISIBLE else View.GONE

            val toggle = Ui.row(this, card, R.drawable.ic_expand, "More details", null)
            val chevron = (toggle as LinearLayout).getChildAt(0)
            chevron.rotation = if (moreOpen) 180f else 0f
            toggle.setOnClickListener {
                moreOpen = !moreOpen
                TransitionManager.beginDelayedTransition(content)
                box.visibility = if (moreOpen) View.VISIBLE else View.GONE
                chevron.animate().rotation(if (moreOpen) 180f else 0f).setDuration(180).start()
            }
            card.addView(box)

            if (c.company.isNotEmpty() || c.jobTitle.isNotEmpty() || c.department.isNotEmpty()) {
                val primary = if (c.company.isNotEmpty()) c.company else c.jobTitle
                val secondary = listOf(c.jobTitle, c.department)
                    .filter { it.isNotEmpty() && it != primary }.joinToString(" - ")
                Ui.row(this, box, R.drawable.ic_business, primary, if (secondary.isEmpty()) "Company" else secondary)
            }
            if (c.nickname.isNotEmpty()) Ui.row(this, box, R.drawable.ic_person, c.nickname, "Nickname")
            for (a in c.addresses) {
                Ui.row(
                    this, box, R.drawable.ic_location, a.value, a.label,
                    onClick = { Actions.directions(this, a.value) }
                )
            }
            for (w in c.websites) {
                Ui.row(this, box, R.drawable.ic_website, w.value, w.label, onClick = { Actions.openUrl(this, w.value) })
            }
            for (ev in c.events) {
                Ui.row(this, box, R.drawable.ic_event, ContactsRepository.prettyDate(ev.value), ev.label)
            }
            for (r in c.relations) Ui.row(this, box, R.drawable.ic_people, r.value, r.label)
            if (c.notes.isNotEmpty()) Ui.row(this, box, R.drawable.ic_notes, c.notes, "Notes")
        }

        // ----- source -----
        val src = Ui.card(this, content, null)
        Ui.row(this, src, R.drawable.ic_cloud, Actions.sourceLabel(c), null)
    }

    private fun setupWhatsAppSection(rawNumber: String) {
        val cleanNumber = rawNumber.replace(Regex("[^0-9]"), "")
        
        // Re-inflate or build the section dynamically inside content
        val sectionView = LayoutInflater.from(this).inflate(R.layout.activity_detail, content, false)
        val waCard = sectionView.findViewById<LinearLayout>(R.id.whatsappSection)
        
        if (waCard != null) {
            (waCard.parent as? ViewGroup)?.removeView(waCard)
            
            val header = waCard.findViewById<LinearLayout>(R.id.whatsappHeader)
            val container = waCard.findViewById<LinearLayout>(R.id.whatsappActionsContainer)
            val icon = waCard.findViewById<ImageView>(R.id.whatsappExpandIcon)

            header?.setOnClickListener {
                isWhatsappExpanded = !isWhatsappExpanded
                TransitionManager.beginDelayedTransition(content)
                container?.visibility = if (isWhatsappExpanded) View.VISIBLE else View.GONE
                icon?.animate()?.rotation(if (isWhatsappExpanded) 180f else 0f)?.setDuration(180)?.start()
            }

            waCard.findViewById<Button>(R.id.btnWaMessage)?.setOnClickListener {
                openWhatsApp(cleanNumber, "send")
            }
            waCard.findViewById<Button>(R.id.btnWaCall)?.setOnClickListener {
                openWhatsApp(cleanNumber, "call")
            }
            waCard.findViewById<Button>(R.id.btnWaVideo)?.setOnClickListener {
                openWhatsApp(cleanNumber, "videocall")
            }

            content.addView(waCard)
        }
    }

    private fun openWhatsApp(number: String, actionType: String) {
        try {
            // WhatsApp expects the full international number without +, spaces, or punctuation.
            val cleanNumber = number.replace(Regex("[^0-9]"), "")
            if (cleanNumber.isBlank()) {
                Toast.makeText(
                    this,
                    "This contact does not have a valid phone number.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                when (actionType) {
                    "send" -> {
                        data = Uri.parse(
                            "https://api.whatsapp.com/send?phone=$cleanNumber"
                        )
                    }

                    "call" -> {
                        data = Uri.parse(
                            "whatsapp://call?phone=$cleanNumber"
                        )
                    }

                    "videocall" -> {
                        data = Uri.parse(
                            "whatsapp://call?phone=$cleanNumber&video=true"
                        )
                    }

                    else -> {
                        Toast.makeText(
                            this@DetailActivity,
                            "Invalid WhatsApp action.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                }
                setPackage("com.whatsapp")
            }

            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "WhatsApp is not installed or this action is unsupported.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun buildSocialGrid(c: Contact) {
        val card = Ui.card(this, content, "Chat and social apps")
        val grid = GridLayout(this)
        grid.columnCount = 4
        for (app in Social.apps) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.setPadding(0, dp(8), 0, dp(8))
            val icon = ImageView(this)
            icon.setImageResource(app.icon)
            icon.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            icon.contentDescription = app.label
            val label = TextView(this)
            label.text = app.label
            label.textSize = 11f
            label.gravity = Gravity.CENTER
            label.maxLines = 1
            label.setTextColor(Ui.color(this, R.color.app_text_secondary))
            label.setPadding(0, dp(4), 0, 0)
            cell.addView(icon)
            cell.addView(label)
            cell.alpha = if (Actions.isInstalled(this, app)) 1f else 0.35f
            cell.setOnClickListener {
                Actions.withNumber(this, c) { n -> Actions.openSocial(this, app, n) }
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            lp.width = 0
            grid.addView(cell, lp)
        }
        card.addView(grid)
    }

    private fun manageLabels(c: Contact) {
        val titles = groups.map { it.title }.distinct().sorted()
        if (titles.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No labels yet")
                .setMessage("Create a label such as Family, Friends or Work.")
                .setPositiveButton("Create label") { _, _ -> promptNewLabel() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val checked = BooleanArray(titles.size) { i ->
            groups.any { it.title == titles[i] && it.id in c.groupIds }
        }
        val original = checked.copyOf()
        AlertDialog.Builder(this)
            .setTitle("Labels")
            .setMultiChoiceItems(titles.toTypedArray(), checked) { _, _, _ -> }
            .setPositiveButton("Save") { _, _ ->
                Thread {
                    var ok = true
                    for (i in titles.indices) {
                        if (checked[i] != original[i]) {
                            val gs = groups.filter { it.title == titles[i] }
                            ok = ContactWriter.setMembership(this, c, gs, checked[i]) && ok
                        }
                    }
                    runOnUiThread {
                        if (!ok) Actions.toast(this, "This label is not available for the account storing this contact")
                        load()
                    }
                }.start()
            }
            .setNeutralButton("New label") { _, _ -> promptNewLabel() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptNewLabel() {
        val et = EditText(this)
        et.hint = "Label name"
        val box = LinearLayout(this)
        box.setPadding(dp(24), dp(8), dp(24), 0)
        box.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle("New label")
            .setView(box)
            .setPositiveButton("Create") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) {
                    Thread {
                        val ok = ContactWriter.createGroup(this, t)
                        runOnUiThread {
                            if (!ok) Actions.toast(this, "Could not create label")
                            load()
                        }
                    }.start()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMore(c: Contact) {
        val acts = ArrayList<Ui.SheetAction>()
        acts.add(Ui.SheetAction("Share contact", R.drawable.ic_share) { Actions.share(this, c) })
        if (c.phones.isNotEmpty()) {
            acts.add(Ui.SheetAction("Copy number", R.drawable.ic_copy) { Actions.copyNumber(this, c) })
        }
        if (c.addresses.isNotEmpty()) {
            acts.add(Ui.SheetAction("Directions", R.drawable.ic_location) {
                Actions.directions(this, c.addresses[0].value)
            })
        }
        acts.add(Ui.SheetAction("Edit in system editor", R.drawable.ic_edit) { Actions.systemEdit(this, c) })
        acts.add(Ui.SheetAction("Delete", R.drawable.ic_delete, true) {
            Actions.confirmDelete(this, c) { finish() }
        })
        Ui.showSheet(this, "More", acts)
    }
}