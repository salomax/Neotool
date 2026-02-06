package io.github.salomax.neotool.security.startup

import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.email.EmailService
import io.micronaut.context.event.StartupEvent
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
import mu.KotlinLogging
import org.reactivestreams.Publisher
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Warms up Hibernate query plans and lazy singletons during startup
 * so the first real request doesn't pay a ~14s cold-start penalty.
 *
 * Also acts as a [HealthIndicator]: the pod reports DOWN until warmup
 * completes, preventing Kubernetes from routing traffic to it too early.
 */
@Singleton
class WarmupService(
    private val userRepository: UserRepository,
    private val principalRepository: PrincipalRepository,
    private val groupMembershipRepository: GroupMembershipRepository,
    @Suppress("unused") private val emailService: EmailService,
) : HealthIndicator {
    private val logger = KotlinLogging.logger {}
    private val warmedUp = AtomicBoolean(false)

    @EventListener
    fun onStartup(event: StartupEvent) {
        val start = System.currentTimeMillis()
        logger.info { "Warmup: compiling Hibernate query plans..." }
        try {
            // These calls force Hibernate to compile and cache the HQL/JPQL query plans.
            // The actual results don't matter — we just need the query plans cached.
            val dummyId = UUID(0, 0)

            userRepository.findByEmail("__warmup__")
            principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, "__warmup__")
            groupMembershipRepository.findActiveMembershipsByUserId(dummyId, Instant.now())

            val elapsed = System.currentTimeMillis() - start
            logger.info { "Warmup: completed in ${elapsed}ms" }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            logger.warn(e) { "Warmup: queries failed after ${elapsed}ms (non-fatal, first request may be slow)" }
        } finally {
            warmedUp.set(true)
        }
    }

    override fun getResult(): Publisher<HealthResult> {
        val status = if (warmedUp.get()) HealthStatus.UP else HealthStatus.DOWN
        return object : Publisher<HealthResult> {
            override fun subscribe(subscriber: org.reactivestreams.Subscriber<in HealthResult>) {
                subscriber.onSubscribe(
                    object : org.reactivestreams.Subscription {
                        override fun request(n: Long) {
                            subscriber.onNext(HealthResult.builder("warmup", status).build())
                            subscriber.onComplete()
                        }
                        override fun cancel() {}
                    },
                )
            }
        }
    }
}
