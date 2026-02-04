package io.github.salomax.neotool.comms.template.substitution

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class VariableSubstitutorTest {
    private val substitutor = VariableSubstitutor()

    @Test
    fun `should substitute simple variable`() {
        val template = "Hello, {{name}}!"
        val variables = mapOf("name" to "World")
        val result = substitutor.substitute(template, variables)
        assertThat(result).isEqualTo("Hello, World!")
    }

    @Test
    fun `should substitute multiple variables`() {
        val template = "Hello, {{firstName}} {{lastName}}!"
        val variables = mapOf("firstName" to "John", "lastName" to "Doe")
        val result = substitutor.substitute(template, variables)
        assertThat(result).isEqualTo("Hello, John Doe!")
    }

    @Test
    fun `should validate required variables`() {
        val template = "Hello, {{name}}!"
        val variables = mapOf<String, Any?>()
        val required = setOf("name")
        val missing = substitutor.validateRequiredVariables(template, variables, required)
        assertThat(missing).containsExactly("name")
    }

    @Test
    fun `should validate required variables when variable is null`() {
        val template = "Hello, {{name}}!"
        val variables = mapOf<String, Any?>("name" to null)
        val required = setOf("name")
        val missing = substitutor.validateRequiredVariables(template, variables, required)
        assertThat(missing).containsExactly("name")
    }

    @Test
    fun `should find unused variables`() {
        val template = "Hello, {{name}}!"
        val variables = mapOf("name" to "World", "unused" to "value")
        val unused = substitutor.findUnusedVariables(template, variables)
        assertThat(unused).containsExactly("unused")
    }

    @Test
    fun `substitute throws when template causes exception`() {
        val invalidTemplate = "{{#section}}" // unclosed section can cause runtime error
        val variables = mapOf<String, Any?>("section" to emptyList<Any>())

        assertThatThrownBy { substitutor.substitute(invalidTemplate, variables) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Variable substitution failed")
    }

    @Test
    fun `MissingVariableException has message with variable names`() {
        val ex = MissingVariableException(listOf("a", "b"))
        assertThat(ex.missingVariables).containsExactly("a", "b")
        assertThat(ex.message).contains("a")
        assertThat(ex.message).contains("b")
    }

    @Test
    fun `VariableSubstitutionException preserves message and cause`() {
        val cause = RuntimeException("root")
        val ex = VariableSubstitutionException("substitution failed", cause)
        assertThat(ex.message).isEqualTo("substitution failed")
        assertThat(ex.cause).isSameAs(cause)
    }
}
