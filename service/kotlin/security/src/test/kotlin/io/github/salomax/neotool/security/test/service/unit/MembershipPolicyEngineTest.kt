package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.service.management.membership.ActorMembershipContext
import io.github.salomax.neotool.security.service.management.membership.MembershipOperation
import io.github.salomax.neotool.security.service.management.membership.MembershipPolicyEngine
import io.github.salomax.neotool.security.service.management.membership.MembershipPolicyResult
import io.github.salomax.neotool.security.service.management.membership.NewOwnerContext
import io.github.salomax.neotool.security.service.management.membership.TargetMemberContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MembershipPolicyEngine Unit Tests")
class MembershipPolicyEngineTest {

    private lateinit var engine: MembershipPolicyEngine

    @BeforeEach
    fun setUp() {
        engine = MembershipPolicyEngine()
    }

    private fun actor(role: AccountRole, status: MembershipStatus = MembershipStatus.ACTIVE) =
        ActorMembershipContext(role, status)

    private fun target(role: AccountRole, status: MembershipStatus, isSelf: Boolean) =
        TargetMemberContext(role, status, isSelf)

    private fun newOwner(role: AccountRole, status: MembershipStatus = MembershipStatus.ACTIVE) =
        NewOwnerContext(role, status)

    @Nested
    @DisplayName("UPDATE_ACCOUNT")
    inner class UpdateAccountTests {
        @Test
        fun `OWNER ACTIVE allows update`() {
            val result = engine.evaluate(MembershipOperation.UPDATE_ACCOUNT, actor(AccountRole.OWNER))
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `no membership denies with NOT_ACCOUNT_MEMBER`() {
            val result = engine.evaluate(MembershipOperation.UPDATE_ACCOUNT, null)
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER))
        }

        @Test
        fun `PENDING membership denies with NOT_ACCOUNT_MEMBER`() {
            val result = engine.evaluate(MembershipOperation.UPDATE_ACCOUNT, actor(AccountRole.OWNER, MembershipStatus.PENDING))
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER))
        }

        @Test
        fun `ADMIN denies with ACCOUNT_ROLE_INSUFFICIENT`() {
            val result = engine.evaluate(MembershipOperation.UPDATE_ACCOUNT, actor(AccountRole.ADMIN))
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))
        }

        @Test
        fun `MEMBER denies with ACCOUNT_ROLE_INSUFFICIENT`() {
            val result = engine.evaluate(MembershipOperation.UPDATE_ACCOUNT, actor(AccountRole.MEMBER))
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))
        }

        @Test
        fun `VIEWER denies with ACCOUNT_ROLE_INSUFFICIENT`() {
            val result = engine.evaluate(MembershipOperation.UPDATE_ACCOUNT, actor(AccountRole.VIEWER))
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))
        }
    }

    @Nested
    @DisplayName("DELETE_ACCOUNT")
    inner class DeleteAccountTests {
        @Test
        fun `OWNER ACTIVE allows delete`() {
            val result = engine.evaluate(MembershipOperation.DELETE_ACCOUNT, actor(AccountRole.OWNER))
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `no membership denies with NOT_ACCOUNT_MEMBER`() {
            val result = engine.evaluate(MembershipOperation.DELETE_ACCOUNT, null)
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER))
        }

        @Test
        fun `ADMIN denies with ACCOUNT_ROLE_INSUFFICIENT`() {
            val result = engine.evaluate(MembershipOperation.DELETE_ACCOUNT, actor(AccountRole.ADMIN))
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))
        }
    }

    @Nested
    @DisplayName("INVITE")
    inner class InviteTests {
        @Test
        fun `OWNER allows invite`() {
            val result = engine.evaluate(MembershipOperation.INVITE, actor(AccountRole.OWNER))
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `ADMIN allows invite`() {
            val result = engine.evaluate(MembershipOperation.INVITE, actor(AccountRole.ADMIN))
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `no membership denies with NOT_ACCOUNT_MEMBER`() {
            val result = engine.evaluate(MembershipOperation.INVITE, null)
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER))
        }

        @Test
        fun `MEMBER denies with ACCOUNT_ROLE_INSUFFICIENT`() {
            val result = engine.evaluate(MembershipOperation.INVITE, actor(AccountRole.MEMBER))
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))
        }

        @Test
        fun `VIEWER denies with ACCOUNT_ROLE_INSUFFICIENT`() {
            val result = engine.evaluate(MembershipOperation.INVITE, actor(AccountRole.VIEWER))
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))
        }
    }

    @Nested
    @DisplayName("CANCEL_INVITATION")
    inner class CancelInvitationTests {
        @Test
        fun `OWNER and PENDING target allows cancel`() {
            val result = engine.evaluate(
                MembershipOperation.CANCEL_INVITATION,
                actor(AccountRole.OWNER),
                target = target(AccountRole.MEMBER, MembershipStatus.PENDING, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `ADMIN and PENDING target allows cancel`() {
            val result = engine.evaluate(
                MembershipOperation.CANCEL_INVITATION,
                actor(AccountRole.ADMIN),
                target = target(AccountRole.MEMBER, MembershipStatus.PENDING, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `no membership denies with NOT_ACCOUNT_MEMBER`() {
            val result = engine.evaluate(
                MembershipOperation.CANCEL_INVITATION,
                null,
                target = target(AccountRole.MEMBER, MembershipStatus.PENDING, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER))
        }

        @Test
        fun `target not PENDING denies with NOT_PENDING_INVITATION`() {
            val result = engine.evaluate(
                MembershipOperation.CANCEL_INVITATION,
                actor(AccountRole.OWNER),
                target = target(AccountRole.MEMBER, MembershipStatus.ACTIVE, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.NOT_PENDING_INVITATION))
        }

        @Test
        fun `null target denies with NOT_PENDING_INVITATION`() {
            val result = engine.evaluate(MembershipOperation.CANCEL_INVITATION, actor(AccountRole.OWNER), target = null)
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.NOT_PENDING_INVITATION))
        }
    }

    @Nested
    @DisplayName("CHANGE_ROLE")
    inner class ChangeRoleTests {
        @Test
        fun `OWNER changing other to ADMIN allows`() {
            val result = engine.evaluate(
                MembershipOperation.CHANGE_ROLE,
                actor(AccountRole.OWNER),
                target = target(AccountRole.MEMBER, MembershipStatus.ACTIVE, isSelf = false),
                newRole = AccountRole.ADMIN,
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `OWNER changing other to MEMBER allows`() {
            val result = engine.evaluate(
                MembershipOperation.CHANGE_ROLE,
                actor(AccountRole.OWNER),
                target = target(AccountRole.ADMIN, MembershipStatus.ACTIVE, isSelf = false),
                newRole = AccountRole.MEMBER,
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `OWNER changing other to VIEWER allows`() {
            val result = engine.evaluate(
                MembershipOperation.CHANGE_ROLE,
                actor(AccountRole.OWNER),
                target = target(AccountRole.MEMBER, MembershipStatus.ACTIVE, isSelf = false),
                newRole = AccountRole.VIEWER,
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `no membership denies with NOT_ACCOUNT_MEMBER`() {
            val result = engine.evaluate(
                MembershipOperation.CHANGE_ROLE,
                null,
                target = target(AccountRole.MEMBER, MembershipStatus.ACTIVE, isSelf = false),
                newRole = AccountRole.ADMIN,
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER))
        }

        @Test
        fun `ADMIN denies with ACCOUNT_ROLE_INSUFFICIENT`() {
            val result = engine.evaluate(
                MembershipOperation.CHANGE_ROLE,
                actor(AccountRole.ADMIN),
                target = target(AccountRole.MEMBER, MembershipStatus.ACTIVE, isSelf = false),
                newRole = AccountRole.ADMIN,
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))
        }

        @Test
        fun `self change denies with SELF_OPERATION_FORBIDDEN`() {
            val result = engine.evaluate(
                MembershipOperation.CHANGE_ROLE,
                actor(AccountRole.OWNER),
                target = target(AccountRole.OWNER, MembershipStatus.ACTIVE, isSelf = true),
                newRole = AccountRole.ADMIN,
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.SELF_OPERATION_FORBIDDEN))
        }

        @Test
        fun `newRole OWNER denies with TARGET_NOT_ELIGIBLE`() {
            val result = engine.evaluate(
                MembershipOperation.CHANGE_ROLE,
                actor(AccountRole.OWNER),
                target = target(AccountRole.MEMBER, MembershipStatus.ACTIVE, isSelf = false),
                newRole = AccountRole.OWNER,
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE))
        }

        @Test
        fun `null target denies with TARGET_NOT_ELIGIBLE`() {
            val result = engine.evaluate(
                MembershipOperation.CHANGE_ROLE,
                actor(AccountRole.OWNER),
                target = null,
                newRole = AccountRole.ADMIN,
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE))
        }
    }

    @Nested
    @DisplayName("REMOVE_MEMBER")
    inner class RemoveMemberTests {
        @Test
        fun `OWNER removing MEMBER allows`() {
            val result = engine.evaluate(
                MembershipOperation.REMOVE_MEMBER,
                actor(AccountRole.OWNER),
                target = target(AccountRole.MEMBER, MembershipStatus.ACTIVE, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `OWNER removing ADMIN allows`() {
            val result = engine.evaluate(
                MembershipOperation.REMOVE_MEMBER,
                actor(AccountRole.OWNER),
                target = target(AccountRole.ADMIN, MembershipStatus.ACTIVE, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `ADMIN removing MEMBER allows`() {
            val result = engine.evaluate(
                MembershipOperation.REMOVE_MEMBER,
                actor(AccountRole.ADMIN),
                target = target(AccountRole.MEMBER, MembershipStatus.ACTIVE, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `ADMIN removing VIEWER allows`() {
            val result = engine.evaluate(
                MembershipOperation.REMOVE_MEMBER,
                actor(AccountRole.ADMIN),
                target = target(AccountRole.VIEWER, MembershipStatus.ACTIVE, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `OWNER removing self denies with SELF_OPERATION_FORBIDDEN`() {
            val result = engine.evaluate(
                MembershipOperation.REMOVE_MEMBER,
                actor(AccountRole.OWNER),
                target = target(AccountRole.OWNER, MembershipStatus.ACTIVE, isSelf = true),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.SELF_OPERATION_FORBIDDEN))
        }

        @Test
        fun `ADMIN removing OWNER denies with TARGET_ROLE_TOO_HIGH`() {
            val result = engine.evaluate(
                MembershipOperation.REMOVE_MEMBER,
                actor(AccountRole.ADMIN),
                target = target(AccountRole.OWNER, MembershipStatus.ACTIVE, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_ROLE_TOO_HIGH))
        }

        @Test
        fun `ADMIN removing ADMIN denies with TARGET_ROLE_TOO_HIGH`() {
            val result = engine.evaluate(
                MembershipOperation.REMOVE_MEMBER,
                actor(AccountRole.ADMIN),
                target = target(AccountRole.ADMIN, MembershipStatus.ACTIVE, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_ROLE_TOO_HIGH))
        }

        @Test
        fun `MEMBER cannot remove anyone denies with ACCOUNT_ROLE_INSUFFICIENT`() {
            val result = engine.evaluate(
                MembershipOperation.REMOVE_MEMBER,
                actor(AccountRole.MEMBER),
                target = target(AccountRole.VIEWER, MembershipStatus.ACTIVE, isSelf = false),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))
        }
    }

    @Nested
    @DisplayName("LEAVE")
    inner class LeaveTests {
        @Test
        fun `MEMBER can leave`() {
            val result = engine.evaluate(MembershipOperation.LEAVE, actor(AccountRole.MEMBER), isSoleOwner = false)
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `ADMIN can leave`() {
            val result = engine.evaluate(MembershipOperation.LEAVE, actor(AccountRole.ADMIN), isSoleOwner = false)
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `VIEWER can leave`() {
            val result = engine.evaluate(MembershipOperation.LEAVE, actor(AccountRole.VIEWER), isSoleOwner = false)
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `OWNER when not sole owner can leave`() {
            val result = engine.evaluate(MembershipOperation.LEAVE, actor(AccountRole.OWNER), isSoleOwner = false)
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `OWNER when sole owner denies with SOLE_OWNER_CANNOT_LEAVE`() {
            val result = engine.evaluate(MembershipOperation.LEAVE, actor(AccountRole.OWNER), isSoleOwner = true)
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.SOLE_OWNER_CANNOT_LEAVE))
        }

        @Test
        fun `no membership denies with NOT_ACCOUNT_MEMBER`() {
            val result = engine.evaluate(MembershipOperation.LEAVE, null, isSoleOwner = false)
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER))
        }
    }

    @Nested
    @DisplayName("TRANSFER_OWNERSHIP")
    inner class TransferOwnershipTests {
        @Test
        fun `OWNER transferring to ADMIN allows`() {
            val result = engine.evaluate(
                MembershipOperation.TRANSFER_OWNERSHIP,
                actor(AccountRole.OWNER),
                newOwner = newOwner(AccountRole.ADMIN),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `OWNER transferring to MEMBER allows`() {
            val result = engine.evaluate(
                MembershipOperation.TRANSFER_OWNERSHIP,
                actor(AccountRole.OWNER),
                newOwner = newOwner(AccountRole.MEMBER),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Allowed)
        }

        @Test
        fun `no membership denies with NOT_ACCOUNT_MEMBER`() {
            val result = engine.evaluate(
                MembershipOperation.TRANSFER_OWNERSHIP,
                null,
                newOwner = newOwner(AccountRole.ADMIN),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER))
        }

        @Test
        fun `ADMIN denies with ACCOUNT_ROLE_INSUFFICIENT`() {
            val result = engine.evaluate(
                MembershipOperation.TRANSFER_OWNERSHIP,
                actor(AccountRole.ADMIN),
                newOwner = newOwner(AccountRole.MEMBER),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))
        }

        @Test
        fun `transfer to VIEWER denies with TARGET_NOT_ELIGIBLE`() {
            val result = engine.evaluate(
                MembershipOperation.TRANSFER_OWNERSHIP,
                actor(AccountRole.OWNER),
                newOwner = newOwner(AccountRole.VIEWER),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE))
        }

        @Test
        fun `transfer to non-ACTIVE new owner denies with TARGET_NOT_ELIGIBLE`() {
            val result = engine.evaluate(
                MembershipOperation.TRANSFER_OWNERSHIP,
                actor(AccountRole.OWNER),
                newOwner = NewOwnerContext(AccountRole.MEMBER, MembershipStatus.PENDING),
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE))
        }

        @Test
        fun `null new owner denies with TARGET_NOT_ELIGIBLE`() {
            val result = engine.evaluate(
                MembershipOperation.TRANSFER_OWNERSHIP,
                actor(AccountRole.OWNER),
                newOwner = null,
            )
            assertThat(result).isEqualTo(MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE))
        }
    }
}
