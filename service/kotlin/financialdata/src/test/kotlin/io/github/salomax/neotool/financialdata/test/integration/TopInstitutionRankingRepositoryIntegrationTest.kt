package io.github.salomax.neotool.financialdata.test.integration

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.financialdata.domain.TopInstitutionMetric
import io.github.salomax.neotool.financialdata.repo.TopInstitutionRankingRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@MicronautTest(startApplication = true)
@DisplayName("TopInstitutionRankingRepository Integration Tests")
@Tag("integration")
@Tag("financial-data")
class TopInstitutionRankingRepositoryIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var topInstitutionRankingRepository: TopInstitutionRankingRepository

    @Test
    fun `findTopByMetric returns list without error`() {
        val result =
            topInstitutionRankingRepository.findTopByMetric(
                TopInstitutionMetric.PROFIT,
                "202403",
                10,
            )
        assertThat(result).isNotNull()
        assertThat(result.size).isLessThanOrEqualTo(10)
        if (result.size >= 2) {
            assertThat(result[0].metricValue).isGreaterThanOrEqualTo(result[1].metricValue)
        }
    }

    @Test
    fun `findTopByMetric respects limit`() {
        val result =
            topInstitutionRankingRepository.findTopByMetric(
                TopInstitutionMetric.BASILEIA_INDEX,
                "202403",
                5,
            )
        assertThat(result).hasSizeLessThanOrEqualTo(5)
    }

    @Test
    fun `findTopByMetric orders mobilization index ascending`() {
        val result =
            topInstitutionRankingRepository.findTopByMetric(
                TopInstitutionMetric.MOBILIZATION_INDEX,
                "202403",
                10,
            )

        assertThat(result).isNotNull()
        assertThat(result.size).isLessThanOrEqualTo(10)
        if (result.size >= 2) {
            assertThat(result[0].metricValue).isLessThanOrEqualTo(result[1].metricValue)
        }
    }

    @Test
    fun `findTopMetricsForInstitutions filters by requested institutions`() {
        val seedInstitutions =
            topInstitutionRankingRepository
                .findTopByMetric(
                    TopInstitutionMetric.PROFIT,
                    "202403",
                    5,
                ).map { it.bacenCodInst }
                .toSet()

        val result =
            topInstitutionRankingRepository.findTopMetricsForInstitutions(
                anoMes = "202403",
                bacenCodInsts = seedInstitutions,
                limit = 100,
            )

        assertThat(result).isNotNull()
        assertThat(result).allMatch { it.bacenCodInst in seedInstitutions }
    }
}
