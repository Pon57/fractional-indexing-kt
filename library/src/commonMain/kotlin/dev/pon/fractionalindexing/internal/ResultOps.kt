package dev.pon.fractionalindexing.internal

internal inline fun <T> invalidArgumentToResult(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (exception: IllegalArgumentException) {
        Result.failure(exception)
    }
