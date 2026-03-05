package io.github.salomax.neotool.comms.graphql.integration

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.KafkaIntegrationTest
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@MicronautTest
@DisplayName("Comms GraphQL Integration Tests")
@Tag("integration")
class CommsGraphQLIntegrationTest : BaseIntegrationTest(), KafkaIntegrationTest {
    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties().toMutableMap()

        props +=
            mapOf(
                "comms.email.provider" to "mock",
                "datasources.default.enabled" to "false",
                "jpa.default.enabled" to "false",
                "flyway.enabled" to "false",
            )

        return props
    }

    @Test
    fun `requestEmailSend should return requestId and status`() {
        val variables =
            mapOf(
                "input" to
                    mapOf(
                        "to" to "user@example.com",
                        "content" to
                            mapOf(
                                "kind" to "RAW",
                                "format" to "TEXT",
                                "subject" to "Hello",
                                "body" to "World",
                                "variables" to emptyMap<String, Any?>(),
                            ),
                    ),
            )

        val response = executeRequestEmailMutation(variables, "Bearer test-token")

        assertThat(response.status.code).isEqualTo(200)
        val payload = response.body() as Map<*, *>
        val errors = payload["errors"]
        assertThat(errors)
            .`as`("GraphQL errors: $errors")
            .isNull()

        val data = payload["data"] as Map<*, *>
        val result = data["requestEmailSend"] as Map<*, *>
        assertThat(result["requestId"]).isNotNull
        assertThat(result["status"]).isEqualTo("QUEUED")
    }

    @Test
    fun `requestEmailSend should return authentication required code when token missing`() {
        val variables =
            mapOf(
                "input" to
                    mapOf(
                        "to" to "user@example.com",
                        "content" to
                            mapOf(
                                "kind" to "RAW",
                                "format" to "TEXT",
                                "subject" to "Hello",
                                "body" to "World",
                                "variables" to emptyMap<String, Any?>(),
                            ),
                    ),
            )

        val response = executeRequestEmailMutation(variables, authorization = null)

        assertThat(response.status.code).isEqualTo(200)
        val payload = response.body() as Map<*, *>
        val errors = payload["errors"] as List<Map<String, Any?>>
        val error = errors.first()

        assertThat(error["message"]).isEqualTo("Authentication required")
        val extensions = error["extensions"] as Map<*, *>
        assertThat(extensions["code"]).isEqualTo("AUTH_AUTHENTICATION_REQUIRED")
        assertThat(extensions["service"]).isEqualTo("comms")
    }

    @Test
    fun `requestEmailSend should return INVALID_INPUT code for template content without templateKey`() {
        val variables =
            mapOf(
                "input" to
                    mapOf(
                        "to" to "user@example.com",
                        "content" to
                            mapOf(
                                "kind" to "TEMPLATE",
                                "locale" to "en",
                                "variables" to mapOf("name" to "Alice"),
                            ),
                    ),
            )

        val response = executeRequestEmailMutation(variables, "Bearer test-token")

        assertThat(response.status.code).isEqualTo(200)
        val payload = response.body() as Map<*, *>
        val errors = payload["errors"] as List<Map<String, Any?>>
        val error = errors.first()

        assertThat(error["message"]).isEqualTo(CommonErrorCode.INVALID_INPUT.defaultMessage)
        val extensions = error["extensions"] as Map<*, *>
        assertThat(extensions["code"]).isEqualTo(CommonErrorCode.INVALID_INPUT.code)
        assertThat(extensions["service"]).isEqualTo("comms")
    }

    private fun executeRequestEmailMutation(
        variables: Map<String, Any?>,
        authorization: String?,
    ) = httpClient.toBlocking().exchange(
        HttpRequest
            .POST(
                "/graphql",
                mapOf(
                    "query" to REQUEST_EMAIL_MUTATION,
                    "variables" to variables,
                ),
            ).contentType(MediaType.APPLICATION_JSON)
            .also { request ->
                if (authorization != null) {
                    request.header("Authorization", authorization)
                }
            },
        Map::class.java,
    )

    private companion object {
        val REQUEST_EMAIL_MUTATION =
            """
            mutation RequestEmail(${'$'}input: EmailSendRequestInput!) {
              requestEmailSend(input: ${'$'}input) {
                requestId
                status
              }
            }
            """.trimIndent()
    }
}
