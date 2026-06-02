package io.github.salomax.neotool.banking.job.pendingaction

import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.service.pendingaction.PendingActionCommsService
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant

/**
 * Daily job that finds pending actions whose [visibleFrom] has been reached and
 * have not yet been communicated to the user. Delegates delivery to [PendingActionCommsService].
 */
@Singleton
@Requires(property = "banking.pending-actions.notifier.enabled", value = "true", defaultValue = "true")
open class PendingActionNotifierJob(
    private val repository: AccountPendingActionRepository,
    private val commsService: PendingActionCommsService,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(cron = "\${banking.pending-actions.notifier.cron:0 0 8 * * ?}")
    open fun run() {
        val now = Instant.now()
        val due = repository.findDueForNotification(now)
        if (due.isEmpty()) {
            logger.debug { "PendingActionNotifierJob: nothing due" }
            return
        }

        var sent = 0
        for (action in due) {
            runCatching { commsService.notify(action) }
                .onSuccess {
                    repository.update(action.copy(notifiedAt = now, updatedAt = now))
                    sent++
                }
                .onFailure { e ->
                    logger.warn(e) { "Failed to notify for pending action ${action.id}" }
                }
        }

        if (sent > 0) logger.info { "PendingActionNotifierJob: notified $sent action(s)" }
    }
}
