package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.repo.transaction.RecurringPaymentRepository
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate

@Singleton
class RecurringMatchingService(
    private val recurringPaymentRepository: RecurringPaymentRepository,
    @Value("\${banking.recurring.suggestion-threshold:0.80}")
    private val threshold: Float,
    @Value("\${banking.recurring.date-gate-days:10}")
    private val dateGateDays: Long,
) {
    fun findBestMatch(transaction: Transaction): RecurringPayment? {
        if (transaction.status != TransactionStatus.POSTED) return null
        if (transaction.type == TransactionType.BALANCE_ADJUSTMENT) return null
        if (transaction.recurringPaymentId != null) return null

        val candidates =
            recurringPaymentRepository.findAllByAccountId(transaction.accountId)
                .filter { it.status == RecurringPaymentStatus.ACTIVE && it.deletedAt == null }

        return candidates
            .asSequence()
            .map { it to score(transaction, it) }
            .filter { (_, s) -> s >= threshold }
            .minByOrNull { (template, _) ->
                Math.abs(transaction.date.toEpochDay() - template.nextDueDate.toEpochDay())
            }
            ?.first
    }

    internal fun score(
        transaction: Transaction,
        template: RecurringPayment,
    ): Float {
        val today = LocalDate.now()
        val isPastDue = template.nextDueDate.isBefore(today)
        val dateDelta = Math.abs(transaction.date.toEpochDay() - template.nextDueDate.toEpochDay())

        if (!isPastDue && dateDelta > dateGateDays) return 0f
        if (isPastDue && transaction.date.isBefore(template.nextDueDate)) return 0f

        var s = 0f
        s += descriptionScore(template.name, transaction.description) * 0.55f
        if (template.merchantId != null && template.merchantId == transaction.merchantId) s += 0.25f

        val expected = template.expectedAmount
        if (expected == null || expected <= BigDecimal.ZERO) {
            // Legacy safety: invalid non-positive expectedAmount behaves like variable amount.
            s += 0.10f
        } else {
            val pct =
                transaction.amount.subtract(expected).abs()
                    .divide(expected.abs(), 6, RoundingMode.HALF_UP)
            if (pct <= BigDecimal("0.15")) s += 0.20f * (1f - pct.toFloat())
        }

        return s.coerceIn(0f, 1f)
    }

    internal fun descriptionScore(
        templateName: String,
        txDescription: String,
    ): Float {
        val normTemplate = normalize(templateName)
        val normTx = normalize(txDescription)
        if (normTx.contains(normTemplate)) return 1.0f
        val tTokens = normTemplate.split(" ").filter { it.length >= 3 }.toSet()
        val dTokens = normTx.split(" ").filter { it.length >= 3 }.toSet()
        val hits = tTokens.intersect(dTokens)
        if (hits.isNotEmpty()) return hits.size.toFloat() / tTokens.size.toFloat()
        return 0f
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
