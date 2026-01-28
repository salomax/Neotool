package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.model.FinancialInstitutionRatingEntity
import io.github.salomax.neotool.financialdata.model.FinancialInstitutionRatingHistoryEntity
import io.github.salomax.neotool.financialdata.repo.FinancialInstitutionRatingHistoryRepository
import io.github.salomax.neotool.financialdata.repo.FinancialInstitutionRatingRepository
import io.github.salomax.neotool.financialdata.service.FinancialInstitutionRatingService
import io.micronaut.data.model.Pageable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class FinancialInstitutionRatingServiceTest {
    private val ratingRepository: FinancialInstitutionRatingRepository = mock()
    private val historyRepository: FinancialInstitutionRatingHistoryRepository = mock()

    private lateinit var service: FinancialInstitutionRatingService

    private val fiId = UUID.randomUUID()
    private val issuerId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service =
            FinancialInstitutionRatingService(
                ratingRepository,
                historyRepository,
            )
    }

    @Test
    fun `getRatings returns mapped domain objects`() {
        val ratingEntity =
            FinancialInstitutionRatingEntity(
                id = UUID.randomUUID(),
                financialInstitutionId = fiId,
                issuerId = issuerId,
                name = "AAA",
                label = "Stable",
                ratingDate = LocalDate.now(),
                customParams = mapOf("outlook" to "stable"),
                sourceEvidenceId = UUID.randomUUID(),
                contentHash = "hash123",
                updatedAt = Instant.now(),
            )

        whenever(
            ratingRepository.findByFinancialInstitutionId(fiId),
        ).thenReturn(listOf(ratingEntity))

        val result = service.getRatings(fiId)

        assertThat(result).hasSize(1)
        val rating = result.first()
        assertThat(rating.id).isEqualTo(ratingEntity.id)
        assertThat(rating.name).isEqualTo("AAA")
        assertThat(rating.label).isEqualTo("Stable")
    }

    @Test
    fun `getHistory returns mapped domain objects`() {
        val historyEntity =
            FinancialInstitutionRatingHistoryEntity(
                id = UUID.randomUUID(),
                financialInstitutionId = fiId,
                issuerId = issuerId,
                name = "BBB",
                label = "Negative",
                ratingDate = LocalDate.now().minusDays(1),
                customParams = emptyMap<String, Any>(),
                sourceEvidenceId = UUID.randomUUID(),
                contentHash = "hashOld",
                changedAt = Instant.now(),
            )

        whenever(
            historyRepository.findByFinancialInstitutionIdOrderByChangedAtDesc(eq(fiId), any<Pageable>()),
        ).thenReturn(listOf(historyEntity))

        val result = service.getHistory(fiId, 10)

        assertThat(result).hasSize(1)
        val history = result.first()
        assertThat(history.name).isEqualTo("BBB")
        assertThat(history.label).isEqualTo("Negative")
    }
}
