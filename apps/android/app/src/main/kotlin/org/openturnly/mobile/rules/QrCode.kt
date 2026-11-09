package org.openturnly.mobile.rules

/**
 * A QR code for the booking page's link, made on the device.
 *
 * Byte mode, error correction level M, versions 1 to 10 (up to 213 bytes,
 * plenty for a link), with the mask chosen by the standard's penalty rules.
 * Written from ISO/IEC 18004 so the app needs no library for it.
 */
class QrCode private constructor(val size: Int, private val modules: Array<BooleanArray>) {
    /** True for a dark module at column [x], row [y]. */
    fun isDark(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        // Level M, versions 1–10: error-correction codewords per block and number of blocks.
        private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
        private val BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)
        private const val FORMAT_BITS_M = 0
        const val MAX_VERSION = 10

        fun rawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val numAlign = version / 7 + 2
                result -= (25 * numAlign - 10) * numAlign - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        fun dataCodewords(version: Int): Int = rawDataModules(version) / 8 - ECC_PER_BLOCK[version] * BLOCKS[version]

        /** The smallest version that holds [byteCount] bytes, or null if none up to 10 does. */
        fun versionFor(byteCount: Int): Int? = (1..MAX_VERSION).firstOrNull { version ->
            val countBits = if (version <= 9) 8 else 16
            4 + countBits + byteCount * 8 <= dataCodewords(version) * 8
        }

        fun encode(text: String): QrCode? {
            val data = text.toByteArray(Charsets.UTF_8)
            val version = versionFor(data.size) ?: return null
            val capacityBits = dataCodewords(version) * 8
            val bits = ArrayList<Boolean>()
            fun append(value: Int, length: Int) { for (i in length - 1 downTo 0) bits.add((value ushr i) and 1 != 0) }
            append(0b0100, 4)
            append(data.size, if (version <= 9) 8 else 16)
            data.forEach { append(it.toInt() and 0xFF, 8) }
            append(0, minOf(4, capacityBits - bits.size))
            append(0, (8 - bits.size % 8) % 8)
            val codewords = ArrayList<Int>()
            for (i in bits.indices step 8) {
                var byte = 0
                for (j in 0 until 8) byte = (byte shl 1) or (if (bits[i + j]) 1 else 0)
                codewords.add(byte)
            }
            var pad = 0xEC
            while (codewords.size < dataCodewords(version)) { codewords.add(pad); pad = pad xor 0xEC xor 0x11 }
            val all = addErrorCorrection(codewords.toIntArray(), version)
            return build(version, all)
        }

        internal fun addErrorCorrection(data: IntArray, version: Int): IntArray {
            val numBlocks = BLOCKS[version]
            val eccLen = ECC_PER_BLOCK[version]
            val rawCodewords = rawDataModules(version) / 8
            val numShort = numBlocks - rawCodewords % numBlocks
            val shortLen = rawCodewords / numBlocks
            val divisor = reedSolomonDivisor(eccLen)
            val blocks = ArrayList<IntArray>()
            var k = 0
            for (i in 0 until numBlocks) {
                val length = shortLen - eccLen + if (i < numShort) 0 else 1
                val dat = data.copyOfRange(k, k + length)
                k += length
                val ecc = reedSolomonRemainder(dat, divisor)
                val block = IntArray(shortLen + 1)
                dat.copyInto(block, 0)
                // Short blocks carry a placeholder so every block has the same length.
                ecc.copyInto(block, shortLen + 1 - eccLen)
                blocks.add(block)
            }
            val result = ArrayList<Int>()
            for (i in 0 until shortLen + 1) for (j in blocks.indices) {
                if (i != shortLen - eccLen || j >= numShort) result.add(blocks[j][i])
            }
            return result.toIntArray()
        }

        internal fun multiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        internal fun reedSolomonDivisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in 0 until degree) {
                    result[j] = multiply(result[j], root)
                    if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
                }
                root = multiply(root, 0x02)
            }
            return result
        }

        internal fun reedSolomonRemainder(data: IntArray, divisor: IntArray): IntArray {
            val result = IntArray(divisor.size)
            for (b in data) {
                val factor = b xor result[0]
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor multiply(divisor[i], factor)
            }
            return result
        }

        /** The 15 format bits for level M and a mask, BCH-protected and XOR-masked. */
        internal fun formatBits(mask: Int): Int {
            val data = (FORMAT_BITS_M shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            return ((data shl 10) or rem) xor 0x5412
        }

        private fun alignmentPositions(version: Int, size: Int): IntArray {
            if (version == 1) return IntArray(0)
            val numAlign = version / 7 + 2
            val step = (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            var pos = size - 7
            for (i in numAlign - 1 downTo 1) { result[i] = pos; pos -= step }
            return result
        }

        private fun build(version: Int, codewords: IntArray): QrCode {
            val size = version * 4 + 17
            val modules = Array(size) { BooleanArray(size) }
            val function = Array(size) { BooleanArray(size) }
            fun set(x: Int, y: Int, dark: Boolean) { modules[y][x] = dark; function[y][x] = true }

            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            for ((cx, cy) in listOf(3 to 3, size - 4 to 3, 3 to size - 4)) {
                for (dy in -4..4) for (dx in -4..4) {
                    val x = cx + dx; val y = cy + dy
                    if (x in 0 until size && y in 0 until size) {
                        val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                        set(x, y, dist != 2 && dist != 4)
                    }
                }
            }
            val align = alignmentPositions(version, size)
            for (i in align.indices) for (j in align.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == align.size - 1) || (i == align.size - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) set(align[i] + dx, align[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
            fun drawFormat(mask: Int) {
                val bits = formatBits(mask)
                fun bit(i: Int) = (bits ushr i) and 1 != 0
                for (i in 0..5) set(8, i, bit(i))
                set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
                for (i in 9 until 15) set(14 - i, 8, bit(i))
                for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
                for (i in 8 until 15) set(8, size - 15 + i, bit(i))
                set(8, size - 8, true)
            }
            drawFormat(0)
            if (version >= 7) {
                var rem = version
                repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
                val bits = (version shl 12) or rem
                for (i in 0 until 18) {
                    val bit = (bits ushr i) and 1 != 0
                    val a = size - 11 + i % 3; val b = i / 3
                    set(a, b, bit); set(b, a, bit)
                }
            }
            // The data, in two-column zigzags from the bottom right.
            var index = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!function[y][x] && index < codewords.size * 8) {
                        modules[y][x] = (codewords[index ushr 3] ushr (7 - (index and 7))) and 1 != 0
                        index++
                    }
                }
                right -= 2
            }
            fun applyMask(mask: Int) {
                for (y in 0 until size) for (x in 0 until size) {
                    if (function[y][x]) continue
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                    if (invert) modules[y][x] = !modules[y][x]
                }
            }
            var best = 0
            var bestScore = Int.MAX_VALUE
            for (mask in 0 until 8) {
                applyMask(mask); drawFormat(mask)
                val score = penalty(modules, size)
                if (score < bestScore) { bestScore = score; best = mask }
                applyMask(mask)
            }
            applyMask(best); drawFormat(best)
            return QrCode(size, modules)
        }

        /** The standard's four penalty rules: runs, blocks, finder look-alikes and balance. */
        private fun penalty(m: Array<BooleanArray>, size: Int): Int {
            var result = 0
            fun line(get: (Int) -> Boolean) {
                var run = 1
                for (i in 1 until size) {
                    if (get(i) == get(i - 1)) { run++; if (run == 5) result += 3 else if (run > 5) result++ } else run = 1
                }
                for (i in 0..size - 11) {
                    val a = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
                    val b = a.reversedArray()
                    if ((0 until 11).all { get(i + it) == a[it] } || (0 until 11).all { get(i + it) == b[it] }) result += 40
                }
            }
            for (y in 0 until size) line { m[y][it] }
            for (x in 0 until size) line { m[it][x] }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = m[y][x]
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) result += 3
            }
            val dark = m.sumOf { row -> row.count { it } }
            val total = size * size
            val k = (kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1
            result += maxOf(0, k) * 10
            return result
        }
    }
}
