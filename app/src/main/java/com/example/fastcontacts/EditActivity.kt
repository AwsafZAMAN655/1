package com.example.fastcontacts

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

class EditActivity : AppCompatActivity() {

    private var existing: Contact? = null
    private lateinit var content: LinearLayout
    private lateinit var avatar: AvatarView
    private var photoBytes: ByteArray? = null
    private lateinit var etFirst: EditText
    private lateinit var etLast: EditText
    private lateinit var etCompany: EditText
    private lateinit var phonesBox: LinearLayout
    private lateinit var emailsBox: LinearLayout
    private lateinit var extrasBox: LinearLayout

    private class TypedRow(var type: Int, val edit: EditText, val view: View)

    private val phoneRows = ArrayList<TypedRow>()
    private val emailRows = ArrayList<TypedRow>()
    private val extraInputs = LinkedHashMap<String, EditText>()

    private val phoneNames = listOf("Mobile", "Home", "Work", "Main", "Other")
    private val phoneInts = listOf(2, 1, 3, 12, 7)
    private val emailNames = listOf("Home", "Work", "Mobile", "Other")
    private val emailInts = listOf(1, 2, 4, 3)
    private val extraKeys = listOf("Job title", "Nickname", "Address", "Website", "Birthday", "Anniversary", "Notes")

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        Ui.systemBars(this)
        content = findViewById(R.id.editContent)
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }

        val id = intent.getLongExtra("contact_id", -1L)
        if (id >= 0) {
            findViewById<TextView>(R.id.editTitle).text = "Edit contact"
            Thread {
                val c = ContactsRepository.loadById(this, id)
                runOnUiThread {
                    if (c == null) {
                        finish()
                    } else {
                        existing = c
                        build(c)
                    }
                }
            }.start()
        } else {
            findViewById<TextView>(R.id.editTitle).text = "New contact"
            build(null)
        }
    }

    private fun dp(v: Int): Int = Ui.dp(this, v)

    private fun field(hint: String, inputType: Int, value: String): Pair<View, EditText> {
        val v = LayoutInflater.from(this).inflate(R.layout.field_input, content, false)
        v.findViewById<TextInputLayout>(R.id.fieldLayout).hint = hint
        val et = v.findViewById<EditText>(R.id.fieldEdit)
        et.inputType = inputType
        et.setText(value)
        return Pair(v, et)
    }

    private fun header(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 14f
        t.setTextColor(Ui.color(this, R.color.app_accent))
        t.setPadding(0, dp(20), 0, 0)
        return t
    }

    private fun textButton(label: String, onClick: () -> Unit): Button {
        val b = LayoutInflater.from(this).inflate(R.layout.btn_text, content, false) as Button
        b.text = label
        b.setOnClickListener { onClick() }
        return b
    }

    private fun build(c: Contact?) {
        content.removeAllViews()
        phoneRows.clear()
        emailRows.clear()
        extraInputs.clear()

        // photo
        val photoBox = LinearLayout(this)
        photoBox.orientation = LinearLayout.VERTICAL
        photoBox.gravity = Gravity.CENTER_HORIZONTAL
        avatar = AvatarView(this)
        avatar.layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
        if (c != null) avatar.bind(c, true) else avatar.bindPlain("?", null)
        avatar.setOnClickListener { pickPhoto.launch("image/*") }
        photoBox.addView(avatar)
        photoBox.addView(textButton("Add photo") { pickPhoto.launch("image/*") })
        content.addView(photoBox)

        val cap = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        val first = field("First name", cap, c?.firstName ?: "")
        etFirst = first.second
        content.addView(first.first)
        val last = field("Last name", cap, c?.lastName ?: "")
        etLast = last.second
        content.addView(last.first)
        val comp = field("Company", cap, c?.company ?: "")
        etCompany = comp.second
        content.addView(comp.first)

        content.addView(header("Phone"))
        phonesBox = LinearLayout(this)
        phonesBox.orientation = LinearLayout.VERTICAL
        content.addView(phonesBox)
        val phones = c?.phones ?: emptyList()
        val prefill = intent.getStringExtra("phone")
        if (phones.isEmpty()) {
            addTypedRow(phonesBox, phoneRows, phoneNames, phoneInts, "Phone", InputType.TYPE_CLASS_PHONE, 2, prefill ?: "")
        } else {
            for (p in phones) {
                addTypedRow(phonesBox, phoneRows, phoneNames, phoneInts, "Phone", InputType.TYPE_CLASS_PHONE, p.typeInt, p.number)
            }
        }
        content.addView(textButton("+ Add phone") {
            addTypedRow(phonesBox, phoneRows, phoneNames, phoneInts, "Phone", InputType.TYPE_CLASS_PHONE, 2, "")
        })

        content.addView(header("Email"))
        emailsBox = LinearLayout(this)
        emailsBox.orientation = LinearLayout.VERTICAL
        content.addView(emailsBox)
        val emails = c?.emails ?: emptyList()
        val emailType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        if (emails.isEmpty()) {
            addTypedRow(emailsBox, emailRows, emailNames, emailInts, "Email", emailType, 1, "")
        } else {
            for (e in emails) {
                addTypedRow(emailsBox, emailRows, emailNames, emailInts, "Email", emailType, e.typeInt, e.address)
            }
        }
        content.addView(textButton("+ Add email") {
            addTypedRow(emailsBox, emailRows, emailNames, emailInts, "Email", emailType, 1, "")
        })

        extrasBox = LinearLayout(this)
        extrasBox.orientation = LinearLayout.VERTICAL
        content.addView(extrasBox)
        if (c != null) {
            if (c.jobTitle.isNotEmpty()) addExtra("Job title", c.jobTitle)
            if (c.nickname.isNotEmpty()) addExtra("Nickname", c.nickname)
            c.addresses.firstOrNull()?.let { addExtra("Address", it.value) }
            c.websites.firstOrNull()?.let { addExtra("Website", it.value) }
            c.events.firstOrNull { it.type == 3 }?.let { addExtra("Birthday", it.value) }
            c.events.firstOrNull { it.type == 1 }?.let { addExtra("Anniversary", it.value) }
            if (c.notes.isNotEmpty()) addExtra("Notes", c.notes)
        }
        content.addView(textButton("Add field") { chooseExtra() })
    }

    private fun addTypedRow(
        box: LinearLayout, rows: MutableList<TypedRow>, names: List<String>, ints: List<Int>,
        hint: String, inputType: Int, type: Int, text: String
    ) {
        val v = LayoutInflater.from(this).inflate(R.layout.row_edit_phone, box, false)
        val btn = v.findViewById<Button>(R.id.rowType)
        v.findViewById<TextInputLayout>(R.id.fieldLayout).hint = hint
        val et = v.findViewById<EditText>(R.id.fieldEdit)
        et.inputType = inputType
        et.setText(text)
        val row = TypedRow(if (ints.contains(type)) type else ints.last(), et, v)
        btn.text = names[ints.indexOf(row.type)]
        btn.setOnClickListener {
            AlertDialog.Builder(this).setItems(names.toTypedArray()) { _, i ->
                row.type = ints[i]
                btn.text = names[i]
            }.show()
        }
        v.findViewById<ImageButton>(R.id.rowRemove).setOnClickListener {
            if (rows.size > 1) {
                box.removeView(v)
                rows.remove(row)
            } else {
                et.setText("")
            }
        }
        box.addView(v)
        rows.add(row)
    }

    private fun chooseExtra() {
        val free = extraKeys.filter { !extraInputs.containsKey(it) }
        if (free.isEmpty()) {
            Actions.toast(this, "All fields are already added")
            return
        }
        AlertDialog.Builder(this).setTitle("Add field")
            .setItems(free.toTypedArray()) { _, i -> addExtra(free[i], "") }.show()
    }

    private fun addExtra(key: String, value: String) {
        if (extraInputs.containsKey(key)) return
        val type = when (key) {
            "Address", "Notes" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            "Website" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            "Birthday", "Anniversary" -> InputType.TYPE_NULL
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val f = field(key, type, value)
        if (key == "Birthday" || key == "Anniversary") {
            f.second.isFocusable = false
            f.second.setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(this, { _, y, m, d ->
                    f.second.setText(String.format("%04d-%02d-%02d", y, m + 1, d))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        extrasBox.addView(f.first)
        extraInputs[key] = f.second
    }

    private fun loadPhoto(uri: Uri) {
        Thread {
            val bmp = Photos.decodeSquare(this, uri, 512)
            runOnUiThread {
                if (bmp == null) {
                    Actions.toast(this, "Could not load that image")
                } else {
                    photoBytes = Photos.toJpeg(bmp)
                    avatar.setPhotoBitmap(bmp)
                }
            }
        }.start()
    }

    private fun extra(key: String): String = extraInputs[key]?.text?.toString() ?: ""

    private fun save() {
        val f = ContactForm()
        f.given = etFirst.text.toString().trim()
        f.family = etLast.text.toString().trim()
        f.company = etCompany.text.toString().trim()
        f.phones = phoneRows.map { Pair(it.type, it.edit.text.toString().trim()) }.filter { it.second.isNotEmpty() }
        f.emails = emailRows.map { Pair(it.type, it.edit.text.toString().trim()) }.filter { it.second.isNotEmpty() }
        f.jobTitle = extra("Job title").trim()
        f.nickname = extra("Nickname").trim()
        f.address = extra("Address").trim()
        f.website = extra("Website").trim()
        f.birthday = extra("Birthday").trim()
        f.anniversary = extra("Anniversary").trim()
        f.notes = extra("Notes").trim()
        f.photo = photoBytes

        if (f.given.isEmpty() && f.family.isEmpty() && f.company.isEmpty() && f.phones.isEmpty() && f.emails.isEmpty()) {
            Actions.toast(this, "Add a name, phone number or email")
            return
        }
        val old = existing
        Thread {
            val ok = if (old == null) ContactWriter.insert(this, f) else ContactWriter.update(this, old.raws[0].id, f)
            runOnUiThread {
                if (ok) {
                    Actions.toast(this, "Saved")
                    finish()
                } else {
                    Actions.toast(this, "Could not save contact")
                }
            }
        }.start()
    }
}
