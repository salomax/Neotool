package io.github.salomax.neotool.comms.graphql.integration

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
        val mutation =
            """
            mutation RequestEmail(${'$'}input: EmailSendRequestInput!) {
              requestEmailSend(input: ${'$'}input) {
                requestId
                status
              }
            }
            """.trimIndent()

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

        val body = mapOf("query" to mutation, "variables" to variables)
        val response =
            httpClient.toBlocking().exchange(
                HttpRequest.POST("/graphql", body)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test-token"),
                Map::class.java,
            )

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
}
