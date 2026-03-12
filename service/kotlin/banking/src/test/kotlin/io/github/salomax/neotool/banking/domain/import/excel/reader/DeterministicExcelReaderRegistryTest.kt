package io.github.salomax.neotool.banking.domain.import.excel.reader

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DeterministicExcelReaderRegistry")
class DeterministicExcelReaderRegistryTest {
    @Test
    fun `findByProfile returns reader that supports profile`() {
        val reader =
            object : DeterministicExcelReader {
                override val readerId: String = "stub-excel-reader"
                override val supportedProfileIds: Set<String> = setOf("excel-profile-a")

                override fun parse(input: DeterministicExcelReaderInput) =
                    throw UnsupportedOperationException("not needed")
            }
        val registry = DeterministicExcelReaderRegistry(listOf(reader))

        val result = registry.findByProfile("excel-profile-a")

        assertThat(result).isNotNull
        assertThat(result?.readerId).isEqualTo("stub-excel-reader")
    }

    @Test
    fun `findByProfile returns null for unsupported profile`() {
        val registry = DeterministicExcelReaderRegistry(emptyList())

        val result = registry.findByProfile("missing")

        assertThat(result).isNull()
    }
}
