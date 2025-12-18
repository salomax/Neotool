package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.GroupManagement
import io.github.salomax.neotool.security.model.rbac.GroupMembershipEntity
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.GroupRepositoryCustom
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.GroupManagementService
import io.github.salomax.neotool.security.service.GroupOrderBy
import io.github.salomax.neotool.security.service.GroupOrderField
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@DisplayName("GroupManagementService Unit Tests")
class GroupManagementServiceTest {
    private lateinit var groupRepository: GroupRepository
    private lateinit var groupSearchRepository: GroupRepositoryCustom
    private lateinit var groupMembershipRepository: GroupMembershipRepository
    private lateinit var groupRoleAssignmentRepository: GroupRoleAssignmentRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var userRepository: UserRepository
    private lateinit var groupManagementService: GroupManagementService

    @BeforeEach
    fun setUp() {
        groupRepository = mock()
        groupSearchRepository = mock()
        groupMembershipRepository = mock()
        groupRoleAssignmentRepository = mock()
        roleRepository = mock()
        userRepository = mock()
        groupManagementService =
            GroupManagementService(
                groupRepository,
                groupSearchRepository,
                groupMembershipRepository,
                groupRoleAssignmentRepository,
                roleRepository,
                userRepository,
            )
    }

    @Nested
    @DisplayName("List Groups")
    inner class ListGroupsTests {
        @Test
        fun `should list groups with default page size`() {
            // Arrange
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Group One")
            val group2 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Group Two")
            val defaultOrderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                groupSearchRepository.searchByName(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(listOf(group1, group2))
            whenever(groupSearchRepository.countByName(null))
                .thenReturn(2L)

            // Act
            val result = groupManagementService.searchGroups(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            assertThat(result.pageInfo.hasNextPage).isFalse()
            verify(
                groupSearchRepository,
            ).searchByName(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, defaultOrderBy)
        }

        @Test
        fun `should list groups with custom page size`() {
            // Arrange
            val first = 10
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Group One")
            val defaultOrderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            whenever(groupSearchRepository.searchByName(null, first + 1, null, defaultOrderBy))
                .thenReturn(listOf(group1))
            whenever(groupSearchRepository.countByName(null))
                .thenReturn(1L)

            // Act
            val result = groupManagementService.searchGroups(query = null, first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(groupSearchRepository).searchByName(null, first + 1, null, defaultOrderBy)
        }

        @Test
        fun `should enforce max page size`() {
            // Arrange
            val first = 200 // Exceeds MAX_PAGE_SIZE
            val defaultOrderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                groupSearchRepository.searchByName(null, PaginationConstants.MAX_PAGE_SIZE + 1, null, defaultOrderBy),
            )
                .thenReturn(emptyList())
            whenever(groupSearchRepository.countByName(null))
                .thenReturn(0L)

            // Act
            val result = groupManagementService.searchGroups(query = null, first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            verify(
                groupSearchRepository,
            ).searchByName(null, PaginationConstants.MAX_PAGE_SIZE + 1, null, defaultOrderBy)
        }

        @Test
        fun `should list groups with cursor pagination using legacy UUID cursor`() {
            // Arrange
            val afterId = UUID.randomUUID()
            val afterCursor = CursorEncoder.encodeCursor(afterId)
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Group One")
            val defaultOrderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            val compositeCursor = CompositeCursor(emptyMap(), afterId.toString())
            whenever(
                groupSearchRepository.searchByName(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    compositeCursor,
                    defaultOrderBy,
                ),
            )
                .thenReturn(listOf(group1))
            whenever(groupSearchRepository.countByName(null))
                .thenReturn(1L)

            // Act
            val result = groupManagementService.searchGroups(query = null, after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(
                groupSearchRepository,
            ).searchByName(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, compositeCursor, defaultOrderBy)
        }

        @Test
        fun `should indicate has more when results exceed page size`() {
            // Arrange
            val groups =
                (1..21).map {
                    SecurityTestDataBuilders.group(
                        id = UUID.randomUUID(),
                        name = "Group $it",
                    )
                }
            val defaultOrderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                groupSearchRepository.searchByName(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(groups)
            whenever(groupSearchRepository.countByName(null))
                .thenReturn(21L)

            // Act
            val result = groupManagementService.searchGroups(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(PaginationConstants.DEFAULT_PAGE_SIZE)
            assertThat(result.pageInfo.hasNextPage).isTrue()
        }

        @Test
        fun `should return empty connection when no groups exist`() {
            // Arrange
            val defaultOrderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                groupSearchRepository.searchByName(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(emptyList())
            whenever(groupSearchRepository.countByName(null))
                .thenReturn(0L)

            // Act
            val result = groupManagementService.searchGroups(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).isEmpty()
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
        }

        @Test
        fun `should throw exception for invalid cursor`() {
            // Arrange
            val invalidCursor = "invalid-cursor"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.searchGroups(query = null, after = invalidCursor)
            }
            verify(groupSearchRepository, never()).searchByName(any(), any(), any(), any())
        }

        @Test
        fun `should use default sort when orderBy is null`() {
            // Arrange
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Alpha")
            val group2 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Beta")
            val defaultOrderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                groupSearchRepository.searchByName(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(listOf(group1, group2))
            whenever(groupSearchRepository.countByName(null))
                .thenReturn(2L)

            // Act
            val result = groupManagementService.searchGroups(query = null, after = null, orderBy = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            verify(
                groupSearchRepository,
            ).searchByName(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, defaultOrderBy)
        }

        @Test
        fun `should sort by NAME DESC when specified`() {
            // Arrange
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Zeta")
            val group2 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Alpha")
            val orderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.DESC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            whenever(groupSearchRepository.searchByName(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, orderBy))
                .thenReturn(listOf(group1, group2))
            whenever(groupSearchRepository.countByName(null))
                .thenReturn(2L)

            // Act
            val result =
                groupManagementService.searchGroups(
                    query = null,
                    after = null,
                    orderBy = listOf(GroupOrderBy(GroupOrderField.NAME, OrderDirection.DESC)),
                )

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            verify(groupSearchRepository).searchByName(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, orderBy)
        }
    }

    @Nested
    @DisplayName("Search Groups")
    inner class SearchGroupsTests {
        @Test
        fun `should search groups by name`() {
            // Arrange
            val query = "admin"
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Admin Group")
            val defaultOrderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                groupSearchRepository.searchByName(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(listOf(group1))
            whenever(groupSearchRepository.countByName(query))
                .thenReturn(1L)

            // Act
            val result = groupManagementService.searchGroups(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            assertThat(result.edges.first().node.name).isEqualTo("Admin Group")
            assertThat(result.totalCount).isEqualTo(1L)
            verify(
                groupSearchRepository,
            ).searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, defaultOrderBy)
            verify(groupSearchRepository).countByName(query)
        }

        @Test
        fun `should search groups with pagination`() {
            // Arrange
            val query = "test"
            val afterId = UUID.randomUUID()
            val afterCursor = CursorEncoder.encodeCursor(afterId)
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Test Group")
            val defaultOrderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            val compositeCursor = CompositeCursor(emptyMap(), afterId.toString())
            whenever(
                groupSearchRepository.searchByName(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    compositeCursor,
                    defaultOrderBy,
                ),
            )
                .thenReturn(listOf(group1))
            whenever(groupSearchRepository.countByName(query))
                .thenReturn(3L)

            // Act
            val result = groupManagementService.searchGroups(query, after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            assertThat(result.totalCount).isEqualTo(3L)
            verify(
                groupSearchRepository,
            ).searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, compositeCursor, defaultOrderBy)
            verify(groupSearchRepository).countByName(query)
        }

        @Test
        fun `should return empty connection when no groups match search`() {
            // Arrange
            val query = "nonexistent"
            val defaultOrderBy =
                listOf(
                    GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                    GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                groupSearchRepository.searchByName(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(emptyList())
            whenever(groupSearchRepository.countByName(query))
                .thenReturn(0L)

            // Act
            val result = groupManagementService.searchGroups(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
            assertThat(result.totalCount).isEqualTo(0L)
            verify(groupSearchRepository).countByName(query)
        }

        @Test
        fun `should throw exception for invalid cursor in search`() {
            // Arrange
            val query = "test"
            val invalidCursor = "invalid-cursor"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.searchGroups(query, after = invalidCursor)
            }
            verify(groupSearchRepository, never()).searchByName(any(), any(), any(), any())
        }
    }

    @Nested
    @DisplayName("Create Group")
    inner class CreateGroupTests {
        @Test
        fun `should create group successfully`() {
            // Arrange
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "New Group",
                    description = "Group description",
                )
            val savedEntity =
                SecurityTestDataBuilders.group(
                    id = UUID.randomUUID(),
                    name = "New Group",
                    description = "Group description",
                )
            whenever(groupRepository.save(any())).thenReturn(savedEntity)

            // Act
            val result = groupManagementService.createGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("New Group")
            assertThat(result.description).isEqualTo("Group description")
            verify(groupRepository).save(any())
        }

        @Test
        fun `should create group without description`() {
            // Arrange
            val command = GroupManagement.CreateGroupCommand(name = "New Group")
            val savedEntity =
                SecurityTestDataBuilders.group(
                    id = UUID.randomUUID(),
                    name = "New Group",
                    description = null,
                )
            whenever(groupRepository.save(any())).thenReturn(savedEntity)

            // Act
            val result = groupManagementService.createGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("New Group")
            assertThat(result.description).isNull()
            verify(groupRepository).save(any())
            verify(groupMembershipRepository, never()).saveAll(any<List<GroupMembershipEntity>>())
        }

        @Test
        fun `should create group with user assignments`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "New Group",
                    userIds = listOf(userId1, userId2),
                )
            val savedEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "New Group",
                )
            val user1 = SecurityTestDataBuilders.user(id = userId1)
            val user2 = SecurityTestDataBuilders.user(id = userId2)
            whenever(groupRepository.save(any())).thenReturn(savedEntity)
            whenever(userRepository.findByIdIn(listOf(userId1, userId2))).thenReturn(listOf(user1, user2))
            whenever(
                groupMembershipRepository.saveAll(any<List<GroupMembershipEntity>>()),
            ).thenAnswer {
                @Suppress("UNCHECKED_CAST")
                it.arguments[0] as List<GroupMembershipEntity>
            }

            // Act
            val result = groupManagementService.createGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("New Group")
            verify(groupRepository).save(any())
            verify(userRepository).findByIdIn(listOf(userId1, userId2))
            val membershipCaptor = argumentCaptor<List<GroupMembershipEntity>>()
            verify(groupMembershipRepository).saveAll(membershipCaptor.capture())
            val savedMemberships = membershipCaptor.firstValue
            assertThat(savedMemberships).hasSize(2)
        }

        @Test
        fun `should create group with null userIds`() {
            // Arrange
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "New Group",
                    userIds = null,
                )
            val savedEntity =
                SecurityTestDataBuilders.group(
                    id = UUID.randomUUID(),
                    name = "New Group",
                )
            whenever(groupRepository.save(any())).thenReturn(savedEntity)

            // Act
            val result = groupManagementService.createGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("New Group")
            verify(groupRepository).save(any())
            verify(userRepository, never()).findByIdIn(any())
            verify(groupMembershipRepository, never()).saveAll(any<List<GroupMembershipEntity>>())
        }

        @Test
        fun `should create group with empty userIds list`() {
            // Arrange
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "New Group",
                    userIds = emptyList(),
                )
            val savedEntity =
                SecurityTestDataBuilders.group(
                    id = UUID.randomUUID(),
                    name = "New Group",
                )
            whenever(groupRepository.save(any())).thenReturn(savedEntity)

            // Act
            val result = groupManagementService.createGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("New Group")
            verify(groupRepository).save(any())
            verify(userRepository, never()).findByIdIn(any())
            verify(groupMembershipRepository, never()).saveAll(any<List<GroupMembershipEntity>>())
        }

        @Test
        fun `should throw exception when creating group with invalid user IDs`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "New Group",
                    userIds = listOf(userId1, userId2),
                )
            val savedEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "New Group",
                )
            val user1 = SecurityTestDataBuilders.user(id = userId1)
            // userId2 is missing
            whenever(groupRepository.save(any())).thenReturn(savedEntity)
            whenever(userRepository.findByIdIn(listOf(userId1, userId2))).thenReturn(listOf(user1))

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.createGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("users not found")
                assertThat(exception.message).contains(userId2.toString())
            }
            verify(groupRepository).save(any())
            verify(userRepository).findByIdIn(listOf(userId1, userId2))
            verify(groupMembershipRepository, never()).saveAll(any<List<GroupMembershipEntity>>())
        }
    }

    @Nested
    @DisplayName("Update Group")
    inner class UpdateGroupTests {
        @Test
        fun `should update group successfully`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val existingEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Old Name",
                    description = "Old Description",
                )
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = groupId,
                    name = "New Name",
                    description = "New Description",
                )
            val updatedEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "New Name",
                    description = "New Description",
                )
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.of(existingEntity))
            whenever(groupRepository.update(any())).thenReturn(updatedEntity)

            // Act
            val result = groupManagementService.updateGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(groupId)
            assertThat(result.name).isEqualTo("New Name")
            assertThat(result.description).isEqualTo("New Description")
            verify(groupRepository).findById(groupId)
            verify(groupRepository).update(any())
        }

        @Test
        fun `should throw exception when group not found`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = groupId,
                    name = "New Name",
                )
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.updateGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("Group not found")
            }
            verify(groupRepository).findById(groupId)
            verify(groupRepository, never()).update(any())
        }

        @Test
        fun `should update group with user assignments`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val existingEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Old Name",
                )
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = groupId,
                    name = "New Name",
                    userIds = listOf(userId1, userId2),
                )
            val updatedEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "New Name",
                )
            val user1 = SecurityTestDataBuilders.user(id = userId1)
            val user2 = SecurityTestDataBuilders.user(id = userId2)
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.of(existingEntity))
            whenever(groupRepository.update(any())).thenReturn(updatedEntity)
            whenever(userRepository.findByIdIn(listOf(userId1, userId2))).thenReturn(listOf(user1, user2))
            whenever(groupMembershipRepository.findByGroupId(groupId)).thenReturn(emptyList())
            whenever(
                groupMembershipRepository.saveAll(any<List<GroupMembershipEntity>>()),
            ).thenAnswer {
                @Suppress("UNCHECKED_CAST")
                it.arguments[0] as List<GroupMembershipEntity>
            }

            // Act
            val result = groupManagementService.updateGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("New Name")
            verify(groupRepository).findById(groupId)
            verify(groupRepository).update(any())
            verify(userRepository).findByIdIn(listOf(userId1, userId2))
            verify(groupMembershipRepository).findByGroupId(groupId)
            val membershipCaptor = argumentCaptor<List<GroupMembershipEntity>>()
            verify(groupMembershipRepository).saveAll(membershipCaptor.capture())
            val savedMemberships = membershipCaptor.firstValue
            assertThat(savedMemberships).hasSize(2)
        }

        @Test
        fun `should update group to add users`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val existingUserId = UUID.randomUUID()
            val newUserId = UUID.randomUUID()
            val existingEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Group",
                )
            val existingMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = existingUserId,
                    groupId = groupId,
                )
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = groupId,
                    name = "Group",
                    userIds = listOf(existingUserId, newUserId),
                )
            val updatedEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Group",
                )
            val existingUser = SecurityTestDataBuilders.user(id = existingUserId)
            val newUser = SecurityTestDataBuilders.user(id = newUserId)
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.of(existingEntity))
            whenever(groupRepository.update(any())).thenReturn(updatedEntity)
            whenever(
                userRepository.findByIdIn(
                    listOf(
                        existingUserId,
                        newUserId,
                    ),
                ),
            ).thenReturn(
                listOf(
                    existingUser,
                    newUser,
                ),
            )
            whenever(groupMembershipRepository.findByGroupId(groupId)).thenReturn(listOf(existingMembership))
            whenever(
                groupMembershipRepository.saveAll(any<List<GroupMembershipEntity>>()),
            ).thenAnswer {
                @Suppress("UNCHECKED_CAST")
                it.arguments[0] as List<GroupMembershipEntity>
            }

            // Act
            val result = groupManagementService.updateGroup(command)

            // Assert
            assertThat(result).isNotNull()
            verify(groupMembershipRepository).findByGroupId(groupId)
            val membershipCaptor = argumentCaptor<List<GroupMembershipEntity>>()
            verify(groupMembershipRepository).saveAll(membershipCaptor.capture())
            val savedMemberships = membershipCaptor.firstValue
            assertThat(savedMemberships).hasSize(1) // Only new user added
        }

        @Test
        fun `should update group to remove users`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val existingEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Group",
                )
            val membership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId1,
                    groupId = groupId,
                )
            val membership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId2,
                    groupId = groupId,
                )
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = groupId,
                    name = "Group",
                    // Remove userId2
                    userIds = listOf(userId1),
                )
            val updatedEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Group",
                )
            val user1 = SecurityTestDataBuilders.user(id = userId1)
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.of(existingEntity))
            whenever(groupRepository.update(any())).thenReturn(updatedEntity)
            whenever(userRepository.findByIdIn(listOf(userId1))).thenReturn(listOf(user1))
            whenever(groupMembershipRepository.findByGroupId(groupId)).thenReturn(listOf(membership1, membership2))
            whenever(
                groupMembershipRepository.deleteAll(any<List<GroupMembershipEntity>>()),
            ).thenAnswer {
                @Suppress("UNCHECKED_CAST")
                it.arguments[0] as List<GroupMembershipEntity>
            }

            // Act
            val result = groupManagementService.updateGroup(command)

            // Assert
            assertThat(result).isNotNull()
            verify(groupMembershipRepository).findByGroupId(groupId)
            val deleteCaptor = argumentCaptor<List<GroupMembershipEntity>>()
            verify(groupMembershipRepository).deleteAll(deleteCaptor.capture())
            val deletedMemberships = deleteCaptor.firstValue
            assertThat(deletedMemberships).hasSize(1) // Only userId2 removed
        }

        @Test
        fun `should update group to remove all users`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val existingEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Group",
                )
            val membership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId1,
                    groupId = groupId,
                )
            val membership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId2,
                    groupId = groupId,
                )
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = groupId,
                    name = "Group",
                    // Remove all users
                    userIds = emptyList(),
                )
            val updatedEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Group",
                )
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.of(existingEntity))
            whenever(groupRepository.update(any())).thenReturn(updatedEntity)
            whenever(groupMembershipRepository.findByGroupId(groupId)).thenReturn(listOf(membership1, membership2))
            whenever(
                groupMembershipRepository.deleteAll(any<List<GroupMembershipEntity>>()),
            ).thenAnswer {
                @Suppress("UNCHECKED_CAST")
                it.arguments[0] as List<GroupMembershipEntity>
            }

            // Act
            val result = groupManagementService.updateGroup(command)

            // Assert
            assertThat(result).isNotNull()
            verify(userRepository, never()).findByIdIn(any())
            verify(groupMembershipRepository).findByGroupId(groupId)
            val deleteCaptor = argumentCaptor<List<GroupMembershipEntity>>()
            verify(groupMembershipRepository).deleteAll(deleteCaptor.capture())
            val deletedMemberships = deleteCaptor.firstValue
            assertThat(deletedMemberships).hasSize(2) // All users removed
        }

        @Test
        fun `should update group without changing memberships when userIds is null`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val existingEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Old Name",
                )
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = groupId,
                    name = "New Name",
                    // No change to memberships
                    userIds = null,
                )
            val updatedEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "New Name",
                )
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.of(existingEntity))
            whenever(groupRepository.update(any())).thenReturn(updatedEntity)

            // Act
            val result = groupManagementService.updateGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("New Name")
            verify(groupRepository).findById(groupId)
            verify(groupRepository).update(any())
            verify(userRepository, never()).findByIdIn(any())
            verify(groupMembershipRepository, never()).findByGroupId(any())
            verify(groupMembershipRepository, never()).saveAll(any<List<GroupMembershipEntity>>())
            verify(groupMembershipRepository, never()).deleteAll(any<List<GroupMembershipEntity>>())
        }

        @Test
        fun `should throw exception when updating group with invalid user IDs`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val existingEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Group",
                )
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = groupId,
                    name = "Group",
                    userIds = listOf(userId1, userId2),
                )
            val updatedEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Group",
                )
            val user1 = SecurityTestDataBuilders.user(id = userId1)
            // userId2 is missing
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.of(existingEntity))
            whenever(groupRepository.update(any())).thenReturn(updatedEntity)
            whenever(userRepository.findByIdIn(listOf(userId1, userId2))).thenReturn(listOf(user1))

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.updateGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("users not found")
                assertThat(exception.message).contains(userId2.toString())
            }
            verify(groupRepository).findById(groupId)
            verify(groupRepository).update(any())
            verify(userRepository).findByIdIn(listOf(userId1, userId2))
            verify(groupMembershipRepository, never()).findByGroupId(any())
            verify(groupMembershipRepository, never()).saveAll(any<List<GroupMembershipEntity>>())
            verify(groupMembershipRepository, never()).deleteAll(any<List<GroupMembershipEntity>>())
        }
    }

    @Nested
    @DisplayName("Delete Group")
    inner class DeleteGroupTests {
        @Test
        fun `should delete group successfully`() {
            // Arrange
            val groupId = UUID.randomUUID()

            // Act
            groupManagementService.deleteGroup(groupId)

            // Assert
            verify(groupRepository).deleteById(groupId)
        }

        @Test
        fun `should delete group even if not found`() {
            // Arrange
            val groupId = UUID.randomUUID()
            // Note: deleteById doesn't throw exception if not found, it just does nothing

            // Act
            groupManagementService.deleteGroup(groupId)

            // Assert
            verify(groupRepository).deleteById(groupId)
        }
    }

    @Nested
    @DisplayName("Batch Methods Tests")
    inner class BatchMethodsTests {
        @Test
        fun `getGroupRolesBatch should return roles for multiple groups`() {
            // Arrange
            val groupId1 = UUID.randomUUID()
            val groupId2 = UUID.randomUUID()
            val roleId1 = UUID.randomUUID()
            val roleId2 = UUID.randomUUID()
            val assignment1 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId1,
                    roleId = roleId1,
                )
            val assignment2 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId2,
                    roleId = roleId2,
                )
            val role1 = SecurityTestDataBuilders.role(id = roleId1, name = "admin")
            val role2 = SecurityTestDataBuilders.role(id = roleId2, name = "editor")

            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(assignment1, assignment2))
            whenever(roleRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(role1, role2))

            // Act
            val result = groupManagementService.getGroupRolesBatch(listOf(groupId1, groupId2))

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[groupId1]).hasSize(1)
            assertThat(result[groupId1]!![0].name).isEqualTo("admin")
            assertThat(result[groupId2]).hasSize(1)
            assertThat(result[groupId2]!![0].name).isEqualTo("editor")
            verify(groupRoleAssignmentRepository).findValidAssignmentsByGroupIds(any<List<UUID>>(), any())
        }

        @Test
        fun `getGroupRolesBatch should return empty map for empty group list`() {
            // Act
            val result = groupManagementService.getGroupRolesBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `getGroupRolesBatch should return empty lists for groups with no roles`() {
            // Arrange
            val groupId = UUID.randomUUID()
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(emptyList())

            // Act
            val result = groupManagementService.getGroupRolesBatch(listOf(groupId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[groupId]).isEmpty()
        }

        @Test
        fun `getGroupRolesBatch should handle groups with multiple roles`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val roleId1 = UUID.randomUUID()
            val roleId2 = UUID.randomUUID()
            val assignment1 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId1,
                )
            val assignment2 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId2,
                )
            val role1 = SecurityTestDataBuilders.role(id = roleId1, name = "admin")
            val role2 = SecurityTestDataBuilders.role(id = roleId2, name = "editor")

            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(assignment1, assignment2))
            whenever(roleRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(role1, role2))

            // Act
            val result = groupManagementService.getGroupRolesBatch(listOf(groupId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[groupId]).hasSize(2)
            assertThat(result[groupId]!!.map { it.name }).containsExactlyInAnyOrder("admin", "editor")
        }
    }
}
