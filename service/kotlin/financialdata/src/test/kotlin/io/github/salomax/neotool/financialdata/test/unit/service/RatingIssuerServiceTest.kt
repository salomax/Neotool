package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.model.RatingIssuerEntity
import io.github.salomax.neotool.financialdata.repo.RatingIssuerRepository
import io.github.salomax.neotool.financialdata.service.RatingIssuerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class RatingIssuerServiceTest {
    private val repository: RatingIssuerRepository = mock()
    private lateinit var service: RatingIssuerService

    @BeforeEach
    fun setUp() {
        service = RatingIssuerService(repository)
    }

    @Test
    fun `getEnabledIssuers returns all enabled issuers from repository`() {
        val entity1 = ratingIssuerEntity("SNP", "S&P")
        val entity2 = ratingIssuerEntity("MOO", "Moody's")
        whenever(repository.findByEnabledTrue()).thenReturn(listOf(entity1, entity2))

        val result = service.getEnabledIssuers()

        assertThat(result).hasSize(2)
        assertThat(result.map { it.code }).containsExactly("SNP", "MOO")
    }

    @Test
    fun `getEnabledIssuers returns empty list when repository returns nothing`() {
        whenever(repository.findByEnabledTrue()).thenReturn(emptyList())

        val result = service.getEnabledIssuers()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getIssuersByIds returns empty map immediately when ids collection is empty`() {
        val result = service.getIssuersByIds(emptyList())

        assertThat(result).isEmpty()
        verify(repository, never()).findByIdIn(any())
    }

    @Test
    fun `getIssuersByIds returns map keyed by UUID`() {
        val id = UUID.randomUUID()
        val entity = ratingIssuerEntity("FIT", "Fitch", id = id)
        whenever(repository.findByIdIn(any())).thenReturn(listOf(entity))

        val result = service.getIssuersByIds(listOf(id))

        assertThat(result.keys).containsExactly(id)
    }

    @Test
    fun `getIssuersByIds fetches all provided ids`() {
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        whenever(repository.findByIdIn(any())).thenReturn(emptyList())

        service.getIssuersByIds(ids)

        verify(repository).findByIdIn(eq(ids))
    }

    private fun ratingIssuerEntity(
        code: String,
        name: String,
        id: UUID = UUID.randomUUID(),
    ): RatingIssuerEntity =
        RatingIssuerEntity(
            id = id,
            code = code,
            name = name,
            website = "https://example.com/$code",
            config = mapOf("k" to "v"),
            logoAssetId = UUID.randomUUID(),
            enabled = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
