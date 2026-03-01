package dev.pon.fractionalindexing

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexTest {
    @Test
    fun default_returnsTerminatorOnlyIndex() {
        val index = FractionalIndex.default()

        assertEquals("80", index.toHexString())
        assertEquals("gA==", index.toBase64String())
    }

    @Test
    fun toString_isForDebugRepresentation() {
        val index = FractionalIndex.default()

        assertEquals("FractionalIndex([128])", index.toString())
    }

    @Test
    fun fromBase64String_parsesValidBase64() {
        val result = FractionalIndex.fromBase64String("gX+A")

        assertTrue(result.isSuccess)
        assertEquals("gX+A", result.getOrThrow().toBase64String())
    }

    @Test
    fun fromBase64String_rejectsGarbageStrings() {
        val garbageInputs = listOf(
            "",
            " ",
            "abc!",
            "日本語",
            "%%%=",
            "gX+A#",
            "gX+A\n",
        )

        for (input in garbageInputs) {
            assertTrue(
                FractionalIndex.fromBase64String(input).isFailure,
                "Expected invalid base64 to fail: '$input'",
            )
        }
    }

    @Test
    fun fromBase64String_rejectsMissingTerminator() {
        val result = FractionalIndex.fromBase64String("gX8=")

        assertTrue(result.isFailure)
    }

    @Test
    fun fromBase64String_rejectsNonCanonicalFormats() {
        // Base64 for non-canonical raw bytes:
        // AIA=  -> 00 80
        // /4A=  -> ff 80
        // AYA=  -> 01 80
        val nonCanonical = listOf(
            "AIA=",
            "/4A=",
            "AYA=",
        )

        for (base64 in nonCanonical) {
            assertTrue(
                FractionalIndex.fromBase64String(base64).isFailure,
                "Expected non-canonical base64 to fail: $base64",
            )
        }
    }

    @Test
    fun fromHexString_parsesValidHex_caseInsensitive() {
        val result = FractionalIndex.fromHexString("817F80")

        assertTrue(result.isSuccess)
        assertEquals("817f80", result.getOrThrow().toHexString())
    }

    @Test
    fun fromHexString_rejectsGarbageStrings() {
        val garbageInputs = listOf(
            "",
            " ",
            "zz80",
            "0x80",
            "gA==",
            "817f80\n",
            "8",
            "日本語",
        )

        for (input in garbageInputs) {
            assertTrue(
                FractionalIndex.fromHexString(input).isFailure,
                "Expected invalid hex to fail: '$input'",
            )
        }
    }

    @Test
    fun fromHexStringOrThrow_parsesValidHex() {
        val index = FractionalIndex.fromHexStringOrThrow("817f80")
        assertEquals("817f80", index.toHexString())
    }

    @Test
    fun fromHexStringOrThrow_throwsOnInvalidHex() {
        assertFailsWith<IllegalArgumentException> {
            FractionalIndex.fromHexStringOrThrow("ff80")
        }
    }

    @Test
    fun fromHexString_rejectsMissingTerminator() {
        val result = FractionalIndex.fromHexString("817f")

        assertTrue(result.isFailure)
    }

    @Test
    fun fromHexString_rejectsNonCanonicalFormats() {
        val nonCanonical = listOf(
            "0080",
            "ff80",
            "0180",
        )

        for (hex in nonCanonical) {
            assertTrue(
                FractionalIndex.fromHexString(hex).isFailure,
                "Expected non-canonical hex to fail: $hex",
            )
        }
    }

    @Test
    fun fromBytes_parsesValidBytes() {
        val result = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u))

        assertTrue(result.isSuccess)
        assertEquals("gX+A", result.getOrThrow().toBase64String())
    }

    @Test
    fun fromBytes_rejectsEmptyBytes() {
        val result = FractionalIndex.fromBytes(ubyteArrayOf())

        assertTrue(result.isFailure)
    }

    @Test
    fun fromBytes_rejectsMissingTerminator() {
        val result = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu))

        assertTrue(result.isFailure)
    }

    @Test
    fun fromBytes_rejectsNonCanonicalFormats() {
        val nonCanonical = listOf(
            ubyteArrayOf(0x00u, 0x80u),
            ubyteArrayOf(0xffu, 0x80u),
            ubyteArrayOf(0x01u, 0x80u),
        )

        for (bytes in nonCanonical) {
            assertTrue(
                FractionalIndex.fromBytes(bytes).isFailure,
                "Expected non-canonical bytes to fail: ${bytes.toHexString()}",
            )
        }
    }

    @Test
    fun fromBytesOrThrow_throwsOnInvalidBytes() {
        assertFailsWith<IllegalArgumentException> {
            FractionalIndex.fromBytesOrThrow(ubyteArrayOf(0xffu, 0x80u))
        }
    }

    @Test
    fun fromBytes_makesDefensiveCopyFromInputArray() {
        val source = ubyteArrayOf(0x81u, 0x80u)
        val index = FractionalIndex.fromBytes(source).getOrThrow()

        source[0] = 0x01u

        assertEquals("gYA=", index.toBase64String())
    }

    @Test
    fun toBase64String_roundTripsWithFromBase64String() {
        val original = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()
        val encoded = original.toBase64String()
        val decoded = FractionalIndex.fromBase64String(encoded).getOrThrow()

        assertEquals(original, decoded)
        assertEquals("gX+A", encoded)
    }

    @Test
    fun toHexString_roundTripsWithFromHexString() {
        val original = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()
        val encoded = original.toHexString()
        val decoded = FractionalIndex.fromHexString(encoded).getOrThrow()

        assertEquals(original, decoded)
        assertEquals("817f80", encoded)
    }

    @Test
    fun compareTo_returnsZero_forSameBytes() {
        val a = FractionalIndex.fromBytes(ubyteArrayOf(0x80u)).getOrThrow()
        val b = FractionalIndex.fromBytes(ubyteArrayOf(0x80u)).getOrThrow()

        assertEquals(0, a.compareTo(b))
        assertEquals(0, b.compareTo(a))
    }

    @Test
    fun compareTo_comparesUnsignedByteValues() {
        val low = FractionalIndex.fromBytes(ubyteArrayOf(0x40u, 0x80u)).getOrThrow()
        val high = FractionalIndex.fromBytes(ubyteArrayOf(0xbfu, 0x80u)).getOrThrow()

        assertTrue(low < high)
        assertTrue(high > low)
    }

    @Test
    fun compareTo_usesLexicographicalOrder() {
        val smaller = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()
        val larger = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x80u)).getOrThrow()

        assertTrue(smaller < larger)
        assertTrue(larger > smaller)
    }

    @Test
    fun compareTo_usesLengthWhenOneIsPrefix() {
        val shorter = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x80u)).getOrThrow()
        val longer = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x80u, 0x80u)).getOrThrow()

        assertTrue(shorter < longer)
        assertTrue(longer > shorter)
    }

    @Test
    fun compareTo_sortsInExpectedOrder() {
        val a = FractionalIndex.fromBytes(ubyteArrayOf(0x7fu, 0x80u)).getOrThrow()
        val b = FractionalIndex.fromBytes(ubyteArrayOf(0x80u)).getOrThrow()
        val c = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()
        val d = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x80u)).getOrThrow()

        val sorted = listOf(d, b, a, c).sorted()

        assertEquals(listOf(a, b, c, d), sorted)
    }

    @Test
    fun equalsAndHashCode_areConsistentForSameContent() {
        val a = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()
        val b = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun bytes_returnsDefensiveCopy() {
        val index = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x80u)).getOrThrow()
        val leaked = index.bytes

        leaked[0] = 0x01u

        assertEquals("gYA=", index.toBase64String())
    }

    @Test
    fun toBase64String_withUrlSafe_usesUrlSafeAlphabet() {
        // 0x81, 0x7f, 0x80 encodes to "gX+A" in standard, "gX-A" in URL-safe
        val index = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()

        assertEquals("gX+A", index.toBase64String())
        assertEquals("gX-A", index.toBase64String(Base64.UrlSafe))
    }

    @Test
    fun toBase64String_withUrlSafe_roundTrips() {
        val original = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()
        val encoded = original.toBase64String(Base64.UrlSafe)
        val decoded = FractionalIndex.fromBase64String(encoded, Base64.UrlSafe).getOrThrow()

        assertEquals(original, decoded)
    }

    @Test
    fun toBase64String_withPaddingAbsent_roundTrips() {
        val noPad = Base64.Default.withPadding(Base64.PaddingOption.ABSENT)
        val index = FractionalIndex.default()

        val encoded = index.toBase64String(noPad)
        assertEquals("gA", encoded) // "gA==" without padding

        val decoded = FractionalIndex.fromBase64String(encoded, noPad).getOrThrow()
        assertEquals(index, decoded)
    }

    @Test
    fun toBase64String_withUrlSafeAndPaddingAbsent_roundTrips() {
        val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val index = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()

        val encoded = index.toBase64String(codec)
        assertEquals("gX-A", encoded)

        val decoded = FractionalIndex.fromBase64String(encoded, codec).getOrThrow()
        assertEquals(index, decoded)
    }

    @Test
    fun fromBase64String_rejectsCrossCodecMismatch() {
        // Standard encodes as "gX+A"; decoding with UrlSafe should fail because '+' is invalid in URL-safe
        val index = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()
        val standardEncoded = index.toBase64String()

        assertTrue(FractionalIndex.fromBase64String(standardEncoded, Base64.UrlSafe).isFailure)
    }

    @Test
    fun fromBase64StringOrThrow_throwsOnInvalidInput() {
        assertFailsWith<IllegalArgumentException> {
            FractionalIndex.fromBase64StringOrThrow("%%%=")
        }
    }

    @Test
    fun fromBase64String_withPresentOptional_acceptsPaddedAndUnpadded() {
        val codec = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
        val index = FractionalIndex.default()

        val padded = FractionalIndex.fromBase64String("gA==", codec).getOrThrow()
        val unpadded = FractionalIndex.fromBase64String("gA", codec).getOrThrow()

        assertEquals(index, padded)
        assertEquals(index, unpadded)
    }

    @Test
    fun toSortableBase64String_roundTripsWithFromSortableBase64String() {
        val original = FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow()
        val encoded = original.toSortableBase64String()
        val decoded = FractionalIndex.fromSortableBase64String(encoded).getOrThrow()

        assertEquals(original, decoded)
        assertEquals("VMy-", encoded)
    }

    @Test
    fun fromSortableBase64String_rejectsNonCanonicalFormats() {
        // Non-canonical raw bytes encoded via sortable base64:
        // "-7-" = 0x00, 0x80 → non-canonical (first byte 0x00 is outside compact range)
        // "zs-" = 0xFF, 0x80 → non-canonical (first byte 0xFF is outside compact range for major=0)
        // "-N-" = 0x01, 0x80 → non-canonical
        val nonCanonical = listOf("-7-", "zs-", "-N-")

        for (str in nonCanonical) {
            assertTrue(
                FractionalIndex.fromSortableBase64String(str).isFailure,
                "Expected non-canonical sortable base64 to fail: $str",
            )
        }
    }

    @Test
    fun fromSortableBase64String_rejectsMissingTerminator() {
        // "VMw" = 0x81, 0x7F → no terminator byte (0x80)
        assertTrue(FractionalIndex.fromSortableBase64String("VMw").isFailure)
    }

    @Test
    fun fromSortableBase64StringOrThrow_throwsOnInvalidInput() {
        assertFailsWith<IllegalArgumentException> {
            FractionalIndex.fromSortableBase64StringOrThrow("zs-")
        }
    }

    @Test
    fun sortableBase64String_preservesSortOrder() {
        val indices = listOf(
            FractionalIndex.fromBytes(ubyteArrayOf(0x40u, 0x80u)).getOrThrow(),
            FractionalIndex.fromBytes(ubyteArrayOf(0x60u, 0x80u)).getOrThrow(),
            FractionalIndex.fromBytes(ubyteArrayOf(0x80u)).getOrThrow(),
            FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x7fu, 0x80u)).getOrThrow(),
            FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x80u)).getOrThrow(),
            FractionalIndex.fromBytes(ubyteArrayOf(0x81u, 0x80u, 0x80u)).getOrThrow(),
            FractionalIndex.fromBytes(ubyteArrayOf(0xBFu, 0x80u)).getOrThrow(),
        )

        val sortedByIndex = indices.sorted()
        val sortedByString = indices.sortedBy { it.toSortableBase64String() }

        assertEquals(sortedByIndex, sortedByString)
    }

}
