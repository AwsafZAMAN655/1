package com.example.fastcontacts

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
    private var engine = SearchEngine(emptyList())
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
            v.setOnTouchListener { view, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                }
                false
            }
            v.setOnClickListener {
                Ui.tap(v)
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

        root.findViewById<ImageButton>(R.id.btnDialCall).setOnClickListener {
            if (number.isNotEmpty()) Actions.call(activity, number.toString())
        }
        root.findViewById<ImageButton>(R.id.btnAddContact).setOnClickListener {
            if (number.isNotEmpty()) Actions.create(activity, number.toString())
            else Actions.toast(activity, "Enter a number first")
        }
        val back = root.findViewById<ImageButton>(R.id.btnBackspace)
        back.setOnClickListener {
            if (number.isNotEmpty()) number.deleteCharAt(number.length - 1)
            refresh()
        }
        back.setOnLongClickListener {
            number.setLength(0)
            refresh()
            true
        }
        refresh()
    }

    fun setContacts(list: List<Contact>) {
        engine = SearchEngine(list)
        refresh()
    }

    private fun refresh() {
        display.text = number.toString()
        display.textSize = if (number.length > 14) 24f else 32f
        if (number.isEmpty()) adapter.submit(emptyList())
        else adapter.submit(engine.search(number.toString()).take(20))
    }
}
