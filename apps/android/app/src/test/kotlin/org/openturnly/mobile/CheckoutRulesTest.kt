package org.openturnly.mobile

import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openturnly.mobile.api.Sale
import org.openturnly.mobile.api.SaleItem
import org.openturnly.mobile.api.SalePayment
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.Checkout
import org.openturnly.mobile.rules.Checkout.Payment
import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.SalesTotals
import org.openturnly.mobile.ui.sales.Span
import org.openturnly.mobile.ui.sales.spanIncludes
import org.openturnly.mobile.ui.sales.spanLabel
import org.openturnly.mobile.ui.sales.spanRange

/** The checkout and sales numbers, so both apps charge and add up the same. */
class CheckoutRulesTest {
    private lateinit var previous: Locale

    @Before fun spanish() { previous = Locale.getDefault(); Locale.setDefault(Locale.forLanguageTag("es-CO")) }
    @After fun restore() { Locale.setDefault(previous) }

    private fun plain(text: String) = text.replace(' ', ' ').replace(' ', ' ')

    private val haircut = Checkout.Line("a", "service", "Corte", appointmentId = "ap1", unitPrice = 40000, discountAmount = 5000)
    private val wax = Checkout.Line("b", "product", "Cera", productId = "p1", quantity = 2, unitPrice = 10000)

    // Summary and «Por cobrar ahora»

    @Test fun summaryKeepsTheSaleTotalApartFromTipsAndDeposits() {
        val summary = Checkout.summary(listOf(haircut, wax), discount = 5000, tips = 8000, deposits = 20000)
        assertEquals(55000L, summary.subtotal)
        assertEquals(5000L, summary.discount)
        assertEquals(50000L, summary.total)
        assertEquals(8000L, summary.tips)
        assertEquals(20000L, summary.deposits)
        assertEquals(58000L, summary.due)
        // Total + tips − deposits.
        assertEquals(38000L, summary.toCollect)
    }

    @Test fun toCollectNeverGoesBelowZero() {
        assertEquals(0L, Checkout.toCollect(total = 35000, tips = 0, deposits = 40000))
        assertEquals(0L, Checkout.summary(listOf(haircut), discount = 0, tips = 0, deposits = 35000).toCollect)
        assertEquals(5000L, Checkout.toCollect(total = 35000, tips = 5000, deposits = 35000))
    }

    @Test fun saleDiscountStaysWithinTheSubtotal() {
        assertEquals(0L, Checkout.saleDiscount(50000, null))
        assertEquals(12000L, Checkout.saleDiscount(50000, 12000))
        assertEquals(50000L, Checkout.saleDiscount(50000, 80000))
        assertEquals(0L, Checkout.saleDiscount(0, 1000))
        // The summary clamps too, so a stale discount can't push the total below zero.
        assertEquals(35000L, Checkout.summary(listOf(haircut), discount = 99000, tips = 0, deposits = 0).discount)
        assertEquals(0L, Checkout.summary(listOf(haircut), discount = 99000, tips = 0, deposits = 0).total)
    }

    @Test fun tipsWithoutAmountOrProfessionalAreDropped() {
        val tips = listOf(Checkout.Tip("1", "camila", 5000), Checkout.Tip("2", "andres", 0), Checkout.Tip("3", null, 2000))
        assertEquals(7000L, Checkout.tipTotal(tips))
        assertEquals(listOf("1"), Checkout.payableTips(tips).map { it.key })
    }

    // The bottom bar

    @Test fun statusLineSaysBalancedMissingOrOver() {
        assertEquals(Checkout.Status.Balanced, Checkout.status(0))
        assertEquals(Checkout.Status.Missing(3000), Checkout.status(-3000))
        assertEquals(Checkout.Status.Over(2000), Checkout.status(2000))
        val payments = listOf(Payment("1", "cash", 30000), Payment("2", "card", 5000))
        assertEquals(-3000L, Checkout.difference(due = 58000, deposits = 20000, payments = payments))
        assertEquals("Faltan $ 3.000", plain(Copy.checkout.missing(Money.format(3000, "COP"))))
        assertEquals("Sobran $ 2.000", plain(Copy.checkout.over(Money.format(2000, "COP"))))
        assertEquals("El pago cuadra con el total.", Copy.checkout.balanced)
    }

    @Test fun buttonClosesTheSaleOnlyForAnAppointmentWithNothingToCollect() {
        assertEquals(Checkout.Submit.CloseSale, Checkout.submit(toCollect = 0, forAppointment = true))
        assertEquals(Checkout.Submit.Charge(0), Checkout.submit(toCollect = 0, forAppointment = false))
        assertEquals(Checkout.Submit.Charge(20000), Checkout.submit(toCollect = 20000, forAppointment = true))
        assertEquals("Cobrar $ 20.000", plain(Copy.checkout.charge(Money.format(20000, "COP"))))
    }

    // «Cómo pagó»

    @Test fun firstRowFollowsWhatIsLeftUntilEdited() {
        val one = listOf(Payment("p0", "cash", 0))
        assertEquals(0, Checkout.followingIndex(one, edited = false))
        assertEquals(38000L, Checkout.follow(one, toCollect = 38000, edited = false).single().amount)
        // Once someone types an amount, it stays as typed.
        assertNull(Checkout.followingIndex(one, edited = true))
        assertEquals(0L, Checkout.follow(one, toCollect = 38000, edited = true).single().amount)
        // A gift card row alone doesn't follow.
        val gift = listOf(Payment("g1", Checkout.GIFT_CARD, 5000, "gc1", "ABC123"))
        assertEquals(gift, Checkout.follow(gift, toCollect = 38000, edited = false))
    }

    @Test fun rowAfterAGiftCardTakesTheRestWhenATipIsAdded() {
        // The card covered everything (30.000), then a 5.000 tip: the cash row takes it.
        val rows = listOf(Payment("g1", Checkout.GIFT_CARD, 30000, "gc1", "ABC123"), Payment("p0", "cash", 0))
        assertEquals(1, Checkout.followingIndex(rows, edited = false))
        val followed = Checkout.follow(rows, toCollect = 35000, edited = false)
        assertEquals(listOf(30000L, 5000L), followed.map { it.amount })
        assertEquals(0L, Checkout.difference(35000, 0, followed))
    }

    @Test fun giftCardPaysUpToWhatIsStillDue() {
        assertEquals(20000L, Checkout.giftCardPayment(20000, 38000))
        assertEquals(38000L, Checkout.giftCardPayment(50000, 38000))
        assertEquals(0L, Checkout.giftCardPayment(50000, 0))

        // A single row keeps the rest; the card goes first.
        val one = listOf(Payment("p0", "cash", 38000))
        val withCard = Checkout.withGiftCard(one, "g1", "gc1", "ABC123", balance = 20000, toCollect = 38000)
        assertEquals(listOf("g1", "p0"), withCard.map { it.key })
        assertEquals(20000L, withCard[0].amount)
        assertTrue(withCard[0].isGiftCard)
        assertEquals("gc1", withCard[0].giftCardId)
        assertEquals(18000L, withCard[1].amount)
        assertEquals(0L, Checkout.difference(due = 38000, deposits = 0, payments = withCard))

        // A card bigger than the bill leaves the cash row at zero (dropped when posting).
        val covered = Checkout.withGiftCard(one, "g1", "gc1", "ABC123", balance = 90000, toCollect = 38000)
        assertEquals(listOf(38000L, 0L), covered.map { it.amount })
        assertEquals(listOf("g1"), Checkout.payable(covered).map { it.key })

        // Several rows stay as they were; the same card twice changes nothing.
        val two = listOf(Payment("p0", "cash", 10000), Payment("p1", "card", 5000))
        assertEquals(listOf("g1", "p0", "p1"), Checkout.withGiftCard(two, "g1", "gc1", "ABC123", 20000, 38000).map { it.key })
        assertEquals(withCard, Checkout.withGiftCard(withCard, "g2", "gc1", "ABC123", 20000, 38000))
        assertEquals("Gift card ABC123", Copy.checkout.giftCard("ABC123"))
        assertEquals("Le quedan $ 20.000", plain(Copy.checkout.cardLeft(Money.format(20000, "COP"))))
    }

    @Test fun splitAddsACardRowWithWhatIsMissingUpToFourRows() {
        val one = listOf(Payment("p0", "cash", 30000))
        val split = Checkout.split(one, difference = -8000, key = "p1")
        assertEquals(Payment("p1", "card", 8000), split[1])
        assertEquals(0L, Checkout.split(one, difference = 2000, key = "p1")[1].amount)
        val four = List(4) { Payment("p$it", "cash", 1000) }
        assertEquals(four, Checkout.split(four, difference = -8000, key = "p9"))
    }

    // Products in an internal sale

    @Test fun internalSaleRepricesProductsStillAtTheirPrice() {
        assertEquals(7000L, Checkout.productPrice(10000, 7000, internal = true))
        assertEquals(10000L, Checkout.productPrice(10000, null, internal = true))
        assertEquals(10000L, Checkout.productPrice(10000, 7000, internal = false))
        val byHand = Checkout.Line("c", "product", "Gel", productId = "p2", unitPrice = 9000)
        val prices = mapOf("p1" to (10000L to 7000L), "p2" to (12000L to 8000L))
        val repriced = Checkout.reprice(listOf(haircut, wax, byHand), prices, internal = true)
        assertEquals(40000L, repriced[0].unitPrice)
        assertEquals(7000L, repriced[1].unitPrice)
        assertEquals(9000L, repriced[2].unitPrice)
        assertEquals(10000L, Checkout.reprice(repriced, prices, internal = false)[1].unitPrice)
    }

    // Ventas

    private fun sale(number: Int, total: Long, tips: Long = 0, status: String = "paid", internal: Boolean = false, payments: List<Pair<String, Long>> = emptyList()) =
        Sale(
            id = "s$number", number = number, status = status, internal = internal, total = total, tipAmount = tips,
            payments = payments.map { (method, amount) -> SalePayment(method = method, amount = amount) },
        )

    @Test fun collectedIncludesTipsAndInternalSalesAndMatchesTheMethods() {
        val sales = listOf(
            sale(1, 30000, 5000, payments = listOf("cash" to 35000L)),
            sale(2, 40000, 0, internal = true, payments = listOf("card" to 25000L, "cash" to 15000L)),
            sale(3, 20000, 3000, payments = listOf("deposit" to 10000L, "card" to 13000L)),
            sale(4, 99000, 1000, status = "void", payments = listOf("transfer" to 100000L)),
        )
        val summary = SalesTotals.summary(sales)
        assertEquals(98000L, summary.collected)
        assertEquals(3, summary.count)
        assertEquals(30000L, summary.average)
        assertEquals(8000L, summary.tips)
        assertEquals(listOf("cash" to 50000L, "card" to 38000L, "deposit" to 10000L), summary.byMethod)
        assertEquals(summary.collected, summary.byMethod.sumOf { it.second })
        assertEquals("Incluye $ 8.000 en propinas", plain(Copy.sales.includesTips(Money.format(summary.tips, "COP"))))
        assertEquals("3 ventas · Promedio $ 30.000", plain(Copy.sales.count(summary.count) + " · " + Copy.sales.average(Money.format(summary.average, "COP"))))
    }

    @Test fun averageRoundsDownAndEmptyPeriodsAreZero() {
        assertEquals(SalesTotals.Summary(0, 0, 0, 0, emptyList()), SalesTotals.summary(emptyList()))
        assertEquals(33333L, SalesTotals.summary(listOf(sale(1, 50000), sale(2, 50000), sale(3, 0))).average)
    }

    @Test fun methodTiesAreSortedByName() {
        val sales = listOf(sale(1, 20000, payments = listOf("transfer" to 10000L, "card" to 10000L)))
        assertEquals(listOf("card", "transfer"), SalesTotals.summary(sales).byMethod.map { it.first })
    }

    @Test fun saleRowListsMethodsOnceAndTwoItems() {
        val row = sale(12, 50000, payments = listOf("cash" to 20000L, "card" to 20000L, "cash" to 10000L))
            .copy(items = listOf(SaleItem(name = "Corte"), SaleItem(name = "Barba"), SaleItem(name = "Cera"), SaleItem(name = "Gel")))
        assertEquals(listOf("cash", "card"), SalesTotals.methods(row))
        assertEquals("Corte, Barba +2", SalesTotals.items(row))
        assertEquals("Corte", SalesTotals.items(row.copy(items = listOf(SaleItem(name = "Corte")))))
    }

    @Test fun periodsAndTheirLabels() {
        val monday = LocalDate.of(2026, 11, 9)
        assertEquals(LocalDate.of(2026, 11, 9) to LocalDate.of(2026, 11, 15), spanRange(Span.WEEK, LocalDate.of(2026, 11, 12)))
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30), spanRange(Span.MONTH, monday))
        assertEquals("Hoy", spanLabel(Span.DAY, monday, today = monday))
        assertEquals("Mar 10 nov", spanLabel(Span.DAY, monday.plusDays(1), today = monday))
        assertEquals("9 nov – 15 nov", spanLabel(Span.WEEK, monday, today = monday))
        assertEquals("Noviembre 2026", spanLabel(Span.MONTH, monday, today = monday))
        // «Hoy» shows only when the period doesn't include today.
        assertTrue(spanIncludes(Span.WEEK, LocalDate.of(2026, 11, 14), monday))
        assertFalse(spanIncludes(Span.DAY, monday.minusDays(1), monday))
        assertFalse(spanIncludes(Span.MONTH, LocalDate.of(2026, 10, 9), monday))
    }
}
