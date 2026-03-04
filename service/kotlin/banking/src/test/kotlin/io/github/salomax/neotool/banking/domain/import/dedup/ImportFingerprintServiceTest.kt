package io.github.salomax.neotool.banking.domain.import.dedup

import io.github.salomax.neotool.banking.repo.TransactionEntityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.UUID

@DisplayName("ImportFingerprintService")
class ImportFingerprintServiceTest {

    private lateinit var transactionEntityRepository: TransactionEntityRepository
    private lateinit var service: ImportFingerprintService

    private val financialAccountId = UUID.fromString("11111111-2222-7222-8222-333333333333")
    private val date = LocalDate.of(2025, 3, 15)
    private val amountCents = 9999L
    private val description = "Coffee shop"

    @BeforeEach
    fun setUp() {
        transactionEntityRepository = mock()
        service = ImportFingerprintService(transactionEntityRepository)
    }

    @Nested
    @DisplayName("compute")
    inner class ComputeTests {

        @Test
        fun `same inputs produce same fingerprint deterministic`() {
            val fp1 = service.compute(financialAccountId, date, amountCents, description)
            val fp2 = service.compute(financialAccountId, date, amountCents, description)
            assertThat(fp1).isEqualTo(fp2)
        }

        @Test
        fun `fingerprint is exactly 64 hex characters`() {
            val fp = service.compute(financialAccountId, date, amountCents, description)
            assertThat(fp).hasSize(64)
            assertThat(fp).matches("^[0-9a-f]{64}$")
        }

        @Test
        fun `different amountCents produce different fingerprints`() {
            val fp1 = service.compute(financialAccountId, date, 1000L, description)
            val fp2 = service.compute(financialAccountId, date, 1001L, description)
            assertThat(fp1).isNotEqualTo(fp2)
        }

        @Test
        fun `different date produces different fingerprint`() {
            val otherDate = LocalDate.of(2025, 3, 16)
            val fp1 = service.compute(financialAccountId, date, amountCents, description)
            val fp2 = service.compute(financialAccountId, otherDate, amountCents, description)
            assertThat(fp1).isNotEqualTo(fp2)
        }

        @Test
        fun `different financialAccountId produces different fingerprint`() {
            val otherAccountId = UUID.fromString("aaaaaaaa-bbbb-7bbb-8bbb-cccccccccccc")
            val fp1 = service.compute(financialAccountId, date, amountCents, description)
            val fp2 = service.compute(otherAccountId, date, amountCents, description)
            assertThat(fp1).isNotEqualTo(fp2)
        }

        @Test
        fun `different description produces different fingerprint`() {
            val fp1 = service.compute(financialAccountId, date, amountCents, "Coffee")
            val fp2 = service.compute(financialAccountId, date, amountCents, "Tea")
            assertThat(fp1).isNotEqualTo(fp2)
        }

        @Test
        fun `description is trimmed and lowercased before hashing`() {
            val fp1 = service.compute(financialAccountId, date, amountCents, "  FOO  ")
            val fp2 = service.compute(financialAccountId, date, amountCents, "foo")
            assertThat(fp1).isEqualTo(fp2)
        }

        @Test
        fun `empty description produces valid fingerprint`() {
            val fp = service.compute(financialAccountId, date, amountCents, "")
            assertThat(fp).hasSize(64)
            assertThat(fp).matches("^[0-9a-f]{64}$")
        }
    }

    @Nested
    @DisplayName("isDuplicate")
    inner class IsDuplicateTests {

        @Test
        fun `returns true when repository returns true`() {
            val fingerprint = service.compute(financialAccountId, date, amountCents, description)
            whenever(
                transactionEntityRepository.existsByFinancialAccountIdAndImportFingerprintAndDeletedAtIsNull(
                    financialAccountId = eq(financialAccountId),
                    importFingerprint = eq(fingerprint),
                ),
            ).thenReturn(true)

            val result = service.isDuplicate(financialAccountId, fingerprint)

            assertThat(result).isTrue()
        }

        @Test
        fun `returns false when repository returns false`() {
            val fingerprint = service.compute(financialAccountId, date, amountCents, description)
            whenever(
                transactionEntityRepository.existsByFinancialAccountIdAndImportFingerprintAndDeletedAtIsNull(
                    financialAccountId = any(),
                    importFingerprint = any(),
                ),
            ).thenReturn(false)

            val result = service.isDuplicate(financialAccountId, fingerprint)

            assertThat(result).isFalse()
        }

        @Test
        fun `calls repository with correct financialAccountId and fingerprint`() {
            val fingerprint = "a".repeat(64)
            whenever(
                transactionEntityRepository.existsByFinancialAccountIdAndImportFingerprintAndDeletedAtIsNull(
                    financialAccountId = any(),
                    importFingerprint = any(),
                ),
            ).thenReturn(false)

            service.isDuplicate(financialAccountId, fingerprint)

            verify(transactionEntityRepository).existsByFinancialAccountIdAndImportFingerprintAndDeletedAtIsNull(
                financialAccountId = financialAccountId,
                importFingerprint = fingerprint,
            )
        }
    }
}
