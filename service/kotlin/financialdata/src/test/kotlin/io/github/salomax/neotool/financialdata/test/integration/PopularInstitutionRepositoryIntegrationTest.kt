package io.github.salomax.neotool.financialdata.test.integration

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.financialdata.model.PopularInstitutionEntity
import io.github.salomax.neotool.financialdata.repo.PopularInstitutionRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("PopularInstitutionRepository Integration Tests")
@Tag("integration")
@Tag("financial-data")
class PopularInstitutionRepositoryIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var popularInstitutionRepository: PopularInstitutionRepository

    private var savedIds: List<UUID> = emptyList()

    @BeforeEach
    fun insertTestData() {
        val entities =
            listOf(
                PopularInstitutionEntity(
                    rank = 22,
                    parentCnpj = "00360305",
                    displayName = "Caixa",
                    shortDescription = "Test bank 2.",
                    active = true,
                ),
                PopularInstitutionEntity(
                    rank = 21,
                    parentCnpj = "18236120",
                    displayName = "Nubank",
                    shortDescription = "Test bank 1.",
                    active = true,
                ),
                PopularInstitutionEntity(
                    rank = 23,
                    parentCnpj = "00000000",
                    displayName = "Banco do Brasil",
                    shortDescription = "Inactive bank.",
                    active = false,
                ),
            )
        savedIds = popularInstitutionRepository.saveAll(entities).mapNotNull { it.id }
    }

    @AfterEach
    fun deleteTestData() {
        savedIds.forEach { id -> popularInstitutionRepository.deleteById(id) }
    }

    @Test
    fun `findAllByActiveTrueOrderByRank returns only active institutions ordered by rank`() {
        val result = popularInstitutionRepository.findAllByActiveTrueOrderByRank()

        val ourRows = result.filter { it.rank in 21..23 }
        assertThat(ourRows).hasSize(2)
        assertThat(ourRows[0].rank).isEqualTo(21)
        assertThat(ourRows[0].parentCnpj).isEqualTo("18236120")
        assertThat(ourRows[0].displayName).isEqualTo("Nubank")
        assertThat(ourRows[1].rank).isEqualTo(22)
        assertThat(ourRows[1].parentCnpj).isEqualTo("00360305")
        assertThat(ourRows[1].displayName).isEqualTo("Caixa")
    }
}
