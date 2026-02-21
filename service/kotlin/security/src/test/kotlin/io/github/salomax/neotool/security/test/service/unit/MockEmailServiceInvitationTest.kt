package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.config.EmailConfig
import io.github.salomax.neotool.security.service.email.MockEmailService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for MockEmailService invitation email (account.invitation).
 * Verifies that invitation email data is stored correctly and template placeholders are defined.
 */
@DisplayName("MockEmailService Invitation Email")
class MockEmailServiceInvitationTest {

    private val emailConfig = EmailConfig(
        from = "noreply@test.com",
        frontendUrl = "http://localhost:3000",
    )
    private val mockEmailService = MockEmailService(emailConfig)

    @Test
    fun `sendAccountInvitationEmail stores data and getLastSentInvitationEmail returns it`() {
        val to = "invitee@example.com"
        val inviterDisplayName = "Alice"
        val accountName = "Acme Corp"
        val acceptLink = "http://localhost:3000/invitations/accept?token=secret-token"
        val expiryDays = 7L
        val role = "MEMBER"
        val locale = "en"

        mockEmailService.sendAccountInvitationEmail(
            to = to,
            inviterDisplayName = inviterDisplayName,
            accountName = accountName,
            acceptLink = acceptLink,
            expiryDays = expiryDays,
            role = role,
            locale = locale,
        )

        val sent = mockEmailService.getLastSentInvitationEmail(to)
        assertThat(sent).isNotNull
        assertThat(sent!!.to).isEqualTo(to)
        assertThat(sent.inviterDisplayName).isEqualTo(inviterDisplayName)
        assertThat(sent.accountName).isEqualTo(accountName)
        assertThat(sent.acceptLink).isEqualTo(acceptLink)
        assertThat(sent.expiryDays).isEqualTo(expiryDays)
        assertThat(sent.role).isEqualTo(role)
        assertThat(sent.locale).isEqualTo(locale)
    }

    @Test
    fun `sendAccountInvitationEmail with null inviterDisplayName stores null`() {
        mockEmailService.sendAccountInvitationEmail(
            to = "other@example.com",
            inviterDisplayName = null,
            accountName = "Team",
            acceptLink = "http://localhost/invite?t=1",
            expiryDays = 7L,
            role = "VIEWER",
            locale = "en",
        )

        val sent = mockEmailService.getLastSentInvitationEmail("other@example.com")
        assertThat(sent).isNotNull
        assertThat(sent!!.inviterDisplayName).isNull()
    }

    @Test
    fun `invitation template en contains required placeholders for rendering`() {
        val templatePath = "/emails/invitation/en.html"
        val resource = javaClass.getResourceAsStream(templatePath)
            ?: throw IllegalStateException("Invitation template not found: $templatePath")
        val template = resource.bufferedReader().use { it.readText() }

        assertThat(template).contains("{{ACCEPT_LINK}}")
        assertThat(template).contains("{{ACCOUNT_NAME}}")
        assertThat(template).contains("{{INVITER_DISPLAY_NAME}}")
        assertThat(template).contains("{{EXPIRY_DAYS}}")
        assertThat(template).contains("{{ROLE}}")
    }
}
