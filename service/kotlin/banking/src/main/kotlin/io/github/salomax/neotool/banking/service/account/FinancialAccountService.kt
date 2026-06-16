package io.github.salomax.neotool.banking.service.account

import io.github.salomax.neotool.banking.domain.account.BankData
import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.model.dto.account.BankDataPatch
import io.github.salomax.neotool.banking.model.dto.account.CardDataPatch
import io.github.salomax.neotool.banking.model.dto.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.model.dto.account.UpdateField
import io.github.salomax.neotool.banking.model.dto.account.UpdateFinancialAccountCommand
import io.github.salomax.neotool.banking.model.dto.account.resolve
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.issuer.IssuerRepository
import io.github.salomax.neotool.banking.repo.transaction.CreditCardBillEntityRepository
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotEntityRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionEntityRepository
import io.github.salomax.neotool.banking.service.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.banking.shared.DateWindowPolicy
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
    private val transactionEntityRepository: TransactionEntityRepository,
    private val creditCardBillEntityRepository: CreditCardBillEntityRepository,
    private val monthlyBalanceSnapshotEntityRepository: MonthlyBalanceSnapshotEntityRepository,
    private val snapshotComputationService: SnapshotComputationService,
) {
    companion object {
        private const val MAX_FINANCIAL_ACCOUNT_NAME_LENGTH = 255
    }

    fun listByAccount(accountId: UUID): List<FinancialAccount> = financialAccountRepository.findByAccountId(accountId)

    fun findById(
        financialAccountId: UUID,
        accountId: UUID,
    ): FinancialAccount? = financialAccountRepository.findById(financialAccountId, accountId)

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
        require(command.issuerId == null || command.issuerName.isNullOrBlank()) {
            "issuerId and issuerName are mutually exclusive"
        }
        val initialBalance = command.initialBalance
        val initialBalanceDate = command.initialBalanceDate
        validateInitialBalanceInputForType(
            type = command.type,
            initialBalanceProvided = initialBalance != null,
            initialBalanceDateProvided = initialBalanceDate != null,
        )
        if (initialBalance != null) {
            validateInitialBalanceNonZero(initialBalance)
            validateInitialBalanceDate(requireInitialBalanceDate(initialBalanceDate))
        }

        validateAccountShape(
            type = command.type,
            bankDataProvided = command.bankData != null,
            cardDataProvided = command.cardData != null,
        )
        validateCardDayOfMonthFields(
            closingDay = command.cardData?.closingDay,
            dueDay = command.cardData?.dueDay,
        )
        val isMultiCard = if (supportsMultiCard(command.type)) (command.cardData?.isMultiCard ?: false) else false
        val normalizedCreateCardMaskedNumber =
            if (isMultiCard) {
                null
            } else {
                normalizeCardMaskedNumber(command.cardData?.maskedNumber)
            }

        val issuer = command.issuerId?.let { issuerRepository.findById(it) }

        val effectiveName =
            command.name?.takeIf { it.isNotBlank() }
                ?: issuer?.name
                ?: command.issuerName
                ?: throw IllegalArgumentException(
                    "Account name could not be resolved; provide name or a valid issuerId",
                )

        if (effectiveName.length > MAX_FINANCIAL_ACCOUNT_NAME_LENGTH) {
            throw ValidationException(
                errorCode = BankingErrorCode.FINANCIAL_ACCOUNT_NAME_TOO_LONG,
                field = "name",
                parameters =
                    mapOf(
                        "maxLength" to MAX_FINANCIAL_ACCOUNT_NAME_LENGTH,
                        "actualLength" to effectiveName.length,
                    ),
            )
        }

        val now = Instant.now()
        val account =
            FinancialAccount(
                accountId = accountId,
                createdByUserId = userId,
                type = command.type,
                subtype = command.subtype,
                name = effectiveName,
                currencyCode = command.currencyCode?.takeIf { it.isNotBlank() } ?: "BRL",
                includeInNetWorth =
                    command.includeInNetWorth
                        ?: (command.subtype != FinancialAccountSubtype.VOUCHER_CARD),
                includeInDashboard = command.includeInDashboard ?: true,
                includeCashInAllocation = command.includeCashInAllocation ?: true,
                issuerId = command.issuerId,
                themeColor = command.themeColor,
                issuerName = issuer?.name ?: command.issuerName,
                issuerCountry = issuer?.countryCode,
                issuerCompeCode = issuer?.compeCode,
                issuerIspb = issuer?.ispb,
                cardLastFourDigits = extractLastFourDigits(normalizedCreateCardMaskedNumber),
                bankData = command.bankData,
                cardData =
                    command.cardData?.copy(
                        maskedNumber = normalizedCreateCardMaskedNumber,
                        isMultiCard = isMultiCard,
                    ),
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
        require(command.issuerId == null || command.issuerName.isNullOrBlank()) {
            "issuerId and issuerName are mutually exclusive"
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

        validateAccountShape(
            type = command.type,
            bankDataProvided = command.bankData != null,
            cardDataProvided = command.cardData != null,
        )
        validateInitialBalanceInputForType(
            type = command.type,
            initialBalanceProvided = command.initialBalance is UpdateField.Present,
            initialBalanceDateProvided = command.initialBalanceDate is UpdateField.Present,
        )

        val issuer = command.issuerId?.let { issuerRepository.findById(it) }
        val isCustomIssuerUpdate = command.issuerId == null && !command.issuerName.isNullOrBlank()
        val resolvedName = command.name.resolve(existing.name)?.takeIf { it.isNotBlank() } ?: existing.name
        if (resolvedName.length > MAX_FINANCIAL_ACCOUNT_NAME_LENGTH) {
            throw ValidationException(
                errorCode = BankingErrorCode.FINANCIAL_ACCOUNT_NAME_TOO_LONG,
                field = "name",
                parameters =
                    mapOf(
                        "maxLength" to MAX_FINANCIAL_ACCOUNT_NAME_LENGTH,
                        "actualLength" to resolvedName.length,
                    ),
            )
        }
        val resolvedCurrency =
            command.currencyCode.resolve(existing.currencyCode)?.takeIf {
                it.isNotBlank()
            } ?: existing.currencyCode
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
        val resolvedIsMultiCard =
            if (supportsMultiCard(
                    command.type,
                )
            ) {
                (resolvedCardData?.isMultiCard ?: false)
            } else {
                false
            }
        val normalizedUpdateCardMaskedNumber =
            if (resolvedIsMultiCard) {
                null
            } else {
                normalizeCardMaskedNumber(resolvedCardData?.maskedNumber)
            }
        val validatedResolvedCardData =
            resolvedCardData?.copy(
                maskedNumber = normalizedUpdateCardMaskedNumber,
                isMultiCard = resolvedIsMultiCard,
            )
        validateCardDayOfMonthFields(
            closingDay = validatedResolvedCardData?.closingDay,
            dueDay = validatedResolvedCardData?.dueDay,
        )
        val now = Instant.now()

        val updated =
            existing.copy(
                createdByUserId = existing.createdByUserId,
                type = command.type,
                subtype = command.subtype,
                name = resolvedName,
                currencyCode = resolvedCurrency,
                includeInNetWorth =
                    command.includeInNetWorth.resolve(
                        existing.includeInNetWorth,
                    ) ?: existing.includeInNetWorth,
                includeInDashboard =
                    command.includeInDashboard.resolve(
                        existing.includeInDashboard,
                    ) ?: existing.includeInDashboard,
                includeCashInAllocation =
                    command.includeCashInAllocation.resolve(
                        existing.includeCashInAllocation,
                    ) ?: existing.includeCashInAllocation,
                issuerId =
                    when {
                        issuer != null -> issuer.id
                        isCustomIssuerUpdate -> null
                        else -> existing.issuerId
                    },
                themeColor = command.themeColor.resolve(existing.themeColor),
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
                    extractLastFourDigits(normalizedUpdateCardMaskedNumber),
                bankData = resolvedBankData,
                cardData = validatedResolvedCardData,
                updatedAt = now,
            )

        val saved = financialAccountRepository.update(updated)
        handleInitialBalanceUpdate(
            financialAccountId = financialAccountId,
            command = command,
            accountId = accountId,
            userId = userId,
            currencyCode = saved.currencyCode,
        )
        return saved
    }

    @Transactional
    open fun delete(
        financialAccountId: UUID,
        accountId: UUID,
    ): Boolean {
        financialAccountRepository.findById(financialAccountId, accountId)
            ?: throw NotFoundException(
                errorCode = CommonErrorCode.NOT_FOUND,
                field = "id",
                parameters = mapOf("financialAccountId" to financialAccountId.toString()),
            )

        val txCount = transactionEntityRepository.countByFinancialAccountIdAndDeletedAtIsNull(financialAccountId)
        if (txCount > 0) {
            throw ValidationException(
                errorCode = BankingErrorCode.FINANCIAL_ACCOUNT_HAS_TRANSACTIONS,
                field = "id",
                parameters = mapOf("count" to txCount),
            )
        }

        val openBillCount = creditCardBillEntityRepository.countOpenByFinancialAccountId(financialAccountId)
        if (openBillCount > 0) {
            throw ValidationException(
                errorCode = BankingErrorCode.FINANCIAL_ACCOUNT_HAS_OPEN_BILLS,
                field = "id",
                parameters = mapOf("count" to openBillCount),
            )
        }

        monthlyBalanceSnapshotEntityRepository.deleteByFinancialAccountId(financialAccountId)
        creditCardBillEntityRepository.softDeleteByFinancialAccountId(financialAccountId, Instant.now())

        return financialAccountRepository.softDelete(financialAccountId, accountId)
    }

    private fun mergeBankData(
        existing: BankData?,
        patch: BankDataPatch?,
    ): BankData? {
        if (patch == null) return existing
        return BankData(
            branchCode = patch.branchCode.resolve(existing?.branchCode),
            number = patch.number.resolve(existing?.number),
            checkDigit = patch.checkDigit.resolve(existing?.checkDigit),
            maskedNumber = patch.maskedNumber.resolve(existing?.maskedNumber),
        )
    }

    private fun mergeCardData(
        existing: CardData?,
        patch: CardDataPatch?,
    ): CardData? {
        if (patch == null) return existing
        val resolvedIsMultiCard = patch.isMultiCard.resolve(existing?.isMultiCard) ?: false
        return CardData(
            maskedNumber = patch.maskedNumber.resolve(existing?.maskedNumber),
            bin = patch.bin.resolve(existing?.bin),
            cardNetwork = patch.cardNetwork.resolve(existing?.cardNetwork),
            cardLevel = patch.cardLevel.resolve(existing?.cardLevel),
            creditLimit = patch.creditLimit.resolve(existing?.creditLimit),
            availableCreditLimit = patch.availableCreditLimit.resolve(existing?.availableCreditLimit),
            minimumPaymentPercentage = patch.minimumPaymentPercentage.resolve(existing?.minimumPaymentPercentage),
            closingDay = patch.closingDay.resolve(existing?.closingDay),
            dueDay = patch.dueDay.resolve(existing?.dueDay),
            linkedBankAccountId = patch.linkedBankAccountId.resolve(existing?.linkedBankAccountId),
            isMultiCard = resolvedIsMultiCard,
        )
    }

    private fun validateAccountShape(
        type: FinancialAccountType,
        bankDataProvided: Boolean,
        cardDataProvided: Boolean,
    ) {
        if (type == FinancialAccountType.BANK) {
            require(!cardDataProvided) {
                "cardData is not supported for BANK accounts"
            }
            return
        }

        require(!bankDataProvided) {
            "bankData is only supported for BANK accounts"
        }
    }

    /** Only constraint: calendar day of month when a value is present (no ordering or pairing rules). */
    private fun validateCardDayOfMonthFields(
        closingDay: Int?,
        dueDay: Int?,
    ) {
        validateDayOfMonthField("closingDay", closingDay)
        validateDayOfMonthField("dueDay", dueDay)
    }

    private fun validateDayOfMonthField(
        field: String,
        value: Int?,
    ) {
        if (value == null) return
        require(value in 1..31) {
            "$field must be between 1 and 31"
        }
    }

    private fun extractLastFourDigits(maskedNumber: String?): String? {
        if (maskedNumber.isNullOrBlank()) return null
        val onlyDigits = maskedNumber.filter(Char::isDigit)
        return if (onlyDigits.length >= 4) onlyDigits.takeLast(4) else null
    }

    private fun normalizeCardMaskedNumber(maskedNumber: String?): String? {
        val trimmed = maskedNumber?.trim()
        if (trimmed.isNullOrBlank()) return null
        val onlyDigits = trimmed.filter(Char::isDigit)
        if (onlyDigits.length != 4) {
            throw ValidationException(
                errorCode = BankingErrorCode.CARD_MASKED_NUMBER_INVALID,
                field = "maskedNumber",
                parameters =
                    mapOf(
                        "requiredDigits" to 4,
                        "actualDigits" to onlyDigits.length,
                    ),
            )
        }
        return onlyDigits
    }

    private fun supportsMultiCard(type: FinancialAccountType): Boolean =
        type == FinancialAccountType.CREDIT || type == FinancialAccountType.PAYMENT

    private fun handleInitialBalanceUpdate(
        financialAccountId: UUID,
        command: UpdateFinancialAccountCommand,
        accountId: UUID,
        userId: UUID,
        currencyCode: String,
    ) {
        val initialBalanceProvided = command.initialBalance is UpdateField.Present
        val initialBalanceDateProvided = command.initialBalanceDate is UpdateField.Present
        validateInitialBalanceInputForType(
            type = command.type,
            initialBalanceProvided = initialBalanceProvided,
            initialBalanceDateProvided = initialBalanceDateProvided,
        )
        if (command.type != FinancialAccountType.BANK) return

        when (val initialBalanceField = command.initialBalance) {
            UpdateField.Absent -> return
            is UpdateField.Present -> {
                val existingTx =
                    transactionEntityRepository
                        .findInitialBalanceTransaction(financialAccountId.toString())
                        .orElse(null)
                val newBalance = initialBalanceField.value

                if (newBalance == null) {
                    if (existingTx != null) {
                        val deletedAt = Instant.now()
                        existingTx.deletedAt = deletedAt
                        existingTx.updatedAt = deletedAt
                        transactionEntityRepository.update(existingTx)
                        transactionEntityRepository.flush()
                        snapshotComputationService.recomputeChain(
                            financialAccountId = financialAccountId,
                            accountId = accountId,
                            fromYear = existingTx.date.year,
                            fromMonth = existingTx.date.monthValue,
                        )
                    }
                    return
                }

                validateInitialBalanceNonZero(newBalance)
                val newDate = resolveInitialBalanceDate(command, existingTx?.date)
                validateInitialBalanceDate(newDate)
                val newCreditDebitType =
                    if (newBalance > BigDecimal.ZERO) {
                        CreditDebitType.CREDIT
                    } else {
                        CreditDebitType.DEBIT
                    }
                val newAmount = newBalance.abs()

                if (existingTx != null) {
                    val oldDate = existingTx.date
                    existingTx.amount = newAmount
                    existingTx.creditDebitType = newCreditDebitType
                    existingTx.date = newDate
                    existingTx.updatedAt = Instant.now()
                    transactionEntityRepository.update(existingTx)
                    transactionEntityRepository.flush()
                    val recomputeFrom = minOf(oldDate, newDate)
                    snapshotComputationService.recomputeChain(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        fromYear = recomputeFrom.year,
                        fromMonth = recomputeFrom.monthValue,
                    )
                    return
                }

                transactionService.create(
                    CreateTransactionCommand(
                        financialAccountId = financialAccountId,
                        creditDebitType = newCreditDebitType,
                        type = TransactionType.BALANCE_ADJUSTMENT,
                        amount = newAmount,
                        date = newDate,
                        description = "Saldo inicial",
                        status = TransactionStatus.POSTED,
                        currencyCode = currencyCode,
                    ),
                    accountId,
                    userId,
                )
            }
        }
    }

    private fun resolveInitialBalanceDate(
        command: UpdateFinancialAccountCommand,
        existingDate: java.time.LocalDate?,
    ): java.time.LocalDate =
        when (val initialBalanceDateField = command.initialBalanceDate) {
            is UpdateField.Present ->
                requireInitialBalanceDate(initialBalanceDateField.value)
            UpdateField.Absent ->
                requireInitialBalanceDate(existingDate)
        }

    private fun validateInitialBalanceInputForType(
        type: FinancialAccountType,
        initialBalanceProvided: Boolean,
        initialBalanceDateProvided: Boolean,
    ) {
        if (type == FinancialAccountType.BANK) return
        if (!initialBalanceProvided && !initialBalanceDateProvided) return
        throw ValidationException(
            errorCode = BankingErrorCode.INITIAL_BALANCE_ONLY_SUPPORTED_FOR_BANK_ACCOUNTS,
            field = if (initialBalanceProvided) "initialBalance" else "initialBalanceDate",
        )
    }

    private fun validateInitialBalanceNonZero(initialBalance: BigDecimal) {
        if (initialBalance.compareTo(BigDecimal.ZERO) == 0) {
            throw ValidationException(
                errorCode = BankingErrorCode.INITIAL_BALANCE_NON_ZERO,
                field = "initialBalance",
            )
        }
    }

    private fun requireInitialBalanceDate(date: java.time.LocalDate?): java.time.LocalDate =
        date ?: throw ValidationException(
            errorCode = BankingErrorCode.INITIAL_BALANCE_DATE_REQUIRED,
            field = "initialBalanceDate",
        )

    private fun validateInitialBalanceDate(date: java.time.LocalDate) {
        val minAllowedDate = DateWindowPolicy.minAllowedDate()
        if (date.isBefore(minAllowedDate)) {
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
        val today = java.time.LocalDate.now()
        if (date.isAfter(today)) {
            throw ValidationException(
                errorCode = BankingErrorCode.TRANSACTION_DATE_IN_FUTURE,
                field = "initialBalanceDate",
                parameters = mapOf("maxDate" to today.toString()),
            )
        }
    }
}
