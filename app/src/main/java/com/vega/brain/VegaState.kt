package com.vega.brain

/** Assistant states from the guide, plus the short orb flashes (done, error) and mic muted. */
enum class VegaState(val holdsAudioFocus: Boolean) {
    IDLE(false),
    LISTENING(true),
    THINKING(true),
    SPEAKING(true),
    CONFIRMING(true), // a message draft waits for "send", "edit" or "cancel"
    CHOOSING(true),   // results on screen, waiting for "number two"
    IN_CALL(false),
    DONE(false),
    ERROR(false),
    MUTED(false),
}
