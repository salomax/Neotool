package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("TransactionCategoryRepository IT")
class TransactionCategoryRepositoryIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var categoryRepository: TransactionCategoryRepository

    private lateinit var accountId: UUID

    @BeforeEach
    fun insertTestAccount() {
        accountId = UUID.randomUUID()
    }

    @Nested
    @DisplayName("findAll")
    inner class FindAllTests {
        @Test
        fun `returns all system categories and user-defined for account excludes soft-deleted`() {
            val all = categoryRepository.findAll(accountId)
            assertThat(all).isNotEmpty
            val system = all.filter { it.accountId == null }
            val userDefined = all.filter { it.accountId == accountId }
            assertThat(system).isNotEmpty
            assertThat(system).allMatch { it.isSystem }
        }

        @Test
        fun `system categories from seed have correct names icons colors and parent child structure`() {
            val all = categoryRepository.findAll(accountId)
            val roots = all.filter { it.parentId == null }
            assertThat(roots).isNotEmpty
            val salary = roots.find { it.name == "Salário" }
            assertThat(salary).isNotNull
            assertThat(salary!!.icon).isEqualTo("payments")
            assertThat(salary.color).isEqualTo("#2E7D32")
            val alimentacao = roots.find { it.name == "Alimentação" }
            assertThat(alimentacao).isNotNull
            assertThat(alimentacao!!.icon).isEqualTo("restaurant")
            val outros = roots.find { it.name == "Outros" }
            assertThat(outros).isNotNull
            assertThat(outros!!.icon).isEqualTo("help_outline")
            val withParent = all.filter { it.parentId != null }
            assertThat(withParent).allMatch { cat -> all.any { it.id == cat.parentId } }
        }
    }

    @Nested
    @DisplayName("findById")
    inner class FindByIdTests {
        @Test
        fun `returns null for soft-deleted category`() {
            val container = getPgContainer()
            val catId = UUID.randomUUID()
            DriverManager.getConnection(
                buildJdbcUrlWithSslDisabled(container.jdbcUrl),
                container.username,
                container.password,
            ).use {
                    conn ->
                conn.createStatement().executeUpdate(
                    """
                    INSERT INTO banking.transaction_categories (id, account_id, name, applicable_to, is_system, sort_order, created_at, updated_at)
                    VALUES ('$catId', '$accountId', 'To Delete', 'ANY', false, 0, NOW(), NOW())
                    """.trimIndent(),
                )
                conn.createStatement().executeUpdate(
                    "UPDATE banking.transaction_categories SET deleted_at = NOW() WHERE id = '$catId'",
                )
            }
            val found = categoryRepository.findById(catId)
            assertThat(found).isNull()
        }
    }
}
