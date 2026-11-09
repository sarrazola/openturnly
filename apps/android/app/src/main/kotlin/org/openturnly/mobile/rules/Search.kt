package org.openturnly.mobile.rules

import java.text.Normalizer

/**
 * Searching a list as you type, the same way on every platform: every word of
 * the query has to appear somewhere in the fields, in any order, ignoring
 * accents and case. A word made only of digits (three or more) also matches the
 * fields' digits, so "300 123" or "3001234" finds "+57 300 123 4567".
 */
object Search {
    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    /** Lower case without accents (and full-width forms), so "andres" finds "Andrés". */
    fun fold(text: String): String = marks.replace(Normalizer.normalize(text, Normalizer.Form.NFKD), "").lowercase()

    /** Whether every word of [query] appears in [fields]; an empty query matches everything. */
    fun matches(query: String, fields: List<String?>): Boolean {
        val words = fold(query).split(spaces).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = fold(fields.filterNotNull().joinToString(" "))
        val digits = haystack.filter { it.isDigit() }
        return words.all { word ->
            haystack.contains(word) || (word.length >= 3 && word.all { it.isDigit() } && digits.contains(word))
        }
    }

    /** A customer is found by name, email, mobile or document. */
    fun customer(query: String, name: String, email: String?, phone: String?, document: String?): Boolean =
        matches(query, listOf(name, email, phone, document))

    /** «#»: the section of names that don't start with a letter from A to Z. */
    const val OTHER = "#"

    /** The A–Z section a name goes under: its first letter without accents ("Álvaro" → "A"), «#» otherwise. */
    fun section(name: String): String {
        val first = fold(name.trim()).firstOrNull() ?: return OTHER
        return if (first in 'a'..'z') first.uppercase() else OTHER
    }

    /**
     * Groups a list under A–Z sections, then «#» last. Inside a section the
     * names read alphabetically, accents ignored; equal names keep their order.
     */
    fun <T> sections(items: List<T>, name: (T) -> String): List<Pair<String, List<T>>> =
        items.groupBy { section(name(it)) }
            .map { (key, group) -> key to group.sortedBy { fold(name(it).trim()) } }
            .sortedWith(compareBy({ it.first == OTHER }, { it.first }))
}
