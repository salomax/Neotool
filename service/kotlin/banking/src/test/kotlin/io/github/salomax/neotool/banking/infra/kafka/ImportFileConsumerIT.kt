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
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Integration test for [ImportFileConsumer]: Postgres + Kafka + MockWebServer (assets service).
 * Publishes [ImportFileRequestedEvent], waits for a terminal job status, and asserts asset delete call.
 */
@MicronautTest(startApplication = true, transactional = false)
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

    @Singleton
    @Replaces(AssistantBankingReaderRequestedProducerKafkaClient::class)
    class ProbeAssistantBankingReaderRequestedProducerKafkaClient : AssistantBankingReaderRequestedProducerKafkaClient {
        override fun send(
            jobId: String,
            event: AssistantBankingReaderRequestedEvent,
        ) {
            published.add(jobId to event)
        }

        companion object {
            val published = CopyOnWriteArrayList<Pair<String, AssistantBankingReaderRequestedEvent>>()
        }
    }

    @Singleton
    @Replaces(PdfExtractorSubmittedProducerKafkaClient::class)
    class ProbePdfExtractorSubmittedProducerKafkaClient : PdfExtractorSubmittedProducerKafkaClient {
        override fun send(
            jobId: String,
            event: PdfExtractorSubmittedEvent,
        ) {
            published.add(jobId to event)
        }

        companion object {
            val published = CopyOnWriteArrayList<Pair<String, PdfExtractorSubmittedEvent>>()
        }
    }

    @Inject
    lateinit var importFileProducer: ImportFileProducer

    @Inject
    lateinit var importReaderStatusConsumer: ImportReaderStatusConsumer

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private lateinit var creditCardFinancialAccountId: UUID

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
        ProbeAssistantBankingReaderRequestedProducerKafkaClient.published.clear()
        ProbePdfExtractorSubmittedProducerKafkaClient.published.clear()
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            userId = UUID.randomUUID()
            accountId = UUID.randomUUID()
            financialAccountId = UUID.randomUUID()
            creditCardFinancialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'IT Consumer', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$creditCardFinancialAccountId', '$accountId', '$userId', 'CREDIT', 'CREDIT_CARD', 'IT Card Consumer', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    @AfterEach
    fun resetMock() {
        ProbeAssistantBankingReaderRequestedProducerKafkaClient.published.clear()
        ProbePdfExtractorSubmittedProducerKafkaClient.published.clear()
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
    fun `ofx bank statement is accepted for bank destination account`() {
        val assetId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId, destinationFinancialAccountId = financialAccountId)
        enqueueAssetResponses(assetId, minimalBankStatementOfx("bank-ok-$assetId"))

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
        assertThat(status).isEqualTo(ImportJobStatus.COMPLETED)
        assertThat(countTransactionsForJob(jobId)).isEqualTo(1)
    }

    @Test
    fun `ofx bank statement is rejected for credit card destination account`() {
        val assetId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId, destinationFinancialAccountId = creditCardFinancialAccountId)
        enqueueAssetResponses(assetId, minimalBankStatementOfx("bank-mismatch-$assetId"))

        importFileProducer.publish(
            ImportFileRequestedEvent(
                jobId = jobId,
                assetId = assetId,
                source = ImportSource.OFX,
                financialAccountId = creditCardFinancialAccountId,
                accountId = accountId,
            ),
        )

        val status = awaitTerminalStatus(jobId, timeoutMs = 25_000L)
        assertThat(status).isEqualTo(ImportJobStatus.FAILED)
        assertThat(countTransactionsForJob(jobId)).isEqualTo(0)
        val snapshot = loadJobSnapshot(jobId)
        assertThat(snapshot).isNotNull
        assertThat(snapshot!!.errorDetails).contains("IMPORT_OFX_DESTINATION_MISMATCH")
    }

    @Test
    fun `ofx credit card statement is accepted for credit card destination account`() {
        val assetId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId, destinationFinancialAccountId = creditCardFinancialAccountId)
        enqueueAssetResponses(assetId, minimalCreditCardStatementOfx("card-ok-$assetId"))

        importFileProducer.publish(
            ImportFileRequestedEvent(
                jobId = jobId,
                assetId = assetId,
                source = ImportSource.OFX,
                financialAccountId = creditCardFinancialAccountId,
                accountId = accountId,
            ),
        )

        val status = awaitTerminalStatus(jobId, timeoutMs = 25_000L)
        assertThat(status).isEqualTo(ImportJobStatus.COMPLETED)
        assertThat(countTransactionsForJob(jobId)).isEqualTo(1)
    }

    @Test
    fun `ofx credit card statement is rejected for bank destination account`() {
        val assetId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId, destinationFinancialAccountId = financialAccountId)
        enqueueAssetResponses(assetId, minimalCreditCardStatementOfx("card-mismatch-$assetId"))

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
        assertThat(status).isEqualTo(ImportJobStatus.FAILED)
        assertThat(countTransactionsForJob(jobId)).isEqualTo(0)
        val snapshot = loadJobSnapshot(jobId)
        assertThat(snapshot).isNotNull
        assertThat(snapshot!!.errorDetails).contains("IMPORT_OFX_DESTINATION_MISMATCH")
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

    @Test
    fun `consumer routes unknown excel to assistant request and keeps job in async processing state`() {
        val assetId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId, source = ImportSource.AUTO, fileName = "planilha.xlsx")

        val downloadPath = "/download-$assetId"
        val downloadUrl = "${assetsBaseUrl()}$downloadPath"
        val unknownWorkbookBytes = buildUnknownXlsxBytes()

        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"asset":{"id":"$assetId","downloadUrl":"$downloadUrl"}}}"""),
        )
        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .setBody(Buffer().write(unknownWorkbookBytes)),
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
                source = ImportSource.AUTO,
                financialAccountId = financialAccountId,
                accountId = accountId,
            ),
        )

        val status = awaitStatus(jobId, ImportJobStatus.PROCESSING, timeoutMs = 25_000L)
        assertThat(status).isEqualTo(ImportJobStatus.PROCESSING)

        val publishedEvent = awaitAssistantRequestedEvent(jobId, timeoutMs = 25_000L)
        assertThat(publishedEvent).isNotNull
        assertThat(publishedEvent!!.job_id).isEqualTo(jobId)
        assertThat(publishedEvent.document_type).isEqualTo("BANK_STATEMENT")
        assertThat(publishedEvent.file_type).isEqualTo("XLSX")
        assertThat(publishedEvent.account_type).isEqualTo("BANK")
        assertThat(publishedEvent.source_topic).isEqualTo("banking.import.file-requested")
        assertThat(publishedEvent.callback_context.job_id).isEqualTo(jobId)
        assertThat(publishedEvent.callback_context.account_id).isEqualTo(accountId)
        assertThat(publishedEvent.callback_context.user_id).isEqualTo(userId)
        assertThat(publishedEvent.extracted_text).contains("workbook_type=XLSX")
        assertThat(publishedEvent.extraction_metadata["workbook_type"]).isEqualTo("XLSX")

        assetsMock.takeRequest(5, TimeUnit.SECONDS) // asset query
        assetsMock.takeRequest(5, TimeUnit.SECONDS) // GET download
        val deleteRequest = assetsMock.takeRequest(5, TimeUnit.SECONDS)
        assertThat(deleteRequest).isNotNull
        assertThat(requireNotNull(deleteRequest).body.readUtf8()).contains("deleteAsset")
    }

    @Test
    fun `consumer routes pdf bank statement to extractor and keeps job processing`() {
        val assetId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId, source = ImportSource.AUTO, fileName = "extrato.pdf")

        val downloadPath = "/download-$assetId"
        val downloadUrl = "${assetsBaseUrl()}$downloadPath"
        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"asset":{"id":"$assetId","downloadUrl":"$downloadUrl"}}}"""),
        )

        importFileProducer.publish(
            ImportFileRequestedEvent(
                jobId = jobId,
                assetId = assetId,
                source = ImportSource.AUTO,
                financialAccountId = financialAccountId,
                accountId = accountId,
            ),
        )

        val status = awaitStatus(jobId, ImportJobStatus.PROCESSING, timeoutMs = 25_000L)
        assertThat(status).isEqualTo(ImportJobStatus.PROCESSING)
        assertThat(awaitAssistantRequestedEvent(jobId, timeoutMs = 2_000L)).isNull()

        val extractorEvent = awaitPdfExtractorSubmittedEvent(jobId, timeoutMs = 25_000L)
        assertThat(extractorEvent).isNotNull
        assertThat(extractorEvent!!.job_id).isEqualTo(jobId)
        assertThat(extractorEvent.asset_id).isEqualTo(assetId)
        assertThat(extractorEvent.document_type).isEqualTo("BANK_STATEMENT")
        assertThat(extractorEvent.download_url).isEqualTo(downloadUrl)
        assertThat(extractorEvent.callback_context.job_id).isEqualTo(jobId)
        assertThat(extractorEvent.callback_context.account_id).isEqualTo(accountId)
        assertThat(extractorEvent.callback_context.user_id).isEqualTo(userId)

        val assetQueryRequest = assetsMock.takeRequest(5, TimeUnit.SECONDS)
        assertThat(assetQueryRequest).isNotNull
        assertThat(requireNotNull(assetQueryRequest).body.readUtf8()).contains("asset(id:")
    }

    @Test
    fun `reprocessing same unknown excel event does not publish duplicate assistant request`() {
        val assetId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId, source = ImportSource.AUTO, fileName = "planilha.xlsx")

        val downloadPath = "/download-$assetId"
        val downloadUrl = "${assetsBaseUrl()}$downloadPath"
        val unknownWorkbookBytes = buildUnknownXlsxBytes()

        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"asset":{"id":"$assetId","downloadUrl":"$downloadUrl"}}}"""),
        )
        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .setBody(Buffer().write(unknownWorkbookBytes)),
        )
        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"deleteAsset":true}}"""),
        )

        val replayEvent =
            ImportFileRequestedEvent(
                jobId = jobId,
                assetId = assetId,
                source = ImportSource.AUTO,
                financialAccountId = financialAccountId,
                accountId = accountId,
            )

        importFileProducer.publish(replayEvent)
        assertThat(awaitStatus(jobId, ImportJobStatus.PROCESSING, timeoutMs = 25_000L)).isEqualTo(ImportJobStatus.PROCESSING)
        assertThat(awaitAssistantRequestedEvent(jobId, timeoutMs = 25_000L)).isNotNull
        assertThat(awaitAssistantRequestedCount(jobId, 1, timeoutMs = 5_000L)).isEqualTo(1)
        assertThat(loadAssistantRequestPublishedAt(jobId)).isNotNull

        // Replay same event: marker should block duplicate assistant publish and avoid file fetch.
        importFileProducer.publish(replayEvent)
        Thread.sleep(1_500L)

        assertThat(loadJobStatus(jobId)).isEqualTo(ImportJobStatus.PROCESSING)
        assertThat(assistantRequestedCount(jobId)).isEqualTo(1)
    }

    @Test
    fun `assistant done event finalizes async excel import job`() {
        val assetId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId, source = ImportSource.AUTO, fileName = "planilha.xlsx")

        val downloadPath = "/download-$assetId"
        val downloadUrl = "${assetsBaseUrl()}$downloadPath"
        val unknownWorkbookBytes = buildUnknownXlsxBytes()

        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"asset":{"id":"$assetId","downloadUrl":"$downloadUrl"}}}"""),
        )
        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .setBody(Buffer().write(unknownWorkbookBytes)),
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
                source = ImportSource.AUTO,
                financialAccountId = financialAccountId,
                accountId = accountId,
            ),
        )

        val processing = awaitStatus(jobId, ImportJobStatus.PROCESSING, timeoutMs = 25_000L)
        assertThat(processing).isEqualTo(ImportJobStatus.PROCESSING)
        assertThat(awaitAssistantRequestedEvent(jobId, timeoutMs = 25_000L)).isNotNull

        importReaderStatusConsumer.onBankingReaderDone(
            mapOf(
                "job_id" to jobId.toString(),
                "document_type" to "BANK_STATEMENT",
                "llm_output" to
                    mapOf(
                        "cards" to
                            listOf(
                                mapOf(
                                    "transactions" to
                                        listOf(
                                            mapOf(
                                                "date" to "2026-02-03",
                                                "description" to "Salario Empresa XYZ",
                                                "amount" to 5600.00,
                                                "credit_debit_type" to "CREDIT",
                                            ),
                                            mapOf(
                                                "date" to "2026-02-04",
                                                "description" to "Mercado Central",
                                                "amount" to 120.45,
                                                "credit_debit_type" to "DEBIT",
                                            ),
                                        ),
                                ),
                            ),
                    ),
                "callback_context" to
                    mapOf(
                        "job_id" to jobId.toString(),
                        "account_id" to accountId.toString(),
                        "user_id" to userId.toString(),
                    ),
            ),
        )

        val status = awaitTerminalStatus(jobId, timeoutMs = 25_000L)
        assertThat(status).isEqualTo(ImportJobStatus.COMPLETED)

        val snapshot = loadJobSnapshot(jobId)
        assertThat(snapshot).isNotNull
        assertThat(snapshot!!.status).isEqualTo(ImportJobStatus.COMPLETED)
        assertThat(snapshot.totalRows).isEqualTo(2)
        assertThat(snapshot.importedCount).isEqualTo(2)
        assertThat(snapshot.errorCount).isEqualTo(0)
    }

    @Test
    fun `assistant failed event marks async excel import as FAILED`() {
        val jobId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        insertPendingImportJob(jobId, assetId, source = ImportSource.AUTO, fileName = "planilha.xlsx")
        moveJobToProcessing(jobId)

        importReaderStatusConsumer.onBankingReaderFailed(
            mapOf(
                "job_id" to jobId.toString(),
                "document_type" to "BANK_STATEMENT",
                "error" to
                    mapOf(
                        "code" to "SCHEMA_VALIDATION_FAILED",
                        "message" to "output did not match contract",
                        "retryable" to false,
                    ),
                "error_metadata" to mapOf("stage" to "OUTPUT_VALIDATION"),
                "callback_context" to
                    mapOf(
                        "job_id" to jobId.toString(),
                        "account_id" to accountId.toString(),
                        "user_id" to userId.toString(),
                    ),
            ),
        )

        val status = awaitTerminalStatus(jobId, timeoutMs = 10_000L)
        assertThat(status).isEqualTo(ImportJobStatus.FAILED)
        val snapshot = loadJobSnapshot(jobId)
        assertThat(snapshot!!.status).isEqualTo(ImportJobStatus.FAILED)
        assertThat(snapshot.errorCount).isEqualTo(1)
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

    private fun awaitStatus(
        jobId: UUID,
        expected: ImportJobStatus,
        timeoutMs: Long = 15_000L,
        pollMs: Long = 200L,
    ): ImportJobStatus? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastSeen: ImportJobStatus? = null
        while (System.currentTimeMillis() < deadline) {
            val status = loadJobStatus(jobId)
            if (status != null) {
                lastSeen = status
                if (status == expected) {
                    return status
                }
            }
            Thread.sleep(pollMs)
        }
        return lastSeen
    }

    private fun awaitAssistantRequestedEvent(
        jobId: UUID,
        timeoutMs: Long = 15_000L,
        pollMs: Long = 100L,
    ): AssistantBankingReaderRequestedEvent? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val match =
                ProbeAssistantBankingReaderRequestedProducerKafkaClient.published
                    .firstOrNull { (_, event) -> event.job_id == jobId }
            if (match != null) {
                return match.second
            }
            Thread.sleep(pollMs)
        }
        return null
    }

    private fun awaitAssistantRequestedCount(
        jobId: UUID,
        expected: Int,
        timeoutMs: Long = 15_000L,
        pollMs: Long = 100L,
    ): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        var count = 0
        while (System.currentTimeMillis() < deadline) {
            count = assistantRequestedCount(jobId)
            if (count == expected) {
                return count
            }
            Thread.sleep(pollMs)
        }
        return count
    }

    private fun awaitPdfExtractorSubmittedEvent(
        jobId: UUID,
        timeoutMs: Long = 15_000L,
        pollMs: Long = 100L,
    ): PdfExtractorSubmittedEvent? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val match =
                ProbePdfExtractorSubmittedProducerKafkaClient.published
                    .firstOrNull { (_, event) -> event.job_id == jobId }
            if (match != null) {
                return match.second
            }
            Thread.sleep(pollMs)
        }
        return null
    }

    private fun assistantRequestedCount(jobId: UUID): Int =
        ProbeAssistantBankingReaderRequestedProducerKafkaClient.published
            .count { (_, event) -> event.job_id == jobId }

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

    private fun loadAssistantRequestPublishedAt(jobId: UUID): String? {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT assistant_request_published_at
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
                    return rs.getString("assistant_request_published_at")
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
        val errorDetails: String?,
    )

    private fun loadJobSnapshot(jobId: UUID): JobSnapshot? {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT status, source, total_rows, imported_count, skipped_count, error_count, error_details
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
                        errorDetails = rs.getString("error_details"),
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
        destinationFinancialAccountId: UUID = financialAccountId,
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
                    '$jobId', '$destinationFinancialAccountId', '$accountId', '$userId',
                    '${source.name}', 'PENDING', 'it-$assetId', '$fileName', NOW(), NOW()
                )
                """.trimIndent(),
            )
        }
    }

    private fun countTransactionsForJob(jobId: UUID): Int {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT COUNT(*) AS total
                FROM banking.transactions
                WHERE import_job_id = ? AND account_id = ? AND deleted_at IS NULL
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, jobId)
                stmt.setObject(2, accountId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    return rs.getInt("total")
                }
            }
        }
    }

    private fun moveJobToProcessing(jobId: UUID) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                UPDATE banking.transaction_imports
                SET status = 'PROCESSING', updated_at = NOW()
                WHERE id = '$jobId' AND account_id = '$accountId'
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

    private fun enqueueAssetResponses(
        assetId: UUID,
        fileBody: String,
    ) {
        val downloadPath = "/download-$assetId"
        val downloadUrl = "${assetsBaseUrl()}$downloadPath"
        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"asset":{"id":"$assetId","downloadUrl":"$downloadUrl"}}}"""),
        )
        assetsMock.enqueue(
            MockResponse().setResponseCode(200).setBody(fileBody),
        )
        assetsMock.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"deleteAsset":true}}"""),
        )
    }

    private fun minimalBankStatementOfx(fitId: String): String =
        """
        OFXHEADER:100
        DATA:OFXSGML
        <OFX>
          <BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
            <STMTTRN>
              <DTPOSTED>20260301
              <TRNAMT>-98.10
              <FITID>$fitId
              <NAME>Padaria Central
            </STMTTRN>
          </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1>
        </OFX>
        """.trimIndent()

    private fun minimalCreditCardStatementOfx(fitId: String): String =
        """
        OFXHEADER:100
        DATA:OFXSGML
        <OFX>
          <CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><BANKTRANLIST>
            <STMTTRN>
              <DTPOSTED>20260302
              <TRNAMT>-45.90
              <FITID>$fitId
              <NAME>Streaming Service
            </STMTTRN>
          </BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1>
        </OFX>
        """.trimIndent()

    private fun buildUnknownXlsxBytes(): ByteArray {
        val workbook: Workbook = XSSFWorkbook()
        workbook.use { wb ->
            val sheet = wb.createSheet("Planilha")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("ColunaA")
                createCell(1).setCellValue("ColunaB")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("valor-a")
                createCell(1).setCellValue("valor-b")
            }
            val out = ByteArrayOutputStream()
            wb.write(out)
            return out.toByteArray()
        }
    }
}
