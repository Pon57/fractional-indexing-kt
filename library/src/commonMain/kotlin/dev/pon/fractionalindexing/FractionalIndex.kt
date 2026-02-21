package dev.pon.fractionalindexing

import kotlin.io.encoding.Base64

@OptIn(ExperimentalUnsignedTypes::class)
public class FractionalIndex internal constructor(
    internal val unsafeRawBytes: UByteArray,
) : Comparable<FractionalIndex> {
    public val bytes: UByteArray
        get() = unsafeRawBytes.copyOf()

    init {
        require(unsafeRawBytes.isNotEmpty()) { "FractionalIndex must not be empty" }
        require(unsafeRawBytes.last() == TERMINATOR) { "FractionalIndex must end with terminator $TERMINATOR value: ${toString()}" }
    }

    public fun toHexString(): String = unsafeRawBytes.toHexString()
    public fun toBase64String(): String = Base64.encode(unsafeRawBytes.asByteArray())

    // Debug-friendly representation. Use toHexString()/toBase64String() for wire format.
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

        public fun default(): FractionalIndex = FractionalIndex(ubyteArrayOf(TERMINATOR))

        public fun fromBytes(bytes: UByteArray): Result<FractionalIndex> = runCatching {
            FractionalIndex(bytes.copyOf())
        }

        public fun fromHexString(hex: String): Result<FractionalIndex> = runCatching {
            FractionalIndex(hex.hexToUByteArray())
        }

        public fun fromBase64String(base64: String): Result<FractionalIndex> = runCatching {
            FractionalIndex(Base64.decode(base64).asUByteArray())
        }
    }
}
