package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.model.transaction.MerchantEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class DefaultMerchantRepositoryTest {
    private lateinit var merchantEntityRepository: MerchantEntityRepository
    private lateinit var logoCacheRepository: MerchantLogoCacheEntityRepository
    private lateinit var repository: DefaultMerchantRepository

    private val accountId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        merchantEntityRepository = mock()
        logoCacheRepository = mock()
        repository = DefaultMerchantRepository(merchantEntityRepository, logoCacheRepository)
    }

    @Test
    fun `searchForAccount applies whole-word search`() {
        val searchRegex = "(^|[^a-z])\\Quber\\E([^a-z]|$)"
        whenever(merchantEntityRepository.countForAccountBySearchRegex(accountId, searchRegex)).thenReturn(2)
        whenever(merchantEntityRepository.searchPageForAccount(accountId, searchRegex, null, null, 21)).thenReturn(
            listOf(
                entity(name = "Uber"),
                entity(name = "Uber Eats"),
            ),
        )

        val page = repository.searchForAccount(accountId, "uber", null, 20)

        assertThat(page.totalCount).isEqualTo(2)
        assertThat(page.items.map { it.name }).containsExactly("Uber", "Uber Eats")
        assertThat(page.hasNextPage).isFalse()
    }

    @Test
    fun `searchForAccount returns empty page when no match`() {
        val searchRegex = "(^|[^a-z])\\Quber\\E([^a-z]|$)"
        whenever(merchantEntityRepository.countForAccountBySearchRegex(accountId, searchRegex)).thenReturn(0)
        whenever(merchantEntityRepository.searchPageForAccount(accountId, searchRegex, null, null, 21)).thenReturn(emptyList())

        val page = repository.searchForAccount(accountId, "uber", null, 20)

        assertThat(page.totalCount).isZero()
        assertThat(page.items).isEmpty()
        assertThat(page.startCursor).isNull()
        assertThat(page.endCursor).isNull()
    }

    @Test
    fun `searchForAccount paginates with keyset cursor`() {
        val amazonId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val appleId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val azureId = UUID.fromString("00000000-0000-0000-0000-000000000003")

        whenever(merchantEntityRepository.countForAccountBySearchRegex(accountId, null)).thenReturn(3)
        whenever(merchantEntityRepository.searchPageForAccount(accountId, null, null, null, 3)).thenReturn(
            listOf(
                entity(name = "Amazon", id = amazonId),
                entity(name = "Apple", id = appleId),
                entity(name = "Azure", id = azureId),
            ),
        )
        whenever(merchantEntityRepository.searchPageForAccount(accountId, null, "Apple", appleId, 3)).thenReturn(
            listOf(entity(name = "Azure", id = azureId)),
        )

        val firstPage = repository.searchForAccount(accountId, null, null, 2)
        val secondPage = repository.searchForAccount(accountId, null, firstPage.endCursor, 2)

        assertThat(firstPage.items.map { it.name }).containsExactly("Amazon", "Apple")
        assertThat(firstPage.hasNextPage).isTrue()
        assertThat(secondPage.items.map { it.name }).containsExactly("Azure")
        assertThat(secondPage.hasPreviousPage).isFalse()
        assertThat(secondPage.hasNextPage).isFalse()
        verify(merchantEntityRepository).searchPageForAccount(eq(accountId), eq(null), eq("Apple"), eq(appleId), eq(3))
    }

    private fun entity(
        name: String,
        id: UUID = UUID.randomUUID(),
    ): MerchantEntity =
        MerchantEntity(
            id = id,
            merchantName = name.lowercase(),
            name = name,
            isSystem = true,
            accountId = null,
        )
}
