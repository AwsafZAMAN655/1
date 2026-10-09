package com.example.fastcontacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class OrganizeActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private var contacts: List<Contact> = emptyList()
    private var groups: List<ContactGroup> = emptyList()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri -> if (uri != null) doExport(uri) }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) doImport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_page)
        Ui.systemBars(this)
        findViewById<TextView>(R.id.pageTitle).text = "Organize"
        findViewById<ImageButton>(R.id.pageBack).setOnClickListener { finish() }
        content = findViewById(R.id.pageContent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        Thread {
            val c = ContactsRepository.loadAll(this)
            val g = ContactsRepository.loadGroups(this)
            runOnUiThread {
                contacts = c
                groups = g
                build()
            }
        }.start()
    }

    private fun build() {
        content.removeAllViews()

        // ---- labels ----
        val labels = Ui.card(this, content, "Labels")
        val titles = groups.map { it.title }.distinct().sorted()
        for (t in titles) {
            val ids = groups.filter { it.title == t }.map { it.id }.toSet()
            val count = contacts.count { c -> c.groupIds.any { it in ids } }
            Ui.row(
                this, labels, R.drawable.ic_label, t, count.toString() + (if (count == 1) " contact" else " contacts"),
                onClick = {
                    val i = Intent()
                    i.putExtra("label_title", t)
                    setResult(RESULT_OK, i)
                    finish()
                },
                onLong = { labelSheet(t) }
            )
        }
        Ui.row(
            this, labels, R.drawable.ic_add, "Create label",
            if (titles.isEmpty()) "Group people like Family, Friends or Work" else "Long-press a label to rename or delete it",
            onClick = { promptLabel(null) }
        )

        // ---- clean up ----
        val clean = Ui.card(this, content, "Clean up")
        val dups = ContactWriter.findDuplicates(contacts)
        Ui.row(
            this, clean, R.drawable.ic_merge, "Merge duplicates",
            if (dups.isEmpty()) "No duplicates found" else dups.size.toString() + " possible duplicate groups",
            onClick = { showDuplicates(dups) }
        )

        // ---- import / export ----
        val io = Ui.card(this, content, "Import and export")
        Ui.row(
            this, io, R.drawable.ic_import, "Import from vCard (.vcf)",
            "Adds contacts to your default account on this phone",
            onClick = { importLauncher.launch(arrayOf("*/*")) }
        )
        Ui.row(
            this, io, R.drawable.ic_export, "Export all to vCard",
            contacts.size.toString() + " contacts saved to a file you choose",
            onClick = { exportLauncher.launch("contacts.vcf") }
        )

        // ---- trash ----
        val trash = Ui.card(this, content, "Trash and restore")
        Ui.row(
            this, trash, R.drawable.ic_restore, "About deleted contacts", "Tap to read how deletion works",
            onClick = {
                AlertDialog.Builder(this)
                    .setTitle("Trash and restore")
                    .setMessage(
                        "Android's Contacts Provider has no local trash, so contacts deleted here are removed " +
                            "immediately and this app cannot restore them. If the contact was synced to a Google " +
                            "account, Google may still hold a copy that you can restore from Google Contacts on " +
                            "the web. To be safe, use Export all to vCard before deleting many contacts."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        )

        // ---- sync ----
        val sync = Ui.card(this, content, "Sync")
        Ui.row(
            this, sync, R.drawable.ic_sync, "Android sync settings",
            "Google and other accounts are synced by Android itself",
            onClick = {
                Actions.safeStart(this, Intent(Settings.ACTION_SYNC_SETTINGS))
            }
        )
    }

    private fun labelSheet(title: String) {
        val gs = groups.filter { it.title == title }
        Ui.showSheet(
            this, title, listOf(
                Ui.SheetAction("Rename", R.drawable.ic_edit) { promptLabel(title) },
                Ui.SheetAction("Delete label", R.drawable.ic_delete, true) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete label")
                        .setMessage("Delete the label " + title + "? The contacts stay, only the label is removed.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            Thread {
                                val ok = ContactWriter.deleteGroups(this, gs)
                                runOnUiThread {
                                    if (!ok) Actions.toast(this, "Could not delete label")
                                    refresh()
                                }
                            }.start()
                        }
                        .show()
                }
            )
        )
    }

    private fun promptLabel(existingTitle: String?) {
        val et = EditText(this)
        et.hint = "Label name"
        if (existingTitle != null) et.setText(existingTitle)
        val box = LinearLayout(this)
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0)
        box.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle(if (existingTitle == null) "New label" else "Rename label")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) {
                    Thread {
                        val ok = if (existingTitle == null) ContactWriter.createGroup(this, t)
                        else ContactWriter.renameGroups(this, groups.filter { it.title == existingTitle }, t)
                        runOnUiThread {
                            if (!ok) Actions.toast(this, "Could not save label")
                            refresh()
                        }
                    }.start()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDuplicates(dups: List<Pair<String, List<Contact>>>) {
        if (dups.isEmpty()) {
            Actions.toast(this, "No duplicates found")
            return
        }
        val labels = dups.map { it.first + " (" + it.second.size + " contacts)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Merge duplicates")
            .setItems(labels) { _, i -> mergeGroups(listOf(dups[i])) }
            .setPositiveButton("Merge all") { _, _ -> mergeGroups(dups) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun mergeGroups(list: List<Pair<String, List<Contact>>>) {
        Thread {
            var done = 0
            for (g in list) if (ContactWriter.merge(this, g.second)) done++
            runOnUiThread {
                Actions.toast(this, "Merged " + done + " group(s)")
                refresh()
            }
        }.start()
    }

    private fun doExport(uri: Uri) {
        Thread {
            val text = VCard.export(contacts)
            val ok = try {
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                Actions.toast(this, if (ok) "Exported " + contacts.size + " contacts" else "Export failed")
            }
        }.start()
    }

    private fun doImport(uri: Uri) {
        Thread {
            var count = 0
            try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                for (f in VCard.parse(text)) if (ContactWriter.insert(this, f)) count++
            } catch (e: Exception) {
                // reported below
            }
            runOnUiThread {
                Actions.toast(this, if (count > 0) "Imported " + count + " contacts" else "No contacts were imported")
                refresh()
            }
        }.start()
    }
}
