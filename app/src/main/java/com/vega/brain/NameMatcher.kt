package com.vega.brain

sealed interface ContactMatch {
    data class One(val contact: Contact) : ContactMatch
    data class Many(val options: List<Contact>) : ContactMatch
    data object None : ContactMatch
    data object NoPermission : ContactMatch
}

/**
 * Matches spoken names against contacts (design rule 4: names are resolved in code, not by the model).
 * Tolerates common Indian spelling variants: Gargi / Gargee, Shreya / Sreya, Vaibhav / Waibhav.
 */
object NameMatcher {
    private const val THRESHOLD = 0.72
    private const val TIE = 0.03

    private val ALIASES = listOf(
        listOf("mom", "mummy", "mumma", "mother", "maa", "aai", "amma"),
        listOf("dad", "daddy", "papa", "pappa", "father", "baba", "pitaji"),
    )

    private val DIGRAPHS = listOf(
        "ee" to "i", "ii" to "i", "oo" to "u", "uu" to "u",
        "th" to "t", "dh" to "d", "bh" to "b", "kh" to "k", "gh" to "g", "ph" to "f", "sh" to "s", "w" to "v",
    )
    private val NOT_NAME = Regex("""[^a-z0-9 ]""")
    private val REPEATS = Regex("""(.)\1+""")

    fun match(query: String, contacts: List<Contact>): ContactMatch {
        val q = simplify(query.lowercase().trim().removePrefix("my "))
        if (q.isEmpty() || contacts.isEmpty()) return ContactMatch.None
        val aliasGroup = ALIASES.firstOrNull { group -> group.any { simplify(it) == q } }?.map(::simplify)

        val scored = contacts
            .map { it to score(q, it.name, aliasGroup) }
            .filter { it.second >= THRESHOLD }
            .sortedByDescending { it.second }
        if (scored.isEmpty()) return ContactMatch.None

        val best = scored.first().second
        val top = scored.filter { it.second >= best - TIE }.map { it.first }
        return if (top.size == 1) ContactMatch.One(top.first()) else ContactMatch.Many(top.take(3))
    }

    /** 0..1 similarity of two names or labels after spelling normalisation. */
    fun closeness(a: String, b: String): Double = similarity(simplify(a), simplify(b))

    private fun score(q: String, name: String, aliasGroup: List<String>?): Double {
        val full = simplify(name)
        val tokens = full.split(' ').filter { it.isNotEmpty() }
        if (aliasGroup != null && tokens.any { it in aliasGroup }) return 1.0
        if (full == q) return 1.0
        val bestToken = tokens.maxOfOrNull { similarity(q, it) } ?: 0.0
        return maxOf(similarity(q, full), bestToken * 0.95)
    }

    fun simplify(s: String): String {
        var out = s.lowercase().replace(NOT_NAME, " ").trim().replace(Regex("""\s+"""), " ")
        for ((from, to) in DIGRAPHS) out = out.replace(from, to)
        return out.replace(REPEATS, "$1")
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
