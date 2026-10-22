package com.example.fastcontacts

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
