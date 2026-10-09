package com.example.fastcontacts

data class Phone(val number: String, val type: String, val typeInt: Int = 0)
data class Email(val address: String, val type: String, val typeInt: Int = 0)
data class LabeledValue(val value: String, val label: String, val type: Int = 0)
data class RawInfo(val id: Long, val accountName: String?, val accountType: String?)
data class ContactGroup(
    val id: Long,
    val title: String,
    val accountName: String?,
    val accountType: String?
)

// name      = Android display name
// firstName = StructuredName.GIVEN_NAME
// lastName  = StructuredName.FAMILY_NAME
data class Contact(
    val id: Long,
    val name: String,
    val firstName: String = "",
    val lastName: String = "",
    val phones: List<Phone> = emptyList(),
    val emails: List<Email> = emptyList(),
    val starred: Boolean = false,
    val lookupKey: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val department: String = "",
    val nickname: String = "",
    val notes: String = "",
    val addresses: List<LabeledValue> = emptyList(),
    val websites: List<LabeledValue> = emptyList(),
    val events: List<LabeledValue> = emptyList(),
    val relations: List<LabeledValue> = emptyList(),
    val groupIds: Set<Long> = emptySet(),
    val raws: List<RawInfo> = emptyList(),
    val photoThumb: String? = null,
    val photoFull: String? = null,
    val hasMultiRows: Boolean = false
)
