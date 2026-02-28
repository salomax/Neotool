package io.github.salomax.neotool.common.tracing

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.annotation.AnnotationValue
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

@DisplayName("TraceExecutionInterceptor Unit Tests")
class TraceExecutionInterceptorTest {
    private lateinit var openTelemetry: OpenTelemetry
    private lateinit var tracer: Tracer
    private lateinit var spanBuilder: SpanBuilder
    private lateinit var span: Span
    private lateinit var scope: Scope
    private lateinit var interceptor: TraceExecutionInterceptor

    private class SampleService

    @BeforeEach
    fun setUp() {
        openTelemetry = mock()
        tracer = mock()
        spanBuilder = mock()
        span = mock()
        scope = mock()

        whenever(openTelemetry.getTracer("io.github.salomax.neotool.execution")).thenReturn(tracer)
        whenever(tracer.spanBuilder(any())).thenReturn(spanBuilder)
        whenever(spanBuilder.setSpanKind(SpanKind.INTERNAL)).thenReturn(spanBuilder)
        whenever(spanBuilder.startSpan()).thenReturn(span)
        whenever(span.makeCurrent()).thenReturn(scope)

        interceptor = TraceExecutionInterceptor(openTelemetry)
    }

    @Nested
    @DisplayName("Annotation Metadata")
    inner class AnnotationMetadataTests {
        @TraceExecution
        private fun defaultLayerMethod() {}

        @TraceExecution(layer = "repository")
        private fun customLayerMethod() {}

        @Test
        fun `should use service as default layer`() {
            val annotation =
                this::class.java.getDeclaredMethod(
                    "defaultLayerMethod",
                ).getAnnotation(TraceExecution::class.java)
            assertThat(annotation.layer).isEqualTo("service")
        }

        @Test
        fun `should support custom layer value`() {
            val annotation =
                this::class.java.getDeclaredMethod(
                    "customLayerMethod",
                ).getAnnotation(TraceExecution::class.java)
            assertThat(annotation.layer).isEqualTo("repository")
        }
    }

    @Nested
    @DisplayName("Intercept")
    inner class InterceptTests {
        @Test
        fun `should proceed and enrich span with default layer when annotation is missing`() {
            val context = mock<MethodInvocationContext<Any, Any>>()
            whenever(context.declaringType).thenReturn(SampleService::class.java as Class<Any>)
            whenever(context.methodName).thenReturn("runTask")
            whenever(context.getAnnotation(TraceExecution::class.java)).thenReturn(null)
            whenever(context.proceed()).thenReturn("ok")

            val result = interceptor.intercept(context)

            assertThat(result).isEqualTo("ok")
            verify(tracer).spanBuilder("service.SampleService.runTask")
            verify(spanBuilder).setSpanKind(SpanKind.INTERNAL)
            verify(span).setAttribute("neotool.execution.layer", "service")
            verify(span).setAttribute("code.namespace", SampleService::class.java.packageName)
            verify(span).setAttribute("code.function", "runTask")
            verify(span).setAttribute("neotool.execution.class", "SampleService")
            verify(scope).close()
            verify(span).end()
        }

        @Test
        fun `should use custom layer from annotation`() {
            val context = mock<MethodInvocationContext<Any, Any>>()
            val annotationValue = mock<AnnotationValue<TraceExecution>>()
            whenever(context.declaringType).thenReturn(SampleService::class.java as Class<Any>)
            whenever(context.methodName).thenReturn("fetch")
            whenever(context.getAnnotation(TraceExecution::class.java)).thenReturn(annotationValue)
            whenever(annotationValue.stringValue("layer")).thenReturn(Optional.of("repository"))
            whenever(context.proceed()).thenReturn(42)

            val result = interceptor.intercept(context)

            assertThat(result).isEqualTo(42)
            verify(tracer).spanBuilder("repository.SampleService.fetch")
            verify(span).setAttribute("neotool.execution.layer", "repository")
            verify(span).end()
        }

        @Test
        fun `should record exception and set error status when proceed throws`() {
            val context = mock<MethodInvocationContext<Any, Any>>()
            val failure = IllegalStateException("boom")
            whenever(context.declaringType).thenReturn(SampleService::class.java as Class<Any>)
            whenever(context.methodName).thenReturn("explode")
            whenever(context.getAnnotation(TraceExecution::class.java)).thenReturn(null)
            whenever(context.proceed()).thenThrow(failure)

            assertThatThrownBy { interceptor.intercept(context) }
                .isSameAs(failure)

            verify(span).recordException(failure)
            verify(span).setStatus(StatusCode.ERROR)
            verify(span).end()
        }

        @Test
        fun `should fallback to default layer when annotation layer is empty`() {
            val context = mock<MethodInvocationContext<Any, Any>>()
            val annotationValue = mock<AnnotationValue<TraceExecution>>()
            whenever(context.declaringType).thenReturn(SampleService::class.java as Class<Any>)
            whenever(context.methodName).thenReturn("fetch")
            whenever(context.getAnnotation(TraceExecution::class.java)).thenReturn(annotationValue)
            whenever(annotationValue.stringValue("layer")).thenReturn(Optional.empty())
            whenever(context.proceed()).thenReturn("done")

            val result = interceptor.intercept(context)

            assertThat(result).isEqualTo("done")
            verify(tracer).spanBuilder("service.SampleService.fetch")
            verify(span).setAttribute("neotool.execution.layer", "service")
        }
    }
}
