package io.github.salomax.neotool.common.security.principal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("AuthContext Tests")
class AuthContextTest {
    @Test
    fun `should keep default optional fields as null`() {
        val userId = UUID.randomUUID()

        val context =
            AuthContext(
                userId = userId,
                email = "user@example.com",
                displayName = null,
                roles = listOf("USER"),
                permissions = listOf("banking:read"),
            )

        assertThat(context.userId).isEqualTo(userId)
        assertThat(context.email).isEqualTo("user@example.com")
        assertThat(context.roles).containsExactly("USER")
        assertThat(context.permissions).containsExactly("banking:read")
        assertThat(context.currentAccountId).isNull()
        assertThat(context.accounts).isNull()
        assertThat(context.sessionVersion).isNull()
    }

    @Test
    fun `should keep all provided values`() {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val accountSummary = AccountSummary(accountId = accountId, role = "OWNER")

        val context =
            AuthContext(
                userId = userId,
                email = "owner@example.com",
                displayName = "Owner User",
                roles = listOf("ADMIN"),
                permissions = listOf("security:user:write"),
                currentAccountId = accountId,
                accounts = listOf(accountSummary),
                sessionVersion = 3L,
            )

        assertThat(context.displayName).isEqualTo("Owner User")
        assertThat(context.currentAccountId).isEqualTo(accountId)
        assertThat(context.accounts).containsExactly(accountSummary)
        assertThat(context.sessionVersion).isEqualTo(3L)
    }
}
