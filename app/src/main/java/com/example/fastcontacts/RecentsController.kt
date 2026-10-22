package com.example.fastcontacts

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
