@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package net.mioyi.kinroll.ios

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

internal fun FloatArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned {
    NSData.create(bytes = it.addressOf(0), length = (size.toLong() * 4).toULong())
}
internal fun LongArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned {
    NSData.create(bytes = it.addressOf(0), length = (size.toLong() * 8).toULong())
}
internal fun NSData.toFloatArray(): FloatArray {
    require(length % 4uL == 0uL && length / 4uL <= Int.MAX_VALUE.toULong())
    return FloatArray((length / 4uL).toInt()).also { result ->
        if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
}
internal fun NSData.toByteArray(): ByteArray {
    require(length <= Int.MAX_VALUE.toULong())
    return ByteArray(length.toInt()).also { result ->
        if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
}
