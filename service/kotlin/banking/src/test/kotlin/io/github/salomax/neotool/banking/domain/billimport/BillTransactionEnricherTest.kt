package io.github.salomax.neotool.banking.domain.billimport

import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("BillTransactionEnricher")
class BillTransactionEnricherTest {
    @Test
    fun `detects installments from common patterns`() {
        assertThat(BillTransactionEnricher.detectInstallment("Hdi Seguros Sa 04/12"))
            .isEqualTo(InstallmentInfo(current = 4, total = 12))
        assertThat(BillTransactionEnricher.detectInstallment("Seguro auto (4/12)"))
            .isEqualTo(InstallmentInfo(current = 4, total = 12))
        assertThat(BillTransactionEnricher.detectInstallment("Compra 4/12"))
            .isEqualTo(InstallmentInfo(current = 4, total = 12))
    }

    @Test
    fun `marks pagamento recebido credit as bill payment`() {
        assertThat(
            BillTransactionEnricher.isBillPayment(
                creditDebitType = CreditDebitType.CREDIT,
                description = "Pagamento recebido",
            ),
        ).isTrue()

        assertThat(
            BillTransactionEnricher.isBillPayment(
                creditDebitType = CreditDebitType.DEBIT,
                description = "Pagamento recebido",
            ),
        ).isFalse()
    }

    @Test
    fun `maps parsed transaction into bill review transaction with enrichment`() {
        val enriched =
            BillTransactionEnricher.toBillReviewTransaction(
                ParsedTransaction(
                    creditDebitType = CreditDebitType.CREDIT,
                    amount = BigDecimal("99.90"),
                    date = LocalDate.of(2026, 1, 10),
                    description = "Pagamento recebido",
                    descriptionRaw = "Hdi Seguros Sa (04/12)",
                ),
            )

        assertThat(enriched.isBillPayment).isTrue()
        assertThat(enriched.installment).isEqualTo(InstallmentInfo(current = 4, total = 12))
    }
}
