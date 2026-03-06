package io.github.salomax.neotool.banking.infra.kafka

import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.integration.BankingPostgresIntegrationTest
import io.github.salomax.neotool.common.test.integration.KafkaIntegrationTest
import io.micronaut.context.annotation.Replaces
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Integration test for [ImportFileConsumer]: Postgres + Kafka + MockWebServer (assets service).
 * Publishes [ImportFileRequestedEvent], waits for a terminal job status, and asserts asset delete call.
 */
@MicronautTest(startApplication = true)
@DisplayName("ImportFileConsumer IT")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class ImportFileConsumerIT : BankingPostgresIntegrationTest(), KafkaIntegrationTest {
    companion object {
        private val assetsMock = MockWebServer()

        init {
            assetsMock.start()
            Runtime.getRuntime().addShutdownHook(Thread { assetsMock.shutdown() })
        }

        private fun assetsBaseUrl() = assetsMock.url("/").toString().removeSuffix("/")
    }

    @Singleton
    @Replaces(ImportJobCompletedProducerKafkaClient::class)
    class NoOpImportJobCompletedProducerKafkaClient : ImportJobCompletedProducerKafkaClient {
        override fun send(
            jobId: String,
            event: ImportJobCompletedEvent,
        ) {
            // no-op for this IT; publish side effects are tested separately in producer tests
        }
    }

    @Inject
    lateinit var importFileProducer: ImportFileProducer

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties().toMutableMap()
        val bootstrapServers = getKafkaContainer().bootstrapServers
        props["kafka.enabled"] = "true"
        props["kafka.bootstrap.servers"] = bootstrapServers
        props["kafka.producers.default.bootstrap.servers"] = bootstrapServers
        props["kafka.consumers.import-file.bootstrap.servers"] = bootstrapServers
        props["graphql.router.url"] = "${assetsBaseUrl()}/graphql"
        props["banking.assets-service.url"] = assetsBaseUrl()
        return props
    }

    @BeforeEach
    fun insertTestData() {
        ensureKafkaTopics()
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            userId = UUID.randomUUID()
            accountId = UUID.randomUUID()
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'IT Consumer', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    @AfterEach
    fun resetMock() {
        while (assetsMock.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // drain any leftover requests without blocking
        }
    }

    @Test
    fun `consumer processes event reads asset completes job and deletes asset`() {
        val assetId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId)
        val uniqueFitId = "it-consumer-fitid-$assetId"
        val uniqueDate = "20991231"
        val uniqueAmount = "-987654.32"

        val downloadPath = "/download-$assetId"
        val downloadUrl = "${assetsBaseUrl()}$downloadPath"
        val minimalOfx =
            """
            OFXHEADER:100
            DATA:OFXSGML
            <OFX>
            <BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
            <STMTTRN>
            <DTPOSTED>$uniqueDate
            <TRNAMT>$uniqueAmount
            <FITID>$uniqueFitId
            <NAME>IT Test
            </STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1>
            </OFX>
            """.trimIndent()

        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"asset":{"id":"$assetId","downloadUrl":"$downloadUrl"}}}"""),
        )
        assetsMock.enqueue(
            MockResponse().setResponseCode(200).setBody(minimalOfx),
        )
        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"deleteAsset":true}}"""),
        )

        importFileProducer.publish(
            ImportFileRequestedEvent(
                jobId = jobId,
                assetId = assetId,
                source = ImportSource.OFX,
                financialAccountId = financialAccountId,
                accountId = accountId,
            ),
        )

        val status = awaitTerminalStatus(jobId, timeoutMs = 25_000L)
        assertThat(status).isNotNull
        assertThat(status)
            .isIn(ImportJobStatus.COMPLETED, ImportJobStatus.REVIEW_REQUIRED)

        assetsMock.takeRequest(5, TimeUnit.SECONDS) // asset query
        assetsMock.takeRequest(5, TimeUnit.SECONDS) // GET download
        val deleteRequest = assetsMock.takeRequest(5, TimeUnit.SECONDS)
        assertThat(deleteRequest).isNotNull
        val nonNullDeleteRequest = requireNotNull(deleteRequest)
        assertThat(nonNullDeleteRequest.path).isEqualTo("/graphql")
        assertThat(nonNullDeleteRequest.body.readUtf8()).contains("deleteAsset")
    }

    @Test
    fun `consumer completes empty csv as COMPLETED and persists detected source`() {
        val assetId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId, source = ImportSource.CSV_AUTO, fileName = "extrato.csv")

        val downloadPath = "/download-$assetId"
        val downloadUrl = "${assetsBaseUrl()}$downloadPath"
        val headerOnlyCsv = "Data;Hora;Descricao;Valor;Saldo\n"

        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"asset":{"id":"$assetId","downloadUrl":"$downloadUrl"}}}"""),
        )
        assetsMock.enqueue(
            MockResponse().setResponseCode(200).setBody(headerOnlyCsv),
        )
        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"deleteAsset":true}}"""),
        )

        importFileProducer.publish(
            ImportFileRequestedEvent(
                jobId = jobId,
                assetId = assetId,
                source = ImportSource.CSV_AUTO,
                financialAccountId = financialAccountId,
                accountId = accountId,
            ),
        )

        val status = awaitTerminalStatus(jobId, timeoutMs = 25_000L)
        assertThat(status).isEqualTo(ImportJobStatus.COMPLETED)

        val snapshot = loadJobSnapshot(jobId)
        assertThat(snapshot).isNotNull
        assertThat(snapshot!!.status).isEqualTo(ImportJobStatus.COMPLETED)
        assertThat(snapshot.source).isEqualTo(ImportSource.CSV_SANTANDER)
        assertThat(snapshot.totalRows).isEqualTo(0)
        assertThat(snapshot.importedCount).isEqualTo(0)
        assertThat(snapshot.skippedCount).isEqualTo(0)
        assertThat(snapshot.errorCount).isEqualTo(0)
    }

    private fun awaitTerminalStatus(
        jobId: UUID,
        timeoutMs: Long = 15_000L,
        pollMs: Long = 200L,
    ): ImportJobStatus? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastSeen: ImportJobStatus? = null
        while (System.currentTimeMillis() < deadline) {
            val status = loadJobStatus(jobId)
            if (status != null) {
                lastSeen = status
                if (status != ImportJobStatus.PENDING && status != ImportJobStatus.PROCESSING) {
                    return status
                }
            }
            Thread.sleep(pollMs)
        }
        return lastSeen
    }

    private fun loadJobStatus(jobId: UUID): ImportJobStatus? {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT status
                FROM banking.transaction_imports
                WHERE id = ? AND account_id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, jobId)
                stmt.setObject(2, accountId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return null
                    }
                    return ImportJobStatus.valueOf(rs.getString("status"))
                }
            }
        }
    }

    private data class JobSnapshot(
        val status: ImportJobStatus,
        val source: ImportSource,
        val totalRows: Int?,
        val importedCount: Int?,
        val skippedCount: Int?,
        val errorCount: Int?,
    )

    private fun loadJobSnapshot(jobId: UUID): JobSnapshot? {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT status, source, total_rows, imported_count, skipped_count, error_count
                FROM banking.transaction_imports
                WHERE id = ? AND account_id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, jobId)
                stmt.setObject(2, accountId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return null
                    }
                    return JobSnapshot(
                        status = ImportJobStatus.valueOf(rs.getString("status")),
                        source = ImportSource.valueOf(rs.getString("source")),
                        totalRows = rs.getObject("total_rows") as? Int,
                        importedCount = rs.getObject("imported_count") as? Int,
                        skippedCount = rs.getObject("skipped_count") as? Int,
                        errorCount = rs.getObject("error_count") as? Int,
                    )
                }
            }
        }
    }

    private fun insertPendingImportJob(
        jobId: UUID,
        assetId: UUID,
        source: ImportSource = ImportSource.OFX,
        fileName: String = "export.ofx",
    ) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.transaction_imports (
                    id, financial_account_id, account_id, created_by_user_id,
                    source, status, file_hash, file_name, created_at, updated_at
                )
                VALUES (
                    '$jobId', '$financialAccountId', '$accountId', '$userId',
                    '${source.name}', 'PENDING', 'it-$assetId', '$fileName', NOW(), NOW()
                )
                """.trimIndent(),
            )
        }
    }

    private fun ensureKafkaTopics() {
        val props = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to getKafkaContainer().bootstrapServers)
        AdminClient.create(props).use { admin ->
            val topics =
                listOf(
                    NewTopic("banking.import.file-requested", 1, 1),
                    NewTopic("banking.import.job-completed", 1, 1),
                )
            try {
                admin.createTopics(topics).all().get()
            } catch (e: Exception) {
                if (!hasTopicExistsCause(e)) {
                    throw e
                }
            }
        }
    }

    private fun hasTopicExistsCause(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            if (current is TopicExistsException) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
