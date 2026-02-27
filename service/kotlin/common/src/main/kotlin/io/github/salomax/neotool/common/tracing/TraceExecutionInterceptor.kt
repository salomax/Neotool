package io.github.salomax.neotool.common.tracing

import io.micronaut.aop.Around
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Type
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import jakarta.inject.Singleton

/**
 * Traces service/repository method execution with low-cardinality span attributes.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Around
@Type(TraceExecutionInterceptor::class)
annotation class TraceExecution(
    val layer: String = "service",
)

@Singleton
class TraceExecutionInterceptor : MethodInterceptor<Any, Any> {
    private val tracer = GlobalOpenTelemetry.getTracer("io.github.salomax.neotool.execution")

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        val declaringType = context.declaringType.simpleName
        val methodName = context.methodName
        val packageName = context.declaringType.packageName
        val annotation = context.getAnnotation(TraceExecution::class.java)
        val layer = annotation?.stringValue("layer")?.orElse("service") ?: "service"
        val spanName = "$layer.$declaringType.$methodName"

        val span =
            tracer
                .spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan()

        try {
            span.setAttribute("neotool.execution.layer", layer)
            span.setAttribute("code.namespace", packageName)
            span.setAttribute("code.function", methodName)
            span.setAttribute("neotool.execution.class", declaringType)
            span.makeCurrent().use { return context.proceed() }
        } catch (t: Throwable) {
            span.recordException(t)
            span.setStatus(StatusCode.ERROR)
            throw t
        } finally {
            span.end()
        }
    }
}
