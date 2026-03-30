package io.github.salomax.neotool.banking.domain.import.nfce

import io.github.salomax.neotool.banking.client.import.nfce.NfceInvalidException
import io.github.salomax.neotool.banking.client.import.nfce.NfceUnavailableException
import io.github.salomax.neotool.banking.service.import.nfce.SpNfceFetcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("SpNfceFetcher")
class SpNfceFetcherTest {

    private val fetcher = SpNfceFetcher()

    @Nested
    @DisplayName("supportedUfCodes")
    inner class SupportedUfCodesTests {
        @Test
        fun `supports only SP (UF code 35)`() {
            assertThat(fetcher.supportedUfCodes()).containsExactly("35")
        }
    }

    @Nested
    @DisplayName("fetchSummaryPage — error handling")
    inner class FetchSummaryErrorTests {
        @Test
        fun `throws NfceUnavailableException for invalid URL`() {
            assertThrows<NfceUnavailableException> {
                fetcher.fetchSummaryPage("not-a-url")
            }
        }
    }

    @Nested
    @DisplayName("fetchDetailedPage — ViewState extraction")
    inner class FetchDetailedTests {
        @Test
        fun `throws NfceInvalidException when VIEWSTATE is missing`() {
            val htmlWithoutViewState = "<html><body>No form fields</body></html>"

            val ex = assertThrows<NfceInvalidException> {
                fetcher.fetchDetailedPage(htmlWithoutViewState, "https://example.com")
            }
            assertThat(ex.message).contains("__VIEWSTATE")
        }
    }
}
