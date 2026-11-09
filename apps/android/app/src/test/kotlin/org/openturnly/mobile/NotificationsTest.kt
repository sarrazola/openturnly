package org.openturnly.mobile

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openturnly.mobile.rules.Inbox
import org.openturnly.mobile.rules.NotificationData

class NotificationsTest {
    private val booked = mapOf(
        "title" to "Nueva reserva",
        "body" to "Ana Pérez · Corte · sáb, 14 nov, 9:00 a. m. · con Juan",
        "badge" to "3",
        "kind" to "appointment_booked",
        "notificationId" to "ntf_1",
        "businessId" to "biz_1",
        "appointmentId" to "apt_1",
        "startsAt" to "2026-11-14T14:00:00.000Z",
    )

    @Test fun readsEveryKeyOfABookingMessage() {
        val data = NotificationData.parse(booked)
        assertEquals("Nueva reserva", data.title)
        assertEquals("Ana Pérez · Corte · sáb, 14 nov, 9:00 a. m. · con Juan", data.body)
        assertEquals(3, data.badge)
        assertEquals("appointment_booked", data.kind)
        assertEquals("ntf_1", data.notificationId)
        assertEquals("biz_1", data.businessId)
        assertEquals("apt_1", data.appointmentId)
        assertEquals("2026-11-14T14:00:00.000Z", data.startsAt)
        assertTrue(data.showable)
    }

    @Test fun theNotificationBlockWinsOverTheData() {
        val data = NotificationData.parse(booked, notificationTitle = "New booking", notificationBody = "Ana · Cut")
        assertEquals("New booking", data.title)
        assertEquals("Ana · Cut", data.body)
        assertEquals("apt_1", data.appointmentId)
        // A blank block leaves the data's words.
        assertEquals("Nueva reserva", NotificationData.parse(booked, notificationTitle = "  ", notificationBody = null).title)
    }

    @Test fun missingAndBlankKeysReadAsNothing() {
        val data = NotificationData.parse(mapOf("title" to "Nueva reserva", "appointmentId" to " ", "badge" to "many", "startsAt" to ""))
        assertNull(data.appointmentId)
        assertNull(data.badge)
        assertNull(data.startsAt)
        assertNull(data.businessId)
        assertEquals("", data.body)
        assertTrue(data.showable)
        assertNull(NotificationData.parse(mapOf("badge" to "-1")).badge)
        assertEquals(0, NotificationData.parse(mapOf("badge" to "0")).badge)
    }

    @Test fun everyKindReadsTheSameWayAndKeepsItsAppointment() {
        for (kind in listOf("appointment_booked", "appointment_rescheduled", "appointment_cancelled", "something_new")) {
            val title = when (kind) { "appointment_rescheduled" -> "Cita movida"; "appointment_cancelled" -> "Cita cancelada"; else -> "Nueva reserva" }
            val data = NotificationData.parse(booked + mapOf("kind" to kind, "title" to title))
            assertEquals(kind, data.kind)
            assertEquals(title, data.title)
            assertEquals("apt_1", data.appointmentId)
            assertEquals("2026-11-14T14:00:00.000Z", data.startsAt)
            assertTrue(data.showable)
        }
        assertNull(NotificationData.parse(booked - "kind").kind)
    }

    @Test fun kindsPickTheirIconAndUnknownOnesFallBack() {
        assertEquals(Inbox.Kind.BOOKED, Inbox.kind("appointment_booked"))
        assertEquals(Inbox.Kind.RESCHEDULED, Inbox.kind("appointment_rescheduled"))
        assertEquals(Inbox.Kind.CANCELLED, Inbox.kind("appointment_cancelled"))
        assertEquals(Inbox.Kind.OTHER, Inbox.kind("payment_received"))
        assertEquals(Inbox.Kind.OTHER, Inbox.kind(null))
        assertEquals(Inbox.Kind.OTHER, Inbox.kind(""))
    }

    @Test fun aMessageWithoutWordsIsNotShown() {
        assertFalse(NotificationData.parse(mapOf("badge" to "2", "businessId" to "biz_1")).showable)
        assertFalse(NotificationData.parse(emptyMap()).showable)
    }

    @Test fun theBellCountsUpToNine() {
        assertNull(Inbox.badge(0))
        assertNull(Inbox.badge(-3))
        assertEquals("1", Inbox.badge(1))
        assertEquals("9", Inbox.badge(9))
        assertEquals("9+", Inbox.badge(10))
        assertEquals("9+", Inbox.badge(250))
    }

    @Test fun agesReadInMinutesHoursDaysThenTheDate() {
        val zone = ZoneId.of("America/Bogota")
        val now = Instant.parse("2026-11-09T20:00:00Z") // 15:00 in Bogotá
        assertEquals(Inbox.Age.Now, Inbox.age(now.minusSeconds(20), now, zone))
        assertEquals(Inbox.Age.Now, Inbox.age(now.plusSeconds(90), now, zone)) // a server clock slightly ahead
        assertEquals(Inbox.Age.Minutes(5), Inbox.age(now.minusSeconds(5 * 60), now, zone))
        assertEquals(Inbox.Age.Minutes(59), Inbox.age(now.minusSeconds(59 * 60 + 30), now, zone))
        assertEquals(Inbox.Age.Hours(3), Inbox.age(now.minusSeconds(3 * 3600 + 600), now, zone))
        assertEquals(Inbox.Age.Yesterday, Inbox.age(Instant.parse("2026-11-09T04:00:00Z"), now, zone)) // 23:00 the day before
        assertEquals(Inbox.Age.Days(3), Inbox.age(Instant.parse("2026-11-06T15:00:00Z"), now, zone))
        assertEquals(Inbox.Age.On(LocalDate.of(2026, 10, 30)), Inbox.age(Instant.parse("2026-10-30T15:00:00Z"), now, zone))
    }
}
