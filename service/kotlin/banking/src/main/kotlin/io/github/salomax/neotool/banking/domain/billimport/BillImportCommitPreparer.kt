package io.github.salomax.neotool.banking.domain.billimport

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBillService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import jakarta.inject.Singleton
import java.time.LocalDate
import java.util.UUID

data class PreparedBillImportCommit(
    val cards: List<PreparedBillImportCard>,
)

data class PreparedBillImportCard(
    val reviewCardId: String,
    val reviewCard: BillImportReviewCard,
    val financialAccount: FinancialAccount,
    val closingDate: LocalDate,
    val dueDate: LocalDate,
    val transactions: List<ConfirmBillReviewTransactionCommand>,
)

@Singleton
class BillImportCommitPreparer(
    private val creditCardBillService: CreditCardBillService,
) {
    fun prepare(
        command: ConfirmBillImportCommand,
        tenantFinancialAccountsById: Map<UUID, FinancialAccount>,
        reviewPayload: BillImportReview,
    ): PreparedBillImportCommit {
        if (command.cards.isEmpty()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "cards",
                parameters = mapOf("reason" to "At least one card is required"),
            )
        }

        val reviewCardsById = reviewPayload.cards.associateBy { it.reviewCardId }

        return PreparedBillImportCommit(
            cards =
                command.cards.mapIndexed { cardIndex, card ->
                    if (card.reviewCardId.isBlank()) {
                        throw ValidationException(
                            CommonErrorCode.INVALID_INPUT,
                            field = "cards[$cardIndex].reviewCardId",
                            parameters = mapOf("reason" to "reviewCardId is required"),
                        )
                    }

                    val account =
                        tenantFinancialAccountsById[card.financialAccountId]
                            ?: throw ValidationException(
                                CommonErrorCode.INVALID_INPUT,
                                field = "cards[$cardIndex].financialAccountId",
                                parameters = mapOf("reason" to "Financial account does not belong to current account"),
                            )

                    if (account.subtype != FinancialAccountSubtype.CREDIT_CARD &&
                        account.subtype != FinancialAccountSubtype.CHARGE_CARD
                    ) {
                        throw ValidationException(
                            CommonErrorCode.INVALID_INPUT,
                            field = "cards[$cardIndex].financialAccountId",
                            parameters = mapOf("reason" to "Financial account must be a credit/charge card"),
                        )
                    }

                    val reviewCard =
                        reviewCardsById[card.reviewCardId]
                            ?: throw ValidationException(
                                CommonErrorCode.INVALID_INPUT,
                                field = "cards[$cardIndex].reviewCardId",
                                parameters = mapOf("reason" to "Card is not present in review payload"),
                            )

                    val effectiveClosingDate =
                        command.closingDate
                            ?: reviewCard.closingDate
                            ?: reviewPayload.statementPeriod?.to
                            ?: reviewPayload.statementPeriod?.from
                            ?: throw ValidationException(
                                CommonErrorCode.INVALID_INPUT,
                                field = "closingDate",
                                parameters = mapOf("reason" to "closingDate is required to import this bill"),
                            )

                    val effectiveDueDate =
                        command.dueDate
                            ?: reviewCard.dueDate
                            ?: account.cardData?.dueDay?.let { creditCardBillService.computeDueDate(effectiveClosingDate, it) }
                            ?: throw ValidationException(
                                CommonErrorCode.INVALID_INPUT,
                                field = "dueDate",
                                parameters =
                                    mapOf(
                                        "reason" to
                                            "dueDate is required to import this bill when the selected card has no dueDay",
                                    ),
                            )

                    val includedTransactions =
                        card.transactions.mapIndexedNotNull { txIndex, tx ->
                            if (!tx.include) return@mapIndexedNotNull null
                            validateIncludedTransaction(cardIndex, txIndex, tx)
                            tx
                        }

                    PreparedBillImportCard(
                        reviewCardId = card.reviewCardId,
                        reviewCard = reviewCard,
                        financialAccount = account,
                        closingDate = effectiveClosingDate,
                        dueDate = effectiveDueDate,
                        transactions = includedTransactions,
                    )
                },
        )
    }

    private fun validateIncludedTransaction(
        cardIndex: Int,
        txIndex: Int,
        tx: ConfirmBillReviewTransactionCommand,
    ) {
        if (tx.description.isBlank()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "cards[$cardIndex].transactions[$txIndex].description",
                parameters = mapOf("reason" to "Description is required"),
            )
        }
        if (tx.amount <= java.math.BigDecimal.ZERO) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "cards[$cardIndex].transactions[$txIndex].amount",
                parameters = mapOf("reason" to "Amount must be greater than zero"),
            )
        }
        tx.installment?.let { installment ->
            if (installment.current < 1) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "cards[$cardIndex].transactions[$txIndex].installment.current",
                    parameters = mapOf("reason" to "current must be >= 1"),
                )
            }
            if (installment.total < 1) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "cards[$cardIndex].transactions[$txIndex].installment.total",
                    parameters = mapOf("reason" to "total must be >= 1"),
                )
            }
            if (installment.current > installment.total) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "cards[$cardIndex].transactions[$txIndex].installment.current",
                    parameters = mapOf("reason" to "current must be <= total"),
                )
            }
        }
    }
}
