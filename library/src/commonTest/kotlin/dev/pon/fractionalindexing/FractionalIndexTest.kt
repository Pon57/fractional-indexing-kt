package dev.pon.fractionalindexing

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun fromHexString_rejectsNonMinimalLongMajorFormats() {
        val nonCanonical = listOf(
            "fa00102a80", // positive 4138 with a redundant leading 00; canonical form is f9102a80
            "05ffefd580", // negative 4138 with a redundant leading ff; canonical form is 06efd580
        )

        for (hex in nonCanonical) {
            assertTrue(
                FractionalIndex.fromHexString(hex).isFailure,
                "Expected non-minimal long-major format to fail: $hex",
            )
        }
    }

    @Test
    fun fromByteArray_parsesValidBytes() {
        val result = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80))

        assertTrue(result.isSuccess)
        assertEquals("gX+A", result.getOrThrow().toBase64String())
    }

    @Test
    fun fromByteArray_rejectsEmptyBytes() {
        val result = FractionalIndex.fromByteArray(byteArrayOf())

        assertTrue(result.isFailure)
    }

    @Test
    fun fromByteArray_rejectsMissingTerminator() {
        val result = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f))

        assertTrue(result.isFailure)
    }

    @Test
    fun fromByteArrayOrThrow_reportsBoundedMissingTerminatorMessage() {
        val bytes = ByteArray(4_096) { 0x7f }

        val exception = assertFailsWith<IllegalArgumentException> {
            FractionalIndex.fromByteArrayOrThrow(bytes)
        }

        assertEquals(
            "FractionalIndex must end with terminator 128 (length=4096)",
            exception.message,
        )
    }

    @Test
    fun fromByteArray_rejectsNonCanonicalFormats() {
        val nonCanonical = listOf(
            byteArray(0x00, 0x80),
            byteArray(0xff, 0x80),
            byteArray(0x01, 0x80),
        )

        for (bytes in nonCanonical) {
            assertTrue(
                FractionalIndex.fromByteArray(bytes).isFailure,
                "Expected non-canonical bytes to fail: ${bytes.toHexString()}",
            )
        }
    }

    @Test
    fun fromByteArrayOrThrow_throwsOnInvalidBytes() {
        assertFailsWith<IllegalArgumentException> {
            FractionalIndex.fromByteArrayOrThrow(byteArray(0xff, 0x80))
        }
    }

    @Test
    fun fromByteArray_makesDefensiveCopyFromInputArray() {
        val source = byteArray(0x81, 0x80)
        val index = FractionalIndex.fromByteArray(source).getOrThrow()

        source[0] = 0x01

        assertEquals("gYA=", index.toBase64String())
    }

    @Test
    fun byteArrayApi_roundTripsWithoutSignednessLoss() {
        val source = byteArray(0x81, 0xff, 0x80)
        val original = FractionalIndex.fromByteArrayOrThrow(source)
        val decoded = FractionalIndex.fromByteArrayOrThrow(original.toByteArray())

        assertContentEquals(source, original.toByteArray())
        assertEquals(original, decoded)
    }

    @Suppress("DEPRECATION")
    @Test
    fun deprecatedUnsignedByteApis_remainCompatible() {
        val source = ubyteArrayOf(0x81u, 0x7fu, 0x80u)
        val expected = source.copyOf()
        val resultIndex = FractionalIndex.fromBytes(source).getOrThrow()
        val throwingIndex = FractionalIndex.fromBytesOrThrow(source)

        source[0] = 0x01u
        val exposed = resultIndex.bytes
        exposed[0] = 0x01u

        assertEquals(resultIndex, throwingIndex)
        assertContentEquals(expected, resultIndex.bytes)
    }

    @Test
    fun toBase64String_roundTripsWithFromBase64String() {
        val original = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()
        val encoded = original.toBase64String()
        val decoded = FractionalIndex.fromBase64String(encoded).getOrThrow()

        assertEquals(original, decoded)
        assertEquals("gX+A", encoded)
    }

    @Test
    fun toHexString_roundTripsWithFromHexString() {
        val original = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()
        val encoded = original.toHexString()
        val decoded = FractionalIndex.fromHexString(encoded).getOrThrow()

        assertEquals(original, decoded)
        assertEquals("817f80", encoded)
    }

    @Test
    fun compareTo_returnsZero_forSameBytes() {
        val a = FractionalIndex.fromByteArray(byteArray(0x80)).getOrThrow()
        val b = FractionalIndex.fromByteArray(byteArray(0x80)).getOrThrow()

        assertEquals(0, a.compareTo(b))
        assertEquals(0, b.compareTo(a))
    }

    @Test
    fun compareTo_comparesUnsignedByteValues() {
        val low = FractionalIndex.fromByteArray(byteArray(0x40, 0x80)).getOrThrow()
        val high = FractionalIndex.fromByteArray(byteArray(0xbf, 0x80)).getOrThrow()

        assertTrue(low < high)
        assertTrue(high > low)
    }

    @Test
    fun compareTo_usesLexicographicalOrder() {
        val smaller = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()
        val larger = FractionalIndex.fromByteArray(byteArray(0x81, 0x80)).getOrThrow()

        assertTrue(smaller < larger)
        assertTrue(larger > smaller)
    }

    @Test
    fun compareTo_usesLengthWhenOneIsPrefix() {
        val shorter = FractionalIndex.fromByteArray(byteArray(0x81, 0x80)).getOrThrow()
        val longer = FractionalIndex.fromByteArray(byteArray(0x81, 0x80, 0x80)).getOrThrow()

        assertTrue(shorter < longer)
        assertTrue(longer > shorter)
    }

    @Test
    fun compareTo_sortsInExpectedOrder() {
        val a = FractionalIndex.fromByteArray(byteArray(0x7f, 0x80)).getOrThrow()
        val b = FractionalIndex.fromByteArray(byteArray(0x80)).getOrThrow()
        val c = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()
        val d = FractionalIndex.fromByteArray(byteArray(0x81, 0x80)).getOrThrow()

        val sorted = listOf(d, b, a, c).sorted()

        assertEquals(listOf(a, b, c, d), sorted)
    }

    @Test
    fun equalsAndHashCode_areConsistentForSameContent() {
        val a = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()
        val b = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun toByteArray_returnsDefensiveCopy() {
        val index = FractionalIndex.fromByteArray(byteArray(0x81, 0x80)).getOrThrow()
        val originalHash = index.hashCode()
        val leaked = index.toByteArray()

        leaked[0] = 0x01

        assertEquals("gYA=", index.toBase64String())
        assertEquals(originalHash, index.hashCode())
        assertContentEquals(byteArray(0x81, 0x80), index.toByteArray())
    }

    @Test
    fun toBase64String_withUrlSafe_usesUrlSafeAlphabet() {
        // 0x81, 0x7f, 0x80 encodes to "gX+A" in standard, "gX-A" in URL-safe
        val index = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()

        assertEquals("gX+A", index.toBase64String())
        assertEquals("gX-A", index.toBase64String(Base64.UrlSafe))
    }

    @Test
    fun toBase64String_withUrlSafe_roundTrips() {
        val original = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()
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
        val index = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()

        val encoded = index.toBase64String(codec)
        assertEquals("gX-A", encoded)

        val decoded = FractionalIndex.fromBase64String(encoded, codec).getOrThrow()
        assertEquals(index, decoded)
    }

    @Test
    fun fromBase64String_rejectsCrossCodecMismatch() {
        // Standard encodes as "gX+A"; decoding with UrlSafe should fail because '+' is invalid in URL-safe
        val index = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()
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
        val original = FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow()
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
            FractionalIndex.fromByteArray(byteArray(0x40, 0x80)).getOrThrow(),
            FractionalIndex.fromByteArray(byteArray(0x60, 0x80)).getOrThrow(),
            FractionalIndex.fromByteArray(byteArray(0x80)).getOrThrow(),
            FractionalIndex.fromByteArray(byteArray(0x81, 0x7f, 0x80)).getOrThrow(),
            FractionalIndex.fromByteArray(byteArray(0x81, 0x80)).getOrThrow(),
            FractionalIndex.fromByteArray(byteArray(0x81, 0x80, 0x80)).getOrThrow(),
            FractionalIndex.fromByteArray(byteArray(0xBF, 0x80)).getOrThrow(),
        )

        val sortedByIndex = indices.sorted()
        val sortedByString = indices.sortedBy { it.toSortableBase64String() }

        assertEquals(sortedByIndex, sortedByString)
    }

    @Test
    fun encodedLength_matchesCompanionMethod() {
        val terminator = ubyteArrayOf(FractionalIndex.TERMINATOR)
        // zero-major, positive short, negative short, zero-major multi-byte,
        // and tier boundaries: 41 (short max), 42 (medium min), 4137 (medium max), 4138 (long min)
        val indices = listOf(
            FractionalIndex.default(),
            FractionalIndex.fromMajorMinor(1, terminator),       // positive short min
            FractionalIndex.fromMajorMinor(-1, terminator),      // negative short min
            FractionalIndex.fromMajorMinor(41, terminator),      // positive short max
            FractionalIndex.fromMajorMinor(-41, terminator),     // negative short max
            FractionalIndex.fromMajorMinor(42, terminator),      // positive medium min
            FractionalIndex.fromMajorMinor(-42, terminator),     // negative medium min
            FractionalIndex.fromMajorMinor(4137, terminator),    // positive medium max
            FractionalIndex.fromMajorMinor(-4137, terminator),   // negative medium max
            FractionalIndex.fromMajorMinor(4138, terminator),    // positive long min
            FractionalIndex.fromMajorMinor(-4138, terminator),   // negative long min
            FractionalIndex.fromByteArrayOrThrow(byteArray(0x81, 0x7f, 0x80)), // zero-major multi-byte
        )
        for (index in indices) {
            assertEquals(
                FractionalIndex.encodedLength(index.major, index.minor.size),
                index.encodedLength,
                "encodedLength mismatch for $index",
            )
        }
    }

    @Test
    fun parseBranchRegression_compact_roundTripsMajorAndMinor() {
        // Compact: first byte in [0x40..0xBF], major=0, minor=rawBytes
        val index = FractionalIndex.fromHexStringOrThrow("817f80")
        assertEquals(0L, index.major)
        assertContentEquals(ubyteArrayOf(0x81u, 0x7fu, 0x80u), index.minor)
        assertEquals("817f80", index.toHexString())
    }

    @Test
    fun parseBranchRegression_positiveShort_roundTripsMajorAndMinor() {
        // Positive short: tag 0xC0 → major=1
        val minor = ubyteArrayOf(0x70u, 0x90u, FractionalIndex.TERMINATOR)
        val encoded = FractionalIndex.fromMajorMinor(1L, minor)
        val decoded = FractionalIndex.fromHexStringOrThrow(encoded.toHexString())
        assertEquals(1L, decoded.major)
        assertContentEquals(minor, decoded.minor)
    }

    @Test
    fun parseBranchRegression_positiveShortMax_roundTripsMajorAndMinor() {
        val minor = ubyteArrayOf(0x70u, 0x90u, FractionalIndex.TERMINATOR)
        val encoded = FractionalIndex.fromMajorMinor(41L, minor)
        val decoded = FractionalIndex.fromHexStringOrThrow(encoded.toHexString())
        assertEquals(41L, decoded.major)
        assertContentEquals(minor, decoded.minor)
    }

    @Test
    fun parseBranchRegression_positiveMedium_roundTripsMajorAndMinor() {
        val minor = ubyteArrayOf(0x70u, 0x90u, FractionalIndex.TERMINATOR)
        val encoded = FractionalIndex.fromMajorMinor(42L, minor)
        val decoded = FractionalIndex.fromHexStringOrThrow(encoded.toHexString())
        assertEquals(42L, decoded.major)
        assertContentEquals(minor, decoded.minor)
    }

    @Test
    fun parseBranchRegression_positiveMediumMax_roundTripsMajorAndMinor() {
        val minor = ubyteArrayOf(0x70u, 0x90u, FractionalIndex.TERMINATOR)
        val encoded = FractionalIndex.fromMajorMinor(4137L, minor)
        val decoded = FractionalIndex.fromHexStringOrThrow(encoded.toHexString())
        assertEquals(4137L, decoded.major)
        assertContentEquals(minor, decoded.minor)
    }

    @Test
    fun parseBranchRegression_positiveLong_roundTripsMajorAndMinor() {
        val minor = ubyteArrayOf(0x70u, 0x90u, FractionalIndex.TERMINATOR)
        val encoded = FractionalIndex.fromMajorMinor(4138L, minor)
        val decoded = FractionalIndex.fromHexStringOrThrow(encoded.toHexString())
        assertEquals(4138L, decoded.major)
        assertContentEquals(minor, decoded.minor)
    }

    @Test
    fun parseBranchRegression_negativeShort_roundTripsMajorAndMinor() {
        val minor = ubyteArrayOf(0x70u, 0x90u, FractionalIndex.TERMINATOR)
        val encoded = FractionalIndex.fromMajorMinor(-1L, minor)
        val decoded = FractionalIndex.fromHexStringOrThrow(encoded.toHexString())
        assertEquals(-1L, decoded.major)
        assertContentEquals(minor, decoded.minor)
    }

    @Test
    fun parseBranchRegression_negativeShortMax_roundTripsMajorAndMinor() {
        val minor = ubyteArrayOf(0x70u, 0x90u, FractionalIndex.TERMINATOR)
        val encoded = FractionalIndex.fromMajorMinor(-41L, minor)
        val decoded = FractionalIndex.fromHexStringOrThrow(encoded.toHexString())
        assertEquals(-41L, decoded.major)
        assertContentEquals(minor, decoded.minor)
    }

    @Test
    fun parseBranchRegression_negativeMedium_roundTripsMajorAndMinor() {
        val minor = ubyteArrayOf(0x70u, 0x90u, FractionalIndex.TERMINATOR)
        val encoded = FractionalIndex.fromMajorMinor(-42L, minor)
        val decoded = FractionalIndex.fromHexStringOrThrow(encoded.toHexString())
        assertEquals(-42L, decoded.major)
        assertContentEquals(minor, decoded.minor)
    }

    @Test
    fun parseBranchRegression_negativeMediumMax_roundTripsMajorAndMinor() {
        val minor = ubyteArrayOf(0x70u, 0x90u, FractionalIndex.TERMINATOR)
        val encoded = FractionalIndex.fromMajorMinor(-4137L, minor)
        val decoded = FractionalIndex.fromHexStringOrThrow(encoded.toHexString())
        assertEquals(-4137L, decoded.major)
        assertContentEquals(minor, decoded.minor)
    }

    @Test
    fun parseBranchRegression_negativeLong_roundTripsMajorAndMinor() {
        val minor = ubyteArrayOf(0x70u, 0x90u, FractionalIndex.TERMINATOR)
        val encoded = FractionalIndex.fromMajorMinor(-4138L, minor)
        val decoded = FractionalIndex.fromHexStringOrThrow(encoded.toHexString())
        assertEquals(-4138L, decoded.major)
        assertContentEquals(minor, decoded.minor)
    }

    private fun byteArray(vararg values: Int): ByteArray {
        require(values.all { it in 0x00..0xff })
        return ByteArray(values.size) { values[it].toByte() }
    }
}
