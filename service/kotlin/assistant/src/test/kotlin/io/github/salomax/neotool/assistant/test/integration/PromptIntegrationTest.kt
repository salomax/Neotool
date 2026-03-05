package io.github.salomax.neotool.assistant.test.integration

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
import java.sql.DriverManager
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Prompt Management Integration Tests")
@Tag("integration")
@Tag("assistant")
class PromptIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
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
            entityManager.createNativeQuery("DELETE FROM security.users").executeUpdate()
        }
    }

    @Test
    fun `full prompt lifecycle via GraphQL`() {
        val createdBy = insertSecurityUser()

        val createPromptPayload =
            graphQLRequest(
                query =
                    """
                    mutation CreatePrompt(${'$'}input: CreatePromptInput!) {
                      createPrompt(input: ${'$'}input) {
                        id
                        name
                        category
                        tags
                        activeVersion { id status }
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "input" to
                            mapOf(
                                "name" to "Billing FAQ",
                                "category" to "faq",
                                "description" to "Prompt lifecycle integration test",
                                "tags" to listOf("billing", "support"),
                                "createdBy" to createdBy.toString(),
                                "template" to "Hello {{customerName}}, your plan is \${plan}.",
                                "variablesSchema" to
                                    mapOf(
                                        "required" to listOf("customerName", "plan"),
                                        "properties" to
                                            mapOf(
                                                "customerName" to mapOf("type" to "string"),
                                                "plan" to mapOf("type" to "string"),
                                            ),
                                    ),
                                "outputFormat" to "TEXT",
                            ),
                    ),
            )

        val createResponse = executeGraphQL(createPromptPayload)
        val createJson: JsonNode = json.read(createResponse)
        createJson["errors"].assertNoErrors()

        val createdPrompt = createJson["data"]["createPrompt"]
        val promptId = createdPrompt["id"].stringValue
        assertThat(promptId).isNotBlank()
        assertThat(createdPrompt["activeVersion"].isNull).isTrue()

        val versionsPayload =
            graphQLRequest(
                query =
                    """
                    query PromptVersions(${'$'}promptId: ID!) {
                      promptVersions(promptId: ${'$'}promptId) {
                        id
                        version
                        status
                        template
                        variables { name required }
                      }
                    }
                    """.trimIndent(),
                variables = mapOf("promptId" to promptId),
            )

        val versionsResponse = executeGraphQL(versionsPayload)
        val versionsJson: JsonNode = json.read(versionsResponse)
        versionsJson["errors"].assertNoErrors()

        val versions = versionsJson["data"]["promptVersions"]
        assertThat(versions.size()).isEqualTo(1)
        val initialVersion = versions[0]
        val versionId = initialVersion["id"].stringValue
        assertThat(initialVersion["version"].intValue).isEqualTo(1)
        assertThat(initialVersion["status"].stringValue).isEqualTo("DRAFT")

        val publishPayload =
            graphQLRequest(
                query =
                    """
                    mutation PublishPromptVersion(${'$'}promptId: ID!, ${'$'}versionId: ID!) {
                      publishPromptVersion(promptId: ${'$'}promptId, versionId: ${'$'}versionId) {
                        id
                        promptId
                        status
                        version
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "promptId" to promptId,
                        "versionId" to versionId,
                    ),
            )

        val publishResponse = executeGraphQL(publishPayload)
        val publishJson: JsonNode = json.read(publishResponse)
        publishJson["errors"].assertNoErrors()
        val publishedVersion = publishJson["data"]["publishPromptVersion"]
        assertThat(publishedVersion["status"].stringValue).isEqualTo("ACTIVE")

        val promptPayload =
            graphQLRequest(
                query =
                    """
                    query Prompt(${'$'}id: ID!) {
                      prompt(id: ${'$'}id) {
                        id
                        name
                        activeVersion {
                          id
                          status
                          version
                        }
                      }
                    }
                    """.trimIndent(),
                variables = mapOf("id" to promptId),
            )

        val promptResponse = executeGraphQL(promptPayload)
        val promptJson: JsonNode = json.read(promptResponse)
        promptJson["errors"].assertNoErrors()
        val prompt = promptJson["data"]["prompt"]
        assertThat(prompt["id"].stringValue).isEqualTo(promptId)
        assertThat(prompt["activeVersion"]["id"].stringValue).isEqualTo(versionId)
        assertThat(prompt["activeVersion"]["status"].stringValue).isEqualTo("ACTIVE")

        val promptsPayload =
            graphQLRequest(
                query =
                    """
                    query Prompts(${'$'}category: String, ${'$'}tags: [String!]) {
                      prompts(category: ${'$'}category, tags: ${'$'}tags) {
                        id
                        category
                        tags
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "category" to "faq",
                        "tags" to listOf("billing"),
                    ),
            )

        val promptsResponse = executeGraphQL(promptsPayload)
        val promptsJson: JsonNode = json.read(promptsResponse)
        promptsJson["errors"].assertNoErrors()
        val prompts = promptsJson["data"]["prompts"]
        val returnedIds =
            (0 until prompts.size()).map { index ->
                prompts[index]["id"].stringValue
            }
        assertThat(returnedIds).contains(promptId)
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

    private fun executeGraphQL(payload: Map<String, Any?>) =
        httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", payload)
                .contentType(MediaType.APPLICATION_JSON),
        )
            .shouldBeSuccessful()
            .shouldBeJson()

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
                .setParameter("email", "prompt-it-$userId@example.com")
                .setParameter("displayName", "Prompt IT User")
                .executeUpdate()
        }
        return userId
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
