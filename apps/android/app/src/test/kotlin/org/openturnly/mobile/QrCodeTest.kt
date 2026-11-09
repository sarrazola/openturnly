package org.openturnly.mobile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openturnly.mobile.rules.QrCode

class QrCodeTest {
    @Test fun reedSolomonMatchesTheStandardsWorkedExample() {
        // "HELLO WORLD" at 1-M, the example most QR tutorials walk through.
        val data = intArrayOf(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17)
        val ecc = QrCode.reedSolomonRemainder(data, QrCode.reedSolomonDivisor(10))
        assertArrayEquals(intArrayOf(196, 35, 39, 119, 235, 215, 231, 226, 93, 23), ecc)
    }

    @Test fun formatBitsForLevelM() {
        assertEquals(0b101010000010010, QrCode.formatBits(0))
        assertEquals(0b101000100100101, QrCode.formatBits(1))
    }

    @Test fun capacitiesAtLevelM() {
        assertEquals(16, QrCode.dataCodewords(1))
        assertEquals(28, QrCode.dataCodewords(2))
        assertEquals(64, QrCode.dataCodewords(4))
        assertEquals(216, QrCode.dataCodewords(10))
        assertEquals(1, QrCode.versionFor(14))
        assertEquals(2, QrCode.versionFor(15))
        assertNull(QrCode.versionFor(400))
    }

    @Test fun aBookingLinkFitsWithFinderPatternsInTheCorners() {
        val qr = QrCode.encode("https://turnos.example.com/barberia-el-amigo")
        assertNotNull(qr)
        qr!!
        assertEquals(0, (qr.size - 17) % 4)
        // Finder patterns: dark 7x7 ring, light ring, dark 3x3 centre.
        for ((x0, y0) in listOf(0 to 0, qr.size - 7 to 0, 0 to qr.size - 7)) {
            assertTrue(qr.isDark(x0, y0))
            assertTrue(qr.isDark(x0 + 6, y0 + 6))
            assertTrue(!qr.isDark(x0 + 1, y0 + 1))
            assertTrue(qr.isDark(x0 + 3, y0 + 3))
        }
        // The dark module beside the bottom-left finder.
        assertTrue(qr.isDark(8, qr.size - 8))
    }
}
