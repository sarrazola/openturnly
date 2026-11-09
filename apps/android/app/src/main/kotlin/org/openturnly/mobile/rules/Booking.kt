package org.openturnly.mobile.rules

import org.openturnly.mobile.api.Location

/** Booking from the phone: what «Crearlo» fills in, and where a booking can happen. */
object Booking {
    /** A new customer started from a search that found nobody. */
    data class Draft(val firstName: String = "", val lastName: String = "", val phone: String = "", val email: String = "")

    /** An e-mail when the query has an «@», a mobile when it has six digits or more, else a name. */
    fun draft(query: String): Draft {
        val text = query.trim()
        if (text.contains('@')) return Draft(email = text)
        val digits = text.filter { it.isDigit() }
        if (digits.length >= 6) return Draft(phone = digits)
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return Draft(firstName = words.firstOrNull() ?: "", lastName = words.drop(1).joinToString(" "))
    }

    /**
     * The locations to book at: the active ones (and [keeping], the one an
     * appointment already has, even if it was closed since), and for a
     * professional only those they work at.
     */
    fun places(locations: List<Location>, keeping: String? = null, professionalId: String? = null): List<Location> = locations.filter { location ->
        (location.active || location.id == keeping) &&
            (professionalId == null || location.resourceIds.isEmpty() || professionalId in location.resourceIds)
    }
}
