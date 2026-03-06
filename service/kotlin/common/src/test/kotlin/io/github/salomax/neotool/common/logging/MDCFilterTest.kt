package io.github.salomax.neotool.common.logging

import ch.qos.logback.core.spi.FilterReply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.slf4j.MDC

@DisplayName("MDCFilter Tests")
class MDCFilterTest {
    private val mdcFilter = MDCFilter()

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun `should generate correlation id when absent`() {
        MDC.clear()

        val result = mdcFilter.decide(mock())

        val correlationId = MDC.get(MDCFilter.CORRELATION_ID_KEY)
        assertThat(result).isEqualTo(FilterReply.ACCEPT)
        assertThat(correlationId).isNotBlank()
        assertThat(correlationId).hasSize(8)
    }

    @Test
    fun `should keep existing correlation id`() {
        MDC.put(MDCFilter.CORRELATION_ID_KEY, "abcd1234")

        val result = mdcFilter.decide(mock())

        assertThat(result).isEqualTo(FilterReply.ACCEPT)
        assertThat(MDC.get(MDCFilter.CORRELATION_ID_KEY)).isEqualTo("abcd1234")
    }
}
