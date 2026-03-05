package io.github.salomax.neotool.assistant.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.assistant.graphql.dto.CompletionChunkDTO
import io.github.salomax.neotool.assistant.graphql.dto.CompletionResponseDTO
import io.github.salomax.neotool.assistant.graphql.dto.PromptDTO
import io.github.salomax.neotool.assistant.graphql.dto.ToolDTO
import io.github.salomax.neotool.assistant.graphql.dto.UsageBudgetDTO
import io.github.salomax.neotool.assistant.graphql.dto.UsageStatsDTO
import io.github.salomax.neotool.assistant.graphql.resolver.BudgetResolver
import io.github.salomax.neotool.assistant.graphql.resolver.CompletionResolver
import io.github.salomax.neotool.assistant.graphql.resolver.PromptResolver
import io.github.salomax.neotool.assistant.graphql.resolver.StreamingResolver
import io.github.salomax.neotool.assistant.graphql.resolver.ToolResolver
import io.github.salomax.neotool.assistant.graphql.resolver.UsageResolver
import io.github.salomax.neotool.assistant.llm.MessageRole
import io.github.salomax.neotool.common.graphql.AuthenticatedGraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class AssistantWiringFactory(
    private val promptResolver: PromptResolver,
    private val completionResolver: CompletionResolver,
    private val streamingResolver: StreamingResolver,
    private val toolResolver: ToolResolver,
    private val usageResolver: UsageResolver,
    private val budgetResolver: BudgetResolver,
    requestPrincipalProvider: RequestPrincipalProvider,
    authorizationChecker: AuthorizationChecker,
    resolverRegistry: GraphQLResolverRegistry,
) : AuthenticatedGraphQLWiringFactory(requestPrincipalProvider, authorizationChecker) {
    init {
        resolverRegistry.register("assistantPromptResolver", promptResolver)
        resolverRegistry.register("assistantCompletionResolver", completionResolver)
        resolverRegistry.register("assistantStreamingResolver", streamingResolver)
        resolverRegistry.register("assistantToolResolver", toolResolver)
        resolverRegistry.register("assistantUsageResolver", usageResolver)
        resolverRegistry.register("assistantBudgetResolver", budgetResolver)
    }

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "conversation",
                createValidatedDataFetcher { _: DataFetchingEnvironment ->
                    // Placeholder: conversation resolver is not yet implemented.
                    // The schema includes conversation, but until AST-S031 is complete
                    // we return null to avoid breaking the schema.
                    null
                },
            )
            .dataFetcher(
                "prompts",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val category = env.getArgument<String?>("category")
                    val tags = env.getArgument<List<String>?>("tags")
                    promptResolver.prompts(category, tags)
                },
            )
            .dataFetcher(
                "prompt",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val id = env.getArgument<String>("id") ?: error("id argument is required")
                    promptResolver.prompt(id)
                },
            )
            .dataFetcher(
                "promptVersions",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val promptId = env.getArgument<String>("promptId") ?: error("promptId argument is required")
                    promptResolver.promptVersions(promptId)
                },
            )
            .dataFetcher(
                "availableTools",
                createValidatedDataFetcher { _: DataFetchingEnvironment ->
                    toolResolver.availableTools()
                },
            )
            .dataFetcher(
                "myUsage",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val principal = env.principal()
                    val accountId = resolveAccountId(principal)
                    val period = env.getArgument<String>("period") ?: error("period argument is required")
                    usageResolver.myUsage(accountId, period)
                },
            )
            .dataFetcher(
                "serviceUsage",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val serviceId = env.getArgument<String>("serviceId") ?: error("serviceId argument is required")
                    val period = env.getArgument<String>("period") ?: error("period argument is required")
                    usageResolver.serviceUsage(serviceId, period)
                },
            )

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "chat",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    // Map chat to generateCompletion with a simple message list.
                    val input =
                        env.getArgument<Map<String, Any?>>("input")
                            ?: error("input argument is required")
                    val sessionId = input["sessionId"]?.toString().orEmpty()
                    val message = input["message"]?.toString().orEmpty()

                    val principal = env.principal()
                    val accountId = resolveAccountId(principal)
                    val userId = principal.userId
                    val requestedByService = resolveRequestedByService(principal)

                    val completionInput =
                        mapOf(
                            "messages" to
                                listOf(
                                    mapOf(
                                        "role" to MessageRole.USER.name,
                                        "content" to message,
                                    ),
                                ),
                            "purpose" to "chat_session_$sessionId",
                        )

                    val response: CompletionResponseDTO =
                        completionResolver.generateCompletion(
                            input = completionInput,
                            accountId = accountId,
                            requestedByService = requestedByService,
                            userId = userId,
                        )

                    mapOf(
                        "sessionId" to sessionId,
                        "response" to response.content,
                        "escalate" to response.escalate,
                        "conversation" to null,
                    )
                },
            )
            .dataFetcher(
                "clearConversation",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    // Until ConversationService and resolver are fully implemented,
                    // accept the mutation and return true to satisfy the schema.
                    env.getArgument<String>("sessionId") ?: error("sessionId argument is required")
                    true
                },
            )
            .dataFetcher(
                "generateCompletion",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val input =
                        env.getArgument<Map<String, Any?>>("input")
                            ?: error("input argument is required")
                    val principal = env.principal()
                    val accountId = resolveAccountId(principal)
                    val userId = principal.userId
                    val requestedByService = resolveRequestedByService(principal)

                    completionResolver.generateCompletion(
                        input = input,
                        accountId = accountId,
                        requestedByService = requestedByService,
                        userId = userId,
                    )
                },
            )
            .dataFetcher(
                "createPrompt",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val input =
                        env.getArgument<Map<String, Any?>>("input")
                            ?: error("input argument is required")
                    promptResolver.createPrompt(input)
                },
            )
            .dataFetcher(
                "publishPromptVersion",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val promptId =
                        env.getArgument<String>("promptId")
                            ?: error("promptId argument is required")
                    val versionId =
                        env.getArgument<String>("versionId")
                            ?: error("versionId argument is required")
                    promptResolver.publishPromptVersion(promptId, versionId)
                },
            )
            .dataFetcher(
                "registerTool",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val input =
                        env.getArgument<Map<String, Any?>>("input")
                            ?: error("input argument is required")
                    toolResolver.registerTool(input)
                },
            )
            .dataFetcher(
                "setUsageBudget",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val input =
                        env.getArgument<Map<String, Any?>>("input")
                            ?: error("input argument is required")
                    budgetResolver.setUsageBudget(input)
                },
            )

    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type.dataFetcher(
            "messageStream",
            createValidatedDataFetcher { env: DataFetchingEnvironment ->
                val conversationId =
                    env.getArgument<String>("conversationId")
                        ?: error("conversationId argument is required")
                val input =
                    env.getArgument<Map<String, Any?>>("input")
                        ?: error("input argument is required")
                val principal = env.principal()
                val accountId = resolveAccountId(principal)
                val userId = principal.userId
                val requestedByService = resolveRequestedByService(principal)

                streamingResolver.messageStream(
                    conversationId = conversationId,
                    input = input,
                    accountId = accountId,
                    requestedByService = requestedByService,
                    userId = userId,
                )
            },
        )

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder =
        builder
            .type("Prompt") { type ->
                type
                    .dataFetcher(
                        "activeVersion",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val prompt = env.getSource<PromptDTO>()
                            prompt?.activeVersion
                        },
                    )
            }
            .type("CompletionResponse") { type ->
                type
                    .dataFetcher(
                        "usage",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val response = env.getSource<CompletionResponseDTO>()
                            response?.usage
                        },
                    )
                    .dataFetcher(
                        "toolCalls",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val response = env.getSource<CompletionResponseDTO>()
                            response?.toolCalls
                        },
                    )
            }
            .type("CompletionChunk") { type ->
                type
                    .dataFetcher(
                        "usage",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val chunk = env.getSource<CompletionChunkDTO>()
                            chunk?.usage
                        },
                    )
            }
            .type("Tool") { type ->
                type
                    .dataFetcher(
                        "retryPolicy",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val tool = env.getSource<ToolDTO>()
                            tool?.retryPolicy
                        },
                    )
            }
            .type("UsageStats") { type ->
                type
                    .dataFetcher(
                        "modelUsage",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val stats = env.getSource<UsageStatsDTO>()
                            stats?.modelUsage
                        },
                    )
                    .dataFetcher(
                        "featureUsage",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val stats = env.getSource<UsageStatsDTO>()
                            stats?.featureUsage
                        },
                    )
            }
            .type("UsageBudget") { type ->
                type
                    .dataFetcher(
                        "limitAmount",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val budget = env.getSource<UsageBudgetDTO>()
                            budget?.limitAmount
                        },
                    )
                    .dataFetcher(
                        "currentUsage",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val budget = env.getSource<UsageBudgetDTO>()
                            budget?.currentUsage
                        },
                    )
                    .dataFetcher(
                        "alertThreshold",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val budget = env.getSource<UsageBudgetDTO>()
                            budget?.alertThreshold
                        },
                    )
            }

    private fun resolveAccountId(principal: RequestPrincipal): UUID {
        return principal.currentAccountId
            ?: principal.accountIds.firstOrNull()
            ?: error("Account context is required for assistant operations")
    }

    private fun resolveRequestedByService(principal: RequestPrincipal): String {
        val userId = principal.userId
        if (userId != null) {
            return "user-$userId"
        }
        val serviceId = principal.serviceId
        return if (serviceId != null) {
            "service-$serviceId"
        } else {
            "assistant"
        }
    }
}
