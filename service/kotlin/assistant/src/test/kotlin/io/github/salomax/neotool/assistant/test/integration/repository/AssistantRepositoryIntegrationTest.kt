package io.github.salomax.neotool.assistant.test.integration.repository

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.domain.enums.BudgetScope
import io.github.salomax.neotool.assistant.domain.enums.PromptVersionStatus
import io.github.salomax.neotool.assistant.entity.CompletionRequestEntity
import io.github.salomax.neotool.assistant.entity.PromptEntity
import io.github.salomax.neotool.assistant.entity.PromptVersionEntity
import io.github.salomax.neotool.assistant.entity.UsageBudgetEntity
import io.github.salomax.neotool.assistant.repository.CompletionRequestRepository
import io.github.salomax.neotool.assistant.repository.PromptRepository
import io.github.salomax.neotool.assistant.repository.PromptVersionRepository
import io.github.salomax.neotool.assistant.repository.UsageBudgetRepository
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Assistant Repository Integration Tests")
@Tag("integration")
@Tag("repository")
@Tag("assistant")
class AssistantRepositoryIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var promptRepository: PromptRepository

    @Inject
    lateinit var promptVersionRepository: PromptVersionRepository

    @Inject
    lateinit var completionRequestRepository: CompletionRequestRepository

    @Inject
    lateinit var usageBudgetRepository: UsageBudgetRepository

    @Inject
    lateinit var entityManager: EntityManager

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties()
        props["jpa.default.enabled"] = "true"
        bootstrapSecuritySchema(props)
        return props
    }

    @AfterEach
    fun cleanup() {
        entityManager.runTransaction {
            entityManager.createNativeQuery("DELETE FROM assistant.completion_responses").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.completion_requests").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.usage_log").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.usage_budgets").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.prompt_versions").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.prompts").executeUpdate()
        }
    }

    @Test
    fun `findActiveVersion returns exactly one active prompt version`() {
        val createdBy = insertSecurityUser()
        val prompt =
            entityManager.runTransaction {
                val saved =
                    promptRepository.save(
                        PromptEntity(
                            name = "FAQ prompt",
                            category = "faq",
                            description = "integration test",
                            tags = arrayOf("faq", "support"),
                            createdBy = createdBy,
                        ),
                    )
                entityManager.flush()
                saved
            }

        entityManager.runTransaction {
            promptVersionRepository.save(
                PromptVersionEntity(
                    promptId = prompt.id!!,
                    version = 1,
                    status = PromptVersionStatus.DRAFT,
                    template = "draft",
                ),
            )
            promptVersionRepository.save(
                PromptVersionEntity(
                    promptId = prompt.id!!,
                    version = 2,
                    status = PromptVersionStatus.ACTIVE,
                    template = "active",
                ),
            )
            promptVersionRepository.save(
                PromptVersionEntity(
                    promptId = prompt.id!!,
                    version = 3,
                    status = PromptVersionStatus.DEPRECATED,
                    template = "deprecated",
                ),
            )
            entityManager.flush()
        }

        val activeFromPromptRepository = promptRepository.findActiveVersion(prompt.id!!)
        val activeFromVersionRepository =
            promptVersionRepository.findByPromptIdAndStatus(prompt.id!!, PromptVersionStatus.ACTIVE)

        assertThat(activeFromPromptRepository).isNotNull
        assertThat(activeFromPromptRepository!!.status).isEqualTo(PromptVersionStatus.ACTIVE)
        assertThat(activeFromVersionRepository).isNotNull
        assertThat(activeFromVersionRepository!!.id).isEqualTo(activeFromPromptRepository.id)
        assertThat(countActiveVersions(prompt.id!!)).isEqualTo(1)
    }

    @Test
    fun `findByAccountIdAndCreatedAtDesc does not return rows from other accounts`() {
        val accountA = insertSecurityAccount()
        val accountB = insertSecurityAccount()

        entityManager.runTransaction {
            completionRequestRepository.save(
                CompletionRequestEntity(
                    accountId = accountA,
                    requestedByService = "svc-a",
                    messages = listOf(mapOf("role" to "USER", "content" to "old-a")),
                    purpose = "test",
                    createdAt = Instant.now().minusSeconds(60),
                ),
            )
            completionRequestRepository.save(
                CompletionRequestEntity(
                    accountId = accountA,
                    requestedByService = "svc-a",
                    messages = listOf(mapOf("role" to "USER", "content" to "new-a")),
                    purpose = "test",
                    createdAt = Instant.now(),
                ),
            )
            completionRequestRepository.save(
                CompletionRequestEntity(
                    accountId = accountB,
                    requestedByService = "svc-b",
                    messages = listOf(mapOf("role" to "USER", "content" to "b")),
                    purpose = "test",
                    createdAt = Instant.now().plusSeconds(30),
                ),
            )
            entityManager.flush()
        }

        val rows = completionRequestRepository.findByAccountIdAndCreatedAtDesc(accountA)

        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.accountId }.distinct()).containsExactly(accountA)
        assertThat(rows[0].createdAt).isAfterOrEqualTo(rows[1].createdAt)
    }

    @Test
    fun `usage budget upsert updates existing row without creating duplicates`() {
        val scopeId = UUID.randomUUID()

        val first =
            upsertUsageBudget(
                scope = BudgetScope.ACCOUNT,
                scopeId = scopeId,
                period = BudgetPeriod.MONTHLY,
                limitAmount = BigDecimal("100.000000"),
                currentUsage = BigDecimal("10.000000"),
            )

        val second =
            upsertUsageBudget(
                scope = BudgetScope.ACCOUNT,
                scopeId = scopeId,
                period = BudgetPeriod.MONTHLY,
                limitAmount = BigDecimal("100.000000"),
                currentUsage = BigDecimal("35.000000"),
            )

        val rows = usageBudgetRepository.findByScopeAndScopeId(BudgetScope.ACCOUNT, scopeId)
        val monthlyRows = rows.filter { it.period == BudgetPeriod.MONTHLY }

        assertThat(monthlyRows).hasSize(1)
        assertThat(second.id).isEqualTo(first.id)
        assertThat(monthlyRows.first().currentUsage).isEqualByComparingTo("35.000000")
    }

    private fun upsertUsageBudget(
        scope: BudgetScope,
        scopeId: UUID,
        period: BudgetPeriod,
        limitAmount: BigDecimal,
        currentUsage: BigDecimal,
    ): UsageBudgetEntity =
        entityManager.runTransaction {
            val existing =
                usageBudgetRepository.findByScopeAndScopeId(
                    scope,
                    scopeId,
                ).firstOrNull { it.period == period }
            if (existing == null) {
                val created =
                    usageBudgetRepository.save(
                        UsageBudgetEntity(
                            scope = scope,
                            scopeId = scopeId,
                            period = period,
                            limitAmount = limitAmount,
                            currentUsage = currentUsage,
                        ),
                    )
                entityManager.flush()
                created
            } else {
                existing.limitAmount = limitAmount
                existing.currentUsage = currentUsage
                val updated = usageBudgetRepository.update(existing)
                entityManager.flush()
                updated
            }
        }

    private fun countActiveVersions(promptId: UUID): Long {
        val result =
            entityManager
                .createNativeQuery(
                    """
                    SELECT COUNT(*)
                    FROM assistant.prompt_versions
                    WHERE prompt_id = :promptId
                      AND status = 'ACTIVE'
                    """.trimIndent(),
                ).setParameter("promptId", promptId)
                .singleResult
        return (result as Number).toLong()
    }

    private fun insertSecurityUser(): UUID {
        val userId = UUID.randomUUID()
        entityManager.runTransaction {
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO security.users (id, email, display_name)
                    VALUES (:id, :email, :displayName)
                    """.trimIndent(),
                ).setParameter("id", userId)
                .setParameter("email", "assistant-test-$userId@example.com")
                .setParameter("displayName", "Assistant Test")
                .executeUpdate()
        }
        return userId
    }

    private fun insertSecurityAccount(): UUID {
        val accountId = UUID.randomUUID()
        entityManager.runTransaction {
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO security.accounts (id, account_name, account_type, account_status)
                    VALUES (:id, :name, 'PERSONAL', 'ACTIVE')
                    """.trimIndent(),
                ).setParameter("id", accountId)
                .setParameter("name", "assistant-account-$accountId")
                .executeUpdate()
        }
        return accountId
    }

    private fun bootstrapSecuritySchema(props: Map<String, String>) {
        val script =
            this::class.java.classLoader
                .getResourceAsStream("db/bootstrap_security_for_migration_test.sql")
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Missing bootstrap script: db/bootstrap_security_for_migration_test.sql")

        val jdbcUrl = props["datasources.default.url"] ?: error("Missing datasource URL in test properties")
        val username = props["datasources.default.username"] ?: error("Missing datasource username in test properties")
        val password = props["datasources.default.password"] ?: error("Missing datasource password in test properties")

        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(script)
            }
        }
    }
}
