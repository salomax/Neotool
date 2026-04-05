package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.graphql.account.*
import io.github.salomax.neotool.banking.graphql.billimport.*
import io.github.salomax.neotool.banking.graphql.import.*
import io.github.salomax.neotool.banking.graphql.investment.*
import io.github.salomax.neotool.banking.graphql.issuer.*
import io.github.salomax.neotool.banking.graphql.merchant.*
import io.github.salomax.neotool.banking.graphql.transaction.*
import io.github.salomax.neotool.banking.graphql.config.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.billimport.ConfirmBillImportCommand
import io.github.salomax.neotool.banking.domain.billimport.BillImportJob
import io.github.salomax.neotool.banking.domain.billimport.BillImportJobStatus
import io.github.salomax.neotool.banking.domain.billimport.BillImportReviewMode
import io.github.salomax.neotool.banking.service.billimport.BillImportService
import io.github.salomax.neotool.banking.domain.billimport.ImportDocumentType
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

@DisplayName("BillImportResolver")
class BillImportResolverTest {
    private lateinit var billImportService: BillImportService
    private lateinit var resolver: BillImportResolver

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val jobId = UUID.randomUUID()
    private val now = Instant.now()

    private fun principal() =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    @BeforeEach
    fun setUp() {
        billImportService = mock()
        resolver = BillImportResolver(billImportService, ObjectMapper())
    }

    @Test
    fun `submitBillImport decodes base64 and returns job dto`() {
        val bytes = "%PDF-1.7 sample".toByteArray()
        val job =
            BillImportJob(
                id = jobId,
                importType = ImportDocumentType.CREDIT_CARD_BILL,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                status = BillImportJobStatus.PENDING,
                assetId = UUID.randomUUID(),
                fileName = "fatura.pdf",
                fileHash = "hash",
                createdAt = now,
                updatedAt = now,
            )
        whenever(
            billImportService.submitBillImport(
                eq(accountId),
                eq(userId),
                eq(ImportDocumentType.CREDIT_CARD_BILL),
                eq(financialAccountId),
                eq("fatura.pdf"),
                any(),
                eq(null),
            ),
        ).thenReturn(job)

        val result =
            resolver.submitBillImport(
                input =
                    mapOf(
                        "financialAccountId" to financialAccountId.toString(),
                        "importType" to "CREDIT_CARD_BILL",
                        "fileName" to "fatura.pdf",
                        "fileBase64" to Base64.getEncoder().encodeToString(bytes),
                    ),
                principal = principal(),
            )

        assertThat(result.id).isEqualTo(jobId)
        assertThat(result.status).isEqualTo(BillImportJobStatus.PENDING)
        verify(billImportService).submitBillImport(
            eq(accountId),
            eq(userId),
            eq(ImportDocumentType.CREDIT_CARD_BILL),
            eq(financialAccountId),
            eq("fatura.pdf"),
            any(),
            eq(null),
        )
    }

    @Test
    fun `submitBillImport forwards optional password`() {
        val bytes = "%PDF-1.7 encrypted".toByteArray()
        val password = "top-secret"
        val job =
            BillImportJob(
                id = jobId,
                importType = ImportDocumentType.CREDIT_CARD_BILL,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                status = BillImportJobStatus.PENDING,
                assetId = UUID.randomUUID(),
                fileName = "fatura.pdf",
                fileHash = "hash",
                createdAt = now,
                updatedAt = now,
            )
        whenever(
            billImportService.submitBillImport(
                eq(accountId),
                eq(userId),
                eq(ImportDocumentType.CREDIT_CARD_BILL),
                eq(financialAccountId),
                eq("fatura.pdf"),
                any(),
                eq(password),
            ),
        ).thenReturn(job)

        resolver.submitBillImport(
            input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "importType" to "CREDIT_CARD_BILL",
                    "fileName" to "fatura.pdf",
                    "fileBase64" to Base64.getEncoder().encodeToString(bytes),
                    "password" to password,
                ),
            principal = principal(),
        )

        verify(billImportService).submitBillImport(
            eq(accountId),
            eq(userId),
            eq(ImportDocumentType.CREDIT_CARD_BILL),
            eq(financialAccountId),
            eq("fatura.pdf"),
            any(),
            eq(password),
        )
    }

    @Test
    fun `submitBillImport throws validation error for invalid base64`() {
        val input =
            mapOf(
                "importType" to "CREDIT_CARD_BILL",
                "fileName" to "fatura.pdf",
                "fileBase64" to "%%%invalid%%%",
            )

        assertThrows<ValidationException> {
            resolver.submitBillImport(input, principal())
        }
    }

    @Test
    fun `billImportJob returns reviewPayload only for awaiting review status`() {
        val awaiting =
            BillImportJob(
                id = jobId,
                importType = ImportDocumentType.CREDIT_CARD_BILL,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                status = BillImportJobStatus.AWAITING_REVIEW,
                assetId = UUID.randomUUID(),
                fileName = "fatura.pdf",
                fileHash = "hash",
                reviewPayload = """{"cards":[{"last_four_digits":"1234"}]}""",
                createdAt = now,
                updatedAt = now,
            )
        whenever(billImportService.getJob(eq(jobId), eq(accountId))).thenReturn(awaiting)
        whenever(billImportService.projectReviewPayload(eq(awaiting.reviewPayload), eq(accountId), eq(userId))).thenReturn(
            io.github.salomax.neotool.banking.domain.billimport.BillImportReview(
                cards =
                    listOf(
                        io.github.salomax.neotool.banking.domain.billimport.BillImportReviewCard(
                            reviewCardId = "card-1",
                            lastFourDigits = "1234",
                        ),
                    ),
            ),
        )

        val result = resolver.billImportJob(jobId.toString(), principal())

        assertThat(result).isNotNull
        assertThat(result!!.reviewPayload).isNotNull
        assertThat(result.reviewPayload!!.reviewMode).isEqualTo(BillImportReviewMode.CARD_BUCKET_MATCHING)
    }

    @Test
    fun `billImportJob falls back to projected canonical payload when review payload is canonical`() {
        val awaiting =
            BillImportJob(
                id = jobId,
                importType = ImportDocumentType.CREDIT_CARD_BILL,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                status = BillImportJobStatus.AWAITING_REVIEW,
                assetId = UUID.randomUUID(),
                fileName = "fatura.pdf",
                fileHash = "hash",
                reviewPayload = """{"schemaId":"banking.reader-output.canonical","normalizedRecords":[]}""",
                createdAt = now,
                updatedAt = now,
            )
        whenever(billImportService.getJob(eq(jobId), eq(accountId))).thenReturn(awaiting)
        whenever(billImportService.projectReviewPayload(any(), eq(accountId), eq(userId))).thenReturn(
            io.github.salomax.neotool.banking.domain.billimport.BillImportReview(
                cards =
                    listOf(
                        io.github.salomax.neotool.banking.domain.billimport.BillImportReviewCard(reviewCardId = "card-1", lastFourDigits = "1234"),
                    ),
            ),
        )

        val result = resolver.billImportJob(jobId.toString(), principal())

        assertThat(result).isNotNull
        assertThat(result!!.reviewPayload).isNotNull
        assertThat(result.reviewPayload!!.reviewMode).isEqualTo(BillImportReviewMode.CARD_BUCKET_MATCHING)
        verify(billImportService).projectReviewPayload(awaiting.reviewPayload, accountId, userId)
    }

    @Test
    fun `confirmBillImport forwards command to service`() {
        val doneJob =
            BillImportJob(
                id = jobId,
                importType = ImportDocumentType.CREDIT_CARD_BILL,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                status = BillImportJobStatus.DONE,
                assetId = UUID.randomUUID(),
                fileName = "fatura.pdf",
                fileHash = "hash",
                completedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        whenever(billImportService.confirmBillImport(eq(accountId), eq(userId), any())).thenReturn(doneJob)

        val result =
            resolver.confirmBillImport(
                input =
                    mapOf(
                        "jobId" to jobId.toString(),
                        "closingDate" to "2026-02-07",
                        "dueDate" to "2026-02-15",
                        "cards" to
                            listOf(
                                mapOf(
                                    "reviewCardId" to "card-1",
                                    "financialAccountId" to financialAccountId.toString(),
                                    "transactions" to
                                        listOf(
                                            mapOf(
                                                "date" to LocalDate.now().toString(),
                                                "description" to "MERCADO",
                                                "amount" to BigDecimal("45.90"),
                                                "creditDebitType" to "DEBIT",
                                                "include" to true,
                                                "installment" to
                                                    mapOf(
                                                        "current" to 1,
                                                        "total" to 6,
                                                    ),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                principal = principal(),
            )

        assertThat(result.id).isEqualTo(jobId)
        assertThat(result.status).isEqualTo(BillImportJobStatus.DONE)
        val commandCaptor = argumentCaptor<ConfirmBillImportCommand>()
        verify(billImportService).confirmBillImport(eq(accountId), eq(userId), commandCaptor.capture())
        assertThat(commandCaptor.firstValue.closingDate).isEqualTo(LocalDate.of(2026, 2, 7))
        assertThat(commandCaptor.firstValue.dueDate).isEqualTo(LocalDate.of(2026, 2, 15))
        val transaction = commandCaptor.firstValue.cards.first().transactions.first()
        assertThat(transaction.installment).isNotNull
        assertThat(transaction.installment!!.current).isEqualTo(1)
        assertThat(transaction.installment!!.total).isEqualTo(6)
    }
}
