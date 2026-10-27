package com.vega.brain

/** Spoken and toast text (design rule 5: confirmations come from templates, never the model). */
object Replies {
    const val DIDNT_CATCH = "Sorry, I didn't catch that."
    const val CANT_YET = "Sorry, I can't do that yet."
    const val MISSING_PERMISSION = "I need a permission for that. Please allow it in the Vega app."
    const val APP_MISSING = "That app isn't installed."
    const val MIC_PERMISSION = "Vega needs microphone permission."
    const val HELP = "You can ask me to play music, navigate, call or message someone, open apps, or control the AC and windows."
    const val WEATHER_LATER = "Weather needs the online service, which isn't set up yet."
    const val SELECT_NOT_READY = "Please tap the one you want. Choosing by number is coming soon."
    const val MEDIA_INFO_NOT_READY = "I can't see what's playing yet."
    const val NAV_STOP_NOT_READY = "I can't stop navigation yet. Please tap the X in Maps."
    const val NOTHING_TO_CHOOSE = "There's nothing to choose from right now."
    const val NO_DRAFT = "There's no message open."
    const val DRAFT_CANCELLED = "Okay, I've cancelled the message."
    const val ASK_EDIT = "What should I add or change?"
    const val ASK_RECIPIENT = "Who should I send it to?"
    const val ASK_NUMBER = "Which number should I call?"
    const val ASK_DEST = "Where would you like to go?"
    const val ASK_WHAT_TO_PLAY = "What would you like to play?"
    const val ASK_APP = "Which app?"
    const val READ_MESSAGES_LATER = "Reading your messages is coming soon."
    const val NEED_WRITE_SETTINGS = "Please allow Vega to change system settings, then ask again."

    fun greeting(name: String, hour: Int) = when (hour) {
        in 4..11 -> "Good morning, $name!"
        in 12..16 -> "Good afternoon, $name!"
        else -> "Good evening, $name!"
    }

    fun goodbye(name: String) = "Bye $name, have a great day!"

    // Apps and system
    fun opening(label: String) = "Opening $label."
    fun closing(label: String) = "Closing $label."
    fun appNotFound(name: String) = "I couldn't find an app called $name."
    fun openingSettings(label: String) = "Opening $label settings."
    fun volume(pct: Int) = "Volume $pct%"
    fun brightness(pct: Int) = "Brightness $pct%"

    // Media
    fun mediaResults(query: String, app: String) = "Here's $query on $app. Which one?"
    fun playingOn(query: String, app: String) = "Playing $query on $app."
    fun mediaToast(action: String) = when (action) {
        "pause" -> "Paused"
        "play", "resume" -> "Playing"
        "next" -> "Next"
        "prev", "previous" -> "Previous"
        else -> "Done"
    }

    // Navigation
    fun navigatingTo(dest: String) = "Starting navigation to $dest."
    fun placeNotSaved(name: String) = "Your $name address isn't saved yet. Add it in the Vega app."
    fun nearby(what: String) = "Showing $what near you. Which one?"

    // Calls and contacts
    fun calling(name: String) = "Calling $name."
    fun contactNotFound(name: String) = "I couldn't find $name in your contacts."
    fun whichContact(names: List<String>) = "I found ${spokenList(names)}. Which one?"
    fun noSuchOption(count: Int) = "Please say a number from 1 to $count."

    // Messages
    fun askBody(name: String) = "What should the message to $name say?"
    fun draftReady(name: String, body: String) = "Message to $name: ${body.trimEnd('.', '!', '?')}. Should I send it?"
    fun draftUpdated(body: String) = "Now it says: ${body.trimEnd('.', '!', '?')}. Send it?"
    fun readDraft(name: String, body: String) = "Your message to $name says: ${body.trimEnd('.', '!', '?')}."
    fun sending(name: String) = "Sending to $name."
    fun whatsappReady(name: String) = "I've opened WhatsApp with your message to $name. Please tap send."
    fun privacy(mode: PrivacyMode) = when (mode) {
        PrivacyMode.ON -> "Privacy is on. I'll only say who messaged."
        PrivacyMode.OFF -> "Privacy is off. I'll read messages aloud."
        PrivacyMode.ASK -> "Okay, I'll ask before reading messages."
    }

    // Car (simulated)
    fun window(pos: String, pct: Int) = when {
        pct <= 0 -> "Closing ${windowLabel(pos)}."
        pct >= 100 -> "Opening ${windowLabel(pos)} fully."
        else -> "Opening ${windowLabel(pos)} to $pct%."
    }
    fun windowNudged(pos: String, opening: Boolean) = if (opening) "Opening ${windowLabel(pos)} a little." else "Closing ${windowLabel(pos)} a little."
    fun acPower(on: Boolean) = if (on) "Turning the AC on." else "Turning the AC off."
    fun acTemp(temp: Int, turnedOn: Boolean) = if (turnedOn) "AC on at $temp degrees." else "Setting the AC to $temp degrees."
    fun fan(level: Int) = "Fan speed $level."
    fun ambient(color: String) = "Ambient light set to $color."

    private fun windowLabel(pos: String) = when (pos) {
        "all" -> "all the windows"
        "front" -> "the front windows"
        "rear" -> "the rear windows"
        "rear_left" -> "the rear left window"
        "rear_right" -> "the rear right window"
        "passenger" -> "the passenger window"
        else -> "the driver window"
    }

    private fun spokenList(items: List<String>) = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }
}
