package dev.pon.fractionalindexing

import kotlin.io.encoding.Base64

/**
 * An opaque key that supports arbitrary insertions between any two existing keys
 * while preserving a total sort order via unsigned-lexicographic byte comparison.
 *
 * Create instances with [default], [fromBytes], [fromHexString], or [fromBase64String].
 * Generate new keys with [FractionalIndexGenerator] (or the [before] / [after] / [between] extensions).
 */
@OptIn(ExperimentalUnsignedTypes::class)
public class FractionalIndex private constructor(
    private val unsafeRawBytes: UByteArray,
    internal val major: Long,
    internal val minor: UByteArray,
) : Comparable<FractionalIndex> {
    /** Returns a defensive copy of the raw bytes backing this index. */
    public val bytes: UByteArray
        get() = unsafeRawBytes.copyOf()

    /**
     * Encodes this index as a lowercase hex string.
     *
     * The hex representation preserves the sort order of [FractionalIndex]:
     * if `a < b`, then `a.toHexString() < b.toHexString()` lexicographically.
     */
    public fun toHexString(): String = unsafeRawBytes.toHexString()

    /**
     * Encodes this index as a Base64 string.
     *
     * **Note:** Base64 encoding does **not** preserve the sort order of [FractionalIndex].
     * Use [toHexString] or [bytes] when lexicographic ordering must be maintained.
     *
     * @param codec the [Base64] instance to use (e.g. `Base64.UrlSafe`).
     */
    public fun toBase64String(codec: Base64 = Base64): String = codec.encode(unsafeRawBytes.asByteArray())

    /** Debug-friendly representation. Use [toHexString] or [toBase64String] for wire format. */
    override fun toString(): String = "FractionalIndex(${unsafeRawBytes.contentToString()})"

    override fun compareTo(other: FractionalIndex): Int {
        val minLength = minOf(unsafeRawBytes.size, other.unsafeRawBytes.size)

        for (i in 0 until minLength) {
            val b1 = unsafeRawBytes[i]
            val b2 = other.unsafeRawBytes[i]

            if (b1 != b2) {
                return b1.compareTo(b2)
            }
        }

        return unsafeRawBytes.size.compareTo(other.unsafeRawBytes.size)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FractionalIndex) return false
        return unsafeRawBytes.contentEquals(other.unsafeRawBytes)
    }

    override fun hashCode(): Int = unsafeRawBytes.contentHashCode()

    public companion object {
        internal const val TERMINATOR: UByte = 0x80u

        private const val INVALID_FORMAT_MESSAGE = "invalid fractional index format"

        // First-byte encoding layout:
        //   0x00..0x3F  negative major (long / medium / short tiers)
        //   0x40..0xBF  compact — major=0, minor is the entire key
        //   0xC0..0xFF  positive major (short / medium / long tiers)
        //
        // Tier capacity:
        //   short  — 1-byte tag, |major| 1..41          (41 values)
        //   medium — 2-byte tag, |major| 42..4137       (16 groups * 256 = 4096 values)
        //   long   — 1-byte tag + 2..8 byte payload     (beyond 4137)
        private const val SHORT_MAJOR_MAX = 41L
        private const val MEDIUM_MAJOR_MAX = 4137L
        private const val NEGATIVE_LONG_MIN_TAG = 0
        private const val NEGATIVE_LONG_MAX_TAG = 6
        private const val NEGATIVE_MEDIUM_MIN_TAG = 7
        private const val NEGATIVE_MEDIUM_MAX_TAG = 22
        private const val NEGATIVE_SHORT_MIN_TAG = 23
        private const val NEGATIVE_SHORT_MAX_TAG = 63
        private const val POSITIVE_SHORT_MIN_TAG = 0xC0
        private const val POSITIVE_SHORT_MAX_TAG = 0xE8
        private const val POSITIVE_MEDIUM_MIN_TAG = 0xE9
        private const val POSITIVE_MEDIUM_MAX_TAG = 0xF8
        private const val POSITIVE_LONG_MIN_TAG = 0xF9
        private const val POSITIVE_LONG_MAX_TAG = 0xFF
        private const val COMPACT_FIRST_MIN = 0x40
        private const val COMPACT_FIRST_MAX = 0xBF
        private const val EXTENDED_MAJOR_MAX_LENGTH = 8

        /** Returns the default index, a neutral starting point for key generation. */
        public fun default(): FractionalIndex {
            val bytes = ubyteArrayOf(TERMINATOR)
            return FractionalIndex(bytes, 0L, bytes)
        }

        /**
         * Decodes a [FractionalIndex] from raw bytes.
         *
         * The input is defensively copied; subsequent mutations to [bytes] do not affect the returned index.
         */
        public fun fromBytes(bytes: UByteArray): Result<FractionalIndex> = runCatching {
            fromRawBytes(bytes.copyOf())
        }

        /**
         * Decodes a [FractionalIndex] from a hex string (case-insensitive).
         *
         * The hex representation preserves sort order, so it is safe to use as a sortable wire format.
         */
        public fun fromHexString(hex: String): Result<FractionalIndex> = runCatching {
            fromRawBytes(hex.hexToUByteArray())
        }

        /**
         * Decodes a [FractionalIndex] from a Base64 string.
         *
         * **Note:** Base64 encoding does **not** preserve the sort order of [FractionalIndex].
         * Use [fromHexString] or [fromBytes] when lexicographic ordering must be maintained.
         *
         * @param codec the [Base64] instance to use — must match the one used for encoding.
         */
        public fun fromBase64String(base64: String, codec: Base64 = Base64): Result<FractionalIndex> = runCatching {
            fromRawBytes(codec.decode(base64).asUByteArray())
        }

        internal fun fromMajorMinor(major: Long, minor: UByteArray): FractionalIndex {
            require(minor.isNotEmpty() && minor.last() == TERMINATOR) { INVALID_FORMAT_MESSAGE }

            if (major == 0L) {
                require(isCompactMinor(minor)) { INVALID_FORMAT_MESSAGE }
                val rawBytes = minor.copyOf()
                return FractionalIndex(rawBytes, 0L, rawBytes)
            }

            if (major > 0L) {
                return encodePositive(major = major, minor = minor)
            }

            return encodeNegative(major = major, minor = minor)
        }

        internal fun encodedLength(major: Long, minorSize: Int): Int {
            require(major != Long.MIN_VALUE) { INVALID_FORMAT_MESSAGE }
            if (major == 0L) {
                return minorSize
            }

            val magnitude = if (major > 0L) major else -major
            return if (magnitude <= SHORT_MAJOR_MAX) {
                1 + minorSize
            } else if (magnitude <= MEDIUM_MAJOR_MAX) {
                2 + minorSize
            } else {
                1 + unsignedMagnitudeByteLength(magnitude) + minorSize
            }
        }

        internal fun isEncodableMinorForMajor(major: Long, minor: UByteArray): Boolean {
            if (minor.isEmpty() || minor.last() != TERMINATOR) {
                return false
            }
            if (major != 0L) {
                return true
            }
            return isCompactMinor(minor)
        }

        internal fun isCompactMinor(minor: UByteArray): Boolean {
            return minor.isNotEmpty() &&
                minor.last() == TERMINATOR &&
                isCompactFirstByte(minor.first())
        }

        private fun fromRawBytes(rawBytes: UByteArray): FractionalIndex {
            require(rawBytes.isNotEmpty()) { "FractionalIndex must not be empty" }
            require(rawBytes.last() == TERMINATOR) {
                "FractionalIndex must end with terminator $TERMINATOR value: FractionalIndex(${rawBytes.contentToString()})"
            }
            val (major, minor) = parseRawBytes(rawBytes)
            return FractionalIndex(rawBytes, major, minor)
        }

        private fun parseRawBytes(bytes: UByteArray): Pair<Long, UByteArray> {
            val first = bytes[0].toInt()
            if (first in 0..NEGATIVE_SHORT_MAX_TAG) {
                return parseNegative(bytes)
            }

            if (first in POSITIVE_SHORT_MIN_TAG..0xFF) {
                return parsePositive(bytes)
            }

            require(isCompactFirstByte(bytes[0])) { INVALID_FORMAT_MESSAGE }
            // major=0: minor is the entire raw bytes; share the same instance
            return Pair(0L, bytes)
        }

        private fun parseNegative(bytes: UByteArray): Pair<Long, UByteArray> {
            require(bytes.size >= 2) { INVALID_FORMAT_MESSAGE }

            val first = bytes[0].toInt()
            return if (first in NEGATIVE_SHORT_MIN_TAG..NEGATIVE_SHORT_MAX_TAG) {
                val magnitude = (NEGATIVE_SHORT_MAX_TAG + 1) - first
                val major = -magnitude.toLong()
                val minor = bytes.copyOfRange(1, bytes.size)
                require(minor.isNotEmpty() && minor.last() == TERMINATOR) { INVALID_FORMAT_MESSAGE }
                Pair(major, minor)
            } else if (first in NEGATIVE_MEDIUM_MIN_TAG..NEGATIVE_MEDIUM_MAX_TAG) {
                require(bytes.size >= 3) { INVALID_FORMAT_MESSAGE }
                val group = NEGATIVE_MEDIUM_MAX_TAG - first
                val remainder = UByte.MAX_VALUE.toInt() - bytes[1].toInt()
                val magnitude = SHORT_MAJOR_MAX + 1L + (group.toLong() * 256L) + remainder.toLong()
                require(magnitude in (SHORT_MAJOR_MAX + 1L)..MEDIUM_MAJOR_MAX) { INVALID_FORMAT_MESSAGE }

                val minor = bytes.copyOfRange(2, bytes.size)
                require(minor.isNotEmpty() && minor.last() == TERMINATOR) { INVALID_FORMAT_MESSAGE }
                Pair(-magnitude, minor)
            } else {
                require(first in NEGATIVE_LONG_MIN_TAG..NEGATIVE_LONG_MAX_TAG) { INVALID_FORMAT_MESSAGE }
                val majorLength = EXTENDED_MAJOR_MAX_LENGTH - first
                require(majorLength in 2..EXTENDED_MAJOR_MAX_LENGTH) { INVALID_FORMAT_MESSAGE }

                val payloadStart = 1
                val payloadEndExclusive = payloadStart + majorLength
                require(bytes.size > payloadEndExclusive) { INVALID_FORMAT_MESSAGE }
                val majorPayload = bytes.copyOfRange(payloadStart, payloadEndExclusive)
                val minor = bytes.copyOfRange(payloadEndExclusive, bytes.size)
                require(minor.isNotEmpty() && minor.last() == TERMINATOR) { INVALID_FORMAT_MESSAGE }

                val magnitude = decodeUnsignedMagnitude(complementBytes(majorPayload))
                require(magnitude > MEDIUM_MAJOR_MAX) { INVALID_FORMAT_MESSAGE }
                Pair(-magnitude, minor)
            }
        }

        private fun parsePositive(bytes: UByteArray): Pair<Long, UByteArray> {
            require(bytes.size >= 2) { INVALID_FORMAT_MESSAGE }

            val first = bytes[0].toInt()
            return if (first in POSITIVE_SHORT_MIN_TAG..POSITIVE_SHORT_MAX_TAG) {
                val major = (first - POSITIVE_SHORT_MIN_TAG + 1).toLong()
                val minor = bytes.copyOfRange(1, bytes.size)
                require(minor.isNotEmpty() && minor.last() == TERMINATOR) { INVALID_FORMAT_MESSAGE }
                Pair(major, minor)
            } else if (first in POSITIVE_MEDIUM_MIN_TAG..POSITIVE_MEDIUM_MAX_TAG) {
                require(bytes.size >= 3) { INVALID_FORMAT_MESSAGE }
                val group = first - POSITIVE_MEDIUM_MIN_TAG
                val remainder = bytes[1].toInt()
                val major = SHORT_MAJOR_MAX + 1L + (group.toLong() * 256L) + remainder.toLong()
                require(major in (SHORT_MAJOR_MAX + 1L)..MEDIUM_MAJOR_MAX) { INVALID_FORMAT_MESSAGE }

                val minor = bytes.copyOfRange(2, bytes.size)
                require(minor.isNotEmpty() && minor.last() == TERMINATOR) { INVALID_FORMAT_MESSAGE }
                Pair(major, minor)
            } else {
                require(first in POSITIVE_LONG_MIN_TAG..POSITIVE_LONG_MAX_TAG) { INVALID_FORMAT_MESSAGE }
                val majorLength = first - POSITIVE_LONG_MIN_TAG + 2
                require(majorLength in 2..EXTENDED_MAJOR_MAX_LENGTH) { INVALID_FORMAT_MESSAGE }

                val payloadStart = 1
                val payloadEndExclusive = payloadStart + majorLength
                require(bytes.size > payloadEndExclusive) { INVALID_FORMAT_MESSAGE }
                val majorPayload = bytes.copyOfRange(payloadStart, payloadEndExclusive)
                val minor = bytes.copyOfRange(payloadEndExclusive, bytes.size)
                require(minor.isNotEmpty() && minor.last() == TERMINATOR) { INVALID_FORMAT_MESSAGE }

                val major = decodeUnsignedMagnitude(majorPayload)
                require(major > MEDIUM_MAJOR_MAX) { INVALID_FORMAT_MESSAGE }
                Pair(major, minor)
            }
        }

        private fun encodePositive(major: Long, minor: UByteArray): FractionalIndex {
            require(major > 0L) { INVALID_FORMAT_MESSAGE }
            if (major <= SHORT_MAJOR_MAX) {
                val out = UByteArray(1 + minor.size)
                out[0] = (POSITIVE_SHORT_MIN_TAG + major.toInt() - 1).toUByte()
                minor.copyInto(out, destinationOffset = 1)
                return FractionalIndex(out, major, minor)
            }

            if (major <= MEDIUM_MAJOR_MAX) {
                val offset = (major - (SHORT_MAJOR_MAX + 1L)).toInt()
                val group = offset / 256
                val remainder = offset % 256

                val out = UByteArray(2 + minor.size)
                out[0] = (POSITIVE_MEDIUM_MIN_TAG + group).toUByte()
                out[1] = remainder.toUByte()
                minor.copyInto(out, destinationOffset = 2)
                return FractionalIndex(out, major, minor)
            }

            val payload = encodeUnsignedMagnitude(major)
            require(payload.size in 2..EXTENDED_MAJOR_MAX_LENGTH) { INVALID_FORMAT_MESSAGE }
            val out = UByteArray(1 + payload.size + minor.size)
            out[0] = (POSITIVE_LONG_MIN_TAG + payload.size - 2).toUByte()
            payload.copyInto(out, destinationOffset = 1)
            minor.copyInto(out, destinationOffset = 1 + payload.size)
            return FractionalIndex(out, major, minor)
        }

        private fun encodeNegative(major: Long, minor: UByteArray): FractionalIndex {
            require(major != Long.MIN_VALUE && major < 0L) { INVALID_FORMAT_MESSAGE }
            val magnitude = -major
            if (magnitude <= SHORT_MAJOR_MAX) {
                val out = UByteArray(1 + minor.size)
                out[0] = ((NEGATIVE_SHORT_MAX_TAG + 1) - magnitude.toInt()).toUByte()
                minor.copyInto(out, destinationOffset = 1)
                return FractionalIndex(out, major, minor)
            }

            if (magnitude <= MEDIUM_MAJOR_MAX) {
                val offset = (magnitude - (SHORT_MAJOR_MAX + 1L)).toInt()
                val group = offset / 256
                val remainder = offset % 256

                val out = UByteArray(2 + minor.size)
                out[0] = (NEGATIVE_MEDIUM_MAX_TAG - group).toUByte()
                out[1] = (UByte.MAX_VALUE.toInt() - remainder).toUByte()
                minor.copyInto(out, destinationOffset = 2)
                return FractionalIndex(out, major, minor)
            }

            val payload = encodeUnsignedMagnitude(magnitude)
            require(payload.size in 2..EXTENDED_MAJOR_MAX_LENGTH) { INVALID_FORMAT_MESSAGE }
            val complemented = complementBytes(payload)
            val out = UByteArray(1 + complemented.size + minor.size)
            out[0] = (EXTENDED_MAJOR_MAX_LENGTH - complemented.size).toUByte()
            complemented.copyInto(out, destinationOffset = 1)
            minor.copyInto(out, destinationOffset = 1 + complemented.size)
            return FractionalIndex(out, major, minor)
        }

        private fun isCompactFirstByte(byte: UByte): Boolean {
            val value = byte.toInt()
            return value in COMPACT_FIRST_MIN..COMPACT_FIRST_MAX
        }

        private fun unsignedMagnitudeByteLength(value: Long): Int {
            require(value > 0L) { INVALID_FORMAT_MESSAGE }
            var current = value
            var count = 0
            do {
                count++
                current = current ushr 8
            } while (current != 0L)
            return count
        }

        private fun encodeUnsignedMagnitude(value: Long): UByteArray {
            require(value > 0L) { INVALID_FORMAT_MESSAGE }
            var current = value
            val length = unsignedMagnitudeByteLength(value)
            val bytes = UByteArray(length)

            for (i in length - 1 downTo 0) {
                bytes[i] = current.toUByte()
                current = current ushr 8
            }
            return bytes
        }

        private fun decodeUnsignedMagnitude(bytes: UByteArray): Long {
            require(bytes.isNotEmpty()) { INVALID_FORMAT_MESSAGE }
            var value = 0L
            for (b in bytes) {
                val unsigned = b.toLong()
                // Guard against overflow: check that value * 256 + unsigned <= Long.MAX_VALUE.
                // Split into two checks: first the multiplication, then the addition.
                if (value > Long.MAX_VALUE / 256L ||
                    value * 256L > Long.MAX_VALUE - unsigned
                ) {
                    throw IllegalArgumentException(INVALID_FORMAT_MESSAGE)
                }
                value = (value * 256L) + unsigned
            }
            return value
        }

        private fun complementBytes(bytes: UByteArray): UByteArray {
            return UByteArray(bytes.size) { i ->
                (bytes[i].toInt() xor 0xFF).toUByte()
            }
        }
    }
}
