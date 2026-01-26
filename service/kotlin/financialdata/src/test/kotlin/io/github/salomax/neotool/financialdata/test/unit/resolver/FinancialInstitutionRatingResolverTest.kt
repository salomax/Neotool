package io.github.salomax.neotool.financialdata.test.unit.resolver

import io.github.salomax.neotool.financialdata.domain.FinancialInstitutionRating
import io.github.salomax.neotool.financialdata.domain.FinancialInstitutionRatingHistory
import io.github.salomax.neotool.financialdata.domain.RatingIssuer
import io.github.salomax.neotool.financialdata.graphql.resolver.FinancialInstitutionRatingResolver
import io.github.salomax.neotool.financialdata.repo.BacenInstitutionRepository
import io.github.salomax.neotool.financialdata.repo.result.BacenInstitutionIdMapping
import io.github.salomax.neotool.financialdata.service.FinancialInstitutionRatingService
import io.github.salomax.neotool.financialdata.service.RatingIssuerService
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FinancialInstitutionRatingResolverTest {
    private val ratingService: FinancialInstitutionRatingService = mock()
    private val ratingIssuerService: RatingIssuerService = mock()
    private val bacenInstitutionRepository: BacenInstitutionRepository = mock()

    private lateinit var resolver: FinancialInstitutionRatingResolver

    private val fiId = UUID.randomUUID()
    private val issuerId = UUID.randomUUID()
    private val bacenCodInst = "001"

    @BeforeEach
    fun setUp() {
        resolver = FinancialInstitutionRatingResolver(
            ratingService,
            ratingIssuerService,
            bacenInstitutionRepository,
        )
    }

    @Test
    fun `resolveRatingsBatch returns DTOs when institution found`() {
        whenever(bacenInstitutionRepository.findFinancialInstitutionIdsByBacenCodInsts(listOf(bacenCodInst)))
            .thenReturn(listOf(BacenInstitutionIdMapping(bacenCodInst, fiId)))

        val ratingDomain = FinancialInstitutionRating(
            id = UUID.randomUUID(),
            financialInstitutionId = fiId,
            issuerId = issuerId,
            name = "AAA",
            label = "Stable",
            ratingDate = LocalDate.now(),
            customParams = emptyMap(),
            sourceEvidenceId = UUID.randomUUID(),
            contentHash = "hash",
            updatedAt = Instant.now(),
        )
        whenever(ratingService.getRatingsByInstitutionIds(listOf(fiId)))
            .thenReturn(mapOf(fiId to listOf(ratingDomain)))

        // Mock Issuer resolution
        val issuerDomain = RatingIssuer(
            id = issuerId,
            code = "FITCH",
            name = "Fitch Ratings",
            enabled = true,
            config = emptyMap(),
        )
        whenever(ratingIssuerService.getIssuersByIds(listOf(issuerId)))
            .thenReturn(mapOf(issuerId to issuerDomain))

        val result = resolver.resolveRatingsBatch(listOf(bacenCodInst))

        assertThat(result).hasSize(1)
        val dto = result.getValue(bacenCodInst).first()
        assertThat(dto.name).isEqualTo("AAA")
        assertThat(dto.label).isEqualTo("Stable")
        assertThat(dto.issuer.code).isEqualTo("FITCH")
    }

    @Test
    fun `resolveRatingsBatch returns empty map when institution ID not resolved`() {
        whenever(bacenInstitutionRepository.findFinancialInstitutionIdsByBacenCodInsts(listOf(bacenCodInst)))
            .thenReturn(emptyList())

        val result = resolver.resolveRatingsBatch(listOf(bacenCodInst))

        assertThat(result).isEmpty()
    }

    @Test
    fun `resolveRatingHistoryBatch returns DTOs with limit`() {
        whenever(bacenInstitutionRepository.findFinancialInstitutionIdsByBacenCodInsts(listOf(bacenCodInst)))
            .thenReturn(listOf(BacenInstitutionIdMapping(bacenCodInst, fiId)))

        val history1 = FinancialInstitutionRatingHistory(
            id = UUID.randomUUID(),
            financialInstitutionId = fiId,
            issuerId = issuerId,
            name = "AAA",
            label = "Stable",
            ratingDate = LocalDate.now(),
            customParams = emptyMap(),
            sourceEvidenceId = UUID.randomUUID(),
            contentHash = "hash1",
            changedAt = Instant.now(),
        )

        whenever(ratingService.getHistoryByInstitutionIds(listOf(fiId), 1))
            .thenReturn(mapOf(fiId to listOf(history1)))

        // Mock Issuer resolution
        val issuerDomain = RatingIssuer(
            id = issuerId,
            code = "FITCH",
            name = "Fitch Ratings",
            enabled = true,
            config = emptyMap(),
        )
        whenever(ratingIssuerService.getIssuersByIds(listOf(issuerId)))
            .thenReturn(mapOf(issuerId to issuerDomain))

        val result = resolver.resolveRatingHistoryBatch(listOf(bacenCodInst), 1)

        assertThat(result).hasSize(1)
        assertThat(result.getValue(bacenCodInst).first().name).isEqualTo("AAA")
    }
}
