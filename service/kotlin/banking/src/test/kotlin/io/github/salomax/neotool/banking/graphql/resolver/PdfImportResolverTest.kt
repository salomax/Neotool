package io.github.salomax.neotool.banking.graphql.resolver

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJob
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJobStatus
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportService
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Base64
import java.util.UUID

@DisplayName("PdfImportResolver")
class PdfImportResolverTest {

    private lateinit var pdfImportService: PdfImportService
    private lateinit var resolver: PdfImportResolver

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val jobId = UUID.randomUUID()
    private val now = Instant.now()

    private fun principal() = RequestPrincipal(
        principalType = PrincipalType.USER,
        userId = userId,
        serviceId = null,
        token = "test-token",
        permissionsFromToken = emptyList(),
        currentAccountId = accountId,
    )

    @BeforeEach
    fun setUp() {
        pdfImportService = mock()
        resolver = PdfImportResolver(pdfImportService, ObjectMapper())
    }

    @Test
    fun `submitPdfBillImport decodes base64 and returns job dto`() {
        val bytes = "%PDF-1.7 sample".toByteArray()
        val job = PdfImportJob(
            id = jobId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            status = PdfImportJobStatus.PENDING,
            assetId = UUID.randomUUID(),
            fileName = "fatura.pdf",
            fileHash = "hash",
            createdAt = now,
            updatedAt = now,
        )
        whenever(
            pdfImportService.submitPdfBillImport(
                eq(accountId),
                eq(userId),
                eq(financialAccountId),
                eq("fatura.pdf"),
                any(),
            ),
        ).thenReturn(job)

        val result = resolver.submitPdfBillImport(
            input = mapOf(
                "financialAccountId" to financialAccountId.toString(),
                "fileName" to "fatura.pdf",
                "fileBase64" to Base64.getEncoder().encodeToString(bytes),
            ),
            principal = principal(),
        )

        assertThat(result.id).isEqualTo(jobId)
        assertThat(result.status).isEqualTo(PdfImportJobStatus.PENDING)
        verify(pdfImportService).submitPdfBillImport(
            eq(accountId),
            eq(userId),
            eq(financialAccountId),
            eq("fatura.pdf"),
            any(),
        )
    }

    @Test
    fun `submitPdfBillImport throws validation error for invalid base64`() {
        val input = mapOf(
            "fileName" to "fatura.pdf",
            "fileBase64" to "%%%invalid%%%",
        )

        assertThrows<ValidationException> {
            resolver.submitPdfBillImport(input, principal())
        }
    }

    @Test
    fun `pdfImportJob returns reviewPayload only for awaiting review status`() {
        val awaiting = PdfImportJob(
            id = jobId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            status = PdfImportJobStatus.AWAITING_REVIEW,
            assetId = UUID.randomUUID(),
            fileName = "fatura.pdf",
            fileHash = "hash",
            reviewPayload = """{"cards":[{"last_four_digits":"1234"}]}""",
            createdAt = now,
            updatedAt = now,
        )
        whenever(pdfImportService.getJob(eq(jobId), eq(accountId))).thenReturn(awaiting)

        val result = resolver.pdfImportJob(jobId.toString(), principal())

        assertThat(result).isNotNull
        assertThat(result!!.reviewPayload).isNotNull
    }
}

