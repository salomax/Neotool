package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.banking.model.transaction.CategoryRuleEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("DefaultCategoryRuleRepository")
class DefaultCategoryRuleRepositoryTest {
    private lateinit var entityRepository: CategoryRuleEntityRepository
    private lateinit var repository: DefaultCategoryRuleRepository

    @BeforeEach
    fun setUp() {
        entityRepository = mock()
        repository = DefaultCategoryRuleRepository(entityRepository)
    }

    @Test
    fun `findSystemRules maps entities to domain in repository order`() {
        val first = entity(priority = 1, matchCount = 10, createdAt = Instant.now())
        val second = entity(priority = 2, matchCount = 5, createdAt = Instant.now().minusSeconds(10))
        whenever(entityRepository.findSystemRules()).thenReturn(listOf(first, second))

        val result = repository.findSystemRules()

        verify(entityRepository).findSystemRules()
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(first.id)
        assertThat(result[0].priority).isEqualTo(1)
        assertThat(result[1].id).isEqualTo(second.id)
        assertThat(result[1].priority).isEqualTo(2)
    }

    @Test
    fun `findSystemRules preserves precedence ordering from entity query`() {
        val highPrecedence = entity(priority = 1, matchCount = 20, createdAt = Instant.now())
        val lowPrecedence = entity(priority = 1, matchCount = 5, createdAt = Instant.now().minusSeconds(100))
        whenever(entityRepository.findSystemRules()).thenReturn(listOf(highPrecedence, lowPrecedence))

        val result = repository.findSystemRules()

        assertThat(result.map { it.id }).containsExactly(highPrecedence.id, lowPrecedence.id)
        verify(entityRepository).findSystemRules()
    }

    @Test
    fun `findRulesForAccount delegates accountId to entity repository`() {
        val accountId = UUID.randomUUID()
        whenever(entityRepository.findRulesForAccount(any())).thenReturn(emptyList())

        repository.findRulesForAccount(accountId)

        verify(entityRepository).findRulesForAccount(eq(accountId))
    }

    @Test
    fun `findDisabledRuleIdsForAccount delegates accountId to entity repository`() {
        val accountId = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        whenever(entityRepository.findDisabledRuleIdsForAccount(accountId)).thenReturn(listOf(ruleId))

        val result = repository.findDisabledRuleIdsForAccount(accountId)

        assertThat(result).containsExactly(ruleId)
        verify(entityRepository).findDisabledRuleIdsForAccount(eq(accountId))
    }

    @Test
    fun `disableSystemRuleForAccount delegates to entity repository`() {
        val accountId = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        whenever(entityRepository.disableSystemRuleForAccount(eq(accountId), eq(ruleId), any())).thenReturn(1)

        val result = repository.disableSystemRuleForAccount(accountId, ruleId)

        assertThat(result).isTrue()
        verify(entityRepository, times(1)).disableSystemRuleForAccount(eq(accountId), eq(ruleId), any())
    }

    @Test
    fun `enableSystemRuleForAccount delegates to entity repository`() {
        val accountId = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        whenever(entityRepository.enableSystemRuleForAccount(accountId, ruleId)).thenReturn(1)

        val result = repository.enableSystemRuleForAccount(accountId, ruleId)

        assertThat(result).isTrue()
        verify(entityRepository).enableSystemRuleForAccount(eq(accountId), eq(ruleId))
    }

    private fun entity(
        id: UUID = UUID.randomUUID(),
        priority: Int = 10,
        matchCount: Int = 0,
        createdAt: Instant = Instant.now(),
        categoryId: UUID? = UUID.randomUUID(),
    ) = CategoryRuleEntity(
        id = id,
        accountId = null,
        categoryId = categoryId,
        merchantId = null,
        descriptionOverride = null,
        tagsOverrideJson = null,
        pattern = "ifood",
        patternType = PatternType.CONTAINS,
        applicableTo = CategoryApplicableTo.ANY,
        priority = priority,
        isSystem = true,
        isImplicit = false,
        matchCount = matchCount,
        deletedAt = null,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
