package com.vega.brain

import java.time.LocalTime

enum class PrivacyMode(val key: String) { ON("on"), OFF("off"), ASK("ask") }

enum class ChoiceKind { MEDIA, NAV, CONTACT }

data class Contact(val name: String, val number: String)

data class MessageDraft(
    val channel: String, // "sms" or "whatsapp"
    val contactName: String,
    val number: String,
    val body: String,
    val lastAdded: String? = null,
)

/** Two contacts matched; the original call or message waits for "number two" or a name. */
data class PendingContact(val kind: Kind, val options: List<Contact>, val body: String = "", val channel: String = "sms") {
    enum class Kind { CALL, MESSAGE }
}

/** What the brain knows about the conversation so far. Touched only from the main thread. */
class DialogContext {
    var driverName = "Niharika"
    var privacy = PrivacyMode.ASK
    var speedKmh = 0
    var online = false
    var mediaApp: String? = null
    var draft: MessageDraft? = null
    var choosing: ChoiceKind? = null
    var pendingContact: PendingContact? = null

    /** The `[ctx …]` header from the guide, exactly as the fine-tuned model will see it. */
    fun toCtxLine(now: LocalTime): String =
        "[ctx t=%02d:%02d spd=%d drv=%s priv=%s net=%d media=%s draft=%s]".format(
            now.hour, now.minute, speedKmh, driverName.substringBefore(' '), privacy.key,
            if (online) 1 else 0, mediaApp ?: "none", if (draft != null) "open" else "none",
        )
}
