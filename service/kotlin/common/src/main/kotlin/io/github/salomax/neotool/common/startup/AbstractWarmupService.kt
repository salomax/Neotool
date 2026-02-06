package io.github.salomax.neotool.common.startup

import io.micronaut.context.event.StartupEvent
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.event.annotation.EventListener
import mu.KotlinLogging
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Abstract base for warming up Hibernate query plans and lazy singletons during startup
 * so the first real request doesn't pay a cold-start penalty.
 *
 * Subclasses implement [runWarmupQueries] to execute module-specific repository calls
 * that compile and cache HQL/JPQL query plans.
 *
 * Also acts as a [HealthIndicator]: the pod reports DOWN until warmup completes,
 * preventing Kubernetes from routing traffic to it too early.
 */
abstract class AbstractWarmupService : HealthIndicator {
    protected val logger = KotlinLogging.logger {}
    protected val warmedUp = AtomicBoolean(false)

    @EventListener
    fun onStartup(event: StartupEvent) {
        val start = System.currentTimeMillis()
        logger.info { "Warmup: compiling Hibernate query plans..." }
        try {
            runWarmupQueries()
            val elapsed = System.currentTimeMillis() - start
            logger.info { "Warmup: completed in ${elapsed}ms" }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            logger.warn(e) { "Warmup: queries failed after ${elapsed}ms (non-fatal, first request may be slow)" }
        } finally {
            warmedUp.set(true)
        }
    }

    /**
     * Execute module-specific queries to compile and cache HQL/JPQL query plans.
     * The actual results don't matter — we just need the query plans cached.
     */
    protected abstract fun runWarmupQueries()

    override fun getResult(): Publisher<HealthResult> {
        val status = if (warmedUp.get()) HealthStatus.UP else HealthStatus.DOWN
        return Publisher { subscriber: Subscriber<in HealthResult> ->
            subscriber.onSubscribe(
                object : Subscription {
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
