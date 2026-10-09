package com.example.fastcontacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

sealed class ListRow {
    data class Header(val letter: String) : ListRow()
    data class Item(val contact: Contact, val showPhone: Boolean, val showEmail: Boolean) : ListRow()
}

class ContactAdapter(
    private val onClick: (Contact) -> Unit,
    private val onCallClick: (Contact) -> Unit,
    private val onSmsClick: (Contact) -> Unit,
    private val onLongClick: (Contact) -> Unit = {},
    private val onFavClick: (Contact) -> Unit = {}
) : ListAdapter<ListRow, RecyclerView.ViewHolder>(Diff()) {

    private class Diff : DiffUtil.ItemCallback<ListRow>() {
        override fun areItemsTheSame(a: ListRow, b: ListRow): Boolean =
            (a is ListRow.Header && b is ListRow.Header && a.letter == b.letter) ||
                (a is ListRow.Item && b is ListRow.Item && a.contact.id == b.contact.id)

        override fun areContentsTheSame(a: ListRow, b: ListRow): Boolean = a == b
    }

    fun submit(
        list: List<Contact>,
        showHeaders: Boolean = false,
        byLast: Boolean = false,
        showPhone: Boolean = true,
        showEmail: Boolean = true,
        commit: (() -> Unit)? = null
    ) {
        val out = ArrayList<ListRow>()
        var lastLetter = ""
        for (c in list) {
            if (showHeaders) {
                val letter = SearchEngine.letterOf(c, byLast)
                if (letter != lastLetter) {
                    out.add(ListRow.Header(letter))
                    lastLetter = letter
                }
            }
            out.add(ListRow.Item(c, showPhone, showEmail))
        }
        submitList(out, Runnable { commit?.invoke() })
    }

    fun headerPositions(): Map<String, Int> {
        val m = LinkedHashMap<String, Int>()
        currentList.forEachIndexed { i, r -> if (r is ListRow.Header) m[r.letter] = i }
        return m
    }

    override fun getItemViewType(position: Int): Int = if (getItem(position) is ListRow.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) HeaderVH(inf.inflate(R.layout.item_header, parent, false))
        else VH(inf.inflate(R.layout.item_contact, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = getItem(position)
        if (holder is HeaderVH && row is ListRow.Header) {
            holder.text.text = row.letter
            return
        }
        if (holder !is VH || row !is ListRow.Item) return
        val c = row.contact
        holder.avatar.bind(c)
        holder.name.text = c.name

        val first = c.phones.firstOrNull()?.number
        if (row.showPhone && first != null) {
            holder.number.visibility = View.VISIBLE
            holder.number.text = if (c.phones.size > 1) first + "  (+" + (c.phones.size - 1) + " more)" else first
        } else {
            holder.number.visibility = View.GONE
        }
        val email = c.emails.firstOrNull()?.address
        if (row.showEmail && !email.isNullOrEmpty()) {
            holder.email.text = email
            holder.email.visibility = View.VISIBLE
        } else {
            holder.email.visibility = View.GONE
        }
        holder.star.visibility = if (c.starred) View.VISIBLE else View.GONE
        holder.star.imageTintList = Ui.tint(holder.itemView.context, R.color.app_star)

        val hasPhone = c.phones.isNotEmpty()
        holder.btnCall.visibility = if (hasPhone) View.VISIBLE else View.GONE
        holder.btnSms.visibility = if (hasPhone) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onClick(c) }
        holder.itemView.setOnLongClickListener {
            onLongClick(c)
            true
        }
        holder.btnCall.setOnClickListener { onCallClick(c) }
        holder.btnSms.setOnClickListener { onSmsClick(c) }
        holder.star.setOnClickListener { onFavClick(c) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: AvatarView = view.findViewById(R.id.avatar)
        val name: TextView = view.findViewById(R.id.name)
        val number: TextView = view.findViewById(R.id.number)
        val email: TextView = view.findViewById(R.id.email)
        val star: ImageButton = view.findViewById(R.id.starBadge)
        val btnCall: ImageButton = view.findViewById(R.id.btnCall)
        val btnSms: ImageButton = view.findViewById(R.id.btnSms)
    }

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.headerText)
    }
}
