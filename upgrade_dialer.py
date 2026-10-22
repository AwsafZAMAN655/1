#!/usr/bin/env python3
# upgrade_dialer.py  -  ADD-ONLY upgrade for Fast Contacts
#   + Recents / call history tab (missed filter, expandable rows, quick actions)
#   + upgraded Dialpad (live number formatting, T9 name search, SMS button, key tones,
#     paste, redial last number, press animations)
#   + tab slide animations, tel: dial links handled
#
# HOW TO RUN (Windows CMD), from the folder that contains the "app" folder:
#     python upgrade_dialer.py
# To go back to exactly how it was:
#     python upgrade_dialer.py --undo
#
# Safety: every file it replaces is backed up as <file>.dialer_bak first, and a file is
# only replaced if it is still the exact version this upgrade was written for.
# Nothing is deleted. All existing screens, buttons and logic are kept.

import hashlib
import os
import shutil
import sys

BAK = ".dialer_bak"
BASE = os.path.join("app", "src", "main")   # used only to check we are in the right folder

# files that already exist: path -> sha256 of the version this upgrade was built against
EXPECTED = {
    "app/src/main/AndroidManifest.xml": "9478701ef4d80ed0f60a3c68584c02ba93a498bb4816966cdc7d62ad150cf780",
    "app/src/main/java/com/example/fastcontacts/Dialpad.kt": "3db015927d8ee758c1078a6b24fe485992493305e4eeafe3d9e4c10e2a42a238",
    "app/src/main/java/com/example/fastcontacts/MainActivity.kt": "d278069de4ba2de2ed7d84a997c0c617cef47f20713f36fb2505de27534565d8",
    "app/src/main/res/layout/activity_main.xml": "85aad1dcfcd8daaefd7f1c7778650a9030383098baaacbe62eb8fa6e73fb3564",
    "app/src/main/res/layout/pane_dialpad.xml": "f6730841a25efb5fa23ce8f03ed62853045bddba98ab5b292f9c3be80016f271",
    "app/src/main/res/menu/bottom_nav.xml": "d0f169e915dae1e4ad1f4ba14cfe98d8caebb211f5fc129386b16beba8be8360"
}

# every file written by this upgrade (changed + brand new)
FILES = {}
FILES['app/src/main/AndroidManifest.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.WRITE_CONTACTS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.READ_CALL_LOG" />

    <queries>
        <package android:name="com.whatsapp" />
        <package android:name="com.whatsapp.w4b" />
        <package android:name="org.telegram.messenger" />
        <package android:name="com.viber.voip" />
        <package android:name="com.imo.android.imoim" />
        <package android:name="org.thoughtcrime.securesms" />
        <package android:name="com.facebook.orca" />
        <package android:name="com.facebook.katana" />
        <package android:name="com.instagram.android" />
        <package android:name="com.skype.raider" />
        <package android:name="com.snapchat.android" />
        <package android:name="com.google.android.apps.tachyon" />

        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="https" />
        </intent>
        <intent>
            <action android:name="android.intent.action.SENDTO" />
            <data android:scheme="mailto" />
        </intent>
        <intent>
            <action android:name="android.intent.action.SENDTO" />
            <data android:scheme="smsto" />
        </intent>
    </queries>

    <application
        android:name=".FastContactsApp"
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:roundIcon="@drawable/ic_launcher"
        android:label="Fast Contacts"
        android:supportsRtl="true"
        android:theme="@style/Theme.FastContacts">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustPan">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.DIAL" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:scheme="tel" />
            </intent-filter>
        </activity>

        <activity android:name=".DetailActivity" />

        <activity
            android:name=".EditActivity"
            android:windowSoftInputMode="adjustResize" />

        <activity android:name=".OrganizeActivity" />
        <activity android:name=".SettingsActivity" />

    </application>
</manifest>
"""

FILES['app/src/main/java/com/example/fastcontacts/Dialpad.kt'] = r"""package com.example.fastcontacts

import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DialpadController(
    private val activity: AppCompatActivity,
    root: View,
    openContact: (Contact) -> Unit
) {
    private val number = StringBuilder()
    private val display: TextView = root.findViewById(R.id.dialDisplay)
    private val btnAdd: ImageButton = root.findViewById(R.id.btnAddContact)
    private val btnSms: ImageButton = root.findViewById(R.id.btnDialSms)
    private val btnBack: ImageButton = root.findViewById(R.id.btnBackspace)
    private val btnCall: ImageButton = root.findViewById(R.id.btnDialCall)
    private var all: List<Contact> = emptyList()
    private var engine = SearchEngine(emptyList())
    private var tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_DTMF, 70)
    } catch (e: Exception) {
        null
    }
    private val adapter = ContactAdapter(
        onClick = openContact,
        onCallClick = { c -> Actions.withNumber(activity, c) { n -> Actions.call(activity, n) } },
        onSmsClick = { c -> Actions.withNumber(activity, c) { n -> Actions.sms(activity, n) } }
    )

    init {
        val rv = root.findViewById<RecyclerView>(R.id.dialMatches)
        rv.layoutManager = LinearLayoutManager(activity)
        rv.adapter = adapter

        val keypad = root.findViewById<GridLayout>(R.id.keypad)
        val keys = listOf(
            "1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to ""
        )
        for ((digit, letters) in keys) {
            val v = LayoutInflater.from(activity).inflate(R.layout.view_dial_key, keypad, false)
            v.findViewById<TextView>(R.id.keyDigit).text = digit
            v.findViewById<TextView>(R.id.keyLetters).text = letters
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            lp.width = 0
            lp.height = Ui.dp(activity, 68)
            lp.setMargins(Ui.dp(activity, 6), Ui.dp(activity, 4), Ui.dp(activity, 6), Ui.dp(activity, 4))
            v.layoutParams = lp
            v.contentDescription = digit
            pressScale(v)
            v.setOnClickListener {
                Ui.tap(v)
                playTone(digit[0])
                number.append(digit)
                refresh()
            }
            if (digit == "0") {
                v.setOnLongClickListener {
                    number.append("+")
                    refresh()
                    true
                }
            }
            keypad.addView(v)
        }

        pressScale(btnCall)
        btnCall.setOnClickListener {
            if (number.isNotEmpty()) {
                Actions.call(activity, number.toString())
            } else {
                // Like Google Phone: call on an empty pad brings back the last dialed number.
                val last = try {
                    CallLog.Calls.getLastOutgoingCall(activity)
                } catch (e: Exception) {
                    ""
                }
                if (!last.isNullOrEmpty()) setNumber(last)
                else Actions.toast(activity, "Enter a number first")
            }
        }
        btnAdd.setOnClickListener {
            if (number.isNotEmpty()) Actions.create(activity, number.toString())
            else Actions.toast(activity, "Enter a number first")
        }
        btnSms.setOnClickListener {
            if (number.isNotEmpty()) Actions.sms(activity, number.toString())
            else Actions.toast(activity, "Enter a number first")
        }
        btnBack.setOnClickListener {
            if (number.isNotEmpty()) number.deleteCharAt(number.length - 1)
            refresh()
        }
        btnBack.setOnLongClickListener {
            number.setLength(0)
            refresh()
            true
        }
        // Long-press the number to paste one.
        display.setOnLongClickListener {
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (!t.isNullOrEmpty()) setNumber(number.toString() + t)
            true
        }
        refresh()
    }

    private fun pressScale(v: View) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
            false
        }
    }

    private fun playTone(ch: Char) {
        val t = when (ch) {
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (ch - '0')
            else -> return
        }
        try {
            tone?.startTone(t, 100)
        } catch (e: Exception) {
            // no sound is fine
        }
    }

    fun setNumber(n: String) {
        number.setLength(0)
        number.append(n.filter { it.isDigit() || it == '+' || it == '*' || it == '#' })
        refresh()
    }

    fun release() {
        tone?.release()
        tone = null
    }

    fun setContacts(list: List<Contact>) {
        all = list
        engine = SearchEngine(list)
        refresh()
    }

    private fun refresh() {
        val shown = CallLogRepository.live(number.toString())
        display.text = shown
        display.textSize = if (shown.length > 14) 24f else 32f
        val has = number.isNotEmpty()
        val a = if (has) 1f else 0.4f
        btnBack.animate().alpha(a).setDuration(120).start()
        btnAdd.animate().alpha(a).setDuration(120).start()
        btnSms.animate().alpha(a).setDuration(120).start()
        if (!has) {
            adapter.submit(emptyList())
        } else {
            val q = number.toString()
            // name matches by T9 first (3-2-8 = David), then number matches
            val merged = (T9.search(all, q) + engine.search(q)).distinctBy { it.id }
            adapter.submit(merged.take(20))
        }
    }
}
"""

FILES['app/src/main/java/com/example/fastcontacts/MainActivity.kt'] = r"""package com.example.fastcontacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ContactAdapter
    private lateinit var favAdapter: ContactAdapter
    private lateinit var dialpad: DialpadController
    private lateinit var recents: RecentsController
    private lateinit var root: ViewGroup
    private lateinit var header: View
    private lateinit var search: EditText
    private lateinit var btnClear: ImageButton
    private lateinit var chipsScroll: View
    private lateinit var chipGroup: ChipGroup
    private lateinit var list: RecyclerView
    private lateinit var favList: RecyclerView
    private lateinit var paneContacts: View
    private lateinit var paneFavorites: View
    private lateinit var paneDialpad: View
    private lateinit var paneRecents: View
    private lateinit var emptyContacts: View
    private lateinit var emptyFav: View
    private lateinit var emptyPermission: View
    private lateinit var index: AlphabetIndexView
    private lateinit var bubble: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var nav: BottomNavigationView

    private var all: List<Contact> = emptyList()
    private var sortedAll: List<Contact> = emptyList()
    private var groups: List<ContactGroup> = emptyList()
    private var labelFilter: String? = null
    private var tab = 0
    private var lastQuery = ""
    private val chipTitles = HashMap<Int, String?>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasContactsPermission()) loadContacts() else applyFilter()
    }

    private val callLogLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadContacts()
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
            // permanently denied: the only way left is the app settings page
            Actions.toast(this, "Turn on Call logs permission in Settings")
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))
            )
        }
    }

    private val organizeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val title = r.data?.getStringExtra("label_title")
        if (title != null) {
            labelFilter = title
            nav.selectedItemId = R.id.nav_contacts
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Ui.systemBars(this)

        root = findViewById(R.id.root)
        header = findViewById(R.id.header)
        search = findViewById(R.id.search)
        btnClear = findViewById(R.id.btnClearSearch)
        chipsScroll = findViewById(R.id.chipsScroll)
        chipGroup = findViewById(R.id.chipGroup)
        list = findViewById(R.id.list)
        favList = findViewById(R.id.favList)
        paneContacts = findViewById(R.id.paneContacts)
        paneFavorites = findViewById(R.id.paneFavorites)
        paneDialpad = findViewById(R.id.paneDialpad)
        paneRecents = findViewById(R.id.paneRecents)
        emptyContacts = findViewById(R.id.emptyContacts)
        emptyFav = findViewById(R.id.emptyFavorites)
        emptyPermission = findViewById(R.id.emptyPermission)
        index = findViewById(R.id.alphabetIndex)
        bubble = findViewById(R.id.letterBubble)
        fab = findViewById(R.id.fab)
        nav = findViewById(R.id.bottomNav)

        adapter = makeAdapter()
        favAdapter = makeAdapter()
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        favList.layoutManager = LinearLayoutManager(this)
        favList.adapter = favAdapter

        dialpad = DialpadController(this, paneDialpad) { openDetail(it) }
        recents = RecentsController(this, paneRecents, { openDetail(it) }) { requestCallLogPermission() }

        search.doAfterTextChanged {
            btnClear.visibility = if (it.isNullOrEmpty()) View.GONE else View.VISIBLE
            applyFilter()
        }
        btnClear.setOnClickListener { search.setText("") }

        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { v ->
            val pm = PopupMenu(this, v)
            pm.menu.add("Organize")
            pm.menu.add("Settings")
            pm.setOnMenuItemClickListener { item ->
                if (item.title == "Organize") {
                    organizeLauncher.launch(Intent(this, OrganizeActivity::class.java))
                } else {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                true
            }
            pm.show()
        }

        fab.setOnClickListener { Actions.create(this) }

        index.onLetter = { letter -> jumpTo(letter) }
        index.onRelease = { hideBubble() }

        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_contacts -> showTab(0)
                R.id.nav_favorites -> showTab(1)
                R.id.nav_recents -> showTab(3)
                R.id.nav_dialpad -> showTab(2)
            }
            true
        }

        if (!hasContactsPermission()) requestPermissions()
        handleDialIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDialIntent(intent)
    }

    override fun onDestroy() {
        dialpad.release()
        super.onDestroy()
    }

    // Other apps can hand us a tel: number: open the dialpad with it filled in.
    private fun handleDialIntent(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme == "tel") {
            dialpad.setNumber(Uri.decode(d.schemeSpecificPart ?: ""))
            nav.selectedItemId = R.id.nav_dialpad
        }
    }

    private fun requestCallLogPermission() {
        callLogLauncher.launch(Manifest.permission.READ_CALL_LOG)
    }

    override fun onResume() {
        super.onResume()
        if (hasContactsPermission()) loadContacts() else applyFilter()
    }

    private fun makeAdapter() = ContactAdapter(
        onClick = { openDetail(it) },
        onCallClick = { c -> Actions.withNumber(this, c) { n -> Actions.call(this, n) } },
        onSmsClick = { c -> Actions.withNumber(this, c) { n -> Actions.sms(this, n) } },
        onLongClick = { showQuickMenu(it) },
        onFavClick = { c ->
            Actions.toggleStar(this, c)
            loadContacts()
        }
    )

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG
            )
        )
    }

    private fun openDetail(c: Contact) {
        val i = Intent(this, DetailActivity::class.java)
        i.putExtra("contact_id", c.id)
        val opts = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_up_in, R.anim.fade_out_soft)
        startActivity(i, opts.toBundle())
    }

    // tab ids: 0 contacts, 1 favorites, 2 dialpad, 3 recents
    // visual order left to right: contacts, favorites, recents, dialpad
    private fun order(t: Int): Int = when (t) {
        3 -> 2
        2 -> 3
        else -> t
    }

    private fun showTab(t: Int) {
        val dir = if (order(t) >= order(tab)) 1 else -1
        tab = t
        TransitionManager.beginDelayedTransition(root)
        header.visibility = if (t == 2 || t == 3) View.GONE else View.VISIBLE
        updateChipsVisibility()
        if (t == 2 || t == 3) fab.hide() else fab.show()
        swap(paneContacts, t == 0, dir)
        swap(paneFavorites, t == 1, dir)
        swap(paneRecents, t == 3, dir)
        swap(paneDialpad, t == 2, dir)
    }

    private fun swap(pane: View, show: Boolean, dir: Int = 1) {
        if (show) {
            if (pane.visibility != View.VISIBLE) {
                pane.alpha = 0f
                pane.translationX = dir * Ui.dp(this, 28).toFloat()
                pane.visibility = View.VISIBLE
                pane.animate().alpha(1f).translationX(0f).setDuration(200).start()
            }
        } else {
            pane.animate().cancel()
            pane.translationX = 0f
            pane.visibility = View.GONE
        }
    }

    private fun updateChipsVisibility() {
        chipsScroll.visibility = if (tab == 0 && chipTitles.size > 1) View.VISIBLE else View.GONE
    }

    private fun loadContacts() {
        Thread {
            val loaded = ContactsRepository.loadAll(this)
            val g = ContactsRepository.loadGroups(this)
            val sorted = SearchEngine.sorted(loaded, Prefs.sortByLast(this))
            val calls = CallLogRepository.load(this, loaded)
            runOnUiThread {
                all = loaded
                groups = g
                sortedAll = sorted
                rebuildChips()
                applyFilter()
                dialpad.setContacts(sorted)
                recents.setData(calls, CallLogRepository.hasPermission(this))
            }
        }.start()
    }

    private fun rebuildChips() {
        chipGroup.setOnCheckedStateChangeListener(null)
        chipGroup.removeAllViews()
        chipTitles.clear()
        val titles = groups.map { it.title }.distinct().sorted()
        if (labelFilter != null && labelFilter !in titles) labelFilter = null
        val inf = LayoutInflater.from(this)
        fun addChip(text: String, title: String?) {
            val chip = inf.inflate(R.layout.chip_filter, chipGroup, false) as Chip
            chip.id = View.generateViewId()
            chip.text = text
            chip.isCheckable = true
            chipTitles[chip.id] = title
            chipGroup.addView(chip)
            if (title == labelFilter) chip.isChecked = true
        }
        if (titles.isNotEmpty()) {
            addChip("All", null)
            for (t in titles) addChip(t, t)
        }
        chipGroup.setOnCheckedStateChangeListener { _, ids ->
            labelFilter = if (ids.isEmpty()) null else chipTitles[ids[0]]
            applyFilter()
        }
        updateChipsVisibility()
    }

    private fun setEmpty(v: View, icon: Int, title: String, sub: String, btn: String?, run: (() -> Unit)?) {
        v.findViewById<ImageView>(R.id.emptyIcon).setImageResource(icon)
        v.findViewById<TextView>(R.id.emptyTitle).text = title
        v.findViewById<TextView>(R.id.emptySubtitle).text = sub
        val b = v.findViewById<Button>(R.id.emptyButton)
        if (btn != null && run != null) {
            b.text = btn
            b.visibility = View.VISIBLE
            b.setOnClickListener { run() }
        } else {
            b.visibility = View.GONE
        }
    }

    private fun applyFilter() {
        if (!hasContactsPermission()) {
            setEmpty(
                emptyPermission, R.drawable.ic_people, "Contacts access needed",
                "Allow access so Fast Contacts can show and manage your contacts.", "Grant access"
            ) { requestPermissions() }
            emptyPermission.visibility = View.VISIBLE
            return
        }
        emptyPermission.visibility = View.GONE

        val q = search.text.toString()
        val byLast = Prefs.sortByLast(this)
        val showPhone = Prefs.showPhone(this)
        val showEmail = Prefs.showEmail(this)

        var base = sortedAll
        val lf = labelFilter
        if (lf != null) {
            val ids = groups.filter { it.title == lf }.map { it.id }.toSet()
            base = base.filter { c -> c.groupIds.any { it in ids } }
        }
        val result = SearchEngine(base).search(q)
        val queryChanged = q != lastQuery
        lastQuery = q
        adapter.submit(result, q.isBlank(), byLast, showPhone, showEmail) {
            if (queryChanged) list.scrollToPosition(0)
        }

        val favResult = SearchEngine(sortedAll.filter { it.starred }).search(q)
        favAdapter.submit(favResult, false, byLast, showPhone, showEmail)

        index.visibility =
            if (Prefs.alphabetIndex(this) && q.isBlank() && result.isNotEmpty()) View.VISIBLE else View.GONE

        if (result.isEmpty()) {
            if (all.isEmpty()) {
                setEmpty(
                    emptyContacts, R.drawable.ic_people, "No contacts yet",
                    "Add your first contact to get started.", "Add contact"
                ) { Actions.create(this) }
            } else if (q.isBlank() && labelFilter != null) {
                setEmpty(
                    emptyContacts, R.drawable.ic_label, "No contacts here yet",
                    "Add people to this label from their contact page.", null, null
                )
            } else {
                setEmpty(
                    emptyContacts, R.drawable.ic_search, "No contacts found",
                    "Try another name or phone number.", null, null
                )
            }
            emptyContacts.visibility = View.VISIBLE
        } else {
            emptyContacts.visibility = View.GONE
        }

        if (favResult.isEmpty()) {
            if (q.isBlank()) {
                setEmpty(
                    emptyFav, R.drawable.ic_star_border, "No favorites yet",
                    "Favorite people you contact often.", null, null
                )
            } else {
                setEmpty(
                    emptyFav, R.drawable.ic_search, "No favorites found",
                    "Try another name or phone number.", null, null
                )
            }
            emptyFav.visibility = View.VISIBLE
        } else {
            emptyFav.visibility = View.GONE
        }
    }

    private fun jumpTo(letter: String) {
        val pos = adapter.headerPositions()
        if (pos.isEmpty()) return
        var target = pos[letter]
        if (target == null) {
            val keys = pos.keys.toList()
            val next = keys.firstOrNull { it > letter }
            target = if (next != null) pos[next] else pos[keys.last()]
        }
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(target ?: 0, 0)
        bubble.text = letter
        bubble.visibility = View.VISIBLE
        bubble.animate().alpha(1f).setDuration(80).start()
    }

    private fun hideBubble() {
        bubble.animate().alpha(0f).setStartDelay(350).setDuration(160).withEndAction {
            bubble.visibility = View.GONE
        }.start()
    }

    // Long-press menu on a contact row.
    private fun showQuickMenu(c: Contact) {
        val acts = ArrayList<Ui.SheetAction>()
        if (c.phones.isNotEmpty()) {
            acts.add(Ui.SheetAction("Call", R.drawable.ic_call) {
                Actions.withNumber(this, c) { n -> Actions.call(this, n) }
            })
            acts.add(Ui.SheetAction("Message", R.drawable.ic_sms) {
                Actions.withNumber(this, c) { n -> Actions.sms(this, n) }
            })
            if (Actions.isInstalled(this, Social.whatsapp)) {
                acts.add(Ui.SheetAction("WhatsApp", R.drawable.ic_whatsapp_placeholder) {
                    Actions.withNumber(this, c) { n -> Actions.openSocial(this, Social.whatsapp, n) }
                })
            }
        }
        acts.add(Ui.SheetAction(
            if (c.starred) "Remove from favorites" else "Add to favorites",
            if (c.starred) R.drawable.ic_star_border else R.drawable.ic_star
        ) {
            Actions.toggleStar(this, c)
            loadContacts()
        })
        acts.add(Ui.SheetAction("Share", R.drawable.ic_share) { Actions.share(this, c) })
        acts.add(Ui.SheetAction("Edit", R.drawable.ic_edit) { Actions.edit(this, c) })
        acts.add(Ui.SheetAction("Delete", R.drawable.ic_delete, true) {
            Actions.confirmDelete(this, c) { loadContacts() }
        })
        Ui.showSheet(this, c.name, acts)
    }
}
"""

FILES['app/src/main/res/layout/activity_main.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/app_bg"
    android:fitsSystemWindows="true"
    android:orientation="vertical">

    <LinearLayout
        android:id="@+id/header"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:layout_margin="12dp"
            android:background="@drawable/bg_search"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingStart="16dp"
            android:paddingEnd="4dp">

            <ImageView
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:importantForAccessibility="no"
                android:src="@drawable/ic_search" />

            <EditText
                android:id="@+id/search"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="@null"
                android:hint="Search contacts"
                android:importantForAutofill="no"
                android:inputType="text"
                android:maxLines="1"
                android:paddingStart="12dp"
                android:paddingEnd="8dp"
                android:textColor="@color/app_text"
                android:textColorHint="@color/app_text_secondary"
                android:textSize="16sp" />

            <ImageButton
                android:id="@+id/btnClearSearch"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="Clear search"
                android:src="@drawable/ic_close"
                android:visibility="gone" />

            <ImageButton
                android:id="@+id/btnMenu"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="More options"
                android:src="@drawable/ic_more" />
        </LinearLayout>

        <HorizontalScrollView
            android:id="@+id/chipsScroll"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:scrollbars="none"
            android:visibility="gone">

            <com.google.android.material.chip.ChipGroup
                android:id="@+id/chipGroup"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:paddingStart="12dp"
                android:paddingEnd="12dp"
                android:paddingBottom="4dp"
                app:selectionRequired="true"
                app:singleLine="true"
                app:singleSelection="true" />
        </HorizontalScrollView>
    </LinearLayout>

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <FrameLayout
            android:id="@+id/paneContacts"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/list"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:clipToPadding="false"
                android:paddingEnd="22dp"
                android:paddingBottom="96dp" />

            <com.example.fastcontacts.AlphabetIndexView
                android:id="@+id/alphabetIndex"
                android:layout_width="26dp"
                android:layout_height="match_parent"
                android:layout_gravity="end"
                android:layout_marginTop="8dp"
                android:layout_marginBottom="88dp"
                android:contentDescription="Alphabet index" />

            <include
                android:id="@+id/emptyContacts"
                layout="@layout/view_empty"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:visibility="gone" />
        </FrameLayout>

        <FrameLayout
            android:id="@+id/paneFavorites"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone">

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/favList"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:clipToPadding="false"
                android:paddingBottom="96dp" />

            <include
                android:id="@+id/emptyFavorites"
                layout="@layout/view_empty"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:visibility="gone" />
        </FrameLayout>

        <include
            android:id="@+id/paneRecents"
            layout="@layout/pane_recents"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone" />

        <include
            android:id="@+id/paneDialpad"
            layout="@layout/pane_dialpad"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone" />

        <include
            android:id="@+id/emptyPermission"
            layout="@layout/view_empty"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@color/app_bg"
            android:visibility="gone" />

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/fab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="16dp"
            android:contentDescription="Create contact"
            android:src="@drawable/ic_add" />

        <TextView
            android:id="@+id/letterBubble"
            android:layout_width="72dp"
            android:layout_height="72dp"
            android:layout_gravity="center"
            android:alpha="0"
            android:background="@drawable/bg_bubble"
            android:gravity="center"
            android:textColor="@color/app_on_accent"
            android:textSize="34sp"
            android:textStyle="bold"
            android:visibility="gone" />
    </FrameLayout>

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNav"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:backgroundTint="@color/app_nav_bg"
        app:menu="@menu/bottom_nav" />
</LinearLayout>
"""

FILES['app/src/main/res/layout/pane_dialpad.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/dialMatches"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="8dp"
        android:paddingEnd="8dp">

        <View
            android:layout_width="48dp"
            android:layout_height="1dp" />

        <TextView
            android:id="@+id/dialDisplay"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="start"
            android:gravity="center"
            android:minHeight="64dp"
            android:padding="8dp"
            android:singleLine="true"
            android:textColor="@color/app_text"
            android:textSize="32sp" />

        <ImageButton
            android:id="@+id/btnBackspace"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Backspace"
            android:src="@drawable/ic_backspace" />
    </LinearLayout>

    <GridLayout
        android:id="@+id/keypad"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:columnCount="3"
        android:paddingStart="24dp"
        android:paddingEnd="24dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:orientation="horizontal"
        android:padding="12dp">

        <ImageButton
            android:id="@+id/btnAddContact"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Add to contacts"
            android:src="@drawable/ic_person_add" />

        <ImageButton
            android:id="@+id/btnDialCall"
            android:layout_width="72dp"
            android:layout_height="72dp"
            android:layout_marginStart="32dp"
            android:layout_marginEnd="32dp"
            android:background="@drawable/bg_call"
            android:contentDescription="Call"
            android:src="@drawable/ic_call"
            android:tint="@color/app_on_call" />

        <ImageButton
            android:id="@+id/btnDialSms"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Send message"
            android:src="@drawable/ic_sms" />
    </LinearLayout>
</LinearLayout>
"""

FILES['app/src/main/res/menu/bottom_nav.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_contacts"
        android:icon="@drawable/ic_people"
        android:title="Contacts" />
    <item
        android:id="@+id/nav_favorites"
        android:icon="@drawable/ic_star"
        android:title="Favorites" />
    <item
        android:id="@+id/nav_recents"
        android:icon="@drawable/ic_history"
        android:title="Recents" />
    <item
        android:id="@+id/nav_dialpad"
        android:icon="@drawable/ic_dialpad"
        android:title="Dialpad" />
</menu>
"""

FILES['app/src/main/java/com/example/fastcontacts/CallLogRepository.kt'] = r"""package com.example.fastcontacts

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
"""

FILES['app/src/main/java/com/example/fastcontacts/RecentsController.kt'] = r"""package com.example.fastcontacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

// The "Recents" tab: call history with missed filter, expandable rows and quick actions.
class RecentsController(
    private val activity: AppCompatActivity,
    root: View,
    private val openContact: (Contact) -> Unit,
    private val askPermission: () -> Unit
) {
    private val list: RecyclerView = root.findViewById(R.id.recentsList)
    private val chips: ChipGroup = root.findViewById(R.id.recentsChips)
    private val empty: View = root.findViewById(R.id.emptyRecents)
    private val adapter = RecentsAdapter()
    private var all: List<CallGroup> = emptyList()
    private var missedOnly = false
    private var permitted = false

    init {
        list.layoutManager = LinearLayoutManager(activity)
        list.adapter = adapter

        val inf = LayoutInflater.from(activity)
        val idAll = View.generateViewId()
        val idMissed = View.generateViewId()
        fun addChip(text: String, id: Int) {
            val chip = inf.inflate(R.layout.chip_filter, chips, false) as Chip
            chip.id = id
            chip.text = text
            chip.isCheckable = true
            chips.addView(chip)
        }
        addChip("All", idAll)
        addChip("Missed", idMissed)
        chips.check(idAll)
        chips.setOnCheckedStateChangeListener { _, ids ->
            missedOnly = ids.contains(idMissed)
            apply()
        }
    }

    fun setData(groups: List<CallGroup>, hasPermission: Boolean) {
        all = groups
        permitted = hasPermission
        apply()
    }

    private fun apply() {
        val shown = if (missedOnly) all.filter { CallLogRepository.isMissed(it.type) } else all
        adapter.items = shown
        adapter.notifyDataSetChanged()
        if (!permitted) {
            setEmpty(
                R.drawable.ic_history, "Call history access needed",
                "Allow access so you can see your recent calls here.", "Grant access"
            ) { askPermission() }
            empty.visibility = View.VISIBLE
        } else if (shown.isEmpty()) {
            if (missedOnly) {
                setEmpty(R.drawable.ic_call_missed, "No missed calls", "Calls you miss will show up here.", null, null)
            } else {
                setEmpty(R.drawable.ic_history, "No recent calls", "Your call history will show up here.", null, null)
            }
            empty.visibility = View.VISIBLE
        } else {
            empty.visibility = View.GONE
        }
    }

    private fun setEmpty(icon: Int, title: String, sub: String, btn: String?, run: (() -> Unit)?) {
        empty.findViewById<ImageView>(R.id.emptyIcon).setImageResource(icon)
        empty.findViewById<TextView>(R.id.emptyTitle).text = title
        empty.findViewById<TextView>(R.id.emptySubtitle).text = sub
        val b = empty.findViewById<Button>(R.id.emptyButton)
        if (btn != null && run != null) {
            b.text = btn
            b.visibility = View.VISIBLE
            b.setOnClickListener { run() }
        } else {
            b.visibility = View.GONE
        }
    }

    private fun callBack(g: CallGroup) {
        if (g.number.isEmpty()) Actions.toast(activity, "No number to call") else Actions.call(activity, g.number)
    }

    private fun details(g: CallGroup) {
        val c = g.contact
        if (c != null) openContact(c) else if (g.number.isNotEmpty()) Actions.create(activity, g.number)
    }

    private fun showMenu(g: CallGroup) {
        if (g.number.isEmpty()) return
        val acts = ArrayList<Ui.SheetAction>()
        acts.add(Ui.SheetAction("Call", R.drawable.ic_call) { Actions.call(activity, g.number) })
        acts.add(Ui.SheetAction("Message", R.drawable.ic_sms) { Actions.sms(activity, g.number) })
        acts.add(Ui.SheetAction("Copy number", R.drawable.ic_copy) {
            Actions.copy(activity, g.number)
            Actions.toast(activity, "Number copied")
        })
        if (g.contact == null) {
            acts.add(Ui.SheetAction("Add to contacts", R.drawable.ic_person_add) {
                Actions.create(activity, g.number)
            })
        }
        Ui.showSheet(activity, g.name ?: CallLogRepository.pretty(g.number), acts)
    }

    private inner class RecentsAdapter : RecyclerView.Adapter<RecentVH>() {
        var items: List<CallGroup> = emptyList()

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentVH =
            RecentVH(LayoutInflater.from(parent.context).inflate(R.layout.item_call, parent, false))

        override fun onBindViewHolder(h: RecentVH, position: Int) {
            val ctx = h.itemView.context
            val g = items[position]
            val missed = CallLogRepository.isMissed(g.type)
            val title = g.name ?: CallLogRepository.pretty(g.number)

            val c = g.contact
            if (c != null) h.avatar.bind(c) else h.avatar.bindPlain("#", null)
            h.name.text = if (g.entries.size > 1) title + " (" + g.entries.size + ")" else title
            h.name.setTextColor(Ui.color(ctx, if (missed) R.color.app_danger else R.color.app_text))
            h.typeIcon.setImageResource(CallLogRepository.typeIcon(g.type))
            h.typeIcon.imageTintList = Ui.tint(ctx, if (missed) R.color.app_danger else R.color.app_text_secondary)
            val time = CallLogRepository.shortWhen(ctx, g.entries[0].date)
            h.sub.text = if (g.name != null && g.number.isNotEmpty())
                CallLogRepository.pretty(g.number) + "  \u00B7  " + time else time

            h.back.visibility = if (g.number.isEmpty()) View.INVISIBLE else View.VISIBLE
            h.back.setOnClickListener { callBack(g) }
            h.top.setOnClickListener {
                val p = h.bindingAdapterPosition
                if (p >= 0) {
                    items[p].expanded = !items[p].expanded
                    notifyItemChanged(p)
                }
            }
            h.top.setOnLongClickListener {
                showMenu(g)
                true
            }

            if (!g.expanded) {
                h.panel.visibility = View.GONE
                return
            }
            h.panel.visibility = View.VISIBLE
            val lines = ArrayList<String>()
            for (e in g.entries.take(10)) {
                var line = CallLogRepository.fullWhen(ctx, e.date) + "  \u00B7  " + CallLogRepository.typeLabel(e.type)
                if (!CallLogRepository.isMissed(e.type) && e.duration > 0) {
                    line += "  \u00B7  " + CallLogRepository.duration(e.duration)
                }
                lines.add(line)
            }
            h.detailsText.text = lines.joinToString("\n")

            val hasNumber = g.number.isNotEmpty()
            h.actCall.visibility = if (hasNumber) View.VISIBLE else View.GONE
            h.actMessage.visibility = if (hasNumber) View.VISIBLE else View.GONE
            // Video call only when a video-call app (Google Meet) is installed.
            h.actVideo.visibility =
                if (hasNumber && Actions.isInstalled(activity, Social.meet)) View.VISIBLE else View.GONE
            h.actDetails.visibility = if (c != null || hasNumber) View.VISIBLE else View.GONE
            h.actDetailsText.text = if (c != null) "Details" else "Add contact"
            h.actCall.setOnClickListener { callBack(g) }
            h.actVideo.setOnClickListener { Actions.videoCall(activity, g.number) }
            h.actMessage.setOnClickListener { Actions.sms(activity, g.number) }
            h.actDetails.setOnClickListener { details(g) }
        }
    }
}

class RecentVH(v: View) : RecyclerView.ViewHolder(v) {
    val top: View = v.findViewById(R.id.callTop)
    val avatar: AvatarView = v.findViewById(R.id.callAvatar)
    val name: TextView = v.findViewById(R.id.callName)
    val typeIcon: ImageView = v.findViewById(R.id.callTypeIcon)
    val sub: TextView = v.findViewById(R.id.callSub)
    val back: ImageButton = v.findViewById(R.id.callBack)
    val panel: LinearLayout = v.findViewById(R.id.callPanel)
    val detailsText: TextView = v.findViewById(R.id.callDetails)
    val actCall: View = v.findViewById(R.id.actCall)
    val actVideo: View = v.findViewById(R.id.actVideo)
    val actMessage: View = v.findViewById(R.id.actMessage)
    val actDetails: View = v.findViewById(R.id.actDetails)
    val actDetailsText: TextView = v.findViewById(R.id.actDetailsText)
}
"""

FILES['app/src/main/java/com/example/fastcontacts/T9.kt'] = r"""package com.example.fastcontacts

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
"""

FILES['app/src/main/res/drawable/ic_call_made.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:tint="?attr/colorOnSurfaceVariant"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M9,5v2h6.59L4,18.59 5.41,20 17,8.41V15h2V5z" />
</vector>
"""

FILES['app/src/main/res/drawable/ic_call_missed.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:tint="?attr/colorOnSurfaceVariant"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M19.59,7L12,14.59 6.41,9H11V7H3v8h2v-4.59l7,7 9,-9z" />
</vector>
"""

FILES['app/src/main/res/drawable/ic_call_received.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:tint="?attr/colorOnSurfaceVariant"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M20,5.41L18.59,4 7,15.59V9H5v10h10v-2H8.41z" />
</vector>
"""

FILES['app/src/main/res/drawable/ic_history.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:tint="?attr/colorOnSurfaceVariant"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z" />
</vector>
"""

FILES['app/src/main/res/drawable/ic_person_add.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:tint="?attr/colorOnSurfaceVariant"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M15,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM6,10V7H4v3H1v2h3v3h2v-3h3v-2H6zM15,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z" />
</vector>
"""

FILES['app/src/main/res/layout/item_call.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical">

    <LinearLayout
        android:id="@+id/callTop"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="?attr/selectableItemBackground"
        android:gravity="center_vertical"
        android:minHeight="64dp"
        android:orientation="horizontal"
        android:paddingStart="16dp"
        android:paddingTop="8dp"
        android:paddingEnd="4dp"
        android:paddingBottom="8dp">

        <com.example.fastcontacts.AvatarView
            android:id="@+id/callAvatar"
            android:layout_width="44dp"
            android:layout_height="44dp" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/callName"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="@color/app_text"
                android:textSize="16sp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageView
                    android:id="@+id/callTypeIcon"
                    android:layout_width="14dp"
                    android:layout_height="14dp"
                    android:layout_marginEnd="6dp"
                    android:importantForAccessibility="no" />

                <TextView
                    android:id="@+id/callSub"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:ellipsize="end"
                    android:maxLines="1"
                    android:textColor="@color/app_text_secondary"
                    android:textSize="13sp" />
            </LinearLayout>
        </LinearLayout>

        <ImageButton
            android:id="@+id/callBack"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Call back"
            android:src="@drawable/ic_call" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/callPanel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="76dp"
        android:paddingEnd="16dp"
        android:paddingBottom="8dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/callDetails"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:lineSpacingExtra="4dp"
            android:textColor="@color/app_text_secondary"
            android:textSize="14sp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:orientation="horizontal">

            <LinearLayout
                android:id="@+id/actCall"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackground"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:importantForAccessibility="no"
                    android:src="@drawable/ic_call"
                    android:tint="?attr/colorPrimary" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="Call"
                    android:textColor="?attr/colorPrimary"
                    android:textSize="12sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/actVideo"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackground"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:importantForAccessibility="no"
                    android:src="@drawable/ic_video"
                    android:tint="?attr/colorPrimary" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="Video"
                    android:textColor="?attr/colorPrimary"
                    android:textSize="12sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/actMessage"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackground"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:importantForAccessibility="no"
                    android:src="@drawable/ic_sms"
                    android:tint="?attr/colorPrimary" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="Message"
                    android:textColor="?attr/colorPrimary"
                    android:textSize="12sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/actDetails"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackground"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:importantForAccessibility="no"
                    android:src="@drawable/ic_info"
                    android:tint="?attr/colorPrimary" />

                <TextView
                    android:id="@+id/actDetailsText"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="Details"
                    android:textColor="?attr/colorPrimary"
                    android:textSize="12sp" />
            </LinearLayout>
        </LinearLayout>
    </LinearLayout>
</LinearLayout>
"""

FILES['app/src/main/res/layout/pane_recents.xml'] = r"""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <HorizontalScrollView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:scrollbars="none">

        <com.google.android.material.chip.ChipGroup
            android:id="@+id/recentsChips"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingStart="12dp"
            android:paddingTop="12dp"
            android:paddingEnd="12dp"
            android:paddingBottom="4dp"
            app:selectionRequired="true"
            app:singleLine="true"
            app:singleSelection="true" />
    </HorizontalScrollView>

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recentsList"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:paddingBottom="16dp" />

        <include
            android:id="@+id/emptyRecents"
            layout="@layout/view_empty"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone" />
    </FrameLayout>
</LinearLayout>
"""



def sha(text):
    return hashlib.sha256(text.replace("\r\n", "\n").encode("utf-8")).hexdigest()


def read(p):
    with open(p, "r", encoding="utf-8") as f:
        return f.read()


def write(p, s):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)


def target(rel):
    return os.path.join(*rel.split("/"))


def main():
    if not os.path.isdir(BASE):
        print("ERROR: run this from the folder that contains the 'app' folder.")
        sys.exit(1)
    force = "--force" in sys.argv

    # 1) check first, change nothing if anything looks different
    problems = []
    for rel, want in EXPECTED.items():
        p = target(rel)
        if not os.path.exists(p):
            problems.append("missing: " + p)
        elif sha(read(p)) != want and not os.path.exists(p + BAK):
            problems.append("changed since this upgrade was made: " + p)
    if problems and not force:
        print("STOPPED - nothing was changed. Reason:")
        for x in problems:
            print("  - " + x)
        print("Send me the file(s) listed above and I will adapt the upgrade to them.")
        sys.exit(1)

    # 2) back up, then write
    for rel in FILES:
        p = target(rel)
        if os.path.exists(p) and rel in EXPECTED and not os.path.exists(p + BAK):
            shutil.copy2(p, p + BAK)
    for rel, text in FILES.items():
        write(target(rel), text)
        print(("updated  " if rel in EXPECTED else "created  ") + target(rel))

    print("""
DONE. Existing features were kept.
 * New bottom tab: Recents (call history)
 * Dialpad upgraded (T9 names, formatting, SMS button, tones, paste, redial)
 * The app will ask for the new 'Call logs' permission the first time you open Recents.

Next: commit and push to GitHub so your Action builds the APK:
    git add -A
    git commit -m "Add Recents tab and upgraded dialpad"
    git push
Undo everything:  python upgrade_dialer.py --undo
""")


def undo():
    print("Undoing ...")
    for rel in FILES:
        p = target(rel)
        b = p + BAK
        if rel in EXPECTED:
            if os.path.exists(b):
                shutil.copy2(b, p)
                os.remove(b)
                print("restored " + p)
        elif os.path.exists(p):
            os.remove(p)
            print("removed  " + p)
    print("Done.")


if __name__ == "__main__":
    if "--undo" in sys.argv:
        undo()
    else:
        main()
