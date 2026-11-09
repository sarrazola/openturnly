package org.openturnly.mobile.rules

import kotlin.math.roundToLong

/**
 * The arithmetic of a sale, done the way the web does it before it posts.
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
        val discountAmount: Long = 0,
    ) {
        val total: Long get() = maxOf(0L, quantity.toLong() * unitPrice - discountAmount)
    }

    fun subtotal(lines: List<Line>): Long = lines.sumOf { it.total }

    /** A percent code takes its share of the subtotal; an amount code takes at most the subtotal. */
    fun codeDiscount(subtotal: Long, percent: Int?, amount: Long?): Long = when {
        percent != null -> (subtotal * percent / 100.0).roundToLong().coerceIn(0L, subtotal)
        amount != null -> minOf(amount, subtotal).coerceAtLeast(0L)
        else -> 0L
    }

    fun total(subtotal: Long, discount: Long): Long = maxOf(0L, subtotal - discount)

    /** What the customer pays in all: the total plus tips, which go on top. */
    fun due(total: Long, tips: Long): Long = total + tips

    /** What is left after deposits and a gift card's balance, never below zero. */
    fun remaining(due: Long, deposits: Long, giftCard: Long): Long = maxOf(0L, due - deposits - giftCard)

    /** Positive when more was entered than is due, negative when something is missing. */
    fun balance(due: Long, paid: Long): Long = paid - due

    /** The part of the bill a gift card's balance can pay. */
    fun giftCardPayment(remainingBalance: Long, owed: Long): Long = maxOf(0L, minOf(remainingBalance, owed))
}
