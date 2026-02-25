package io.github.salomax.neotool.security.test.model

import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("AccountEntity Tests")
class AccountEntityTest {
    @Nested
    @DisplayName("construction and mapping")
    inner class ConstructionTests {
        @Test
        fun `should create entity with PERSONAL type`() {
            val entity = AccountEntity(
                accountName = "My Account",
                accountType = AccountType.PERSONAL,
                ownerUserId = UUID.randomUUID(),
            )
            assertThat(entity.accountType).isEqualTo(AccountType.PERSONAL)
            assertThat(entity.accountStatus).isEqualTo(AccountStatus.ACTIVE)
            assertThat(entity.accountName).isEqualTo("My Account")
        }

        @Test
        fun `should create entity with FAMILY type`() {
            val entity = AccountEntity(
                accountName = "Family Account",
                accountType = AccountType.FAMILY,
            )
            assertThat(entity.accountType).isEqualTo(AccountType.FAMILY)
        }

        @Test
        fun `should create entity with BUSINESS type`() {
            val entity = AccountEntity(
                accountName = "Acme Corp",
                accountType = AccountType.BUSINESS,
            )
            assertThat(entity.accountType).isEqualTo(AccountType.BUSINESS)
        }

        @Test
        fun `should map all account status values`() {
            listOf(AccountStatus.ACTIVE, AccountStatus.SUSPENDED, AccountStatus.DELETED).forEach { status ->
                val entity = AccountEntity(
                    accountName = "Test",
                    accountType = AccountType.PERSONAL,
                    accountStatus = status,
                )
                assertThat(entity.accountStatus).isEqualTo(status)
            }
        }

        @Test
        fun `should preserve id version and timestamps`() {
            val id = UUID.randomUUID()
            val createdAt = Instant.now().minusSeconds(3600)
            val updatedAt = Instant.now()
            val entity = AccountEntity(
                id = id,
                accountName = "Test",
                accountType = AccountType.PERSONAL,
                createdAt = createdAt,
                updatedAt = updatedAt,
                version = 3L,
            )
            assertThat(entity.id).isEqualTo(id)
            assertThat(entity.createdAt).isEqualTo(createdAt)
            assertThat(entity.updatedAt).isEqualTo(updatedAt)
            assertThat(entity.version).isEqualTo(3L)
        }

        @Test
        fun `should allow null owner_user_id and deleted_at`() {
            val entity = AccountEntity(
                accountName = "Test",
                accountType = AccountType.FAMILY,
                ownerUserId = null,
                deletedAt = null,
            )
            assertThat(entity.ownerUserId).isNull()
            assertThat(entity.deletedAt).isNull()
        }

        @Test
        fun `should allow set deleted_at for soft delete`() {
            val entity = AccountEntity(
                accountName = "Test",
                accountType = AccountType.PERSONAL,
            )
            val now = Instant.now()
            entity.deletedAt = now
            assertThat(entity.deletedAt).isEqualTo(now)
        }
    }
}
