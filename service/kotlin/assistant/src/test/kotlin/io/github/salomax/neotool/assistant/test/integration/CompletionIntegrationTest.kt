package io.github.salomax.neotool.assistant.test.integration

import io.github.salomax.neotool.assistant.repository.CompletionRequestRepository
import io.github.salomax.neotool.assistant.repository.CompletionResponseRepository
import io.github.salomax.neotool.assistant.test.mock.MockProviderClient
import io.github.salomax.neotool.assistant.test.security.AssistantTestSecurityBeans
import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.json.read
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.json.tree.JsonNode
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
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Completion Integration Tests")
@Tag("integration")
@Tag("assistant")
class CompletionIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var completionRequestRepository: CompletionRequestRepository

    @Inject
    lateinit var completionResponseRepository: CompletionResponseRepository

    @Inject
    lateinit var mockProviderClient: MockProviderClient

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties()
        props["jpa.default.enabled"] = "true"
        bootstrapSecuritySchema(props)
        return props
    }

    @AfterEach
    fun cleanup() {
        mockProviderClient.clear()
        entityManager.runTransaction {
            entityManager.createNativeQuery("DELETE FROM assistant.completion_responses").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.completion_requests").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.usage_log").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.usage_budgets").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.prompt_versions").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.prompts").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.llm_models").executeUpdate()
            entityManager.createNativeQuery("DELETE FROM assistant.llm_providers").executeUpdate()
            entityManager
                .createNativeQuery("DELETE FROM security.users WHERE id = :id")
                .setParameter("id", AssistantTestSecurityBeans.TEST_USER_ID)
                .executeUpdate()
            entityManager
                .createNativeQuery("DELETE FROM security.accounts WHERE account_name LIKE 'completion-it-account-%'")
                .executeUpdate()
        }
    }

    @Test
    fun `generateCompletion mutation persists request and response using account from JWT token`() {
        val accountId = UUID.randomUUID()
        val seededModel = insertProviderAndModel()
        insertSecurityAccount(accountId)
        insertSecurityUser(AssistantTestSecurityBeans.TEST_USER_ID)

        mockProviderClient.setDefaultResponse(
            io.github.salomax.neotool.assistant.provider.model.ProviderCompletionResponse(
                id = "chatcmpl-it-1",
                content = "Integration completion response",
                finishReason = io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason.STOP,
                usage =
                    io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage(
                        promptTokens = 21,
                        completionTokens = 9,
                        totalTokens = 30,
                    ),
            ),
        )

        val payload =
            graphQLRequest(
                query =
                    """
                    mutation GenerateCompletion(${'$'}input: CompletionInput!) {
                      generateCompletion(input: ${'$'}input) {
                        id
                        content
                        finishReason
                        usage {
                          promptTokens
                          completionTokens
                          totalTokens
                        }
                        model
                        latencyMs
                        cost
                        escalate
                        toolCalls {
                          id
                          type
                          name
                          arguments
                        }
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "input" to
                            mapOf(
                                "messages" to
                                    listOf(
                                        mapOf(
                                            "role" to "USER",
                                            "content" to "Summarize account spend in one line.",
                                        ),
                                    ),
                                "modelPreference" to seededModel.modelName,
                                "purpose" to "completion_integration_test",
                                "metadata" to mapOf("source" to "integration-test"),
                            ),
                    ),
            )

        val response = executeGraphQL(payload, accountId)
        val jsonResponse: JsonNode = json.read(response)
        jsonResponse["errors"].assertNoErrors()

        val completion = jsonResponse["data"]["generateCompletion"]
        assertThat(completion["content"].stringValue).isEqualTo("Integration completion response")
        assertThat(completion["finishReason"].stringValue).isEqualTo("stop")
        assertThat(completion["model"].stringValue).isEqualTo(seededModel.modelName)
        assertThat(completion["usage"]["totalTokens"].intValue).isEqualTo(30)
        assertThat(completion["escalate"].booleanValue).isFalse()
        assertThat(completion["toolCalls"].size()).isEqualTo(0)

        val completionId = UUID.fromString(completion["id"].stringValue)
        val requests = completionRequestRepository.findByAccountIdAndCreatedAtDesc(accountId)
        assertThat(requests).hasSize(1)
        val savedRequest = requests.first()
        val requestId = requireNotNull(savedRequest.id)
        assertThat(savedRequest.accountId).isEqualTo(accountId)
        assertThat(savedRequest.userId).isEqualTo(AssistantTestSecurityBeans.TEST_USER_ID)
        assertThat(savedRequest.requestedByService).isEqualTo("user-${AssistantTestSecurityBeans.TEST_USER_ID}")
        assertThat(savedRequest.purpose).isEqualTo("completion_integration_test")
        assertThat(savedRequest.messages).hasSize(1)

        val savedResponse = completionResponseRepository.findByRequestId(requestId)
        assertThat(savedResponse).isNotNull
        assertThat(savedResponse!!.id).isEqualTo(completionId)
        assertThat(savedResponse.accountId).isEqualTo(accountId)
        assertThat(savedResponse.providerId).isEqualTo(seededModel.providerId)
        assertThat(savedResponse.modelId).isEqualTo(seededModel.modelId)
        assertThat(savedResponse.totalTokens).isEqualTo(30)
        assertThat(savedResponse.finishReason).isEqualTo("stop")

        val capturedProviderRequests = mockProviderClient.capturedRequests()
        assertThat(capturedProviderRequests).hasSize(1)
        assertThat(capturedProviderRequests.first().model).isEqualTo(seededModel.modelName)
    }

    private fun graphQLRequest(
        query: String,
        variables: Map<String, Any?>? = null,
    ): Map<String, Any?> =
        mutableMapOf<String, Any?>("query" to query).apply {
            if (variables != null) {
                this["variables"] = variables
            }
        }

    private fun executeGraphQL(
        payload: Map<String, Any?>,
        accountId: UUID,
    ) = httpClient
        .exchangeAsString(
            HttpRequest.POST("/graphql", payload)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${tokenForAccount(accountId)}"),
        ).shouldBeSuccessful()
        .shouldBeJson()

    private fun tokenForAccount(accountId: UUID): String = "${AssistantTestSecurityBeans.TOKEN_PREFIX}$accountId"

    private fun insertSecurityUser(userId: UUID) {
        entityManager.runTransaction {
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO security.users (id, email, display_name)
                    VALUES (:id, :email, :displayName)
                    ON CONFLICT (id) DO NOTHING
                    """.trimIndent(),
                ).setParameter("id", userId)
                .setParameter("email", "completion-it-user-$userId@example.com")
                .setParameter("displayName", "Completion Integration Test User")
                .executeUpdate()
        }
    }

    private fun insertSecurityAccount(accountId: UUID) {
        entityManager.runTransaction {
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO security.accounts (id, account_name, account_type, account_status)
                    VALUES (:id, :name, 'PERSONAL', 'ACTIVE')
                    ON CONFLICT (id) DO NOTHING
                    """.trimIndent(),
                ).setParameter("id", accountId)
                .setParameter("name", "completion-it-account-$accountId")
                .executeUpdate()
        }
    }

    private fun insertProviderAndModel(): SeededModel {
        val providerId = UUID.randomUUID()
        val modelId = UUID.randomUUID()
        val modelName = "gpt-4o-mini-it-${modelId.toString().substring(0, 8)}"

        entityManager.runTransaction {
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO assistant.llm_providers (id, name, type, base_url, api_key_ref, status, priority)
                    VALUES (:id, :name, 'OPENAI', :baseUrl, :apiKeyRef, 'ACTIVE', :priority)
                    """.trimIndent(),
                ).setParameter("id", providerId)
                .setParameter("name", "completion-it-provider-${providerId.toString().substring(0, 8)}")
                .setParameter("baseUrl", "https://api.openai.com")
                .setParameter("apiKeyRef", "env:OPENAI_API_KEY")
                .setParameter("priority", -1000)
                .executeUpdate()

            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO assistant.llm_models (
                        id,
                        provider_id,
                        model_name,
                        display_name,
                        capabilities,
                        context_window,
                        max_output_tokens,
                        cost_per_input_token,
                        cost_per_output_token,
                        status
                    ) VALUES (
                        :id,
                        :providerId,
                        :modelName,
                        :displayName,
                        ARRAY['JSON_MODE','TOOLS']::text[],
                        :contextWindow,
                        :maxOutputTokens,
                        :costIn,
                        :costOut,
                        'ACTIVE'
                    )
                    """.trimIndent(),
                ).setParameter("id", modelId)
                .setParameter("providerId", providerId)
                .setParameter("modelName", modelName)
                .setParameter("displayName", "Completion Integration Model")
                .setParameter("contextWindow", 128000)
                .setParameter("maxOutputTokens", 4096)
                .setParameter("costIn", BigDecimal("0.000001"))
                .setParameter("costOut", BigDecimal("0.000002"))
                .executeUpdate()
        }

        return SeededModel(
            providerId = providerId,
            modelId = modelId,
            modelName = modelName,
        )
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

    private data class SeededModel(
        val providerId: UUID,
        val modelId: UUID,
        val modelName: String,
    )
}
