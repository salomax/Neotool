package io.github.salomax.neotool.comms.template.test

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateContent
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.domain.TemplateMetadata
import io.github.salomax.neotool.comms.template.domain.VariableDefinition
import java.util.Locale

/**
 * Test utilities for template testing.
 */
object TemplateTestUtils {
    /**
     * Create a simple email template for testing.
     */
    fun createTestEmailTemplate(
        key: String = "test.email",
        subject: String = "Test Subject",
        body: String = "Test Body",
        variables: List<VariableDefinition> = emptyList(),
    ): TemplateDefinition {
        return TemplateDefinition(
            key = key,
            channel = Channel.EMAIL,
            metadata = TemplateMetadata(name = "Test Email Template"),
            variables = variables,
            locales =
                mapOf(
                    Locale.ENGLISH to
                        TemplateContent(
                            locale = Locale.ENGLISH,
                            subject = subject,
                            body = body,
                        ),
                ),
            defaultLocale = Locale.ENGLISH,
        )
    }

    /**
     * Create a template with variables for testing.
     */
    fun createTemplateWithVariables(
        key: String,
        channel: Channel,
        variables: List<VariableDefinition>,
        subjectTemplate: String,
        bodyTemplate: String,
    ): TemplateDefinition {
        return TemplateDefinition(
            key = key,
            channel = channel,
            metadata = TemplateMetadata(name = "Test Template"),
            variables = variables,
            locales =
                mapOf(
                    Locale.ENGLISH to
                        TemplateContent(
                            locale = Locale.ENGLISH,
                            subject = subjectTemplate,
                            body = bodyTemplate,
                        ),
                ),
            defaultLocale = Locale.ENGLISH,
        )
    }

    /**
     * Create an email template with HTML and optional channel config for testing CSS inlining.
     */
    fun createEmailTemplateWithHtml(
        key: String = "test.email",
        subject: String = "Test Subject",
        htmlBody: String = "<p>Hello</p>",
        variables: List<VariableDefinition> = emptyList(),
        channelConfig: Map<String, Any> = emptyMap(),
    ): TemplateDefinition {
        return TemplateDefinition(
            key = key,
            channel = Channel.EMAIL,
            metadata = TemplateMetadata(name = "Test Email Template"),
            variables = variables,
            locales =
                mapOf(
                    Locale.ENGLISH to
                        TemplateContent(
                            locale = Locale.ENGLISH,
                            subject = subject,
                            body = htmlBody,
                            channelConfig = channelConfig,
                        ),
                ),
            defaultLocale = Locale.ENGLISH,
        )
    }
}
