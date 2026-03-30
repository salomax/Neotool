package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.repo.import.ImportJobRepository
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("TransactionImportRepository IT")
class TransactionImportRepositoryIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var importJobRepository: ImportJobRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID

    @BeforeEach
    fun insertTestData() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            userId = UUID.randomUUID()
            accountId = UUID.randomUUID()
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'IT Import', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    private fun baseJob(
        fileHash: String = "sha256-${UUID.randomUUID()}",
        status: ImportJobStatus = ImportJobStatus.PENDING,
    ): ImportJob {
        val now = Instant.now()
        return ImportJob(
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            source = ImportSource.OFX,
            fileName = "export.ofx",
            fileHash = fileHash,
            status = status,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Nested
    @DisplayName("save")
    inner class SaveTests {
        @Test
        fun `persists job and returns with id and timestamps`() {
            val job = baseJob()

            val saved = importJobRepository.save(job)

            assertThat(saved.id).isNotNull()
            assertThat(saved.financialAccountId).isEqualTo(financialAccountId)
            assertThat(saved.accountId).isEqualTo(accountId)
            assertThat(saved.fileHash).isEqualTo(job.fileHash)
            assertThat(saved.status).isEqualTo(ImportJobStatus.PENDING)
            assertThat(saved.source).isEqualTo(ImportSource.OFX)
            assertThat(saved.createdAt).isNotNull()
            assertThat(saved.updatedAt).isNotNull()
        }

        @Test
        fun `persists optional fields totalRows importedCount dateFrom dateTo`() {
            val job =
                baseJob().copy(
                    totalRows = 10,
                    importedCount = 8,
                    skippedCount = 1,
                    errorCount = 1,
                    dateFrom = LocalDate.of(2024, 1, 1),
                    dateTo = LocalDate.of(2024, 1, 31),
                )

            val saved = importJobRepository.save(job)

            assertThat(saved.id).isNotNull()
            assertThat(saved.totalRows).isEqualTo(10)
            assertThat(saved.importedCount).isEqualTo(8)
            assertThat(saved.skippedCount).isEqualTo(1)
            assertThat(saved.errorCount).isEqualTo(1)
            assertThat(saved.dateFrom).isEqualTo(LocalDate.of(2024, 1, 1))
            assertThat(saved.dateTo).isEqualTo(LocalDate.of(2024, 1, 31))
        }
    }

    @Nested
    @DisplayName("findById")
    inner class FindByIdTests {
        @Test
        fun `returns job when id and accountId match`() {
            val saved = importJobRepository.save(baseJob())

            val found = importJobRepository.findById(saved.id!!, accountId)

            assertThat(found).isNotNull()
            assertThat(found!!.id).isEqualTo(saved.id)
            assertThat(found.fileHash).isEqualTo(saved.fileHash)
        }

        @Test
        fun `returns null when accountId does not match tenant isolation`() {
            val saved = importJobRepository.save(baseJob())
            val otherAccountId = UUID.randomUUID()

            val found = importJobRepository.findById(saved.id!!, otherAccountId)

            assertThat(found).isNull()
        }
    }

    @Nested
    @DisplayName("findByFileHash")
    inner class FindByFileHashTests {
        @Test
        fun `returns job when financialAccountId and fileHash match`() {
            val fileHash = "hash-${UUID.randomUUID()}"
            val saved = importJobRepository.save(baseJob(fileHash = fileHash))

            val found = importJobRepository.findByFileHash(financialAccountId, fileHash)

            assertThat(found).isNotNull()
            assertThat(found!!.id).isEqualTo(saved.id)
            assertThat(found.fileHash).isEqualTo(fileHash)
        }

        @Test
        fun `returns null when fileHash differs`() {
            importJobRepository.save(baseJob(fileHash = "hash-a"))

            val found = importJobRepository.findByFileHash(financialAccountId, "hash-b")

            assertThat(found).isNull()
        }

        @Test
        fun `returns null when financialAccountId differs`() {
            val fileHash = "hash-${UUID.randomUUID()}"
            importJobRepository.save(baseJob(fileHash = fileHash))
            val otherFinancialAccountId = UUID.randomUUID()

            val found = importJobRepository.findByFileHash(otherFinancialAccountId, fileHash)

            assertThat(found).isNull()
        }
    }

    @Nested
    @DisplayName("findByFinancialAccount")
    inner class FindByFinancialAccountTests {
        @Test
        fun `returns jobs for financial account and account ordered by created_at desc`() {
            val j1 = importJobRepository.save(baseJob(fileHash = "fa-1"))
            val j2 = importJobRepository.save(baseJob(fileHash = "fa-2"))

            val list = importJobRepository.findByFinancialAccount(financialAccountId, accountId)

            assertThat(list).hasSize(2)
            assertThat(list[0].id).isEqualTo(j2.id)
            assertThat(list[1].id).isEqualTo(j1.id)
        }

        @Test
        fun `returns empty when accountId does not match`() {
            importJobRepository.save(baseJob())
            val otherAccountId = UUID.randomUUID()

            val list = importJobRepository.findByFinancialAccount(financialAccountId, otherAccountId)

            assertThat(list).isEmpty()
        }
    }

    @Nested
    @DisplayName("findByAccount")
    inner class FindByAccountTests {
        @Test
        fun `returns all jobs for account ordered by created_at desc`() {
            val j1 = importJobRepository.save(baseJob(fileHash = "acc-1"))
            val j2 = importJobRepository.save(baseJob(fileHash = "acc-2"))

            val list = importJobRepository.findByAccount(accountId)

            assertThat(list).hasSize(2)
            assertThat(list[0].id).isEqualTo(j2.id)
            assertThat(list[1].id).isEqualTo(j1.id)
        }

        @Test
        fun `returns empty for unknown accountId`() {
            importJobRepository.save(baseJob())

            val list = importJobRepository.findByAccount(UUID.randomUUID())

            assertThat(list).isEmpty()
        }
    }

    @Nested
    @DisplayName("update")
    inner class UpdateTests {
        @Test
        fun `updates status and counts`() {
            val saved = importJobRepository.save(baseJob(status = ImportJobStatus.PENDING))

            val updated =
                importJobRepository.update(
                    saved.copy(
                        status = ImportJobStatus.COMPLETED,
                        totalRows = 5,
                        importedCount = 5,
                        completedAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )

            assertThat(updated.id).isEqualTo(saved.id)
            assertThat(updated.status).isEqualTo(ImportJobStatus.COMPLETED)
            assertThat(updated.totalRows).isEqualTo(5)
            assertThat(updated.importedCount).isEqualTo(5)
            assertThat(updated.completedAt).isNotNull()
        }

        @Test
        fun `findById returns updated data after update`() {
            val saved = importJobRepository.save(baseJob())
            importJobRepository.update(
                saved.copy(
                    status = ImportJobStatus.FAILED,
                    errorCount = 1,
                    errorDetails = """{"message":"Parse error"}""",
                    updatedAt = Instant.now(),
                ),
            )

            val found = importJobRepository.findById(saved.id!!, accountId)

            assertThat(found).isNotNull()
            assertThat(found!!.status).isEqualTo(ImportJobStatus.FAILED)
            assertThat(found.errorCount).isEqualTo(1)
            assertThat(found.errorDetails).contains("Parse error")
        }
    }

    @Nested
    @DisplayName("markAssistantRequestPublished")
    inner class MarkAssistantRequestPublishedTests {
        @Test
        fun `marks assistant request once and returns false on second call`() {
            val saved = importJobRepository.save(baseJob(status = ImportJobStatus.PROCESSING))
            val jobId = requireNotNull(saved.id)

            val first = importJobRepository.markAssistantRequestPublished(jobId)
            val second = importJobRepository.markAssistantRequestPublished(jobId)

            assertThat(first).isTrue()
            assertThat(second).isFalse()
        }
    }
}
