package io.github.salomax.neotool.financialdata.test.unit.resolver

import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.InstitutionSearchMapper
import io.github.salomax.neotool.financialdata.graphql.resolver.InstitutionSearchResolver
import io.github.salomax.neotool.financialdata.service.InstitutionSearchService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InstitutionSearchResolverTest {
    private val institutionSearchService: InstitutionSearchService = mock()
    private val mapper: InstitutionSearchMapper = mock()
    private lateinit var resolver: InstitutionSearchResolver

    @BeforeEach
    fun setUp() {
        resolver = InstitutionSearchResolver(institutionSearchService, mapper)
    }

    @Test
    fun `searchBacenInstitutionsAutocomplete returns DTO when query is valid`() {
        whenever(
            institutionSearchService.searchBacenInstitutionsAutocomplete(
                eq("Banco"),
                eq(null),
                eq(20),
            ),
        )
            .thenReturn(listOf("Banco A"))
        val expectedDto =
            BacenInstitutionAutocompleteConnectionDTO(
                suggestions = listOf(BacenInstitutionAutocompleteItemDTO("Banco A")),
            )
        whenever(mapper.toBacenInstitutionAutocompleteConnectionDTO(any())).thenReturn(expectedDto)

        val result = resolver.searchBacenInstitutionsAutocomplete("Banco", null)

        assertThat(result.suggestions).hasSize(1)
        assertThat(result.suggestions[0].name).isEqualTo("Banco A")
    }

    @Test
    fun `searchBacenInstitutionsAutocomplete throws when query is blank`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.searchBacenInstitutionsAutocomplete("  ", null)
            }
        assertThat(ex.message).contains("Query cannot be empty for autocomplete search")
    }

    @Test
    fun `bacenInstitutions returns DTO with default page size`() {
        val connection =
            ConnectionBuilder
                .buildConnection(
                    items = emptyList<BacenInstitutionSummary>(),
                    hasMore = false,
                    encodeCursor = { _ -> "" },
                    totalCount = 0L,
                )
        whenever(
            institutionSearchService.bacenInstitutions(eq(null), eq(null), eq(20), eq(null)),
        )
            .thenReturn(connection)
        val expectedDto =
            BacenInstitutionConnectionDTO(
                edges = emptyList(),
                pageInfo =
                    PageInfoDTO(
                        hasNextPage = false,
                        hasPreviousPage = false,
                        startCursor = null,
                        endCursor = null,
                    ),
                totalCount = 0,
            )
        whenever(mapper.toBacenInstitutionConnectionDTO(any())).thenReturn(expectedDto)

        val result = resolver.bacenInstitutions(null, null, null, null)

        assertThat(result).isEqualTo(expectedDto)
        verify(institutionSearchService).bacenInstitutions(eq(null), eq(null), eq(20), eq(null))
    }

    @Test
    fun `bacenInstitutions validates page size parameter`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.bacenInstitutions(null, null, 0, null)
            }
        assertThat(ex.message).contains("first")
    }

    @Test
    fun `bacenInstitutions passes all parameters to service`() {
        val connection =
            ConnectionBuilder
                .buildConnection<BacenInstitutionSummary>(
                    items = emptyList(),
                    hasMore = false,
                    encodeCursor = { _ -> "" },
                    totalCount = 0L,
                )
        whenever(
            institutionSearchService.bacenInstitutions(eq("x"), eq("202401"), eq(10), eq("c")),
        )
            .thenReturn(connection)
        whenever(mapper.toBacenInstitutionConnectionDTO(any()))
            .thenReturn(
                BacenInstitutionConnectionDTO(
                    edges = emptyList(),
                    pageInfo =
                        PageInfoDTO(
                            hasNextPage = false,
                            hasPreviousPage = false,
                            startCursor = null,
                            endCursor = null,
                        ),
                    totalCount = 0,
                ),
            )

        resolver.bacenInstitutions("x", "202401", 10, "c")

        verify(institutionSearchService).bacenInstitutions(eq("x"), eq("202401"), eq(10), eq("c"))
    }
}
