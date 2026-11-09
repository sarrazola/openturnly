package org.openturnly.mobile.rules

import kotlin.math.roundToLong
import org.openturnly.mobile.api.Sale

/**
 * The arithmetic of a sale, done the way the web's checkout does it before it posts.
 *
 * The server checks it: lines add up to the subtotal, the discount stays within
 * it, and the payments add up exactly to the total plus tips. A discount code
 * used at the counter is computed here, since the server only records its use.
 */
object Checkout {
    data class Line(
        val key: String,
        val kind: String,
        val name: String,
        val serviceId: String? = null,
        val productId: String? = null,
        val appointmentId: String? = null,
        val resourceId: String? = null,
        val quantity: Int = 1,
        val unitPrice: Long = 0,
        /** The booking discount an appointment's line carries; other lines have none. */
        val discountAmount: Long = 0,
    ) {
        val total: Long get() = maxOf(0L, quantity.toLong() * unitPrice - discountAmount)
    }

    /** One row of «Cómo pagó»: a method and its amount, or a gift card's balance. */
    data class Payment(
        val key: String,
        val method: String,
        val amount: Long = 0,
        val giftCardId: String? = null,
        val giftCardCode: String? = null,
    ) {
        val isGiftCard: Boolean get() = method == GIFT_CARD
    }

    /** A tip for one professional. */
    data class Tip(val key: String, val resourceId: String?, val amount: Long)

    const val GIFT_CARD = "gift_card"

    /** Methods a person picks at the counter (deposits and gift cards come from elsewhere). */
    val counterMethods = listOf("cash", "card", "transfer", "other")

    /** At most this many rows in «Cómo pagó» ("Dividir el pago"). */
    const val MAX_PAYMENTS = 4

    fun subtotal(lines: List<Line>): Long = lines.sumOf { it.total }

    /** A percent code takes its share of the subtotal; an amount code takes at most the subtotal. */
    fun codeDiscount(subtotal: Long, percent: Int?, amount: Long?): Long = when {
        percent != null -> (subtotal * percent / 100.0).roundToLong().coerceIn(0L, subtotal)
        amount != null -> minOf(amount, subtotal).coerceAtLeast(0L)
        else -> 0L
    }

    /** A discount typed by hand on the whole sale: never below zero nor above the subtotal. */
    fun saleDiscount(subtotal: Long, typed: Long?): Long = (typed ?: 0L).coerceIn(0L, maxOf(0L, subtotal))

    fun total(subtotal: Long, discount: Long): Long = maxOf(0L, subtotal - discount)

    /** What the customer pays in all: the total plus tips, which go on top. */
    fun due(total: Long, tips: Long): Long = total + tips

    /** What is left after deposits and a gift card's balance, never below zero. */
    fun remaining(due: Long, deposits: Long, giftCard: Long): Long = maxOf(0L, due - deposits - giftCard)

    /** Positive when more was entered than is due, negative when something is missing. */
    fun balance(due: Long, paid: Long): Long = paid - due

    /** The part of the bill a gift card's balance can pay: what it holds, up to what is still due. */
    fun giftCardPayment(remainingBalance: Long, owed: Long): Long = maxOf(0L, minOf(remainingBalance, owed))

    /** Tips with no amount don't count. */
    fun tipTotal(tips: List<Tip>): Long = tips.sumOf { maxOf(0L, it.amount) }

    /** The tips that get posted: with an amount and someone to receive it. */
    fun payableTips(tips: List<Tip>): List<Tip> = tips.filter { it.amount > 0 && it.resourceId != null }

    /**
     * The summary card, top to bottom: «Subtotal», «Descuento», «Total» (the sale's
     * total, as its detail shows it), «Propinas», «Abonos», and «Por cobrar ahora»
     * (total plus tips minus deposits). A gift card is a payment, not a line here.
     */
    data class Summary(
        val subtotal: Long,
        val discount: Long,
        val total: Long,
        val tips: Long,
        val deposits: Long,
        val toCollect: Long,
    ) {
        /** What the payments, deposits included, must add up to. */
        val due: Long get() = total + tips
    }

    fun summary(lines: List<Line>, discount: Long, tips: Long, deposits: Long): Summary {
        val subtotal = subtotal(lines)
        val applied = discount.coerceIn(0L, maxOf(0L, subtotal))
        val total = total(subtotal, applied)
        return Summary(subtotal, applied, total, tips, deposits, toCollect(total, tips, deposits))
    }

    /** «Por cobrar ahora»: the total plus tips, minus the deposits used, never below zero. */
    fun toCollect(total: Long, tips: Long, deposits: Long): Long = maxOf(0L, total + tips - deposits)

    /** Paid minus due: zero when it balances, negative when something is missing. */
    fun difference(due: Long, deposits: Long, payments: List<Payment>): Long =
        deposits + payments.sumOf { it.amount } - due

    /** The line over the charge button. */
    sealed interface Status {
        /** «El pago cuadra con el total.» */
        data object Balanced : Status
        /** «Faltan $ X» */
        data class Missing(val amount: Long) : Status
        /** «Sobran $ X» */
        data class Over(val amount: Long) : Status
    }

    fun status(difference: Long): Status = when {
        difference == 0L -> Status.Balanced
        difference < 0 -> Status.Missing(-difference)
        else -> Status.Over(difference)
    }

    /** The charge button: «Cerrar venta» for an appointment with nothing left to collect, else «Cobrar $ X». */
    sealed interface Submit {
        data object CloseSale : Submit
        data class Charge(val amount: Long) : Submit
    }

    fun submit(toCollect: Long, forAppointment: Boolean): Submit =
        if (forAppointment && toCollect == 0L) Submit.CloseSale else Submit.Charge(toCollect)

    /**
     * The row that follows «Por cobrar ahora»: the first one that isn't a gift card, until
     * someone types an amount, removes a row or splits the payment. Null when none does.
     */
    fun followingIndex(payments: List<Payment>, edited: Boolean): Int? =
        if (edited) null else payments.indexOfFirst { !it.isGiftCard }.takeIf { it >= 0 }

    /** That row takes what is left to collect after the other rows, as on the iPhone. */
    fun follow(payments: List<Payment>, toCollect: Long, edited: Boolean): List<Payment> {
        val index = followingIndex(payments, edited) ?: return payments
        val others = payments.filterIndexed { i, _ -> i != index }.sumOf { it.amount }
        return payments.mapIndexed { i, payment -> if (i == index) payment.copy(amount = maxOf(0L, toCollect - others)) else payment }
    }

    /**
     * A gift card with balance becomes the first row of «Cómo pagó», paying what
     * it holds up to what is still to collect. A single row left keeps the rest;
     * several rows stay as they were. The same card twice changes nothing.
     */
    fun withGiftCard(payments: List<Payment>, key: String, giftCardId: String, code: String, balance: Long, toCollect: Long): List<Payment> {
        if (payments.any { it.giftCardId == giftCardId }) return payments
        val amount = giftCardPayment(balance, toCollect)
        val card = Payment(key, GIFT_CARD, amount, giftCardId, code)
        val rest = if (payments.size == 1) listOf(payments[0].copy(amount = maxOf(0L, toCollect - amount))) else payments
        return listOf(card) + rest
    }

    /** «Dividir el pago» adds a «Tarjeta» row with what is missing, up to [MAX_PAYMENTS] rows. */
    fun split(payments: List<Payment>, difference: Long, key: String): List<Payment> =
        if (payments.size >= MAX_PAYMENTS) payments else payments + Payment(key, "card", maxOf(0L, -difference))

    /** The rows that get posted: those with an amount, as the server keeps them. */
    fun payable(payments: List<Payment>): List<Payment> = payments.filter { it.amount > 0 }

    /** What a product costs in this sale: its price for the team in an internal sale, if it has one. */
    fun productPrice(price: Long, internalPrice: Long?, internal: Boolean): Long =
        if (internal && internalPrice != null) internalPrice else price

    /**
     * Switching to or from an internal sale reprices the products still at their
     * other price; one whose price was changed by hand keeps it. [prices] maps a
     * product to its regular and internal price.
     */
    fun reprice(lines: List<Line>, prices: Map<String, Pair<Long, Long?>>, internal: Boolean): List<Line> = lines.map { line ->
        val (price, internalPrice) = line.productId?.let { prices[it] } ?: return@map line
        if (line.unitPrice == productPrice(price, internalPrice, !internal)) line.copy(unitPrice = productPrice(price, internalPrice, internal)) else line
    }
}

/** The sales list's summary: what came in, how many sales, their average and by method. */
object SalesTotals {
    data class Summary(
        /** «Recaudado»: every paid sale (internal ones too) with its tips, so it equals the method breakdown. */
        val collected: Long,
        val count: Int,
        /** The average sale total, tips apart; 0 without sales. */
        val average: Long,
        val tips: Long,
        /** Method and amount, biggest first. */
        val byMethod: List<Pair<String, Long>>,
    )

    fun summary(sales: List<Sale>): Summary {
        val paid = sales.filter { it.status == "paid" }
        val byMethod = paid.flatMap { it.payments }
            .groupBy { it.method }
            .map { (method, list) -> method to list.sumOf { it.amount } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
        return Summary(
            collected = paid.sumOf { it.total + it.tipAmount },
            count = paid.size,
            average = if (paid.isEmpty()) 0L else paid.sumOf { it.total } / paid.size,
            tips = paid.sumOf { it.tipAmount },
            byMethod = byMethod,
        )
    }

    /** A sale's payment methods, each once, in the order they were paid. */
    fun methods(sale: Sale): List<String> = sale.payments.map { it.method }.distinct()

    /** The first two items, then «+N». */
    fun items(sale: Sale): String {
        val names = sale.items.map { it.name }
        val shown = names.take(2).joinToString(", ")
        return if (names.size > 2) "$shown +${names.size - 2}" else shown
    }
}
