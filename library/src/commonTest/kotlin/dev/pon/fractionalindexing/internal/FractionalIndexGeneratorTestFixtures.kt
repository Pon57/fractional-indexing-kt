package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import dev.pon.fractionalindexing.FractionalIndexGenerator
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int

@OptIn(ExperimentalUnsignedTypes::class)
internal object FractionalIndexGeneratorTestFixtures {
    val fractionalIndexArb: Arb<FractionalIndex> = arbitrary(
        edgecases = listOf(
            FractionalIndex.default(),
            FractionalIndexGenerator.before(FractionalIndex.default()),
            FractionalIndexGenerator.after(FractionalIndex.default()),
            walkBefore(64),
            walkAfter(64),
            walkBefore(4_200),
            walkAfter(4_200),
            FractionalIndexGenerator
                .between(
                    walkBefore(2_100),
                    walkAfter(2_100),
                )
                .getOrThrow(),
        ),
    ) {
        val steps = Arb.int(0..512).bind()
        var current = FractionalIndex.default()
        repeat(steps) {
            when (Arb.int(0..2).bind()) {
                0 -> current = FractionalIndexGenerator.before(current)
                1 -> current = FractionalIndexGenerator.after(current)
                else -> {
                    val other = if (Arb.int(0..1).bind() == 0) {
                        FractionalIndexGenerator.before(current)
                    } else {
                        FractionalIndexGenerator.after(current)
                    }
                    val lower = minOf(current, other)
                    val upper = maxOf(current, other)
                    current = FractionalIndexGenerator.between(lower, upper).getOrThrow()
                }
            }
        }
        current
    }

    fun walkBefore(steps: Int): FractionalIndex {
        var current = FractionalIndex.default()
        repeat(steps) {
            current = FractionalIndexGenerator.before(current)
        }
        return current
    }

    fun walkAfter(steps: Int): FractionalIndex {
        var current = FractionalIndex.default()
        repeat(steps) {
            current = FractionalIndexGenerator.after(current)
        }
        return current
    }
}
