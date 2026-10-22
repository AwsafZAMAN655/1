package com.example.fastcontacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
    private lateinit var root: ViewGroup
    private lateinit var header: View
    private lateinit var search: EditText
    private lateinit var btnClear: ImageButton
    private lateinit var chipsScroll: View
    private lateinit var chipGroup: ChipGroup
    private lateinit var list: RecyclerView
    private lateinit var favList: RecyclerView
    private lateinit var paneContacts: View
    private lateinit var paneDialpad: View
    private lateinit var paneDialpad: View
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
        paneDialpad = findViewById(R.id.paneDialpad)
        paneDialpad = findViewById(R.id.paneDialpad)
        emptyContacts = findViewById(R.id.emptyContacts)
        emptyFav = findViewById(R.id.emptyDialpad)
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
                R.id.nav_dialer_recents -> showTab(1)
                R.id.nav_dialer_recents -> showTab(2)
            }
            true
        }

        if (!hasContactsPermission()) requestPermissions()
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
                Manifest.permission.CALL_PHONE
            )
        )
    }

    private fun openDetail(c: Contact) {
        val i = Intent(this, DetailActivity::class.java)
        i.putExtra("contact_id", c.id)
        val opts = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_up_in, R.anim.fade_out_soft)
        startActivity(i, opts.toBundle())
    }

    private fun showTab(t: Int) {
        tab = t
        TransitionManager.beginDelayedTransition(root)
        header.visibility = if (t == 2) View.GONE else View.VISIBLE
        updateChipsVisibility()
        if (t == 2) fab.hide() else fab.show()
        swap(paneContacts, t == 0)
        swap(paneDialpad, t == 1)
        swap(paneDialpad, t == 2)
    }

    private fun swap(pane: View, show: Boolean) {
        if (show) {
            if (pane.visibility != View.VISIBLE) {
                pane.alpha = 0f
                pane.visibility = View.VISIBLE
                pane.animate().alpha(1f).setDuration(160).start()
            }
        } else {
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
            runOnUiThread {
                all = loaded
                groups = g
                sortedAll = sorted
                rebuildChips()
                applyFilter()
                dialpad.setContacts(sorted)
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
                    emptyFav, R.drawable.ic_star_border, "No dialpad yet",
                    "Favorite people you contact often.", null, null
                )
            } else {
                setEmpty(
                    emptyFav, R.drawable.ic_search, "No dialpad found",
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
            if (c.starred) "Remove from dialpad" else "Add to dialpad",
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
