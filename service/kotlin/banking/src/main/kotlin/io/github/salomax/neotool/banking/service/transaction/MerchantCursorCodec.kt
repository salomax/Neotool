package io.github.salomax.neotool.banking.service.transaction

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

data class MerchantCursor(
    val name: String,
    val id: UUID,
)

object MerchantCursorCodec {
    fun encode(
        name: String,
        id: UUID,
    ): String {
        val raw = "$name:$id"
        return Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(cursor: String): MerchantCursor {
        val raw =
            try {
                String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid merchant cursor", e)
            }
        val separator = raw.lastIndexOf(':')
        require(separator > 0 && separator < raw.lastIndex) { "Invalid merchant cursor format" }
        val name = raw.substring(0, separator)
        val id =
            try {
                UUID.fromString(raw.substring(separator + 1))
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid merchant cursor id", e)
            }
        return MerchantCursor(name = name, id = id)
    }
}
