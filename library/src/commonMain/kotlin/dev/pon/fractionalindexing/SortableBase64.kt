package dev.pon.fractionalindexing

/**
 * A Base64-like encoding that preserves the sort order of the underlying byte arrays.
 *
 * The encoding scheme is identical to standard Base64 (RFC 4648) except:
 * - The alphabet is 64 URL-safe characters in ASCII ascending order:
 *   `-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz`
 * - No padding characters (`=`) are appended.
 *   Remainder bytes produce 2 chars (1 byte) or 3 chars (2 bytes).
 *   Unused trailing bits in the last character must be zero.
 * - String length `4n+1` is invalid (cannot represent a whole number of bytes).
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object SortableBase64 {
    private const val ALPHABET = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz"

    private val DECODE_TABLE = IntArray(128) { -1 }.also { table ->
        for (i in ALPHABET.indices) {
            table[ALPHABET[i].code] = i
        }
    }

    fun encode(bytes: UByteArray): String {
        if (bytes.isEmpty()) return ""

        val sb = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0

        // Process full 3-byte groups
        while (i + 2 < bytes.size) {
            val b0 = bytes[i].toInt()
            val b1 = bytes[i + 1].toInt()
            val b2 = bytes[i + 2].toInt()

            sb.append(ALPHABET[b0 shr 2])
            sb.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 shr 4)])
            sb.append(ALPHABET[((b1 and 0x0F) shl 2) or (b2 shr 6)])
            sb.append(ALPHABET[b2 and 0x3F])

            i += 3
        }

        // Handle remaining bytes
        when (bytes.size - i) {
            1 -> {
                val b0 = bytes[i].toInt()
                sb.append(ALPHABET[b0 shr 2])
                sb.append(ALPHABET[(b0 and 0x03) shl 4])
            }

            2 -> {
                val b0 = bytes[i].toInt()
                val b1 = bytes[i + 1].toInt()
                sb.append(ALPHABET[b0 shr 2])
                sb.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 shr 4)])
                sb.append(ALPHABET[(b1 and 0x0F) shl 2])
            }
        }

        return sb.toString()
    }

    fun decode(str: String): UByteArray {
        if (str.isEmpty()) return UByteArray(0)

        val len = str.length
        require(len % 4 != 1) { "invalid sortable base64 string length" }

        val fullQuads = len / 4
        val remainderChars = len % 4

        val outputLength = fullQuads * 3 + when (remainderChars) {
            0 -> 0
            2 -> 1
            3 -> 2
            else -> error("unreachable")
        }

        val result = UByteArray(outputLength)
        var si = 0 // string index
        var bi = 0 // byte index

        // Process full 4-character groups
        repeat(fullQuads) {
            val c0 = decodeChar(str[si++])
            val c1 = decodeChar(str[si++])
            val c2 = decodeChar(str[si++])
            val c3 = decodeChar(str[si++])

            result[bi++] = ((c0 shl 2) or (c1 shr 4)).toUByte()
            result[bi++] = (((c1 and 0x0F) shl 4) or (c2 shr 2)).toUByte()
            result[bi++] = (((c2 and 0x03) shl 6) or c3).toUByte()
        }

        // Handle remaining characters
        when (remainderChars) {
            2 -> {
                val c0 = decodeChar(str[si++])
                val c1 = decodeChar(str[si])
                require(c1 and 0x0F == 0) { "non-zero padding bits in sortable base64" }
                result[bi] = ((c0 shl 2) or (c1 shr 4)).toUByte()
            }

            3 -> {
                val c0 = decodeChar(str[si++])
                val c1 = decodeChar(str[si++])
                val c2 = decodeChar(str[si])
                require(c2 and 0x03 == 0) { "non-zero padding bits in sortable base64" }
                result[bi++] = ((c0 shl 2) or (c1 shr 4)).toUByte()
                result[bi] = (((c1 and 0x0F) shl 4) or (c2 shr 2)).toUByte()
            }
        }

        return result
    }

    private fun decodeChar(c: Char): Int {
        val code = c.code
        require(code in 0..127) { "invalid character in sortable base64: '$c'" }
        val value = DECODE_TABLE[code]
        require(value >= 0) { "invalid character in sortable base64: '$c'" }
        return value
    }
}
