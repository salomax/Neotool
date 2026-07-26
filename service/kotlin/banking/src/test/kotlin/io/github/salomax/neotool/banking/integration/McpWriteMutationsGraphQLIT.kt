package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.config.BankingTestTokenPrincipalDecoder
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("MCP write mutations GraphQL IT")
@Tag("integration")
@Tag("banking")
class McpWriteMutationsGraphQLIT : BankingPostgresIntegrationTest() {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    private lateinit var financialAccountId: UUID

    override fun getProperties(): MutableMap<String, String> =
        super.getProperties().also {
            it["banking.mcp-write-bulk.max-affected-records"] = "2"
        }

    @BeforeEach
    fun seedFinancialAccount() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (
                    id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at
                )
                VALUES (
                    '$financialAccountId',
                    '${BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID}',
                    '${BankingTestTokenPrincipalDecoder.TEST_USER_ID}',
                    'BANK',
                    'CONTA_CORRENTE',
                    'MCP Write GraphQL IT',
                    'BRL',
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `createTransaction replays same key and different key creates a second row`() {
        val description = "MCP create ${UUID.randomUUID()}"

        val first =
            createTransaction(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                description = description,
                idempotencyKey = "create-key",
                toolCallId = UUID.randomUUID(),
            )
        val second =
            createTransaction(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                description = description,
                idempotencyKey = "create-key",
                toolCallId = UUID.randomUUID(),
            )
        val third =
            createTransaction(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                description = description,
                idempotencyKey = "create-key-2",
                toolCallId = UUID.randomUUID(),
            )

        assertThat(first.createTransactionId()).isEqualTo(second.createTransactionId())
        assertThat(third.createTransactionId()).isNotEqualTo(first.createTransactionId())
        assertThat(countTransactionsByDescription(description)).isEqualTo(2)
    }

    @Test
    fun `updateTransaction replays same key and different key applies a new update`() {
        // Seed via GraphQL (HTTP) so the row is committed and visible to later mutation calls.
        // Injected TransactionService.create would stay inside the MicronautTest transaction.
        val existingId =
            createTransaction(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                description = "Before update ${UUID.randomUUID()}",
                idempotencyKey = "seed-update-${UUID.randomUUID()}",
                toolCallId = UUID.randomUUID(),
            ).createTransactionId().let(UUID::fromString)

        val first =
            updateTransaction(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                transactionId = existingId,
                description = "Updated once",
                idempotencyKey = "update-key",
                toolCallId = UUID.randomUUID(),
            )
        val second =
            updateTransaction(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                transactionId = existingId,
                description = "Updated once",
                idempotencyKey = "update-key",
                toolCallId = UUID.randomUUID(),
            )
        val third =
            updateTransaction(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                transactionId = existingId,
                description = "Updated twice",
                idempotencyKey = "update-key-2",
                toolCallId = UUID.randomUUID(),
            )

        assertThat(first.updateTransactionId())
            .isEqualTo(second.updateTransactionId())
            .isEqualTo(third.updateTransactionId())
        assertThat(first.updatedAt()).isEqualTo(second.updatedAt())
        assertThat(third.updatedAt()).isNotEqualTo(first.updatedAt())
        assertThat(loadTransactionDescription(existingId)).isEqualTo("Updated twice")
    }

    @Test
    fun `createTransactionRule replays same key and different key creates a second rule`() {
        val firstPattern = "ifood-${UUID.randomUUID()}"
        val secondPattern = "uber-${UUID.randomUUID()}"

        val first =
            createTransactionRule(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                pattern = firstPattern,
                idempotencyKey = "rule-key",
                toolCallId = UUID.randomUUID(),
            )
        val second =
            createTransactionRule(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                pattern = firstPattern,
                idempotencyKey = "rule-key",
                toolCallId = UUID.randomUUID(),
            )
        val third =
            createTransactionRule(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                pattern = secondPattern,
                idempotencyKey = "rule-key-2",
                toolCallId = UUID.randomUUID(),
            )

        assertThat(first.ruleId()).isEqualTo(second.ruleId())
        assertThat(third.ruleId()).isNotEqualTo(first.ruleId())
        assertThat(countRulesByPattern(firstPattern)).isEqualTo(1)
        assertThat(countRulesByPattern(secondPattern)).isEqualTo(1)
    }

    @Test
    fun `agent write without idempotency key is rejected before any row is written`() {
        val description = "Missing key ${UUID.randomUUID()}"

        val response =
            execute(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                toolCallId = UUID.randomUUID(),
                query =
                    """
                    mutation CreateTransactionWithoutKey(${'$'}input: CreateTransactionInput!) {
                      createTransaction(input: ${'$'}input) {
                        transaction {
                          id
                        }
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "input" to baseCreateTransactionInput(description),
                    ),
            )

        val errors = response["errors"] as List<*>
        val firstError = errors.first() as Map<*, *>
        val extensions = firstError["extensions"] as Map<*, *>
        assertThat(extensions["code"]).isEqualTo("MCP_IDEMPOTENCY_KEY_REQUIRED")
        assertThat(countTransactionsByDescription(description)).isZero()
    }

    @Test
    fun `ui createTransaction without agent context stays unchanged`() {
        val description = "UI path ${UUID.randomUUID()}"

        val response =
            execute(
                token = BankingTestTokenPrincipalDecoder.TEST_TOKEN,
                query =
                    """
                    mutation UiCreateTransaction(${'$'}input: CreateTransactionInput!) {
                      createTransaction(input: ${'$'}input) {
                        transaction {
                          id
                        }
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "input" to baseCreateTransactionInput(description),
                    ),
            )

        val data = response["data"] as Map<*, *>
        val payload = data["createTransaction"] as Map<*, *>
        val transaction = payload["transaction"] as Map<*, *>
        assertThat(transaction["id"]).isNotNull()
        assertThat(countTransactionsByDescription(description)).isEqualTo(1)
    }

    @Test
    fun `agent createTransaction stamps MCP origin from trusted headers`() {
        val description = "Origin stamped ${UUID.randomUUID()}"
        val toolCallId = UUID.randomUUID()

        createTransaction(
            token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
            description = description,
            idempotencyKey = "origin-key-${UUID.randomUUID()}",
            toolCallId = toolCallId,
        )

        val stored = loadStoredOrigin(description)
        assertThat(stored.originChannel).isEqualTo("MCP")
        assertThat(stored.originClientId).isEqualTo("mcp-client")
        assertThat(stored.originToolCallId).isEqualTo(toolCallId)
    }

    @Test
    fun `agent bulkCategorize replays same key without a second audit write`() {
        val categoryId = UUID.randomUUID()
        val firstToolCallId = UUID.randomUUID()
        val secondToolCallId = UUID.randomUUID()
        val transactionIds = List(2) { UUID.randomUUID() }

        insertCategory(categoryId, BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID, "Replay Category")
        transactionIds.forEachIndexed { index, transactionId ->
            insertTransaction(
                id = transactionId,
                accountId = BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID,
                financialAccountId = financialAccountId,
                description = "Replay tx $index ${UUID.randomUUID()}",
            )
        }

        val first =
            bulkCategorize(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                transactionIds = transactionIds,
                categoryId = categoryId,
                idempotencyKey = "bulk-replay-key",
                toolCallId = firstToolCallId,
            )
        val second =
            bulkCategorize(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                transactionIds = transactionIds,
                categoryId = categoryId,
                idempotencyKey = "bulk-replay-key",
                toolCallId = secondToolCallId,
            )

        assertThat(first.bulkOperationOutcome()).isEqualTo("EXECUTED")
        assertThat(second.bulkOperationOutcome()).isEqualTo("EXECUTED")
        assertThat(first.bulkOperationAffectedCount()).isEqualTo(2)
        assertThat(second.bulkOperationAffectedCount()).isEqualTo(2)
        assertThat(loadTransactionCategoryIds(transactionIds)).containsOnly(categoryId)
        assertThat(countChangeLogEntriesByToolCallId(firstToolCallId)).isEqualTo(2)
        assertThat(countChangeLogEntriesByToolCallId(secondToolCallId)).isZero()
    }

    @Test
    fun `agent bulkCategorize above cap returns confirmation required without mutating`() {
        val categoryId = UUID.randomUUID()
        val transactionIds = List(3) { UUID.randomUUID() }

        insertCategory(categoryId, BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID, "Guarded Category")
        transactionIds.forEachIndexed { index, transactionId ->
            insertTransaction(
                id = transactionId,
                accountId = BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID,
                financialAccountId = financialAccountId,
                description = "Guarded tx $index ${UUID.randomUUID()}",
            )
        }

        val response =
            bulkCategorize(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                transactionIds = transactionIds,
                categoryId = categoryId,
                idempotencyKey = "bulk-over-cap",
                toolCallId = UUID.randomUUID(),
            )

        assertThat(response.bulkOperationOutcome()).isEqualTo("CONFIRMATION_REQUIRED")
        assertThat(response.bulkOperationAffectedCount()).isEqualTo(3)
        assertThat(response.bulkOperationErrors()).isEmpty()
        assertThat(loadTransactionCategoryIds(transactionIds)).containsOnlyNulls()
    }

    @Test
    fun `agent applyTransactionRuleToHistorical above cap returns confirmation required without mutating matches`() {
        val categoryId = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val transactionIds = List(3) { UUID.randomUUID() }
        val pattern = "NETFLIX-${UUID.randomUUID()}"

        insertCategory(categoryId, BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID, "Historical Category")
        insertTransactionRule(ruleId = ruleId, categoryId = categoryId, pattern = pattern)
        transactionIds.forEachIndexed { index, transactionId ->
            insertTransaction(
                id = transactionId,
                accountId = BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID,
                financialAccountId = financialAccountId,
                description = "Subscription $pattern $index",
                source = "OFX",
            )
        }

        val response =
            applyTransactionRuleToHistorical(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                ruleId = ruleId,
                idempotencyKey = "rule-over-cap",
                toolCallId = UUID.randomUUID(),
            )

        assertThat(response.bulkOperationOutcome()).isEqualTo("CONFIRMATION_REQUIRED")
        assertThat(response.bulkOperationAffectedCount()).isEqualTo(3)
        assertThat(response.bulkOperationErrors()).isEmpty()
        assertThat(loadTransactionCategoryIds(transactionIds)).containsOnlyNulls()
    }

    @Test
    fun `agent bulkCategorize ignores foreign account transaction ids`() {
        val foreignAccountId = UUID.randomUUID()
        val foreignFinancialAccountId = seedFinancialAccount(foreignAccountId)
        val categoryId = UUID.randomUUID()
        val ownTransactionId = UUID.randomUUID()
        val foreignTransactionId = UUID.randomUUID()

        insertCategory(categoryId, BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID, "Cross Tenant Category")
        insertTransaction(
            id = ownTransactionId,
            accountId = BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID,
            financialAccountId = financialAccountId,
            description = "Own transaction ${UUID.randomUUID()}",
        )
        insertTransaction(
            id = foreignTransactionId,
            accountId = foreignAccountId,
            financialAccountId = foreignFinancialAccountId,
            description = "Foreign transaction ${UUID.randomUUID()}",
        )

        val response =
            bulkCategorize(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                transactionIds = listOf(ownTransactionId, foreignTransactionId),
                categoryId = categoryId,
                idempotencyKey = "bulk-cross-tenant",
                toolCallId = UUID.randomUUID(),
            )

        assertThat(response.bulkOperationOutcome()).isEqualTo("EXECUTED")
        assertThat(response.bulkOperationAffectedCount()).isEqualTo(1)
        assertThat(loadTransactionCategoryIds(listOf(ownTransactionId, foreignTransactionId)))
            .containsExactly(categoryId, null)
    }

    @Test
    fun `agent applyTransactionRuleToHistorical ignores matching transactions from another account`() {
        val foreignAccountId = UUID.randomUUID()
        val foreignFinancialAccountId = seedFinancialAccount(foreignAccountId)
        val categoryId = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val ownTransactionId = UUID.randomUUID()
        val foreignTransactionId = UUID.randomUUID()
        val pattern = "SPOTIFY-${UUID.randomUUID()}"
        val ownDescription = "Subscription $pattern"
        val foreignDescription = "Foreign subscription $pattern"

        insertCategory(categoryId, BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID, "Historical Cross Tenant")
        insertTransactionRule(ruleId = ruleId, categoryId = categoryId, pattern = pattern)
        insertTransaction(
            id = ownTransactionId,
            accountId = BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID,
            financialAccountId = financialAccountId,
            description = ownDescription,
            source = "OFX",
        )
        insertTransaction(
            id = foreignTransactionId,
            accountId = foreignAccountId,
            financialAccountId = foreignFinancialAccountId,
            description = foreignDescription,
            source = "OFX",
        )

        val response =
            applyTransactionRuleToHistorical(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                ruleId = ruleId,
                idempotencyKey = "rule-cross-tenant",
                toolCallId = UUID.randomUUID(),
            )

        assertThat(response.bulkOperationOutcome()).isEqualTo("EXECUTED")
        assertThat(response.bulkOperationAffectedCount()).isEqualTo(1)
        assertThat(loadTransactionCategoryIds(listOf(ownTransactionId, foreignTransactionId)))
            .containsExactly(categoryId, null)
    }

    @Test
    fun `agent createTransaction cannot use another account financialAccount`() {
        val foreignAccountId = UUID.randomUUID()
        val foreignFinancialAccountId = seedFinancialAccount(foreignAccountId)
        val description = "Foreign account ${UUID.randomUUID()}"

        val response =
            execute(
                token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                toolCallId = UUID.randomUUID(),
                query =
                    """
                    mutation McpCreateTransaction(${'$'}input: CreateTransactionInput!, ${'$'}idempotencyKey: String) {
                      createTransaction(input: ${'$'}input, idempotencyKey: ${'$'}idempotencyKey) {
                        transaction { id }
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "input" to
                            baseCreateTransactionInput(description) +
                            ("financialAccountId" to foreignFinancialAccountId.toString()),
                        "idempotencyKey" to "foreign-key",
                    ),
            )

        assertThat(response["errors"]).isNotNull()
        assertThat(countTransactionsByDescription(description)).isZero()
        assertThat(countTransactionsForAccount(foreignAccountId, description)).isZero()
    }

    private fun createTransaction(
        token: String,
        description: String,
        idempotencyKey: String,
        toolCallId: UUID,
    ): Map<*, *> =
        execute(
            token = token,
            toolCallId = toolCallId,
            query =
                """
                mutation McpCreateTransaction(${'$'}input: CreateTransactionInput!, ${'$'}idempotencyKey: String) {
                  createTransaction(input: ${'$'}input, idempotencyKey: ${'$'}idempotencyKey) {
                    transaction {
                      id
                      updatedAt
                    }
                  }
                }
                """.trimIndent(),
            variables =
                mapOf(
                    "input" to baseCreateTransactionInput(description),
                    "idempotencyKey" to idempotencyKey,
                ),
        )

    private fun updateTransaction(
        token: String,
        transactionId: UUID,
        description: String,
        idempotencyKey: String,
        toolCallId: UUID,
    ): Map<*, *> =
        execute(
            token = token,
            toolCallId = toolCallId,
            query =
                """
                mutation McpUpdateTransaction(${'$'}id: ID!, ${'$'}input: UpdateTransactionInput!, ${'$'}idempotencyKey: String) {
                  updateTransaction(id: ${'$'}id, input: ${'$'}input, idempotencyKey: ${'$'}idempotencyKey) {
                    id
                    updatedAt
                    description
                  }
                }
                """.trimIndent(),
            variables =
                mapOf(
                    "id" to transactionId.toString(),
                    "input" to mapOf("description" to description),
                    "idempotencyKey" to idempotencyKey,
                ),
        )

    private fun createTransactionRule(
        token: String,
        pattern: String,
        idempotencyKey: String,
        toolCallId: UUID,
    ): Map<*, *> =
        execute(
            token = token,
            toolCallId = toolCallId,
            query =
                """
                mutation McpCreateTransactionRule(${'$'}input: CreateTransactionRuleInput!, ${'$'}idempotencyKey: String) {
                  createTransactionRule(input: ${'$'}input, idempotencyKey: ${'$'}idempotencyKey) {
                    id
                    pattern
                  }
                }
                """.trimIndent(),
            variables =
                mapOf(
                    "input" to
                        mapOf(
                            "pattern" to pattern,
                            "patternType" to "CONTAINS",
                            "applicableTo" to "EXPENSE",
                            "descriptionOverride" to "Rule override",
                        ),
                    "idempotencyKey" to idempotencyKey,
                ),
        )

    private fun bulkCategorize(
        token: String,
        transactionIds: List<UUID>,
        categoryId: UUID,
        idempotencyKey: String,
        toolCallId: UUID,
    ): Map<*, *> =
        execute(
            token = token,
            toolCallId = toolCallId,
            query =
                """
                mutation McpBulkCategorize(${'$'}transactionIds: [ID!]!, ${'$'}categoryId: ID!, ${'$'}idempotencyKey: String) {
                  bulkCategorize(transactionIds: ${'$'}transactionIds, categoryId: ${'$'}categoryId, idempotencyKey: ${'$'}idempotencyKey) {
                    affectedCount
                    errors
                    outcome
                  }
                }
                """.trimIndent(),
            variables =
                mapOf(
                    "transactionIds" to transactionIds.map(UUID::toString),
                    "categoryId" to categoryId.toString(),
                    "idempotencyKey" to idempotencyKey,
                ),
        )

    private fun applyTransactionRuleToHistorical(
        token: String,
        ruleId: UUID,
        idempotencyKey: String,
        toolCallId: UUID,
    ): Map<*, *> =
        execute(
            token = token,
            toolCallId = toolCallId,
            query =
                """
                mutation McpApplyTransactionRuleToHistorical(${'$'}id: ID!, ${'$'}idempotencyKey: String) {
                  applyTransactionRuleToHistorical(id: ${'$'}id, idempotencyKey: ${'$'}idempotencyKey) {
                    affectedCount
                    errors
                    outcome
                  }
                }
                """.trimIndent(),
            variables =
                mapOf(
                    "id" to ruleId.toString(),
                    "idempotencyKey" to idempotencyKey,
                ),
        )

    private fun execute(
        token: String,
        query: String,
        variables: Map<String, Any>,
        toolCallId: UUID? = null,
    ): Map<*, *> {
        val request =
            HttpRequest
                .POST(
                    "/graphql",
                    mapOf(
                        "query" to query,
                        "variables" to variables,
                    ),
                ).header("Authorization", "Bearer $token")

        val withHeaders =
            if (toolCallId == null) {
                request
            } else {
                request
                    .header(AgentCallContext.TOOL_CALL_ID_HEADER, toolCallId.toString())
                    .header(AgentCallContext.CLIENT_ID_HEADER, "mcp-client")
                    .header(AgentCallContext.TOKEN_ID_HEADER, "origin-token-id")
            }

        return client.toBlocking().retrieve(withHeaders, Map::class.java)
    }

    private fun baseCreateTransactionInput(description: String): Map<String, Any> =
        mapOf(
            "financialAccountId" to financialAccountId.toString(),
            "creditDebitType" to "DEBIT",
            "type" to "OTHER_EXPENSE",
            "amount" to 10.0,
            "date" to "2026-07-25",
            "description" to description,
        )

    private fun Map<*, *>.createTransactionId(): String =
        requireGraphQlField("createTransaction").let { payload ->
            (payload["transaction"] as Map<*, *>)["id"].toString()
        }

    private fun Map<*, *>.updateTransactionId(): String = requireGraphQlField("updateTransaction")["id"].toString()

    private fun Map<*, *>.updatedAt(): String = requireGraphQlField("updateTransaction")["updatedAt"].toString()

    private fun Map<*, *>.ruleId(): String = requireGraphQlField("createTransactionRule")["id"].toString()

    private fun Map<*, *>.bulkOperationAffectedCount(): Int =
        (requireGraphQlBulkOperationField()["affectedCount"] as Number).toInt()

    private fun Map<*, *>.bulkOperationErrors(): List<*> = requireGraphQlBulkOperationField()["errors"] as List<*>

    private fun Map<*, *>.bulkOperationOutcome(): String = requireGraphQlBulkOperationField()["outcome"].toString()

    private fun Map<*, *>.requireGraphQlBulkOperationField(): Map<*, *> {
        val data = this["data"] as? Map<*, *>
        val bulkCategorizePayload = data?.get("bulkCategorize") as? Map<*, *>
        if (bulkCategorizePayload != null) {
            return bulkCategorizePayload
        }
        val applyRulePayload = data?.get("applyTransactionRuleToHistorical") as? Map<*, *>
        require(applyRulePayload != null) {
            "Bulk operation payload missing. errors=${this["errors"]} data=$data"
        }
        return applyRulePayload
    }

    private fun Map<*, *>.requireGraphQlField(field: String): Map<*, *> {
        val errors = this["errors"]
        val data = this["data"] as? Map<*, *>
        val payload = data?.get(field) as? Map<*, *>
        require(payload != null) {
            "GraphQL field '$field' missing. errors=$errors data=$data"
        }
        return payload
    }

    private fun seedFinancialAccount(accountId: UUID): UUID {
        val account = UUID.randomUUID()
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (
                    id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at
                )
                VALUES (
                    '$account',
                    '$accountId',
                    '${BankingTestTokenPrincipalDecoder.TEST_USER_ID}',
                    'BANK',
                    'CONTA_CORRENTE',
                    'Foreign MCP Write GraphQL IT',
                    'BRL',
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )
        }
        return account
    }

    private fun countTransactionsByDescription(description: String): Int =
        countTransactionsForAccount(BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID, description)

    private fun countTransactionsForAccount(
        accountId: UUID,
        description: String,
    ): Int {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT COUNT(*)
                FROM banking.transactions
                WHERE account_id = ? AND description = ? AND deleted_at IS NULL
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, accountId)
                stmt.setString(2, description)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    return rs.getInt(1)
                }
            }
        }
    }

    private fun insertCategory(
        id: UUID,
        accountId: UUID,
        name: String,
    ) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.transaction_categories (
                    id, account_id, name, applicable_to, is_system, sort_order, created_at, updated_at
                ) VALUES (
                    '$id', '$accountId', '$name', 'EXPENSE', false, 0, NOW(), NOW()
                )
                """.trimIndent(),
            )
        }
    }

    private fun insertTransaction(
        id: UUID,
        accountId: UUID,
        financialAccountId: UUID,
        description: String,
        source: String = "MANUAL",
    ) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO banking.transactions (
                    id, financial_account_id, account_id, created_by_user_id, belongs_to_user_id,
                    credit_debit_type, type, status, source, amount, currency_code, date, description,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'DEBIT', 'OTHER_EXPENSE', 'POSTED', ?, ?, 'BRL', ?, ?, NOW(), NOW())
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, financialAccountId)
                stmt.setObject(3, accountId)
                stmt.setObject(4, BankingTestTokenPrincipalDecoder.TEST_USER_ID)
                stmt.setObject(5, BankingTestTokenPrincipalDecoder.TEST_USER_ID)
                stmt.setString(6, source)
                stmt.setBigDecimal(7, java.math.BigDecimal("10.00"))
                stmt.setObject(8, java.time.LocalDate.of(2026, 7, 25))
                stmt.setString(9, description)
                stmt.executeUpdate()
            }
        }
    }

    private fun insertTransactionRule(
        ruleId: UUID,
        categoryId: UUID,
        pattern: String,
    ) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.transaction_rules (
                    id, account_id, category_id, merchant_id, type_override, pattern, pattern_type,
                    applicable_to, priority, is_system, is_implicit, description_override, tags_override_json,
                    match_count, created_at, updated_at
                ) VALUES (
                    '$ruleId',
                    '${BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID}',
                    '$categoryId',
                    NULL,
                    NULL,
                    '$pattern',
                    'CONTAINS',
                    'EXPENSE',
                    0,
                    false,
                    false,
                    NULL,
                    NULL,
                    0,
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )
        }
    }

    private fun loadTransactionCategoryIds(transactionIds: List<UUID>): List<UUID?> {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT category_id
                FROM banking.transactions
                WHERE id = ?
                """.trimIndent(),
            ).use { stmt ->
                return transactionIds.map { transactionId ->
                    stmt.setObject(1, transactionId)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        rs.getObject("category_id", UUID::class.java)
                    }
                }
            }
        }
    }

    private fun countChangeLogEntriesByToolCallId(toolCallId: UUID): Int {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT COUNT(*)
                FROM banking.mcp_change_log
                WHERE tool_call_id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, toolCallId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    return rs.getInt(1)
                }
            }
        }
    }

    private fun loadStoredOrigin(description: String): StoredOrigin {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT origin_channel, origin_client_id, origin_tool_call_id
                FROM banking.transactions
                WHERE account_id = ? AND description = ? AND deleted_at IS NULL
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID)
                stmt.setString(2, description)
                stmt.executeQuery().use { rs ->
                    require(rs.next()) { "Transaction not found for description=$description" }
                    return StoredOrigin(
                        originChannel = rs.getString("origin_channel"),
                        originClientId = rs.getString("origin_client_id"),
                        originToolCallId = rs.getObject("origin_tool_call_id", UUID::class.java),
                    )
                }
            }
        }
    }

    private data class StoredOrigin(
        val originChannel: String,
        val originClientId: String?,
        val originToolCallId: UUID?,
    )

    private fun countRulesByPattern(pattern: String): Int {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT COUNT(*)
                FROM banking.transaction_rules
                WHERE account_id = ? AND pattern = ? AND deleted_at IS NULL
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID)
                stmt.setString(2, pattern)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    return rs.getInt(1)
                }
            }
        }
    }

    private fun loadTransactionDescription(transactionId: UUID): String {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT description
                FROM banking.transactions
                WHERE id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, transactionId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    return rs.getString("description")
                }
            }
        }
    }
}
