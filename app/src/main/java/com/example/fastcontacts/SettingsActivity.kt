package com.example.fastcontacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_page)
        Ui.systemBars(this)
        findViewById<TextView>(R.id.pageTitle).text = "Settings"
        findViewById<ImageButton>(R.id.pageBack).setOnClickListener { finish() }
        content = findViewById(R.id.pageContent)
        build()
    }

    private fun themeName(): String = when (Prefs.theme(this)) {
        1 -> "Light"
        2 -> "Dark"
        else -> "System default"
    }

    private fun build() {
        content.removeAllViews()

        val appearance = Ui.card(this, content, "Appearance")
        Ui.row(this, appearance, R.drawable.ic_settings, "Theme", themeName(), onClick = { pickTheme() })

        val contacts = Ui.card(this, content, "Contacts")
        Ui.row(
            this, contacts, R.drawable.ic_people, "Sort by",
            if (Prefs.sortByLast(this)) "Last name" else "First name", onClick = { pickSort() }
        )
        Ui.switchRow(this, contacts, "Alphabetical index", "A-Z side index on the contact list",
            Prefs.alphabetIndex(this)) { Prefs.setAlphabetIndex(this, it) }
        Ui.switchRow(this, contacts, "Show phone preview", null,
            Prefs.showPhone(this)) { Prefs.setShowPhone(this, it) }
        Ui.switchRow(this, contacts, "Show email", null,
            Prefs.showEmail(this)) { Prefs.setShowEmail(this, it) }

        val comm = Ui.card(this, content, "Communication")
        val cc = Prefs.countryCode(this)
        Ui.row(
            this, comm, R.drawable.ic_call, "Default country code",
            if (cc.isEmpty()) "Not set (used for WhatsApp, Telegram, Viber and similar)" else "+" + cc,
            onClick = { pickCountryCode() }
        )
        Ui.row(
            this, comm, R.drawable.ic_sms, "Chat apps on this phone", "See which supported apps are installed",
            onClick = { showChatApps() }
        )

        val org = Ui.card(this, content, "Organization")
        Ui.row(
            this, org, R.drawable.ic_organize, "Labels, import / export, duplicates", null,
            onClick = { startActivity(Intent(this, OrganizeActivity::class.java)) }
        )

        val system = Ui.card(this, content, "System")
        Ui.row(
            this, system, R.drawable.ic_info, "Contact permissions", "Open this app's permission settings",
            onClick = {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                i.data = Uri.parse("package:" + packageName)
                Actions.safeStart(this, i)
            }
        )
        Ui.row(
            this, system, R.drawable.ic_sync, "Android sync settings", "Google and other accounts",
            onClick = { Actions.safeStart(this, Intent(Settings.ACTION_SYNC_SETTINGS)) }
        )

        val about = Ui.card(this, content, "About")
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        Ui.row(this, about, R.drawable.ic_info, "Version", version)
        Ui.row(
            this, about, R.drawable.ic_info, "Privacy", "No ads, no tracking, no internet permission",
            onClick = {
                AlertDialog.Builder(this)
                    .setTitle("Privacy")
                    .setMessage(
                        "Fast Contacts has no ads, no analytics, no login and no internet permission. " +
                            "Your contacts stay in Android's own Contacts storage. If a contact is saved to a " +
                            "Google account, Android's existing sync handles it, not this app."
                    )
                    .setPositiveButton("OK", null).show()
            }
        )
        Ui.row(
            this, about, R.drawable.ic_info, "Open-source information", "Libraries and icons",
            onClick = {
                AlertDialog.Builder(this)
                    .setTitle("Open-source information")
                    .setMessage(
                        "Built with AndroidX and Material Components for Android (Apache License 2.0). " +
                            "Interface icons are Material-style vector shapes (Apache License 2.0). " +
                            "Brand icons are placeholders until you add your own."
                    )
                    .setPositiveButton("OK", null).show()
            }
        )
    }

    private fun pickTheme() {
        val names = arrayOf("System default", "Light", "Dark")
        AlertDialog.Builder(this).setTitle("Theme")
            .setSingleChoiceItems(names, Prefs.theme(this)) { d, which ->
                Prefs.setTheme(this, which)
                d.dismiss()
                Prefs.applyTheme(this)
                build()
            }.show()
    }

    private fun pickSort() {
        val names = arrayOf("First name", "Last name")
        AlertDialog.Builder(this).setTitle("Sort by")
            .setSingleChoiceItems(names, if (Prefs.sortByLast(this)) 1 else 0) { d, which ->
                Prefs.setSortByLast(this, which == 1)
                d.dismiss()
                build()
            }.show()
    }

    private fun pickCountryCode() {
        val et = EditText(this)
        et.hint = "Digits only, for example 880"
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.setText(Prefs.countryCode(this))
        val box = LinearLayout(this)
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0)
        box.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle("Default country code")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                Prefs.setCountryCode(this, et.text.toString().filter { it.isDigit() })
                build()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChatApps() {
        val lines = Social.apps.map {
            it.label + ": " + (if (Actions.isInstalled(this, it)) "installed" else "not installed")
        }
        AlertDialog.Builder(this)
            .setTitle("Chat apps")
            .setMessage(lines.joinToString(System.lineSeparator()))
            .setPositiveButton("OK", null).show()
    }
}
