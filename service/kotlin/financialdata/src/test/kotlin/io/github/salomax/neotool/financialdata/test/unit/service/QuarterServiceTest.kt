package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.repo.QuarterRepositoryCustom
import io.github.salomax.neotool.financialdata.service.QuarterService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class QuarterServiceTest {
    private val quarterRepository: QuarterRepositoryCustom = mock()
    private lateinit var service: QuarterService

    @BeforeEach
    fun setUp() {
        service = QuarterService(quarterRepository)
    }

    @Test
    fun `getLatestQuarter returns quarter from repository`() {
        whenever(quarterRepository.getLatestQuarter()).thenReturn("202403")

        val result = service.getLatestQuarter()

        assertThat(result).isEqualTo("202403")
    }

    @Test
    fun `getLatestQuarter returns null when repository returns null`() {
        whenever(quarterRepository.getLatestQuarter()).thenReturn(null)

        val result = service.getLatestQuarter()

        assertThat(result).isNull()
    }

    @Test
    fun `getLatestQuarter returns null when repository throws exception`() {
        whenever(quarterRepository.getLatestQuarter()).thenThrow(RuntimeException("DB error"))

        val result = service.getLatestQuarter()

        assertThat(result).isNull()
    }
}
