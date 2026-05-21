package io.github.salomax.neotool.banking.service.billimport.pdf

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DeterministicPdfReaderRegistry")
class DeterministicPdfReaderRegistryTest {
    @Test
    fun `findByProfile returns reader that supports profile`() {
        val reader =
            object : DeterministicPdfReader {
                override val readerId: String = "stub-reader"
                override val supportedProfileIds: Set<String> = setOf("profile-a")

                override fun parse(input: DeterministicPdfReaderInput) =
                    throw UnsupportedOperationException("not needed")
            }
        val registry = DeterministicPdfReaderRegistry(listOf(reader))

        val result = registry.findByProfile("profile-a")

        assertThat(result).isNotNull
        assertThat(result?.readerId).isEqualTo("stub-reader")
    }

    @Test
    fun `findByProfile returns null for unsupported profile`() {
        val registry = DeterministicPdfReaderRegistry(emptyList())

        val result = registry.findByProfile("missing")

        assertThat(result).isNull()
    }
}
