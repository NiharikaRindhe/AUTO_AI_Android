package com.vega.llm

object TextNorm {
    private val WAKE = Regex("""^(?:hey|hi|ok|okay)\s+vega\b[,\s]*""", RegexOption.IGNORE_CASE)
    private val NOT_WORD = Regex("""[^a-z0-9 ]""")
    private val SPACES = Regex("""\s+""")

    /** Keeps the user's casing; drops the wake phrase and trailing punctuation. */
    fun clean(text: String): String =
        text.trim().replace(WAKE, "").trim().trimEnd('.', '!', '?', ',').trim()

    /** Lowercase key for fixed-phrase lookup: "Don't send it!" → "dont send it". */
    fun key(text: String): String = clean(text).lowercase()
        .replace(NOT_WORD, "")
        .replace(SPACES, " ")
        .trim()
        .removePrefix("please ")
        .removeSuffix(" please")
}
