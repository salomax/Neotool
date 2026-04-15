package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.service.transaction.MerchantCursorCodec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class MerchantCursorCodecTest {
    @Test
    fun `cursor roundtrip encodes and decodes name and id`() {
        val id = UUID.randomUUID()
        val cursor = MerchantCursorCodec.encode("Uber Eats", id)

        val decoded = MerchantCursorCodec.decode(cursor)

        assertThat(decoded.name).isEqualTo("Uber Eats")
        assertThat(decoded.id).isEqualTo(id)
    }

    @Test
    fun `invalid cursor throws`() {
        assertThrows<IllegalArgumentException> {
            MerchantCursorCodec.decode("not-base64")
        }
    }
}
