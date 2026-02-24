package dev.pon.fractionalindexing.example

import dev.pon.fractionalindexing.FractionalIndex

@OptIn(ExperimentalUnsignedTypes::class)
fun FractionalIndex.getByteSize(): Int = this.bytes.size