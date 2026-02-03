package io.github.salomax.neotool.comms.template.registry

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateContent
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.domain.TemplateMetadata
import io.github.salomax.neotool.comms.template.domain.VariableDefinition
import io.github.salomax.neotool.comms.template.domain.VariableType
import io.github.salomax.neotool.comms.template.test.TemplateTestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Locale

class TemplateValidatorTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `validate returns empty list for valid template`() {
        val template = TemplateTestUtils.createTestEmailTemplate()
        val errors = TemplateValidator.validate(template, tempDir)
        assertThat(errors).isEmpty()
    }

    @Test
    fun `validate returns error when key is blank`() {
        val template =
            TemplateDefinition(
                key = "",
                channel = Channel.EMAIL,
                metadata = TemplateMetadata(name = "Test"),
                variables = emptyList(),
                locales =
                    mapOf(
                        Locale.ENGLISH to
                            TemplateContent(
                                locale = Locale.ENGLISH,
                                subject = "Subject",
                                body = "Body",
                            ),
                    ),
                defaultLocale = Locale.ENGLISH,
            )
        val errors = TemplateValidator.validate(template, tempDir)
        assertThat(errors).anyMatch { it.field == "key" && it.message.contains("blank") }
    }

    @Test
    fun `validate returns error when metadata name is blank`() {
        val template =
            TemplateDefinition(
                key = "test",
                channel = Channel.EMAIL,
                metadata = TemplateMetadata(name = ""),
                variables = emptyList(),
                locales =
                    mapOf(
                        Locale.ENGLISH to
                            TemplateContent(
                                locale = Locale.ENGLISH,
                                subject = "Subject",
                                body = "Body",
                            ),
                    ),
                defaultLocale = Locale.ENGLISH,
            )
        val errors = TemplateValidator.validate(template, tempDir)
        assertThat(errors).anyMatch { it.field == "metadata.name" }
    }

    @Test
    fun `validate returns error when locales is empty`() {
        val template =
            TemplateDefinition(
                key = "test",
                channel = Channel.EMAIL,
                metadata = TemplateMetadata(name = "Test"),
                variables = emptyList(),
                locales = emptyMap(),
                defaultLocale = Locale.ENGLISH,
            )
        val errors = TemplateValidator.validate(template, tempDir)
        assertThat(errors).anyMatch { it.field == "locales" }
    }

    @Test
    fun `validate returns error when default locale not in locales`() {
        val template =
            TemplateDefinition(
                key = "test",
                channel = Channel.EMAIL,
                metadata = TemplateMetadata(name = "Test"),
                variables = emptyList(),
                locales =
                    mapOf(
                        Locale.GERMAN to
                            TemplateContent(
                                locale = Locale.GERMAN,
                                subject = "Subject",
                                body = "Body",
                            ),
                    ),
                defaultLocale = Locale.ENGLISH,
            )
        val errors = TemplateValidator.validate(template, tempDir)
        assertThat(errors).anyMatch { it.field == "defaultLocale" }
    }

    @Test
    fun `validate returns error when body is blank for locale`() {
        val template =
            TemplateDefinition(
                key = "test",
                channel = Channel.EMAIL,
                metadata = TemplateMetadata(name = "Test"),
                variables = emptyList(),
                locales =
                    mapOf(
                        Locale.ENGLISH to
                            TemplateContent(
                                locale = Locale.ENGLISH,
                                subject = "Subject",
                                body = "",
                            ),
                    ),
                defaultLocale = Locale.ENGLISH,
            )
        val errors = TemplateValidator.validate(template, tempDir)
        assertThat(errors).anyMatch { it.message.contains("Body content") }
    }

    @Test
    fun `validate returns error when variable name is blank`() {
        val template =
            TemplateDefinition(
                key = "test",
                channel = Channel.EMAIL,
                metadata = TemplateMetadata(name = "Test"),
                variables = listOf(VariableDefinition("", VariableType.STRING, required = true)),
                locales =
                    mapOf(
                        Locale.ENGLISH to
                            TemplateContent(
                                locale = Locale.ENGLISH,
                                subject = "Subject",
                                body = "Body",
                            ),
                    ),
                defaultLocale = Locale.ENGLISH,
            )
        val errors = TemplateValidator.validate(template, tempDir)
        assertThat(errors).anyMatch { it.field == "variables[].name" }
    }

    @Test
    fun `validateAll returns duplicate key errors when same key in same channel`() {
        val template = TemplateTestUtils.createTestEmailTemplate("dup")
        val pair1 = template to tempDir
        val pair2 = template to tempDir
        val errors = TemplateValidator.validateAll(listOf(pair1, pair2))
        assertThat(errors["dup"]).isNotEmpty
        assertThat(errors["dup"]!!.any { it.message.contains("Duplicate") }).isTrue
    }

    @Test
    fun `validateAll merges validation errors with duplicate key errors`() {
        val invalidTemplate =
            TemplateDefinition(
                key = "bad",
                channel = Channel.EMAIL,
                metadata = TemplateMetadata(name = ""),
                variables = emptyList(),
                locales = emptyMap(),
                defaultLocale = Locale.ENGLISH,
            )
        val errors = TemplateValidator.validateAll(listOf(invalidTemplate to tempDir))
        assertThat(errors["bad"]).isNotEmpty
    }
}
