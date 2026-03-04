package io.github.salomax.neotool.banking.domain.import

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class DecodedImportText(
    val text: String,
    val encoding: String,
)

/**
 * Decodes import payload bytes with strict UTF-8 first, then legacy encodings commonly used by banks.
 */
object ImportTextDecoder {
    private val WINDOWS_1252 = Charset.forName("windows-1252")
    private val CANDIDATES = listOf(
        StandardCharsets.UTF_8,
        WINDOWS_1252,
        StandardCharsets.ISO_8859_1,
    ).distinctBy(Charset::name)

    fun decode(bytes: ByteArray): DecodedImportText {
        CANDIDATES.forEach { charset ->
            decodeStrict(bytes, charset)?.let { text ->
                return DecodedImportText(
                    text = text,
                    encoding = charset.name(),
                )
            }
        }

        return DecodedImportText(
            text = String(bytes, StandardCharsets.UTF_8),
            encoding = StandardCharsets.UTF_8.name(),
        )
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? =
        runCatching {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
}
