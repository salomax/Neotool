package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.repo.transaction.CategoryRuleEntityRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("CategoryRule disablement IT")
class CategoryRuleDisablementIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var entityRepository: CategoryRuleEntityRepository

    @Test
    fun `findRulesForAccount skips disabled system rules for account`() {
        val accountId = UUID.randomUUID()
        val systemRuleId = UUID.randomUUID()
        val userRuleId = UUID.randomUUID()
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)

        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.transaction_rules (
                  id, account_id, category_id, merchant_id, pattern, pattern_type, applicable_to,
                  priority, is_system, is_implicit, description_override, tags_override_json, match_count, created_at, updated_at
                ) VALUES (
                  '$systemRuleId', NULL, NULL, NULL, 'streaming test', 'CONTAINS', 'EXPENSE',
                  1, true, false, 'override', '[]', 0, NOW(), NOW()
                )
                """.trimIndent(),
            )
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.transaction_rules (
                  id, account_id, category_id, merchant_id, pattern, pattern_type, applicable_to,
                  priority, is_system, is_implicit, description_override, tags_override_json, match_count, created_at, updated_at
                ) VALUES (
                  '$userRuleId', '$accountId', NULL, NULL, 'user test', 'CONTAINS', 'EXPENSE',
                  2, false, false, 'override', '[]', 0, NOW(), NOW()
                )
                """.trimIndent(),
            )
        }

        val beforeDisable = entityRepository.findRulesForAccount(accountId).mapNotNull { it.id }.toSet()
        assertThat(beforeDisable).contains(systemRuleId, userRuleId)

        val disabled = entityRepository.disableSystemRuleForAccount(accountId, systemRuleId, java.time.Instant.now())
        assertThat(disabled).isEqualTo(1)

        val afterDisable = entityRepository.findRulesForAccount(accountId).mapNotNull { it.id }.toSet()
        assertThat(afterDisable).contains(userRuleId)
        assertThat(afterDisable).doesNotContain(systemRuleId)

        entityRepository.enableSystemRuleForAccount(accountId, systemRuleId)

        val afterEnable = entityRepository.findRulesForAccount(accountId).mapNotNull { it.id }.toSet()
        assertThat(afterEnable).contains(systemRuleId, userRuleId)
    }
}
