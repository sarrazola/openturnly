package org.openturnly.mobile.rules

import java.util.Locale

/**
 * Phone numbers: the server keeps them in E.164 ("+573001234567") and staff
 * bookings must send the country code, so the input pairs a country with the
 * national number. Flags are the emoji an ISO code maps to; no assets needed.
 */
object Phones {
    data class Country(val iso: String, val code: String) {
        val flag: String get() = flag(iso)
        val name: String get() = name(iso)
    }

    private const val TABLE = "AD,376 AE,971 AF,93 AG,1268 AI,1264 AL,355 AM,374 AO,244 AR,54 AS,1684 AT,43 AU,61 AW,297 AZ,994 BA,387 BB,1246 BD,880 BE,32 " +
        "BF,226 BG,359 BH,973 BI,257 BJ,229 BM,1441 BN,673 BO,591 BR,55 BS,1242 BT,975 BW,267 BY,375 BZ,501 CA,1 CD,243 CF,236 CG,242 CH,41 CI,225 CK,682 " +
        "CL,56 CM,237 CN,86 CO,57 CR,506 CU,53 CV,238 CW,599 CY,357 CZ,420 DE,49 DJ,253 DK,45 DM,1767 DO,1809 DZ,213 EC,593 EE,372 EG,20 ER,291 ES,34 " +
        "ET,251 FI,358 FJ,679 FM,691 FO,298 FR,33 GA,241 GB,44 GD,1473 GE,995 GF,594 GH,233 GI,350 GL,299 GM,220 GN,224 GP,590 GQ,240 GR,30 GT,502 " +
        "GU,1671 GW,245 GY,592 HK,852 HN,504 HR,385 HT,509 HU,36 ID,62 IE,353 IL,972 IN,91 IQ,964 IR,98 IS,354 IT,39 JM,1876 JO,962 JP,81 KE,254 KG,996 " +
        "KH,855 KI,686 KM,269 KN,1869 KP,850 KR,82 KW,965 KY,1345 KZ,7 LA,856 LB,961 LC,1758 LI,423 LK,94 LR,231 LS,266 LT,370 LU,352 LV,371 LY,218 " +
        "MA,212 MC,377 MD,373 ME,382 MG,261 MH,692 MK,389 ML,223 MM,95 MN,976 MO,853 MQ,596 MR,222 MS,1664 MT,356 MU,230 MV,960 MW,265 MX,52 MY,60 " +
        "MZ,258 NA,264 NC,687 NE,227 NG,234 NI,505 NL,31 NO,47 NP,977 NR,674 NZ,64 OM,968 PA,507 PE,51 PF,689 PG,675 PH,63 PK,92 PL,48 PR,1787 PS,970 " +
        "PT,351 PW,680 PY,595 QA,974 RE,262 RO,40 RS,381 RU,7 RW,250 SA,966 SB,677 SC,248 SD,249 SE,46 SG,65 SI,386 SK,421 SL,232 SM,378 SN,221 SO,252 " +
        "SR,597 SS,211 ST,239 SV,503 SX,1721 SY,963 SZ,268 TC,1649 TD,235 TG,228 TH,66 TJ,992 TL,670 TM,993 TN,216 TO,676 TR,90 TT,1868 TV,688 TW,886 " +
        "TZ,255 UA,380 UG,256 US,1 UY,598 UZ,998 VC,1784 VE,58 VG,1284 VI,1340 VN,84 VU,678 WS,685 YE,967 ZA,27 ZM,260 ZW,263"

    val countries: List<Country> = TABLE.split(" ").map { entry -> entry.split(",").let { Country(it[0], it[1]) } }

    // When several countries share a dial code, a stored number cannot tell them
    // apart; pick the most common owner of the code.
    private val sharedPreference = mapOf("1" to "US", "7" to "RU", "44" to "GB", "61" to "AU", "212" to "MA", "262" to "RE", "590" to "GP", "599" to "CW")
    private val byLength = countries.sortedByDescending { it.code.length }

    fun flag(iso: String): String = iso.uppercase().map { String(Character.toChars(0x1F1A5 + it.code)) }.joinToString("")
    fun name(iso: String): String =
        runCatching { Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.getDefault()) }.getOrNull()?.ifEmpty { null } ?: iso
    fun country(iso: String?): Country? = iso?.let { code -> countries.firstOrNull { it.iso == code.uppercase() } }

    val sortedByName: List<Country> get() = countries.sortedBy { it.name.lowercase(Locale.getDefault()) }

    /** The business's country first, then the phone's region, then Colombia. */
    fun defaultCountry(businessCountry: String?): Country =
        country(businessCountry) ?: country(Locale.getDefault().country) ?: country("CO")!!

    /** Splits a stored number into its country and national digits. */
    fun split(phone: String?): Pair<Country, String>? {
        val digits = phone?.filter { it.isDigit() } ?: return null
        if (digits.isEmpty()) return null
        for (entry in byLength) if (digits.startsWith(entry.code)) {
            val preferred = sharedPreference[entry.code]?.let { country(it) } ?: entry
            return preferred to digits.drop(entry.code.length)
        }
        return null
    }

    /** What the server wants: "+57 3001234567". Null when the national part is empty. */
    fun compose(country: Country, national: String): String? {
        var digits = national.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        // Someone pasted the full number with its code.
        if (national.trim().startsWith("+") && digits.startsWith(country.code)) digits = digits.drop(country.code.length)
        return "+${country.code} $digits"
    }

    private fun groupNational(digits: String): String {
        val groups = ArrayList<String>()
        var rest = digits
        while (rest.length > 4) { groups.add(rest.take(3)); rest = rest.drop(3) }
        if (rest.isNotEmpty()) groups.add(rest)
        return groups.joinToString(" ")
    }

    /** "+57 300 123 4567". */
    fun format(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val (country, national) = split(phone) ?: return phone
        return "+${country.code} ${groupNational(national)}".trim()
    }

    /** `tel:` keeps the plus so the dialer knows it is international. */
    fun dialUri(phone: String): String = "tel:+" + phone.filter { it.isDigit() }

    /** WhatsApp's click-to-chat link takes the full number, digits only. */
    fun whatsappUrl(phone: String): String = "https://wa.me/" + phone.filter { it.isDigit() }
}

/** Names on cards and avatars. */
object Names {
    fun full(first: String?, last: String?): String = listOfNotNull(first?.trim(), last?.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    /** "AP" for "Ana Pérez", "C" for "Camila", "?" for nothing. */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
        if (words.isEmpty()) return "?"
        val first = words.first().first().uppercaseChar()
        val last = if (words.size > 1) words.last().first().uppercaseChar().toString() else ""
        return "$first$last"
    }

    /** A stable colour slot for a name, so the same person keeps the same avatar tint. */
    fun colorIndex(key: String, count: Int): Int = if (count <= 0) 0 else Math.floorMod(key.hashCode(), count)
}
