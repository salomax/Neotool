package io.github.salomax.neotool.mcp.dispatch

import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.OperationDefinition
import graphql.language.Type
import graphql.language.TypeName
import graphql.parser.Parser
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.UnExecutableSchemaGenerator
import graphql.validation.Validator
import io.github.salomax.neotool.common.graphql.BaseSchemaRegistryFactory
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import io.github.salomax.neotool.mcp.catalog.AssistantMcpToolCatalog
import io.github.salomax.neotool.mcp.security.McpPrincipal
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID

@MicronautTest(startApplication = false)
open class McpGraphQLDocumentValidationTest : TestPropertyProvider {
    @Inject
    lateinit var handlerRegistry: McpToolHandlerRegistry

    private val parser = Parser()
    private val validator = Validator()

    override fun getProperties(): MutableMap<String, String> =
        mutableMapOf(
            "mcp.resource.audience" to "https://mcp.example.test",
            "mcp.resource.metadata-url" to "https://mcp.example.test/.well-known/oauth-protected-resource",
            "mcp.resource.metadata.cache-max-age-seconds" to "7200",
            "datasources.default.enabled" to "false",
            "flyway.datasources.default.enabled" to "false",
            "micronaut.executors.scheduled.enabled" to "false",
            "mcp.rate-limit.cleanup.enabled" to "false",
            "mcp.revocation.eviction.enabled" to "false",
        )

    @Test
    fun `transaction read documents parse and validate against the banking contract`() {
        val transactionHandlers =
            handlerRegistry
                .all()
                .filter { it.name in setOf("list_transactions", "get_transaction") }

        validateRegisteredDocuments(transactionHandlers, bankingSchema)
    }

    @Test
    fun `every registered banking handler exposes at least one document and all validate against the schema`() {
        val handlers = handlerRegistry.all()

        assertThat(handlers)
            .describedAs("Injected McpToolHandlerRegistry must not be empty or document validation becomes vacuous")
            .isNotEmpty()
        requireDocuments(handlers)
        validateRegisteredDocuments(handlers, bankingSchema)
    }

    @Test
    fun `assistant catalog documents validate against the assistant contract schema`() {
        // AssistantMcpToolCatalog is the only MCP GraphQL document owner outside the handler registry today.
        assertThat(AssistantMcpToolCatalog.graphQLDocuments).isNotEmpty()
        AssistantMcpToolCatalog.graphQLDocuments.forEach { document ->
            val errors = validateDocument(document, assistantSchema)
            assertThat(errors).isEmpty()
        }
    }

    @Test
    fun `invalid investment asset symbol selection is rejected by the validation harness`() {
        val invalidDocument =
            """
            query InvalidTransactionDocument {
              transaction(id: "tx-1") {
                linkedInvestmentAsset {
                  id
                  symbol
                }
              }
            }
            """.trimIndent()

        val errors = validateDocument(invalidDocument, bankingSchema)

        assertThat(errors.map { it.message }).anyMatch { it.contains("symbol") && it.contains("InvestmentAsset") }
    }

    @Test
    fun `banking handlers without exposed documents fail the registry guard`() {
        val undocumentedHandler =
            object : AbstractMcpBankingToolHandler(NoopBankingClient) {
                override val name: String = "undocumented_tool"

                override fun call(
                    arguments: Map<String, Any?>,
                    principal: McpPrincipal,
                ) = error("unused")
            }

        assertThatThrownBy { requireDocuments(listOf(undocumentedHandler)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("undocumented_tool")
    }

    @Test
    fun `transaction handlers send variables matching their operation definitions`() {
        val listClient = CapturingBankingClient()
        val listHandler = ListTransactionsToolHandler(listClient)
        listHandler.call(
            arguments =
                mapOf(
                    "financialAccountId" to UUID.randomUUID().toString(),
                    "search" to "coffee",
                    "uncategorized" to true,
                    "tags" to listOf("food"),
                    "creditDebitType" to "DEBIT",
                    "types" to listOf("EXPENSE"),
                    "month" to 7,
                    "year" to 2026,
                    "first" to 25,
                    "after" to "cursor-1",
                ),
            principal = principal(),
        )
        assertVariablesMatchOperation(
            document = listHandler.graphQLDocuments.single(),
            variables = listClient.variables,
            schema = bankingSchema,
        )

        val getClient = CapturingBankingClient()
        val getHandler = GetTransactionToolHandler(getClient)
        getHandler.call(arguments = mapOf("id" to UUID.randomUUID().toString()), principal = principal())
        assertVariablesMatchOperation(
            document = getHandler.graphQLDocuments.single(),
            variables = getClient.variables,
            schema = bankingSchema,
        )
    }

    private fun validateRegisteredDocuments(
        handlers: Collection<McpToolHandler>,
        schema: GraphQLSchema,
    ) {
        handlers
            .flatMap { handler -> handler.graphQLDocuments.map { handler.name to it } }
            .forEach { (handlerName, document) ->
                val errors = validateDocument(document, schema)
                assertThat(errors)
                    .describedAs(
                        "Expected GraphQL document for handler '%s' to validate but got: %s",
                        handlerName,
                        errors,
                    )
                    .isEmpty()
            }
    }

    private fun requireDocuments(handlers: Collection<McpToolHandler>) {
        val undocumentedHandlers =
            handlers
                .filterIsInstance<AbstractMcpBankingToolHandler>()
                .filter { it.graphQLDocuments.isEmpty() }
                .map { it.name }

        check(undocumentedHandlers.isEmpty()) {
            "Every banking handler must expose graphQLDocuments, missing: ${undocumentedHandlers.joinToString(", ")}"
        }
    }

    private fun validateDocument(
        document: String,
        schema: GraphQLSchema,
    ) = validator.validateDocument(schema, parser.parseDocument(document), Locale.US)

    private fun assertVariablesMatchOperation(
        document: String,
        variables: Map<String, Any>,
        schema: GraphQLSchema,
    ) {
        val operation =
            parser.parseDocument(document)
                .definitions
                .filterIsInstance<OperationDefinition>()
                .single()
        val variableDefinitions = operation.variableDefinitions.associateBy { it.name }

        assertThat(variables.keys).allMatch(variableDefinitions::containsKey)
        variables.forEach { (name, value) ->
            val type = requireNotNull(variableDefinitions[name]).type
            assertThat(valueMatchesType(value, type, schema))
                .withFailMessage("Variable '%s' does not match declared GraphQL type %s", name, type)
                .isTrue()
        }
    }

    private fun valueMatchesType(
        value: Any?,
        type: Type<*>,
        schema: GraphQLSchema,
    ): Boolean =
        when (type) {
            is NonNullType -> value != null && valueMatchesType(value, type.type, schema)
            is ListType -> value is List<*> && value.all { valueMatchesType(it, type.type, schema) }
            is TypeName ->
                when (type.name) {
                    "ID", "String", "Date", "DateTime", "UUID" -> value is String
                    "Int" -> value is Int
                    "Long" -> value is Int || value is Long
                    "Float" -> value is Number
                    "Boolean" -> value is Boolean
                    "JSON" -> isJsonValue(value)
                    else ->
                        when (schema.getType(type.name)) {
                            is GraphQLEnumType -> value is String
                            is GraphQLInputObjectType -> value is Map<*, *>
                            else -> false
                        }
                }
            else -> false
        }

    private fun isJsonValue(value: Any?): Boolean =
        when (value) {
            null -> true
            is String, is Number, is Boolean -> true
            is Map<*, *> -> value.values.all(::isJsonValue)
            is List<*> -> value.all(::isJsonValue)
            else -> false
        }

    private class CapturingBankingClient : McpBankingGraphQLClient {
        var variables: Map<String, Any> = emptyMap()

        override suspend fun query(
            query: String,
            variables: Map<String, Any>,
            principal: McpPrincipal,
        ): GraphQLResponse {
            this.variables = variables
            return GraphQLResponse(
                data =
                    mapOf(
                        "transactions" to emptyList<Map<String, Any?>>(),
                        "transaction" to emptyMap<String, Any?>(),
                    ),
            )
        }
    }

    private object NoopBankingClient : McpBankingGraphQLClient {
        override suspend fun query(
            query: String,
            variables: Map<String, Any>,
            principal: McpPrincipal,
        ): GraphQLResponse = GraphQLResponse(data = emptyMap())
    }

    @MockBean(McpBankingGraphQLClient::class)
    open fun bankingClient(): McpBankingGraphQLClient = NoopBankingClient

    @MockBean(GraphQLServiceClient::class)
    open fun graphQLServiceClient(): GraphQLServiceClient =
        throw UnsupportedOperationException("GraphQLServiceClient is not used by this test")

    private fun principal(): McpPrincipal =
        McpPrincipal(
            principal =
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = UUID.randomUUID(),
                    serviceId = null,
                    token = "token",
                    tokenId = "token-id",
                    permissionsFromToken = emptyList(),
                    currentAccountId = UUID.randomUUID(),
                ),
            clientId = "client",
            scopes = setOf("finance:transactions:read"),
        )

    private companion object {
        private val schemaParser = SchemaParser()
        private val duplicatedBaseScalars = setOf("Long", "Date", "DateTime", "JSON", "UUID")
        private val bankingSchema: GraphQLSchema by lazy {
            loadSchemaFromContractsProperty("mcp.contracts.bankingSchemaPath")
        }
        private val assistantSchema: GraphQLSchema by lazy {
            loadSchemaFromContractsProperty(
                propertyName = "mcp.contracts.assistantSchemaPath",
                rewriteRootOperationTypes = true,
            )
        }

        private fun loadSchemaFromContractsProperty(
            propertyName: String,
            rewriteRootOperationTypes: Boolean = false,
        ): GraphQLSchema {
            val contractsPath =
                Path.of(
                    requireNotNull(System.getProperty(propertyName)) {
                        "Missing system property $propertyName"
                    },
                )
            require(Files.isRegularFile(contractsPath)) {
                "GraphQL contracts schema not found at: $contractsPath"
            }
            val moduleSchema = readModuleSchema(contractsPath, rewriteRootOperationTypes)
            val typeRegistry =
                object : BaseSchemaRegistryFactory() {
                    override fun loadModuleSchemas(): List<TypeDefinitionRegistry> =
                        listOf(
                            schemaParser.parse(moduleSchema),
                        )
                }.typeRegistry()

            return UnExecutableSchemaGenerator.makeUnExecutableSchema(typeRegistry)
        }

        private fun readModuleSchema(
            contractsPath: Path,
            rewriteRootOperationTypes: Boolean,
        ): String =
            Files
                .readString(contractsPath)
                .lineSequence()
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (
                        trimmed.startsWith("scalar ") &&
                        trimmed.removePrefix("scalar ").trim() in duplicatedBaseScalars
                    ) {
                        return@mapNotNull null
                    }
                    if (!rewriteRootOperationTypes) {
                        return@mapNotNull line
                    }

                    when (trimmed) {
                        "type Query {" -> line.replace("type Query", "extend type Query")
                        "type Mutation {" -> line.replace("type Mutation", "extend type Mutation")
                        "type Subscription {" -> line.replace("type Subscription", "extend type Subscription")
                        else -> line
                    }
                }.joinToString("\n")
    }
}
