package io.github.salomax.neotool.banking.service.account

import io.github.salomax.neotool.banking.domain.account.*
import io.github.salomax.neotool.banking.shared.DateWindowPolicy
import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Domain service for [FinancialAccount] operations.
 *
 * [accountId] is always the tenant account ID resolved from the JWT by the GraphQL layer.
 * [userId] is the authenticated user ID (audit field).
 */
@Singleton
open class FinancialAccountService(
    private val financialAccountRepository: FinancialAccountRepository,
    private val issuerRepository: IssuerRepository,
    private val transactionService: TransactionService,
) {
    fun listByAccount(accountId: UUID): List<FinancialAccount> = financialAccountRepository.findByAccountId(accountId)

    @Transactional
    open fun create(
        command: CreateFinancialAccountCommand,
        accountId: UUID,
        userId: UUID,
    ): FinancialAccount {
        // TODO(billing): enforce plan-based quota before creation.
        //   Gate: plan_allows("banking:account:create") AND quota("financial_accounts.max") not exceeded.
        //   Planned limits — FREE: 5, BASIC: 20, PRO: 100.
        //   Implement when the billing service and PlanAccessSnapshot are available.

        require(command.issuerId != null || !command.issuerName.isNullOrBlank()) {
            "Either issuerId or issuerName must be provided"
        }
        val initialBalance = command.initialBalance
        val initialBalanceDate = command.initialBalanceDate
        if (initialBalance != null) {
            require(initialBalance.compareTo(BigDecimal.ZERO) != 0) {
                "initialBalance must be non-zero when provided"
            }
            require(initialBalanceDate != null) {
                "initialBalanceDate is required when initialBalance is provided"
            }
            val minAllowedDate = DateWindowPolicy.minAllowedDate()
            if (initialBalanceDate.isBefore(minAllowedDate)) {
                throw ValidationException(
                    errorCode = BankingErrorCode.TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW,
                    field = "initialBalanceDate",
                    parameters =
                        mapOf(
                            "minAllowedDate" to minAllowedDate.toString(),
                            "windowYears" to DateWindowPolicy.MAX_PAST_YEARS,
                        ),
                )
            }
        }

        val issuer = command.issuerId?.let { issuerRepository.findById(it) }

        val effectiveName =
            command.name?.takeIf { it.isNotBlank() }
                ?: issuer?.name
                ?: command.issuerName
                ?: throw IllegalArgumentException(
                    "Account name could not be resolved; provide name or a valid issuerId",
                )

        val now = Instant.now()
        val account =
            FinancialAccount(
                accountId = accountId,
                createdByUserId = userId,
                type = command.type,
                subtype = command.subtype,
                name = effectiveName,
                currencyCode = command.currencyCode?.takeIf { it.isNotBlank() } ?: "BRL",
                includeInNetWorth = command.includeInNetWorth ?: true,
                includeInDashboard = command.includeInDashboard ?: true,
                issuerId = command.issuerId,
                themeColor = command.themeColor,
                issuerName = issuer?.name ?: command.issuerName,
                issuerCountry = issuer?.countryCode,
                issuerCompeCode = issuer?.compeCode,
                issuerIspb = issuer?.ispb,
                cardLastFourDigits = extractLastFourDigits(command.cardData?.maskedNumber),
                bankData = command.bankData,
                cardData = command.cardData,
                createdAt = now,
                updatedAt = now,
            )

        val saved = financialAccountRepository.save(account)

        if (initialBalance != null) {
            transactionService.create(
                CreateTransactionCommand(
                    financialAccountId = saved.id!!,
                    creditDebitType =
                        if (initialBalance > BigDecimal.ZERO) {
                            CreditDebitType.CREDIT
                        } else {
                            CreditDebitType.DEBIT
                        },
                    type = TransactionType.BALANCE_ADJUSTMENT,
                    amount = initialBalance.abs(),
                    date = initialBalanceDate!!,
                    description = "Saldo inicial",
                    status = TransactionStatus.POSTED,
                    currencyCode = saved.currencyCode,
                ),
                accountId,
                userId,
            )
        }

        return saved
    }

    @Transactional
    open fun update(
        financialAccountId: UUID,
        command: UpdateFinancialAccountCommand,
        accountId: UUID,
        userId: UUID,
    ): FinancialAccount {
        val existing =
            financialAccountRepository.findById(financialAccountId, accountId)
                ?: throw NotFoundException(
                    errorCode = CommonErrorCode.NOT_FOUND,
                    field = "id",
                    parameters = mapOf("financialAccountId" to financialAccountId.toString()),
                )

        require(command.issuerId != null || !command.issuerName.isNullOrBlank()) {
            "Either issuerId or issuerName must be provided"
        }

        val isExistingBank = existing.type == FinancialAccountType.BANK
        if (isExistingBank) {
            require(command.type == FinancialAccountType.BANK) {
                "Cannot change account type from BANK"
            }
        } else {
            require(command.type != FinancialAccountType.BANK) {
                "Cannot change account type to BANK"
            }
        }

        val issuer = command.issuerId?.let { issuerRepository.findById(it) }
        val isCustomIssuerUpdate = command.issuerId == null && !command.issuerName.isNullOrBlank()
        val resolvedName = command.name?.takeIf { it.isNotBlank() } ?: existing.name
        val resolvedCurrency = command.currencyCode?.takeIf { it.isNotBlank() } ?: existing.currencyCode
        val resolvedBankData =
            if (isExistingBank) {
                mergeBankData(existing.bankData, command.bankData)
            } else {
                null
            }
        val resolvedCardData =
            if (!isExistingBank) {
                mergeCardData(existing.cardData, command.cardData)
            } else {
                null
            }
        val now = Instant.now()

        val updated =
            existing.copy(
                createdByUserId = existing.createdByUserId,
                type = command.type,
                subtype = command.subtype,
                name = resolvedName,
                currencyCode = resolvedCurrency,
                includeInNetWorth = command.includeInNetWorth ?: existing.includeInNetWorth,
                includeInDashboard = command.includeInDashboard ?: existing.includeInDashboard,
                issuerId =
                    when {
                        issuer != null -> issuer.id
                        isCustomIssuerUpdate -> null
                        else -> existing.issuerId
                    },
                themeColor = command.themeColor ?: existing.themeColor,
                issuerName =
                    when {
                        issuer != null -> issuer.name
                        isCustomIssuerUpdate -> command.issuerName?.trim()
                        else -> existing.issuerName
                    },
                issuerCountry =
                    when {
                        issuer != null -> issuer.countryCode
                        isCustomIssuerUpdate -> null
                        else -> existing.issuerCountry
                    },
                issuerCompeCode =
                    when {
                        issuer != null -> issuer.compeCode
                        isCustomIssuerUpdate -> null
                        else -> existing.issuerCompeCode
                    },
                issuerIspb =
                    when {
                        issuer != null -> issuer.ispb
                        isCustomIssuerUpdate -> null
                        else -> existing.issuerIspb
                    },
                cardLastFourDigits =
                    extractLastFourDigits(resolvedCardData?.maskedNumber)
                        ?: existing.cardLastFourDigits,
                bankData = resolvedBankData,
                cardData = resolvedCardData,
                updatedAt = now,
            )

        return financialAccountRepository.update(updated)
    }

    private fun mergeBankData(
        existing: BankData?,
        patch: BankData?,
    ): BankData? {
        if (existing == null) return patch
        if (patch == null) return existing

        return existing.copy(
            branchCode = patch.branchCode ?: existing.branchCode,
            number = patch.number ?: existing.number,
            checkDigit = patch.checkDigit ?: existing.checkDigit,
            maskedNumber = patch.maskedNumber ?: existing.maskedNumber,
        )
    }

    private fun mergeCardData(
        existing: CardData?,
        patch: CardData?,
    ): CardData? {
        if (existing == null) return patch
        if (patch == null) return existing

        return existing.copy(
            maskedNumber = patch.maskedNumber ?: existing.maskedNumber,
            bin = patch.bin ?: existing.bin,
            cardNetwork = patch.cardNetwork ?: existing.cardNetwork,
            cardLevel = patch.cardLevel ?: existing.cardLevel,
            creditLimit = patch.creditLimit ?: existing.creditLimit,
            availableCreditLimit = patch.availableCreditLimit ?: existing.availableCreditLimit,
            minimumPaymentPercentage = patch.minimumPaymentPercentage ?: existing.minimumPaymentPercentage,
            closingDay = patch.closingDay ?: existing.closingDay,
            dueDay = patch.dueDay ?: existing.dueDay,
            linkedBankAccountId = patch.linkedBankAccountId ?: existing.linkedBankAccountId,
        )
    }

    private fun extractLastFourDigits(maskedNumber: String?): String? {
        if (maskedNumber.isNullOrBlank()) return null
        val onlyDigits = maskedNumber.filter(Char::isDigit)
        return if (onlyDigits.length >= 4) onlyDigits.takeLast(4) else null
    }
}
