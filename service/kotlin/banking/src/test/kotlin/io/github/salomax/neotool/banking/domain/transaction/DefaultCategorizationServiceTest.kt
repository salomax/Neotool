package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRuleRepository
import io.github.salomax.neotool.banking.service.transaction.DefaultTransactionRuleEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("DefaultTransactionRuleEngine")
class DefaultCategorizationServiceTest {
    private lateinit var ruleRepository: TransactionRuleRepository
    private lateinit var categoryRepository: TransactionCategoryRepository
    private lateinit var service: DefaultTransactionRuleEngine

    private val accountId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        ruleRepository = mock()
        categoryRepository = mock()
        service = DefaultTransactionRuleEngine(ruleRepository, categoryRepository)
    }

    @Nested
    @DisplayName("suggest")
    inner class SuggestTests {
        @Test
        fun `returns null when description is null or blank`() {
            assertThat(service.suggest(accountId, null, CreditDebitType.DEBIT)).isNull()
            assertThat(service.suggest(accountId, "   ", CreditDebitType.DEBIT)).isNull()

            verifyNoInteractions(ruleRepository)
            verifyNoInteractions(categoryRepository)
        }

        @Test
        fun `normalizes accents punctuation and whitespace before matching`() {
            val matchedRule =
                rule(
                    pattern = "sao paulo supermercado",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(matchedRule))
            whenever(
                categoryRepository.findById(matchedRule.categoryId!!),
            ).thenReturn(category(matchedRule.categoryId!!))

            val suggestion = service.suggest(accountId, "  São Paulo - Supermercado!!!  ", CreditDebitType.DEBIT)

            assertThat(suggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(matchedRule.id),
                    categoryId = matchedRule.categoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(requireNotNull(matchedRule.id)),
                ),
            )
            verify(ruleRepository).incrementMatchCount(requireNotNull(matchedRule.id))
        }

        @Test
        fun `matches CONTAINS pattern case-insensitively`() {
            val matchedRule =
                rule(
                    pattern = "UBER",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(matchedRule))
            whenever(
                categoryRepository.findById(matchedRule.categoryId!!),
            ).thenReturn(category(matchedRule.categoryId!!))

            val suggestion = service.suggest(accountId, "PAGAMENTO UBER EATS", CreditDebitType.DEBIT)

            assertThat(suggestion?.categoryId).isEqualTo(matchedRule.categoryId)
            verify(ruleRepository).incrementMatchCount(requireNotNull(matchedRule.id))
        }

        @Test
        fun `does not match CONTAINS pattern embedded inside another word`() {
            val matchedRule =
                rule(
                    pattern = "UBER",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(matchedRule))

            val suggestion = service.suggest(accountId, "CLAUBER LTDA", CreditDebitType.DEBIT)

            assertThat(suggestion).isNull()
            verify(ruleRepository, never()).incrementMatchCount(any())
        }

        @Test
        fun `keeps asterisk in normalized descriptor for matching`() {
            val matchedRule =
                rule(
                    pattern = "ifood*br",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(matchedRule))
            whenever(
                categoryRepository.findById(matchedRule.categoryId!!),
            ).thenReturn(category(matchedRule.categoryId!!))

            val suggestion = service.suggest(accountId, "IFOOD*BR  LTDA", CreditDebitType.DEBIT)

            assertThat(suggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(matchedRule.id),
                    categoryId = matchedRule.categoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(requireNotNull(matchedRule.id)),
                ),
            )
            verify(ruleRepository).incrementMatchCount(requireNotNull(matchedRule.id))
        }

        @Test
        fun `matches STARTS_WITH pattern`() {
            val matchedRule =
                rule(
                    pattern = "mercado extra",
                    patternType = PatternType.STARTS_WITH,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(matchedRule))
            whenever(
                categoryRepository.findById(matchedRule.categoryId!!),
            ).thenReturn(category(matchedRule.categoryId!!))

            val suggestion = service.suggest(accountId, "Mercado Extra Vila Madalena", CreditDebitType.DEBIT)

            assertThat(suggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(matchedRule.id),
                    categoryId = matchedRule.categoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(requireNotNull(matchedRule.id)),
                ),
            )
            verify(ruleRepository).incrementMatchCount(requireNotNull(matchedRule.id))
        }

        @Test
        fun `matches EXACT pattern`() {
            val matchedRule =
                rule(
                    pattern = "pix fulano",
                    patternType = PatternType.EXACT,
                    applicableTo = CategoryApplicableTo.INCOME,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(matchedRule))
            whenever(
                categoryRepository.findById(matchedRule.categoryId!!),
            ).thenReturn(category(matchedRule.categoryId!!))

            val suggestion = service.suggest(accountId, "PIX Fulano", CreditDebitType.CREDIT)

            assertThat(suggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(matchedRule.id),
                    categoryId = matchedRule.categoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(requireNotNull(matchedRule.id)),
                ),
            )
            verify(ruleRepository).incrementMatchCount(requireNotNull(matchedRule.id))
        }

        @Test
        fun `matches EXACT pattern regardless of stored pattern case`() {
            val matchedRule =
                rule(
                    pattern = "PIX FULANO",
                    patternType = PatternType.EXACT,
                    applicableTo = CategoryApplicableTo.INCOME,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(matchedRule))
            whenever(
                categoryRepository.findById(matchedRule.categoryId!!),
            ).thenReturn(category(matchedRule.categoryId!!))

            val suggestion = service.suggest(accountId, "Pix Fulano", CreditDebitType.CREDIT)

            assertThat(suggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(matchedRule.id),
                    categoryId = matchedRule.categoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(requireNotNull(matchedRule.id)),
                ),
            )
            verify(ruleRepository).incrementMatchCount(requireNotNull(matchedRule.id))
        }

        @Test
        fun `matches REGEX pattern`() {
            val matchedRule =
                rule(
                    pattern = "uber\\s+trip.*",
                    patternType = PatternType.REGEX,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(matchedRule))
            whenever(
                categoryRepository.findById(matchedRule.categoryId!!),
            ).thenReturn(category(matchedRule.categoryId!!))

            val suggestion = service.suggest(accountId, "UBER trip 1234", CreditDebitType.DEBIT)

            assertThat(suggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(matchedRule.id),
                    categoryId = matchedRule.categoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(requireNotNull(matchedRule.id)),
                ),
            )
            verify(ruleRepository).incrementMatchCount(requireNotNull(matchedRule.id))
        }

        @Test
        fun `invalid regex rule is ignored and does not break matching`() {
            val invalidRegexRule =
                rule(
                    id = UUID.randomUUID(),
                    categoryId = UUID.randomUUID(),
                    pattern = "[invalid",
                    patternType = PatternType.REGEX,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            val validRule =
                rule(
                    id = UUID.randomUUID(),
                    categoryId = UUID.randomUUID(),
                    pattern = "ifood",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(invalidRegexRule, validRule))
            whenever(categoryRepository.findById(validRule.categoryId!!)).thenReturn(category(validRule.categoryId!!))

            val suggestion = service.suggest(accountId, "iFood pedido", CreditDebitType.DEBIT)

            assertThat(suggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(validRule.id),
                    categoryId = validRule.categoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(requireNotNull(validRule.id)),
                ),
            )
            verify(ruleRepository, never()).incrementMatchCount(requireNotNull(invalidRegexRule.id))
            verify(ruleRepository).incrementMatchCount(requireNotNull(validRule.id))
        }

        @Test
        fun `contains does not match substring inside a longer word`() {
            val matchedRule =
                rule(
                    pattern = "UBER",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(matchedRule))

            val suggestion = service.suggest(accountId, "CLAUBER", CreditDebitType.DEBIT)

            assertThat(suggestion).isNull()
            verify(ruleRepository, never()).incrementMatchCount(any())
            verifyNoInteractions(categoryRepository)
        }

        @Test
        fun `applies applicableTo filter for debit and credit`() {
            val incomeRule =
                rule(
                    id = UUID.randomUUID(),
                    categoryId = UUID.randomUUID(),
                    pattern = "salario",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.INCOME,
                )
            val expenseRule =
                rule(
                    id = UUID.randomUUID(),
                    categoryId = UUID.randomUUID(),
                    pattern = "mercado",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(incomeRule, expenseRule))
            whenever(categoryRepository.findById(incomeRule.categoryId!!)).thenReturn(category(incomeRule.categoryId!!))
            whenever(
                categoryRepository.findById(expenseRule.categoryId!!),
            ).thenReturn(category(expenseRule.categoryId!!))

            val debitSuggestion = service.suggest(accountId, "Mercado Central", CreditDebitType.DEBIT)
            val creditSuggestion = service.suggest(accountId, "Salário empresa", CreditDebitType.CREDIT)

            assertThat(debitSuggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(expenseRule.id),
                    categoryId = expenseRule.categoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(requireNotNull(expenseRule.id)),
                ),
            )
            assertThat(creditSuggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(incomeRule.id),
                    categoryId = incomeRule.categoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(requireNotNull(incomeRule.id)),
                ),
            )
        }

        @Test
        fun `returns null when matching rule targets a deleted category`() {
            val matchedRule =
                rule(
                    pattern = "mercado",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(matchedRule))
            whenever(categoryRepository.findById(matchedRule.categoryId!!)).thenReturn(null)

            val suggestion = service.suggest(accountId, "Mercado Central", CreditDebitType.DEBIT)

            assertThat(suggestion).isNull()
            verify(ruleRepository, never()).incrementMatchCount(requireNotNull(matchedRule.id))
        }

        @Test
        fun `merges enrichment fields from multiple matching rules`() {
            val systemRule =
                rule(
                    id = UUID.randomUUID(),
                    categoryId = categoryId,
                    pattern = "ifood*123",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            val implicitRule =
                rule(
                    id = UUID.randomUUID(),
                    categoryId = null,
                    descriptionOverride = "iFood",
                    pattern = "ifood*123",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    isImplicit = true,
                )
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(listOf(systemRule, implicitRule))
            whenever(categoryRepository.findById(systemRule.categoryId!!)).thenReturn(category(systemRule.categoryId!!))

            val suggestion = service.suggest(accountId, "IFOOD*123", CreditDebitType.DEBIT)

            assertThat(suggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(systemRule.id),
                    categoryId = systemRule.categoryId,
                    merchantId = null,
                    descriptionOverride = "iFood",
                    tagsOverride = null,
                    appliedRuleIds = listOf(requireNotNull(systemRule.id), requireNotNull(implicitRule.id)),
                ),
            )
            verify(ruleRepository).incrementMatchCount(requireNotNull(systemRule.id))
            verify(ruleRepository).incrementMatchCount(requireNotNull(implicitRule.id))
        }
    }

    @Nested
    @DisplayName("suggestFromRulesRecordingMatch")
    inner class SuggestFromRulesRecordingMatchTests {
        @Test
        fun `increments match count when rule matches preloaded rules`() {
            val matchedRule =
                rule(
                    pattern = "pix",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )
            val rules = listOf(matchedRule)
            whenever(
                categoryRepository.findById(matchedRule.categoryId!!),
            ).thenReturn(category(matchedRule.categoryId!!))

            val suggestion =
                service.suggestFromRulesRecordingMatch(rules, "Pagamento PIX loja", CreditDebitType.DEBIT)

            assertThat(suggestion).isEqualTo(
                RuleSuggestion(
                    ruleId = requireNotNull(matchedRule.id),
                    categoryId = matchedRule.categoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(requireNotNull(matchedRule.id)),
                ),
            )
            verify(ruleRepository).incrementMatchCount(requireNotNull(matchedRule.id))
        }

        @Test
        fun `does not hit rule repository when no match`() {
            val rule =
                rule(
                    pattern = "nope",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )

            val suggestion = service.suggestFromRulesRecordingMatch(listOf(rule), "Outro texto", CreditDebitType.DEBIT)

            assertThat(suggestion).isNull()
            verify(ruleRepository, never()).incrementMatchCount(any())
        }
    }

    @Nested
    @DisplayName("learnFromManualAssignment")
    inner class LearnFromManualAssignmentTests {
        @Test
        fun `creates implicit rule when none exists`() {
            whenever(ruleRepository.findImplicitByPattern(accountId, "cafe pao")).thenReturn(null)
            whenever(ruleRepository.save(any())).thenAnswer { it.arguments[0] as TransactionRule }
            val newCategoryId = UUID.randomUUID()

            service.learnFromManualAssignment(accountId, "  Café & Pão  ", newCategoryId, null)

            val ruleCaptor = argumentCaptor<TransactionRule>()
            verify(ruleRepository).save(ruleCaptor.capture())
            val saved = ruleCaptor.firstValue
            assertThat(saved.accountId).isEqualTo(accountId)
            assertThat(saved.categoryId).isEqualTo(newCategoryId)
            assertThat(saved.merchantId).isNull()
            assertThat(saved.pattern).isEqualTo("cafe pao")
            assertThat(saved.patternType).isEqualTo(PatternType.CONTAINS)
            assertThat(saved.applicableTo).isEqualTo(CategoryApplicableTo.ANY)
            assertThat(saved.priority).isEqualTo(60)
            assertThat(saved.isImplicit).isTrue()
            assertThat(saved.isSystem).isFalse()
            assertThat(saved.matchCount).isEqualTo(0)
        }

        @Test
        fun `updates existing implicit rule when category changes`() {
            val existing =
                rule(
                    id = UUID.randomUUID(),
                    categoryId = categoryId,
                    pattern = "ifood pedido",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.ANY,
                    isImplicit = true,
                    updatedAt = Instant.now().minusSeconds(60),
                )
            val newCategoryId = UUID.randomUUID()
            whenever(ruleRepository.findImplicitByPattern(accountId, "ifood pedido")).thenReturn(existing)
            whenever(ruleRepository.save(any())).thenAnswer { it.arguments[0] as TransactionRule }

            service.learnFromManualAssignment(accountId, "iFood pedido", newCategoryId, null)

            val ruleCaptor = argumentCaptor<TransactionRule>()
            verify(ruleRepository).save(ruleCaptor.capture())
            val saved = ruleCaptor.firstValue
            assertThat(saved.id).isEqualTo(existing.id)
            assertThat(saved.categoryId).isEqualTo(newCategoryId)
            assertThat(saved.pattern).isEqualTo(existing.pattern)
            assertThat(saved.patternType).isEqualTo(existing.patternType)
            assertThat(saved.updatedAt).isAfter(existing.updatedAt)
        }

        @Test
        fun `updates existing implicit rule when merchant is provided later`() {
            val merchantId = UUID.randomUUID()
            val existing =
                rule(
                    id = UUID.randomUUID(),
                    categoryId = categoryId,
                    pattern = "netflix",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.ANY,
                    isImplicit = true,
                    updatedAt = Instant.now().minusSeconds(60),
                )
            whenever(ruleRepository.findImplicitByPattern(accountId, "netflix")).thenReturn(existing)
            whenever(ruleRepository.save(any())).thenAnswer { it.arguments[0] as TransactionRule }

            service.learnFromManualAssignment(accountId, "NETFLIX", null, merchantId)

            val ruleCaptor = argumentCaptor<TransactionRule>()
            verify(ruleRepository).save(ruleCaptor.capture())
            val saved = ruleCaptor.firstValue
            assertThat(saved.id).isEqualTo(existing.id)
            assertThat(saved.categoryId).isEqualTo(existing.categoryId)
            assertThat(saved.merchantId).isEqualTo(merchantId)
            assertThat(saved.updatedAt).isAfter(existing.updatedAt)
        }

        @Test
        fun `does nothing when description is blank`() {
            service.learnFromManualAssignment(accountId, "   ", categoryId, null)

            verifyNoInteractions(ruleRepository)
        }

        @Test
        fun `does nothing when both categoryId and merchantId are null`() {
            service.learnFromManualAssignment(accountId, "some description", null, null)

            verifyNoInteractions(ruleRepository)
        }
    }

    private fun rule(
        id: UUID = UUID.randomUUID(),
        categoryId: UUID? = this.categoryId,
        descriptionOverride: String? = null,
        tagsOverride: List<String>? = null,
        pattern: String,
        patternType: PatternType,
        applicableTo: CategoryApplicableTo,
        isImplicit: Boolean = false,
        updatedAt: Instant = Instant.now(),
    ) = TransactionRule(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        merchantId = null,
        descriptionOverride = descriptionOverride,
        tagsOverride = tagsOverride,
        pattern = pattern,
        patternType = patternType,
        applicableTo = applicableTo,
        priority = 100,
        isSystem = false,
        isImplicit = isImplicit,
        matchCount = 0,
        deletedAt = null,
        createdAt = Instant.now(),
        updatedAt = updatedAt,
    )

    private fun category(id: UUID) =
        TransactionCategory(
            id = id,
            accountId = null,
            parentId = null,
            name = "Test Category",
            applicableTo = CategoryApplicableTo.EXPENSE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
