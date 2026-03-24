package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.*
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import jakarta.inject.Singleton
import java.time.LocalDate
import java.util.UUID

/**
 * Domain service for credit card bill (fatura) lifecycle.
 *
 * Responsible for:
 * - Bill cycle date arithmetic: given a transaction date and [closingDay], determine which
 *   [CreditCardBill.closingDate] it belongs to and what the corresponding [CreditCardBill.dueDate] is.
 * - findOrCreate the correct bill for a transaction.
 * - Recompute [CreditCardBill.totalAmount] and [CreditCardBill.status] after every mutation.
 * - Query operations (list, find by closing date).
 *
 * Called synchronously by [TransactionService] after create/update/delete on card accounts.
 */
@Singleton
open class CreditCardBillService(
    private val billRepository: CreditCardBillRepository,
) {
    companion object {
        /** Account subtypes for which billing cycle logic applies. */
        val CARD_SUBTYPES =
            setOf(
                FinancialAccountSubtype.CREDIT_CARD,
                FinancialAccountSubtype.CHARGE_CARD,
            )
    }

    // ── Date arithmetic ───────────────────────────────────────────────────────

    /**
     * Returns the closing date of the billing cycle that contains [transactionDate].
     *
     * Algorithm:
     * - effectiveDay = min(closingDay, last day of transactionDate's month)  [month-end clamp]
     * - If transactionDate.dayOfMonth <= effectiveDay → closes this month
     * - Otherwise → closes next month (also clamped to that month's last day)
     *
     * Examples (closingDay = 7):
     *   Feb 7 → Feb 7   (on closing day: belongs to this month)
     *   Feb 8 → Mar 7   (after closing day: belongs to next month)
     *   Jan 31, closingDay=31 → Jan 31
     *   Feb 28, closingDay=31 → Feb 28  (clamped: Feb has only 28 days)
     */
    fun computeClosingDate(
        transactionDate: LocalDate,
        closingDay: Int,
    ): LocalDate {
        val effectiveDay = closingDay.coerceAtMost(transactionDate.lengthOfMonth())
        return if (transactionDate.dayOfMonth <= effectiveDay) {
            transactionDate.withDayOfMonth(effectiveDay)
        } else {
            val nextMonth = transactionDate.plusMonths(1)
            nextMonth.withDayOfMonth(closingDay.coerceAtMost(nextMonth.lengthOfMonth()))
        }
    }

    /**
     * Returns the payment due date for a bill that closes on [closingDate].
     *
     * Algorithm:
     * - candidate = closingDate with day = min(dueDay, last day of closingDate's month)
     * - If candidate is after closingDate → dueDate = candidate (same month)
     * - Otherwise → dueDate = candidate pushed to next month (also clamped)
     *
     * Examples (closingDay=7, dueDay=15):
     *   closingDate=Feb 7 → candidate=Feb 15 → Feb 15 > Feb 7 → dueDate=Feb 15
     *
     * Examples (closingDay=25, dueDay=10):
     *   closingDate=Mar 25 → candidate=Mar 10 → Mar 10 <= Mar 25 → dueDate=Apr 10
     */
    fun computeDueDate(
        closingDate: LocalDate,
        dueDay: Int,
    ): LocalDate {
        val candidate = closingDate.withDayOfMonth(dueDay.coerceAtMost(closingDate.lengthOfMonth()))
        return if (candidate.isAfter(closingDate)) {
            candidate
        } else {
            val nextMonth = closingDate.plusMonths(1)
            nextMonth.withDayOfMonth(dueDay.coerceAtMost(nextMonth.lengthOfMonth()))
        }
    }

    // ── Bill resolution ───────────────────────────────────────────────────────

    /**
     * Returns the bill UUID for the given [transactionDate] on [account], creating the bill
     * if it does not yet exist.
     *
     * Returns null when:
     * - [account.subtype] is not in [CARD_SUBTYPES] (e.g. BANK, CASH, VOUCHER_CARD)
     * - [account.cardData] is missing or has no [closingDay]/[dueDay]
     * - [type] is [TransactionType.BALANCE_ADJUSTMENT] (never assigned to a bill)
     */
    fun resolveBillForTransaction(
        account: FinancialAccount,
        transactionDate: LocalDate,
        type: TransactionType,
    ): UUID? {
        if (account.subtype !in CARD_SUBTYPES) return null
        if (type == TransactionType.BALANCE_ADJUSTMENT) return null
        val cardData = account.cardData ?: return null
        val closingDay = cardData.closingDay ?: return null
        val dueDay = cardData.dueDay ?: return null

        val closingDate = computeClosingDate(transactionDate, closingDay)
        val dueDate = computeDueDate(closingDate, dueDay)

        return billRepository.findOrCreate(
            financialAccountId = account.id!!,
            accountId = account.accountId,
            closingDate = closingDate,
            dueDate = dueDate,
        ).id!!
    }

    /**
     * Returns the bill UUID for an explicitly reviewed import cycle.
     *
     * Unlike [resolveBillForTransaction], this does not derive the cycle from account card settings.
     * It trusts the already-reviewed [closingDate]/[dueDate] pair and uses them directly.
     */
    fun resolveBillForReviewedCycle(
        account: FinancialAccount,
        closingDate: LocalDate,
        dueDate: LocalDate,
        type: TransactionType,
    ): UUID? {
        if (account.subtype !in CARD_SUBTYPES) return null
        if (type == TransactionType.BALANCE_ADJUSTMENT) return null
        return billRepository.findOrCreate(
            financialAccountId = account.id!!,
            accountId = account.accountId,
            closingDate = closingDate,
            dueDate = dueDate,
        ).id!!
    }

    // ── Recompute ─────────────────────────────────────────────────────────────

    /**
     * Recomputes [CreditCardBill.totalAmount] and [CreditCardBill.status] for the given bill.
     * No-op if [billId] is null.
     */
    fun recomputeBill(
        billId: UUID?,
        accountId: UUID,
    ) {
        if (billId == null) return
        billRepository.recomputeTotal(billId, accountId)
    }

    /**
     * Marks a bill as PAID (terminal status) and returns the updated bill.
     * The caller is responsible for ensuring the bill is not already paid.
     */
    fun markAsPaid(
        billId: UUID,
        accountId: UUID,
    ): CreditCardBill {
        billRepository.setStatus(billId, BillStatus.PAID, accountId)
        return requireBillForAccount(billId, accountId)
    }

    /**
     * Ensures that the given [billId] belongs to [accountId], throwing if not found or not owned.
     * Returns the resolved [CreditCardBill] for callers that need its attributes.
     */
    fun requireBillForAccount(
        billId: UUID,
        accountId: UUID,
    ): CreditCardBill =
        billRepository.findById(billId, accountId)
            ?: throw NoSuchElementException("CreditCardBill $billId not found for account $accountId")

    // ── Queries ───────────────────────────────────────────────────────────────

    fun listBills(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<CreditCardBill> = billRepository.listByAccount(financialAccountId, accountId)

    /**
     * Finds a specific bill by [closingDate], or the current open/most-recent bill when
     * [closingDate] is null.
     */
    fun findBill(
        financialAccountId: UUID,
        accountId: UUID,
        closingDate: LocalDate?,
    ): CreditCardBill? {
        if (closingDate != null) {
            return billRepository.findByClosingDate(financialAccountId, closingDate)
        }
        val all = billRepository.listByAccount(financialAccountId, accountId)
        val today = LocalDate.now()
        // Prefer the open bill (closes in the future), else fall back to most recent.
        return all.firstOrNull { it.closingDate >= today } ?: all.firstOrNull()
    }
}
