package io.github.salomax.neotool.security.test.model

import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.MembershipStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("AccountMembershipEntity Tests")
class AccountMembershipEntityTest {
    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Nested
    @DisplayName("construction and mapping")
    inner class ConstructionTests {
        @Test
        fun `should create entity with all account roles`() {
            listOf(AccountRole.OWNER, AccountRole.ADMIN, AccountRole.MEMBER, AccountRole.VIEWER).forEach { role ->
                val entity = AccountMembershipEntity(
                    accountId = accountId,
                    userId = userId,
                    accountRole = role,
                    membershipStatus = MembershipStatus.ACTIVE,
                )
                assertThat(entity.accountRole).isEqualTo(role)
            }
        }

        @Test
        fun `should create entity with all membership statuses`() {
            listOf(MembershipStatus.PENDING, MembershipStatus.ACTIVE, MembershipStatus.REMOVED).forEach { status ->
                val entity = AccountMembershipEntity(
                    accountId = accountId,
                    userId = userId,
                    accountRole = AccountRole.MEMBER,
                    membershipStatus = status,
                )
                assertThat(entity.membershipStatus).isEqualTo(status)
            }
        }

        @Test
        fun `should default membership_status to PENDING and is_default to false`() {
            val entity = AccountMembershipEntity(
                accountId = accountId,
                userId = userId,
                accountRole = AccountRole.MEMBER,
            )
            assertThat(entity.membershipStatus).isEqualTo(MembershipStatus.PENDING)
            assertThat(entity.isDefault).isFalse()
        }

        @Test
        fun `should allow is_default true when membership_status is ACTIVE`() {
            val entity = AccountMembershipEntity(
                accountId = accountId,
                userId = userId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = true,
            )
            assertThat(entity.isDefault).isTrue()
            assertThat(entity.membershipStatus).isEqualTo(MembershipStatus.ACTIVE)
        }

        @Test
        fun `default membership invariant - is_default true requires ACTIVE for DB constraint`() {
            val entity = AccountMembershipEntity(
                accountId = accountId,
                userId = userId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = true,
            )
            assertThat(entity.isDefault).isTrue()
            assertThat(entity.membershipStatus).isEqualTo(MembershipStatus.ACTIVE)
        }

        @Test
        fun `should preserve invitation and removal fields`() {
            val invitedBy = UUID.randomUUID()
            val removedBy = UUID.randomUUID()
            val invitedAt = Instant.now().minusSeconds(86400)
            val joinedAt = Instant.now()
            val removedAt = Instant.now()
            val entity = AccountMembershipEntity(
                accountId = accountId,
                userId = userId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.REMOVED,
                joinedAt = joinedAt,
                invitedBy = invitedBy,
                invitedAt = invitedAt,
                invitationToken = "token-123",
                invitationExpiresAt = Instant.now().plusSeconds(604800),
                removedAt = removedAt,
                removedBy = removedBy,
            )
            assertThat(entity.invitedBy).isEqualTo(invitedBy)
            assertThat(entity.invitedAt).isEqualTo(invitedAt)
            assertThat(entity.joinedAt).isEqualTo(joinedAt)
            assertThat(entity.invitationToken).isEqualTo("token-123")
            assertThat(entity.removedAt).isEqualTo(removedAt)
            assertThat(entity.removedBy).isEqualTo(removedBy)
        }

        @Test
        fun `should preserve id and version`() {
            val id = UUID.randomUUID()
            val entity = AccountMembershipEntity(
                id = id,
                accountId = accountId,
                userId = userId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                version = 2L,
            )
            assertThat(entity.id).isEqualTo(id)
            assertThat(entity.version).isEqualTo(2L)
        }
    }
}
