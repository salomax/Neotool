package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.service.import.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@DisplayName("ImportTextDecoder")
class ImportTextDecoderTest {
    @Test
    fun `decodes utf8 bytes using utf8`() {
        val bytes = "Lançamento Fácil".toByteArray(StandardCharsets.UTF_8)

        val decoded = ImportTextDecoder.decode(bytes)

        assertThat(decoded.encoding).isEqualTo("UTF-8")
        assertThat(decoded.text).isEqualTo("Lançamento Fácil")
    }

    @Test
    fun `falls back to windows-1252 for latin bytes`() {
        val bytes = "Lançamento Fácil".toByteArray(Charset.forName("ISO-8859-1"))

        val decoded = ImportTextDecoder.decode(bytes)

        assertThat(decoded.encoding).isEqualTo("windows-1252")
        assertThat(decoded.text).isEqualTo("Lançamento Fácil")
    }
}
