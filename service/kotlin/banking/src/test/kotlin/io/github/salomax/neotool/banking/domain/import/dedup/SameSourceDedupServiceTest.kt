package io.github.salomax.neotool.banking.service.import.dedup

import io.github.salomax.neotool.banking.service.import.dedup.*
import io.github.salomax.neotool.banking.service.import.*
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.repo.transaction.TransactionEntityRepository
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
import java.util.UUID

@DisplayName("SameSourceDedupService")
class SameSourceDedupServiceTest {
    private lateinit var transactionEntityRepository: TransactionEntityRepository
    private lateinit var service: SameSourceDedupService

    private val financialAccountId = UUID.randomUUID()
    private val providerTransactionId = "ofx-fitid-12345"
    private val source = TransactionSource.OPEN_FINANCE

    @BeforeEach
    fun setUp() {
        transactionEntityRepository = mock()
        service = SameSourceDedupService(transactionEntityRepository)
    }

    @Nested
    @DisplayName("isDuplicate")
    inner class IsDuplicateTests {
        @Test
        fun `returns true when repository returns true`() {
            whenever(
                transactionEntityRepository
                    .existsByFinancialAccountIdAndProviderTransactionIdAndSourceAndDeletedAtIsNull(
                        financialAccountId = eq(financialAccountId),
                        providerTransactionId = eq(providerTransactionId),
                        source = eq(source),
                    ),
            ).thenReturn(true)

            val result = service.isDuplicate(financialAccountId, providerTransactionId, source)

            assertThat(result).isTrue()
        }

        @Test
        fun `returns false when repository returns false`() {
            whenever(
                transactionEntityRepository
                    .existsByFinancialAccountIdAndProviderTransactionIdAndSourceAndDeletedAtIsNull(
                        financialAccountId = any(),
                        providerTransactionId = any(),
                        source = any(),
                    ),
            ).thenReturn(false)

            val result = service.isDuplicate(financialAccountId, providerTransactionId, source)

            assertThat(result).isFalse()
        }

        @Test
        fun `calls repository with correct financialAccountId providerTransactionId and source`() {
            whenever(
                transactionEntityRepository
                    .existsByFinancialAccountIdAndProviderTransactionIdAndSourceAndDeletedAtIsNull(
                        financialAccountId = any(),
                        providerTransactionId = any(),
                        source = any(),
                    ),
            ).thenReturn(false)

            service.isDuplicate(financialAccountId, providerTransactionId, source)

            verify(
                transactionEntityRepository,
            ).existsByFinancialAccountIdAndProviderTransactionIdAndSourceAndDeletedAtIsNull(
                financialAccountId = financialAccountId,
                providerTransactionId = providerTransactionId,
                source = source,
            )
        }
    }
}
