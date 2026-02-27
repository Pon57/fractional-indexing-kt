package dev.pon.fractionalindexing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class SortableBase64Test {

    @Test
    fun encode_emptyArray_returnsEmptyString() {
        assertEquals("", SortableBase64.encode(UByteArray(0)))
    }

    @Test
    fun decode_emptyString_returnsEmptyArray() {
        assertEquals(0, SortableBase64.decode("").size)
    }

    @Test
    fun roundTrip_variousLengths() {
        for (len in 0..20) {
            val input = UByteArray(len) { it.toUByte() }
            val encoded = SortableBase64.encode(input)
            val decoded = SortableBase64.decode(encoded)

            assertTrue(
                input.contentEquals(decoded),
                "Round-trip failed for length $len",
            )
        }
    }

    @Test
    fun decode_rejectsInvalidCharacters() {
        val invalidInputs = listOf(
            "==",
            "+A",
            "/A",
            "A!",
            " A",
            "日",
        )

        for (input in invalidInputs) {
            assertFailsWith<IllegalArgumentException>("Expected rejection: '$input'") {
                SortableBase64.decode(input)
            }
        }
    }

    @Test
    fun decode_rejectsInvalidLength() {
        // length % 4 == 1 is always invalid
        assertFailsWith<IllegalArgumentException> {
            SortableBase64.decode("A")
        }
        assertFailsWith<IllegalArgumentException> {
            SortableBase64.decode("AAAAA")
        }
    }

    @Test
    fun decode_rejectsNonZeroPaddingBits_2chars() {
        // 2-char encoding: bottom 4 bits of second char must be zero.
        // ALPHABET[1] = '0' → value 1, bottom 4 bits = 1 (non-zero)
        assertFailsWith<IllegalArgumentException> {
            SortableBase64.decode("-0")
        }
    }

    @Test
    fun decode_rejectsNonZeroPaddingBits_3chars() {
        // 3-char encoding: bottom 2 bits of third char must be zero.
        // ALPHABET[1] = '0' → value 1, bottom 2 bits = 1 (non-zero)
        assertFailsWith<IllegalArgumentException> {
            SortableBase64.decode("--0")
        }
    }

    @Test
    fun sortOrder_isPreserved() {
        val rng = Random(42)
        val byteArrays = (1..200).map { _ ->
            val len = rng.nextInt(1, 10)
            UByteArray(len) { rng.nextInt(256).toUByte() }
        }

        val sortedByBytes = byteArrays.sortedWith(Comparator { a, b ->
            val minLen = minOf(a.size, b.size)
            for (i in 0 until minLen) {
                val cmp = a[i].compareTo(b[i])
                if (cmp != 0) return@Comparator cmp
            }
            a.size.compareTo(b.size)
        })

        val sortedByEncoded = byteArrays
            .map { it to SortableBase64.encode(it) }
            .sortedBy { it.second }
            .map { it.first }

        assertEquals(sortedByBytes.size, sortedByEncoded.size)
        for (i in sortedByBytes.indices) {
            assertTrue(
                sortedByBytes[i].contentEquals(sortedByEncoded[i]),
                "Sort order mismatch at index $i: " +
                    "bytes=${sortedByBytes[i].toHexString()} vs encoded=${sortedByEncoded[i].toHexString()}",
            )
        }
    }

    @Test
    fun encode_knownValues() {
        // 1 byte (remainder 1)
        assertEquals("--", SortableBase64.encode(ubyteArrayOf(0x00u)))
        assertEquals("V-", SortableBase64.encode(ubyteArrayOf(0x80u)))
        assertEquals("zk", SortableBase64.encode(ubyteArrayOf(0xFFu)))

        // 2 bytes (remainder 2)
        assertEquals("VMw", SortableBase64.encode(ubyteArrayOf(0x81u, 0x7Fu)))

        // 3 bytes (full group)
        assertEquals("----", SortableBase64.encode(ubyteArrayOf(0x00u, 0x00u, 0x00u)))
        assertEquals("zk1f", SortableBase64.encode(ubyteArrayOf(0xFFu, 0x00u, 0xABu)))
        assertEquals("zzzz", SortableBase64.encode(ubyteArrayOf(0xFFu, 0xFFu, 0xFFu)))
    }

    @Test
    fun decode_knownValues() {
        // 1 byte (remainder 1)
        assertTrue(ubyteArrayOf(0x00u).contentEquals(SortableBase64.decode("--")))
        assertTrue(ubyteArrayOf(0x80u).contentEquals(SortableBase64.decode("V-")))
        assertTrue(ubyteArrayOf(0xFFu).contentEquals(SortableBase64.decode("zk")))

        // 2 bytes (remainder 2)
        assertTrue(ubyteArrayOf(0x81u, 0x7Fu).contentEquals(SortableBase64.decode("VMw")))

        // 3 bytes (full group)
        assertTrue(ubyteArrayOf(0x00u, 0x00u, 0x00u).contentEquals(SortableBase64.decode("----")))
        assertTrue(ubyteArrayOf(0xFFu, 0x00u, 0xABu).contentEquals(SortableBase64.decode("zk1f")))
        assertTrue(ubyteArrayOf(0xFFu, 0xFFu, 0xFFu).contentEquals(SortableBase64.decode("zzzz")))
    }
}
