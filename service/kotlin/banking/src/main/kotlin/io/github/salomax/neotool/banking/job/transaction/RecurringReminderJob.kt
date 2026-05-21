package io.github.salomax.neotool.banking.job.transaction

import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.domain.transaction.RecurringPayment
import io.github.salomax.neotool.banking.repo.transaction.RecurringPaymentRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Singleton
@Requires(property = "banking.recurring-reminders.enabled", notEquals = "false")
open class RecurringReminderJob(
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val pendingActionRepository: AccountPendingActionRepository,
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "PT1H")
    open fun run() {
        val templates = recurringPaymentRepository.findActiveDueForReminder(LocalDate.now())
        templates.forEach { template ->
            runCatching { maybeCreateReminder(template) }
                .onFailure { e ->
                    log.error(e) { "Recurring reminder failed for template=${template.id}" }
                }
        }
    }

    private fun maybeCreateReminder(template: RecurringPayment) {
        val nowDate = LocalDate.now()
        val daysUntilDue = ChronoUnit.DAYS.between(nowDate, template.nextDueDate)
        if (daysUntilDue > template.remindDaysBefore) return

        val templateId = template.id ?: return
        val exists =
            pendingActionRepository.existsOpenForEntity(
                entityType = ENTITY_TYPE,
                entityId = templateId,
                type = PendingActionType.RECURRING_PAYMENT_DUE,
            )
        if (exists) return

        val now = Instant.now()
        pendingActionRepository.save(
            AccountPendingAction(
                accountId = template.accountId,
                type = PendingActionType.RECURRING_PAYMENT_DUE,
                severity = PendingActionSeverity.INFO,
                status = AccountPendingActionStatus.PENDING,
                entityType = ENTITY_TYPE,
                entityId = templateId,
                relatedTransactionId = null,
                payload =
                    mapOf(
                        "recurringPaymentName" to template.name,
                        "nextDueDate" to template.nextDueDate.toString(),
                        "expectedAmount" to template.expectedAmount?.toPlainString(),
                        "currencyCode" to template.currencyCode,
                        "financialAccountId" to template.financialAccountId?.toString(),
                        "variableAmount" to (template.expectedAmount == null),
                    ),
                visibleFrom = now,
                notifiedAt = null,
                dismissedAt = null,
                completedAt = null,
                cancelledAt = null,
                expiresAt = template.nextDueDate.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    companion object {
        private const val ENTITY_TYPE = "RecurringPayment"
    }
}
