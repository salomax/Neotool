package io.github.salomax.neotool.financialdata.test.unit.dataloader

import io.github.salomax.neotool.financialdata.graphql.dataloader.InstitutionRatingHistoryDataLoader
import io.github.salomax.neotool.financialdata.graphql.dataloader.InstitutionRatingsDataLoader
import io.github.salomax.neotool.financialdata.graphql.dto.InstitutionRatingDTO
import io.github.salomax.neotool.financialdata.graphql.dto.RatingHistoryDTO
import io.github.salomax.neotool.financialdata.graphql.dto.RatingIssuerDTO
import io.github.salomax.neotool.financialdata.graphql.resolver.FinancialInstitutionRatingResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InstitutionDataLoadersTest {
    private val ratingResolver: FinancialInstitutionRatingResolver = mock()

    @Test
    fun `institution ratings data loader batches requests and returns empty list for missing keys`() {
        val issuerId = UUID.randomUUID()
        val rating =
            InstitutionRatingDTO(
                id = UUID.randomUUID(),
                name = "AAA",
                label = "Stable",
                ratingDate = LocalDate.of(2026, 1, 15),
                customParams = emptyMap(),
                issuer =
                    RatingIssuerDTO(
                        id = issuerId,
                        code = "FITCH",
                        name = "Fitch Ratings",
                        website = null,
                        logoAssetId = null,
                    ),
                updatedAt = Instant.parse("2026-01-15T12:00:00Z"),
            )

        whenever(ratingResolver.resolveRatingsBatch(listOf("001", "002", "404")))
            .thenReturn(mapOf("001" to listOf(rating)))

        val loader = InstitutionRatingsDataLoader.create(ratingResolver)
        val first = loader.load("001")
        val second = loader.load("002")
        val missing = loader.load("404")

        loader.dispatchAndJoin()

        assertThat(first.join()).containsExactly(rating)
        assertThat(second.join()).isEmpty()
        assertThat(missing.join()).isEmpty()
        verify(ratingResolver).resolveRatingsBatch(listOf("001", "002", "404"))
    }

    @Test
    fun `institution rating history data loader batches by limit and preserves fallback behavior`() {
        val historyFirst =
            RatingHistoryDTO(
                id = UUID.randomUUID(),
                name = "AA",
                label = "Watch Positive",
                ratingDate = LocalDate.of(2026, 2, 1),
                customParams = emptyMap(),
                issuerCode = "SP",
                changedAt = Instant.parse("2026-02-01T10:00:00Z"),
            )
        val historyThird =
            RatingHistoryDTO(
                id = UUID.randomUUID(),
                name = "A",
                label = "Stable",
                ratingDate = LocalDate.of(2026, 2, 10),
                customParams = emptyMap(),
                issuerCode = "MOODYS",
                changedAt = Instant.parse("2026-02-10T10:00:00Z"),
            )

        whenever(ratingResolver.resolveRatingHistoryBatch(listOf("001", "002"), 5))
            .thenReturn(mapOf("001" to listOf(historyFirst)))
        whenever(ratingResolver.resolveRatingHistoryBatch(listOf("003", "404"), 10))
            .thenReturn(mapOf("003" to listOf(historyThird)))

        val loader = InstitutionRatingHistoryDataLoader.create(ratingResolver)
        val firstKey = InstitutionRatingHistoryDataLoader.RatingHistoryKey("001", 5)
        val secondKey = InstitutionRatingHistoryDataLoader.RatingHistoryKey("002", 5)
        val thirdKey = InstitutionRatingHistoryDataLoader.RatingHistoryKey("003", 10)
        val missingKey = InstitutionRatingHistoryDataLoader.RatingHistoryKey("404", 10)

        val first = loader.load(firstKey)
        val second = loader.load(secondKey)
        val third = loader.load(thirdKey)
        val missing = loader.load(missingKey)

        loader.dispatchAndJoin()

        assertThat(first.join()).containsExactly(historyFirst)
        assertThat(second.join()).isEmpty()
        assertThat(third.join()).containsExactly(historyThird)
        assertThat(missing.join()).isEmpty()

        verify(ratingResolver).resolveRatingHistoryBatch(listOf("001", "002"), 5)
        verify(ratingResolver).resolveRatingHistoryBatch(listOf("003", "404"), 10)
    }
}
