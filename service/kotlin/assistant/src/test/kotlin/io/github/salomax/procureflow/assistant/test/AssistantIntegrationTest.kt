package io.github.salomax.neotool.assistant.test

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance

/**
 * Base integration test class for assistant service tests.
 *
 * Provides common setup and GraphQL helper methods for testing the assistant service.
 * Note: The assistant service uses in-memory storage for conversations, so it does not
 * require PostgresIntegrationTest.
 */
@MicronautTest(startApplication = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("assistant")
abstract class AssistantIntegrationTest : BaseIntegrationTest() {
    /**
     * Creates a GraphQL query/mutation request payload
     */
    protected fun graphQLQuery(
        query: String,
        variables: Map<String, Any>? = null,
    ): Map<String, Any> =
        mapOf("query" to query) + if (variables != null) mapOf("variables" to variables) else emptyMap()

    /**
     * Creates a GraphQL mutation for sending a chat message
     */
    protected fun chatMutation(
        sessionId: String,
        message: String,
    ): Map<String, Any> =
        graphQLQuery(
            """
            mutation Chat(${'$'}input: ChatInput!) {
                chat(input: ${'$'}input) {
                    sessionId
                    response
                }
            }
            """.trimIndent(),
            mapOf(
                "input" to
                    mapOf(
                        "sessionId" to sessionId,
                        "message" to message,
                    ),
            ),
        )

    /**
     * Creates a GraphQL query for retrieving conversation context
     */
    protected fun conversationQuery(sessionId: String): Map<String, Any> =
        graphQLQuery(
            """
            query Conversation(${'$'}sessionId: String!) {
                conversation(sessionId: ${'$'}sessionId) {
                    sessionId
                    messageCount
                    lastMessage
                }
            }
            """.trimIndent(),
            mapOf("sessionId" to sessionId),
        )

    /**
     * Creates a GraphQL mutation for clearing conversation history
     */
    protected fun clearConversationMutation(sessionId: String): Map<String, Any> =
        graphQLQuery(
            """
            mutation ClearConversation(${'$'}sessionId: String!) {
                clearConversation(sessionId: ${'$'}sessionId)
            }
            """.trimIndent(),
            mapOf("sessionId" to sessionId),
        )
}
