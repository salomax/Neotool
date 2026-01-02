package io.github.salomax.neotool.security.test.integration

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.domain.GroupManagement
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.management.GroupManagementService
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Integration tests for Group Management Service.
 * Tests full flow from service layer to database, including user assignment functionality.
 */
@MicronautTest(startApplication = true)
@DisplayName("Group Management Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("group-management")
@Tag("database")
@TestMethodOrder(MethodOrderer.Random::class)
open class GroupManagementIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var groupMembershipRepository: GroupMembershipRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var groupManagementService: GroupManagementService

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var entityManager: EntityManager

    /**
     * Create a test user with database-generated UUID v7.
     * Returns the saved user with the generated ID.
     */
    private fun createTestUser(emailSuffix: String = ""): UserEntity {
        val user =
            SecurityTestDataBuilders.user(
                id = null,
                email = SecurityTestDataBuilders.uniqueEmail("group-test$emailSuffix"),
            )
        return entityManager.runTransaction {
            val savedUser = authenticationService.saveUser(user)
            entityManager.flush()
            savedUser
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            // Clean up in reverse order of dependencies
            groupMembershipRepository.deleteAll()
            groupRepository.deleteAll()
            userRepository.deleteAll()
            entityManager.flush()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Create Group with User Assignment")
    inner class CreateGroupWithUsersTests {
        @Test
        fun `should create group with user assignments`() {
            // Arrange
            val user1 = createTestUser("1")
            val user2 = createTestUser("2")
            val userId1 = user1.id!!
            val userId2 = user2.id!!
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "Test Group",
                    description = "Test Description",
                    userIds = listOf(userId1, userId2),
                )

            // Act
            val result = groupManagementService.createGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("Test Group")
            assertThat(result.description).isEqualTo("Test Description")

            // Verify memberships were created
            val groupId = result.id!!
            val memberships = groupMembershipRepository.findByGroupId(groupId)
            assertThat(memberships).hasSize(2)
            val membershipUserIds = memberships.map { it.userId }.toSet()
            assertThat(membershipUserIds).containsExactlyInAnyOrder(userId1, userId2)
            memberships.forEach { membership ->
                assertThat(membership.groupId).isEqualTo(groupId)
                assertThat(membership.membershipType.name).isEqualTo("MEMBER")
                assertThat(membership.validUntil).isNull()
            }
        }

        @Test
        fun `should create group without user assignments`() {
            // Arrange
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "Test Group",
                    userIds = null,
                )

            // Act
            val result = groupManagementService.createGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("Test Group")

            // Verify no memberships were created
            val groupId = result.id!!
            val memberships = groupMembershipRepository.findByGroupId(groupId)
            assertThat(memberships).isEmpty()
        }

        @Test
        fun `should create group with empty user assignments list`() {
            // Arrange
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "Test Group",
                    userIds = emptyList(),
                )

            // Act
            val result = groupManagementService.createGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("Test Group")

            // Verify no memberships were created
            val groupId = result.id!!
            val memberships = groupMembershipRepository.findByGroupId(groupId)
            assertThat(memberships).isEmpty()
        }

        @Test
        fun `should throw exception when creating group with invalid user IDs`() {
            // Arrange
            val validUser = createTestUser("1")
            val validUserId = validUser.id!!
            val invalidUserId = UUID.randomUUID()
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "Test Group",
                    userIds = listOf(validUserId, invalidUserId),
                )

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.createGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("users not found")
                assertThat(exception.message).contains(invalidUserId.toString())
                // The transaction is rolled back because the exception is thrown.
                // However, the transaction is not rolled back until the end of the test.
                // Thus, we need to rollback the transaction manually.
                entityManager.transaction.rollback()
            }

            entityManager.runTransaction {
                val groupOptional = groupRepository.findByName("Test Group")
                assertThat(groupOptional.isPresent).isFalse()
            }
        }
    }

    @Nested
    @DisplayName("Update Group with User Assignment")
    inner class UpdateGroupWithUsersTests {
        @Test
        fun `should update group to add users`() {
            // Arrange
            val user1 = createTestUser("1")
            val user2 = createTestUser("2")
            val userId1 = user1.id!!
            val userId2 = user2.id!!
            val group =
                groupManagementService.createGroup(
                    GroupManagement.CreateGroupCommand(
                        name = "Test Group",
                        userIds = listOf(userId1),
                    ),
                )

            // Act
            val result =
                groupManagementService.updateGroup(
                    GroupManagement.UpdateGroupCommand(
                        groupId = group.id!!,
                        name = "Test Group",
                        userIds = listOf(userId1, userId2),
                    ),
                )

            // Assert
            assertThat(result).isNotNull()
            val groupId = result.id!!
            val memberships = groupMembershipRepository.findByGroupId(groupId)
            assertThat(memberships).hasSize(2)
            val membershipUserIds = memberships.map { it.userId }.toSet()
            assertThat(membershipUserIds).containsExactlyInAnyOrder(userId1, userId2)
        }

        @Test
        fun `should update group to remove users`() {
            // Arrange
            val user1 = createTestUser("1")
            val user2 = createTestUser("2")
            val userId1 = user1.id!!
            val userId2 = user2.id!!
            val group =
                groupManagementService.createGroup(
                    GroupManagement.CreateGroupCommand(
                        name = "Test Group",
                        userIds = listOf(userId1, userId2),
                    ),
                )

            // Act
            val result =
                groupManagementService.updateGroup(
                    GroupManagement.UpdateGroupCommand(
                        groupId = group.id!!,
                        name = "Test Group",
                        // Remove user2
                        userIds = listOf(userId1),
                    ),
                )

            // Assert
            assertThat(result).isNotNull()
            val groupId = result.id!!
            val memberships = groupMembershipRepository.findByGroupId(groupId)
            assertThat(memberships).hasSize(1)
            assertThat(memberships.first().userId).isEqualTo(userId1)
        }

        @Test
        fun `should update group to replace all users`() {
            // Arrange
            val user1 = createTestUser("1")
            val user2 = createTestUser("2")
            val user3 = createTestUser("3")
            val userId1 = user1.id!!
            val userId2 = user2.id!!
            val userId3 = user3.id!!
            val group =
                groupManagementService.createGroup(
                    GroupManagement.CreateGroupCommand(
                        name = "Test Group",
                        userIds = listOf(userId1, userId2),
                    ),
                )

            // Act
            val result =
                groupManagementService.updateGroup(
                    GroupManagement.UpdateGroupCommand(
                        groupId = group.id!!,
                        name = "Test Group",
                        // Replace with user3
                        userIds = listOf(userId3),
                    ),
                )

            // Assert
            assertThat(result).isNotNull()
            val groupId = result.id!!
            val memberships = groupMembershipRepository.findByGroupId(groupId)
            assertThat(memberships).hasSize(1)
            assertThat(memberships.first().userId).isEqualTo(userId3)
        }

        @Test
        fun `should update group to remove all users`() {
            // Arrange
            val user1 = createTestUser("1")
            val user2 = createTestUser("2")
            val userId1 = user1.id!!
            val userId2 = user2.id!!
            val group =
                groupManagementService.createGroup(
                    GroupManagement.CreateGroupCommand(
                        name = "Test Group",
                        userIds = listOf(userId1, userId2),
                    ),
                )

            // Act
            val result =
                groupManagementService.updateGroup(
                    GroupManagement.UpdateGroupCommand(
                        groupId = group.id!!,
                        name = "Test Group",
                        // Remove all users
                        userIds = emptyList(),
                    ),
                )

            // Assert
            assertThat(result).isNotNull()
            val groupId = result.id!!
            val memberships = groupMembershipRepository.findByGroupId(groupId)
            assertThat(memberships).isEmpty()
        }

        @Test
        fun `should update group without changing memberships when userIds is null`() {
            // Arrange
            val user1 = createTestUser("1")
            val user2 = createTestUser("2")
            val userId1 = user1.id!!
            val userId2 = user2.id!!
            val group =
                groupManagementService.createGroup(
                    GroupManagement.CreateGroupCommand(
                        name = "Test Group",
                        userIds = listOf(userId1, userId2),
                    ),
                )

            // Act
            val result =
                groupManagementService.updateGroup(
                    GroupManagement.UpdateGroupCommand(
                        groupId = group.id!!,
                        name = "Updated Group Name",
                        // No change to memberships
                        userIds = null,
                    ),
                )

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("Updated Group Name")
            val resultGroupId = result.id!!
            val memberships = groupMembershipRepository.findByGroupId(resultGroupId)
            assertThat(memberships).hasSize(2) // Memberships unchanged
            val membershipUserIds = memberships.map { it.userId }.toSet()
            assertThat(membershipUserIds).containsExactlyInAnyOrder(userId1, userId2)
        }

        @Test
        fun `should throw exception when updating group with invalid user IDs`() {
            // Arrange
            val validUser = createTestUser("1")
            val validUserId = validUser.id!!
            val group =
                groupManagementService.createGroup(
                    GroupManagement.CreateGroupCommand(
                        name = "Test Group",
                        userIds = listOf(validUserId),
                    ),
                )
            val invalidUserId = UUID.randomUUID()

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.updateGroup(
                    GroupManagement.UpdateGroupCommand(
                        groupId = group.id!!,
                        name = "Test Group",
                        userIds = listOf(validUserId, invalidUserId),
                    ),
                )
            }.also { exception ->
                assertThat(exception.message).contains("users not found")
                assertThat(exception.message).contains(invalidUserId.toString())
            }

            // Verify memberships were not changed (transaction rolled back)
            val savedGroupId = group.id!!
            val memberships = groupMembershipRepository.findByGroupId(savedGroupId)
            assertThat(memberships).hasSize(1)
            assertThat(memberships.first().userId).isEqualTo(validUserId)
        }
    }
}
