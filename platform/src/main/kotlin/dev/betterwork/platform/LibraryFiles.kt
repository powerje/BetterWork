package dev.betterwork.platform

import dev.betterwork.data.MAX_FILE_BYTES
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded reads also work on Android API 30–32, before InputStream.readNBytes. */
fun InputStream.readLibraryText(): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, MAX_FILE_BYTES + 1 - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
        require(output.size() <= MAX_FILE_BYTES) { "Library exceeds 1 MB" }
    }
    return output.toByteArray().decodeToString(throwOnInvalidSequence = true)
}
