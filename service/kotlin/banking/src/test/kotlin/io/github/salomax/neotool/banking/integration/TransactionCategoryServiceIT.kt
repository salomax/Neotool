package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo.EXPENSE
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo.INCOME
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo.TRANSFER
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableToMismatchException
import io.github.salomax.neotool.banking.domain.transaction.CategoryDepthExceededException
import io.github.salomax.neotool.banking.service.transaction.CreateCategoryInput
import io.github.salomax.neotool.banking.domain.transaction.DuplicateCategoryNameException
import io.github.salomax.neotool.banking.domain.transaction.SystemCategoryNotEditableException
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.service.transaction.TransactionCategoryService
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("TransactionCategoryService IT")
class TransactionCategoryServiceIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var service: TransactionCategoryService

    @Inject
    lateinit var categoryRepository: TransactionCategoryRepository

    private lateinit var accountId: UUID

    @BeforeEach
    fun initAccount() {
        accountId = UUID.randomUUID()
    }

    private fun findSystemRoot(name: String) =
        categoryRepository
            .findAll(accountId)
            .first { it.parentId == null && it.isSystem && it.name == name }

    @Nested
    @DisplayName("create")
    inner class CreateTests {
        @Test
        fun `create root category sets accountId and non-system`() {
            val created =
                service.create(
                    input =
                        CreateCategoryInput(
                            parentId = null,
                            name = "Música ao vivo",
                            applicableTo = EXPENSE,
                            icon = "music_note",
                            color = "#FF4081",
                            sortOrder = null,
                        ),
                    accountId = accountId,
                )

            assertThat(created.id).isNotNull
            assertThat(created.parentId).isNull()
            assertThat(created.accountId).isEqualTo(accountId)
            assertThat(created.isSystem).isFalse()
            assertThat(created.deletedAt).isNull()
        }

        @Test
        fun `create subcategory with valid parent succeeds`() {
            val parent = findSystemRoot("Alimentação")

            val created =
                service.create(
                    input =
                        CreateCategoryInput(
                            parentId = parent.id,
                            name = "Padaria",
                            applicableTo = EXPENSE,
                            icon = "bakery_dining",
                            color = "#FF9800",
                            sortOrder = null,
                        ),
                    accountId = accountId,
                )

            assertThat(created.parentId).isEqualTo(parent.id)
            assertThat(created.applicableTo).isEqualTo(EXPENSE)
        }

        @Test
        fun `creating subcategory of subcategory throws CategoryDepthExceededException`() {
            val parent = findSystemRoot("Alimentação")
            val child =
                service.create(
                    input =
                        CreateCategoryInput(
                            parentId = parent.id,
                            name = "Padaria",
                            applicableTo = EXPENSE,
                            icon = null,
                            color = null,
                            sortOrder = null,
                        ),
                    accountId = accountId,
                )

            assertThatThrownBy {
                service.create(
                    input =
                        CreateCategoryInput(
                            parentId = child.id,
                            name = "Sub-Padaria",
                            applicableTo = EXPENSE,
                            icon = null,
                            color = null,
                            sortOrder = null,
                        ),
                    accountId = accountId,
                )
            }.isInstanceOf(CategoryDepthExceededException::class.java)
        }

        @Test
        fun `creating category with duplicate name and same parent throws DuplicateCategoryNameException`() {
            service.create(
                input =
                    CreateCategoryInput(
                        parentId = null,
                        name = "Freelance",
                        applicableTo = INCOME,
                        icon = "work",
                        color = "#4CAF50",
                        sortOrder = null,
                    ),
                accountId = accountId,
            )

            assertThatThrownBy {
                service.create(
                    input =
                        CreateCategoryInput(
                            parentId = null,
                            name = "Freelance",
                            applicableTo = INCOME,
                            icon = "work",
                            color = "#4CAF50",
                            sortOrder = null,
                        ),
                    accountId = accountId,
                )
            }.isInstanceOf(DuplicateCategoryNameException::class.java)
        }

        @Test
        fun `creating subcategory with incompatible applicableTo throws CategoryApplicableToMismatchException`() {
            val salary = findSystemRoot("Salário")

            assertThat(salary.applicableTo).isEqualTo(INCOME)

            assertThatThrownBy {
                service.create(
                    input =
                        CreateCategoryInput(
                            parentId = salary.id,
                            name = "Aluguel recebido",
                            applicableTo = TRANSFER,
                            icon = null,
                            color = null,
                            sortOrder = null,
                        ),
                    accountId = accountId,
                )
            }.isInstanceOf(CategoryApplicableToMismatchException::class.java)
        }
    }

    @Nested
    @DisplayName("update")
    inner class UpdateTests {
        @Test
        fun `updating system category throws SystemCategoryNotEditableException`() {
            val systemCategory = findSystemRoot("Alimentação")

            assertThatThrownBy {
                service.update(
                    id = requireNotNull(systemCategory.id),
                    input =
                        io.github.salomax.neotool.banking.service.transaction.UpdateCategoryInput(
                            name = "Alimentação Editada",
                            icon = null,
                            color = null,
                            sortOrder = null,
                        ),
                    accountId = accountId,
                )
            }.isInstanceOf(SystemCategoryNotEditableException::class.java)
        }
    }
}
