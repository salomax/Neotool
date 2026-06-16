package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionRuleInput
import io.github.salomax.neotool.banking.model.dto.transaction.UpdateTransactionRuleInput
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRuleRepository
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("TransactionRuleService")
class CategoryRuleServiceTest {
    private lateinit var ruleRepository: TransactionRuleRepository
    private lateinit var categoryRepository: TransactionCategoryRepository
    private lateinit var merchantRepository: MerchantRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var service: TransactionRuleService

    private val accountId = UUID.randomUUID()
    private val otherAccountId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        ruleRepository = mock()
        categoryRepository = mock()
        merchantRepository = mock()
        transactionRepository = mock()
        service = TransactionRuleService(ruleRepository, categoryRepository, merchantRepository, transactionRepository)
    }

    @Nested
    @DisplayName("listUserRules")
    inner class ListUserRulesTests {
        @Test
        fun `delegates to repository`() {
            val rules = listOf(rule())
            whenever(ruleRepository.findUserRulesForAccount(accountId)).thenReturn(rules)

            val result = service.listUserRules(accountId)

            assertThat(result).isEqualTo(rules)
            verify(ruleRepository).findUserRulesForAccount(accountId)
        }

        @Test
        fun `listRules with null filter returns all rules`() {
            val rules = listOf(rule(pattern = "all"))
            whenever(ruleRepository.findRulesForAccount(accountId)).thenReturn(rules)

            val result = service.listRules(accountId, null)

            assertThat(result).isEqualTo(rules)
            verify(ruleRepository).findRulesForAccount(accountId)
        }

        @Test
        fun `listRules with false filter returns only user rules`() {
            val rules = listOf(rule(pattern = "user"))
            whenever(ruleRepository.findUserRulesForAccount(accountId)).thenReturn(rules)

            val result = service.listRules(accountId, false)

            assertThat(result).isEqualTo(rules)
            verify(ruleRepository).findUserRulesForAccount(accountId)
        }

        @Test
        fun `listRules with true filter returns system rules`() {
            val rules = listOf(rule(pattern = "system", accountId = null, isSystem = true))
            whenever(ruleRepository.findSystemRules()).thenReturn(rules)

            val result = service.listRules(accountId, true)

            assertThat(result).isEqualTo(rules)
            verify(ruleRepository).findSystemRules()
        }

        @Test
        fun `disabledRuleIds delegates to repository`() {
            val disabled = setOf(UUID.randomUUID())
            whenever(ruleRepository.findDisabledRuleIdsForAccount(accountId)).thenReturn(disabled)

            val result = service.disabledRuleIds(accountId)

            assertThat(result).isEqualTo(disabled)
            verify(ruleRepository).findDisabledRuleIdsForAccount(accountId)
        }
    }

    @Nested
    @DisplayName("setSystemRuleEnabled")
    inner class SetSystemRuleEnabledTests {
        private val ruleId = UUID.randomUUID()

        @Test
        fun `disables active system rule for account`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(id = ruleId, accountId = null, isSystem = true),
            )
            whenever(ruleRepository.disableSystemRuleForAccount(accountId, ruleId)).thenReturn(true)

            val result = service.setSystemRuleEnabled(accountId, ruleId, enabled = false)

            assertThat(result).isTrue()
            verify(ruleRepository).disableSystemRuleForAccount(accountId, ruleId)
        }

        @Test
        fun `enables previously disabled system rule for account`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(id = ruleId, accountId = null, isSystem = true),
            )
            whenever(ruleRepository.enableSystemRuleForAccount(accountId, ruleId)).thenReturn(true)

            val result = service.setSystemRuleEnabled(accountId, ruleId, enabled = true)

            assertThat(result).isTrue()
            verify(ruleRepository).enableSystemRuleForAccount(accountId, ruleId)
        }

        @Test
        fun `throws when target rule is not system-scoped`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(id = ruleId, accountId = accountId, isSystem = false),
            )

            assertThrows<SystemTransactionRuleNotEditableException> {
                service.setSystemRuleEnabled(accountId, ruleId, enabled = false)
            }

            verify(ruleRepository, never()).disableSystemRuleForAccount(any(), any())
            verify(ruleRepository, never()).enableSystemRuleForAccount(any(), any())
        }
    }

    @Nested
    @DisplayName("previewPattern — CONTAINS word-boundary matching")
    inner class PreviewPatternContainsTests {
        @Test
        fun `does not match when pattern is a substring of a longer word`() {
            // "CLAUBER" contains "uber" as raw substring but "uber" is not a whole word there
            assertThat(service.previewPattern("UBER", PatternType.CONTAINS, "CLAUBER")).isFalse()
        }

        @Test
        fun `does not match when pattern is a prefix of a longer word`() {
            assertThat(service.previewPattern("UBER", PatternType.CONTAINS, "UBERLANINHA")).isFalse()
        }

        @Test
        fun `matches when pattern appears as a standalone word`() {
            assertThat(service.previewPattern("UBER", PatternType.CONTAINS, "UBER EATS")).isTrue()
        }

        @Test
        fun `matches when pattern is separated by asterisk`() {
            assertThat(service.previewPattern("UBER", PatternType.CONTAINS, "UBER*TRIP")).isTrue()
        }

        @Test
        fun `matches when pattern is followed by digits`() {
            assertThat(service.previewPattern("UBER", PatternType.CONTAINS, "UBER99")).isTrue()
        }

        @Test
        fun `matches when pattern is preceded by digits`() {
            assertThat(service.previewPattern("UBER", PatternType.CONTAINS, "99UBER")).isTrue()
        }

        @Test
        fun `matches regardless of case`() {
            assertThat(service.previewPattern("uber", PatternType.CONTAINS, "UBER EATS")).isTrue()
            assertThat(service.previewPattern("UBER", PatternType.CONTAINS, "uber eats")).isTrue()
        }

        @Test
        fun `matches when pattern is the entire description`() {
            assertThat(service.previewPattern("UBER", PatternType.CONTAINS, "UBER")).isTrue()
        }

        @Test
        fun `matches multi-word pattern as a phrase`() {
            assertThat(service.previewPattern("UBER EATS", PatternType.CONTAINS, "PAG UBER EATS HOJE")).isTrue()
        }

        @Test
        fun `normalizes special characters in pattern to match imported descriptions`() {
            // Pattern created from tx.description (e.g. "NUBANK R$ 50.00") must match the
            // normalized description ("nubank r  50 00") — both $ and . become spaces.
            assertThat(service.previewPattern("NUBANK R\$ 50.00", PatternType.CONTAINS, "NUBANK R\$ 50.00")).isTrue()
        }

        @Test
        fun `normalizes accented characters in pattern`() {
            assertThat(service.previewPattern("café", PatternType.CONTAINS, "CAFE DO BRASIL")).isTrue()
        }
    }

    @Nested
    @DisplayName("create")
    inner class CreateTests {
        @Test
        fun `throws when category does not exist`() {
            whenever(categoryRepository.findById(categoryId)).thenReturn(null)

            assertThrows<CategoryNotFoundException> {
                service.create(createInput(categoryId = categoryId), accountId)
            }

            verify(ruleRepository, never()).save(any())
        }

        @Test
        fun `throws when category belongs to another account`() {
            whenever(categoryRepository.findById(categoryId)).thenReturn(
                category(id = categoryId, accountId = otherAccountId),
            )

            assertThrows<CategoryNotFoundException> {
                service.create(createInput(categoryId = categoryId), accountId)
            }

            verify(ruleRepository, never()).save(any())
        }

        @Test
        fun `throws for invalid regex pattern`() {
            whenever(categoryRepository.findById(categoryId)).thenReturn(
                category(id = categoryId, accountId = null, isSystem = true),
            )

            assertThrows<InvalidRegexPatternException> {
                service.create(
                    createInput(
                        categoryId = categoryId,
                        pattern = "[invalid",
                        patternType = PatternType.REGEX,
                    ),
                    accountId,
                )
            }

            verify(ruleRepository, never()).findUserRulesForAccount(any())
            verify(ruleRepository, never()).save(any())
        }

        @Test
        fun `throws when duplicate pattern and type already exist`() {
            whenever(categoryRepository.findById(categoryId)).thenReturn(category(id = categoryId))
            whenever(ruleRepository.findUserRulesForAccount(accountId)).thenReturn(
                listOf(rule(pattern = "ifood", patternType = PatternType.CONTAINS)),
            )

            assertThrows<DuplicateTransactionRuleException> {
                service.create(
                    createInput(
                        categoryId = categoryId,
                        pattern = "ifood",
                        patternType = PatternType.CONTAINS,
                    ),
                    accountId,
                )
            }

            verify(ruleRepository, never()).save(any())
        }

        @Test
        fun `trims pattern and defaults priority to 40`() {
            whenever(categoryRepository.findById(categoryId)).thenReturn(category(id = categoryId))
            whenever(ruleRepository.findUserRulesForAccount(accountId)).thenReturn(emptyList())
            whenever(ruleRepository.save(any())).thenAnswer { it.arguments[0] as TransactionRule }

            val result =
                service.create(
                    createInput(
                        categoryId = categoryId,
                        pattern = "  supermercado  ",
                        patternType = PatternType.CONTAINS,
                        priority = null,
                    ),
                    accountId,
                )

            val ruleCaptor = argumentCaptor<TransactionRule>()
            verify(ruleRepository).save(ruleCaptor.capture())
            val saved = ruleCaptor.firstValue
            assertThat(saved.pattern).isEqualTo("supermercado")
            assertThat(saved.priority).isEqualTo(40)
            assertThat(saved.isSystem).isFalse()
            assertThat(saved.isImplicit).isFalse()
            assertThat(saved.accountId).isEqualTo(accountId)

            assertThat(result.pattern).isEqualTo("supermercado")
            assertThat(result.priority).isEqualTo(40)
        }
    }

    @Nested
    @DisplayName("update")
    inner class UpdateTests {
        private val ruleId = UUID.randomUUID()

        @Test
        fun `throws when rule is not found`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(null)

            assertThrows<TransactionRuleNotFoundException> {
                service.update(ruleId, UpdateTransactionRuleInput(pattern = "novo"), accountId)
            }

            verify(ruleRepository, never()).save(any())
        }

        @Test
        fun `throws when rule belongs to another account`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(rule(id = ruleId, accountId = otherAccountId))

            assertThrows<TransactionRuleNotFoundException> {
                service.update(ruleId, UpdateTransactionRuleInput(pattern = "novo"), accountId)
            }

            verify(ruleRepository, never()).save(any())
        }

        @Test
        fun `throws when rule is system rule`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(id = ruleId, accountId = accountId, isSystem = true),
            )

            assertThrows<SystemTransactionRuleNotEditableException> {
                service.update(ruleId, UpdateTransactionRuleInput(pattern = "novo"), accountId)
            }

            verify(ruleRepository, never()).save(any())
        }

        @Test
        fun `throws when changing pattern and type would create duplicate`() {
            val existing =
                rule(
                    id = ruleId,
                    accountId = accountId,
                    pattern = "ifood",
                    patternType = PatternType.CONTAINS,
                )
            val duplicate =
                rule(
                    id = UUID.randomUUID(),
                    accountId = accountId,
                    pattern = "uber",
                    patternType = PatternType.EXACT,
                )
            whenever(ruleRepository.findById(ruleId)).thenReturn(existing)
            whenever(ruleRepository.findUserRulesForAccount(accountId)).thenReturn(listOf(existing, duplicate))

            assertThrows<DuplicateTransactionRuleException> {
                service.update(
                    id = ruleId,
                    input = UpdateTransactionRuleInput(pattern = "uber", patternType = PatternType.EXACT),
                    accountId = accountId,
                )
            }

            verify(ruleRepository, never()).save(any())
        }

        @Test
        fun `updates category pattern type and priority when no duplicate exists`() {
            val existing =
                rule(
                    id = ruleId,
                    accountId = accountId,
                    categoryId = categoryId,
                    pattern = "ifood",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    priority = 30,
                )
            val newCategoryId = UUID.randomUUID()
            whenever(ruleRepository.findById(ruleId)).thenReturn(existing)
            whenever(ruleRepository.findUserRulesForAccount(accountId)).thenReturn(listOf(existing))
            whenever(categoryRepository.findById(newCategoryId)).thenReturn(category(id = newCategoryId))
            whenever(ruleRepository.save(any())).thenAnswer { it.arguments[0] as TransactionRule }

            val result =
                service.update(
                    id = ruleId,
                    input =
                        UpdateTransactionRuleInput(
                            categoryId = newCategoryId,
                            pattern = "  uber ",
                            patternType = PatternType.STARTS_WITH,
                            priority = 5,
                        ),
                    accountId = accountId,
                )

            val ruleCaptor = argumentCaptor<TransactionRule>()
            verify(ruleRepository).save(ruleCaptor.capture())
            val saved = ruleCaptor.firstValue
            assertThat(saved.id).isEqualTo(ruleId)
            assertThat(saved.categoryId).isEqualTo(newCategoryId)
            assertThat(saved.pattern).isEqualTo("uber")
            assertThat(saved.patternType).isEqualTo(PatternType.STARTS_WITH)
            assertThat(saved.priority).isEqualTo(5)
            assertThat(saved.applicableTo).isEqualTo(CategoryApplicableTo.EXPENSE)

            assertThat(result.categoryId).isEqualTo(newCategoryId)
            assertThat(result.pattern).isEqualTo("uber")
            assertThat(result.patternType).isEqualTo(PatternType.STARTS_WITH)
            assertThat(result.priority).isEqualTo(5)
            assertThat(result.applicableTo).isEqualTo(CategoryApplicableTo.EXPENSE)
        }
    }

    @Nested
    @DisplayName("historical matching")
    inner class HistoricalMatchingTests {
        private val ruleId = UUID.randomUUID()

        @Test
        fun `countHistoricalMatches uses the same case-insensitive exact matcher as rule application`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(
                    id = ruleId,
                    accountId = accountId,
                    pattern = "PIX FULANO",
                    patternType = PatternType.EXACT,
                    applicableTo = CategoryApplicableTo.INCOME,
                ),
            )
            whenever(transactionRepository.findByAccount(accountId)).thenReturn(
                listOf(
                    transaction(
                        description = "Pix Fulano",
                        creditDebitType = CreditDebitType.CREDIT,
                        source = TransactionSource.CSV,
                    ),
                ),
            )

            val result = service.countHistoricalMatches(ruleId, accountId)

            assertThat(result).isEqualTo(1)
        }

        @Test
        fun `countHistoricalMatches normalizes pattern special characters to match imported transactions`() {
            // Regression: pattern created from tx.description may contain R$, dots, etc.
            // TransactionDescriptionNormalizer strips those to spaces; the matcher must do
            // the same on the pattern side — otherwise countHistoricalMatches returns 0 and
            // the "apply to history" dialog never shows.
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(
                    id = ruleId,
                    accountId = accountId,
                    pattern = "NUBANK R\$ 50.00",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                ),
            )
            whenever(transactionRepository.findByAccount(accountId)).thenReturn(
                listOf(
                    transaction(
                        description = "NUBANK R\$ 50.00",
                        creditDebitType = CreditDebitType.DEBIT,
                        source = TransactionSource.OFX,
                    ),
                    transaction(
                        description = "OTHER BANK",
                        creditDebitType = CreditDebitType.DEBIT,
                        source = TransactionSource.OFX,
                    ),
                ),
            )

            val result = service.countHistoricalMatches(ruleId, accountId)

            assertThat(result).isEqualTo(1)
        }

        @Test
        fun `countHistoricalMatches excludes MANUAL transactions`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(
                    id = ruleId,
                    accountId = accountId,
                    pattern = "ifood",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                ),
            )
            whenever(transactionRepository.findByAccount(accountId)).thenReturn(
                listOf(
                    transaction(description = "iFood", source = TransactionSource.MANUAL),
                    transaction(description = "iFood", source = TransactionSource.CSV),
                ),
            )

            val result = service.countHistoricalMatches(ruleId, accountId)

            assertThat(result).isEqualTo(1)
        }

        @Test
        fun `applyToHistorical normalizes pattern special characters when updating transactions`() {
            val savedCaptor = argumentCaptor<Transaction>()
            val tx = transaction(
                description = "NUBANK R\$ 50.00",
                creditDebitType = CreditDebitType.DEBIT,
                source = TransactionSource.OFX,
            )
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(
                    id = ruleId,
                    accountId = accountId,
                    pattern = "NUBANK R\$ 50.00",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                ),
            )
            whenever(transactionRepository.findByAccount(accountId)).thenReturn(listOf(tx))
            whenever(transactionRepository.update(any())).thenAnswer { it.getArgument(0) }

            val result = service.applyToHistorical(ruleId, accountId)

            assertThat(result.affectedCount).isEqualTo(1)
            verify(transactionRepository).update(savedCaptor.capture())
            assertThat(savedCaptor.firstValue.categoryId).isEqualTo(categoryId)
            assertThat(savedCaptor.firstValue.categorySource).isEqualTo(CategorySource.RULE)
            verify(ruleRepository).incrementMatchCountBy(ruleId, 1)
        }

        @Test
        fun `applyToHistorical does not overwrite manually categorized transactions`() {
            val manualCategoryId = UUID.randomUUID()
            val tx = transaction(
                description = "NUBANK R\$ 50.00",
                creditDebitType = CreditDebitType.DEBIT,
                source = TransactionSource.OFX,
            ).copy(categoryId = manualCategoryId, categorySource = CategorySource.MANUAL)
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(
                    id = ruleId,
                    accountId = accountId,
                    pattern = "NUBANK R\$ 50.00",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                ),
            )
            whenever(transactionRepository.findByAccount(accountId)).thenReturn(listOf(tx))

            val result = service.applyToHistorical(ruleId, accountId)

            assertThat(result.affectedCount).isEqualTo(0)
            verify(transactionRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("delete")
    inner class DeleteTests {
        private val ruleId = UUID.randomUUID()

        @Test
        fun `throws when rule is not found`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(null)

            assertThrows<TransactionRuleNotFoundException> {
                service.delete(ruleId, accountId)
            }

            verify(ruleRepository, never()).softDelete(any(), any())
        }

        @Test
        fun `throws when rule belongs to another account`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(rule(id = ruleId, accountId = otherAccountId))

            assertThrows<TransactionRuleNotFoundException> {
                service.delete(ruleId, accountId)
            }

            verify(ruleRepository, never()).softDelete(any(), any())
        }

        @Test
        fun `throws when rule is a system rule`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(id = ruleId, accountId = accountId, isSystem = true),
            )

            assertThrows<SystemTransactionRuleNotDeletableException> {
                service.delete(ruleId, accountId)
            }

            verify(ruleRepository, never()).softDelete(any(), any())
        }

        @Test
        fun `soft deletes user rule`() {
            whenever(ruleRepository.findById(ruleId)).thenReturn(
                rule(id = ruleId, accountId = accountId, isSystem = false),
            )

            val result = service.delete(ruleId, accountId)

            assertThat(result).isTrue()
            verify(ruleRepository).softDelete(eq(ruleId), eq(accountId))
        }
    }

    private fun createInput(
        categoryId: UUID,
        pattern: String = "ifood",
        patternType: PatternType = PatternType.CONTAINS,
        priority: Int? = 10,
    ) = CreateTransactionRuleInput(
        categoryId = categoryId,
        pattern = pattern,
        patternType = patternType,
        applicableTo = CategoryApplicableTo.EXPENSE,
        priority = priority,
    )

    private fun category(
        id: UUID = UUID.randomUUID(),
        accountId: UUID? = this.accountId,
        isSystem: Boolean = accountId == null,
    ) = TransactionCategory(
        id = id,
        accountId = accountId,
        parentId = null,
        name = "Alimentação",
        applicableTo = CategoryApplicableTo.EXPENSE,
        icon = "restaurant",
        color = "#F57C00",
        isSystem = isSystem,
        sortOrder = 10,
        deletedAt = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun rule(
        id: UUID = UUID.randomUUID(),
        accountId: UUID? = this.accountId,
        categoryId: UUID = this.categoryId,
        pattern: String = "ifood",
        patternType: PatternType = PatternType.CONTAINS,
        applicableTo: CategoryApplicableTo = CategoryApplicableTo.EXPENSE,
        priority: Int = 100,
        isSystem: Boolean = false,
        isImplicit: Boolean = false,
        matchCount: Int = 0,
    ) = TransactionRule(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        merchantId = null,
        pattern = pattern,
        patternType = patternType,
        applicableTo = applicableTo,
        priority = priority,
        isSystem = isSystem,
        isImplicit = isImplicit,
        matchCount = matchCount,
        deletedAt = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun transaction(
        description: String,
        creditDebitType: CreditDebitType = CreditDebitType.DEBIT,
        source: TransactionSource = TransactionSource.MANUAL,
        type: TransactionType = TransactionType.OTHER_EXPENSE,
    ) = Transaction(
        id = UUID.randomUUID(),
        financialAccountId = UUID.randomUUID(),
        accountId = accountId,
        createdByUserId = UUID.randomUUID(),
        creditDebitType = creditDebitType,
        type = type,
        source = source,
        amount = BigDecimal("10.00"),
        date = LocalDate.now(),
        description = description,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
