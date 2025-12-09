@security @authorization @admin
Feature: Authorization Entities Management (Users, Groups, Roles, Permissions)
  As an Administrator
  I want to manage the system's authorization entities — Users, Groups, Roles, and Permissions
  So that I can maintain a secure, organized, and scalable access-control structure across the entire platform

  Background:
    Given I am authenticated as an administrator
    And I have permission "authorization:manage"
    And the authorization system is configured

  Rule: User Management
    @happy-path
    Scenario: List all users with pagination
      Given users exist in the system
      When I list users with first "10" and after "cursor"
      Then I should receive a paginated list of users
      And the response should use Relay pagination format with edges, nodes, and pageInfo
      And users should be ordered by name ascending by default

    @happy-path
    Scenario: List users without pagination parameters
      Given users exist in the system
      When I list users
      Then I should receive a paginated list of users
      And the response should include pageInfo with hasNextPage and hasPreviousPage
      And users should be ordered by name ascending

    @happy-path
    Scenario: Search users by name
      Given a user "Alice Smith" exists with email "alice@example.com"
      And a user "Bob Johnson" exists with email "bob@example.com"
      When I search users with query "Alice"
      Then I should receive users matching "Alice"
      And user "Alice Smith" should be in the results
      And user "Bob Johnson" should not be in the results

    @happy-path
    Scenario: Search users by email
      Given a user "Alice Smith" exists with email "alice@example.com"
      And a user "Bob Johnson" exists with email "bob@example.com"
      When I search users with query "alice@example.com"
      Then I should receive users matching the email
      And user "Alice Smith" should be in the results

    @happy-path
    Scenario: Search users by identifier
      Given a user "Alice Smith" exists with identifier "user-123"
      When I search users with query "user-123"
      Then I should receive users matching the identifier
      And user "Alice Smith" should be in the results

    @happy-path
    Scenario: Enable user account
      Given a user "disabled-user" exists
      And user "disabled-user" is disabled
      When I enable user "disabled-user"
      Then user "disabled-user" should be enabled
      And the status change should be persisted
      And user "disabled-user" should appear as enabled in listings

    @happy-path
    Scenario: Disable user account
      Given a user "active-user" exists
      And user "active-user" is enabled
      When I disable user "active-user"
      Then user "active-user" should be disabled
      And the status change should be persisted
      And user "active-user" should appear as disabled in listings

    @validation
    Scenario: Cannot enable non-existent user
      Given a user "nonexistent" does not exist
      When I attempt to enable user "nonexistent"
      Then an error should indicate user "nonexistent" does not exist

    @validation
    Scenario: Cannot disable non-existent user
      Given a user "nonexistent" does not exist
      When I attempt to disable user "nonexistent"
      Then an error should indicate user "nonexistent" does not exist

    @non-functional
    Scenario: User node exposes relationships
      Given a user "alice" exists
      And a role "editor" exists
      And a group "developers" exists
      And user "alice" has role "editor" assigned
      And user "alice" belongs to group "developers"
      When I query the User node for "alice"
      Then the response should include roles assigned to the user
      And the response should include groups the user belongs to
      And the response should include permissions effectively granted

  Rule: Group Management
    @happy-path
    Scenario: List all groups with pagination
      Given groups exist in the system
      When I list groups with first "10" and after "cursor"
      Then I should receive a paginated list of groups
      And the response should use Relay pagination format with edges, nodes, and pageInfo
      And groups should be ordered alphabetically by default

    @happy-path
    Scenario: Search groups by name
      Given a group "finance-team" exists
      And a group "sales-team" exists
      When I search groups with query "finance"
      Then I should receive groups matching "finance"
      And group "finance-team" should be in the results
      And group "sales-team" should not be in the results

    @happy-path
    Scenario: Create a new group
      Given a group "new-team" does not exist
      When I create a group with name "new-team"
      Then group "new-team" should be created
      And group "new-team" should appear in group listings

    @happy-path
    Scenario: Update an existing group
      Given a group "old-name" exists
      When I update group "old-name" with name "new-name"
      Then group "old-name" should be renamed to "new-name"
      And group "new-name" should appear in group listings
      And group "old-name" should not exist

    @happy-path
    Scenario: Delete a group without dependencies
      Given a group "empty-group" exists
      And group "empty-group" has no assigned users
      And group "empty-group" has no assigned roles
      And group "empty-group" has no other bindings
      When I delete group "empty-group"
      Then group "empty-group" should be deleted
      And group "empty-group" should not appear in group listings

    @validation
    Scenario: Cannot delete group with assigned users
      Given a group "team-with-users" exists
      And user "alice" is a member of group "team-with-users"
      When I attempt to delete group "team-with-users"
      Then an error should indicate group "team-with-users" has dependencies
      And the error should mention assigned users
      And group "team-with-users" should not be deleted

    @validation
    Scenario: Cannot delete group with assigned roles
      Given a group "team-with-roles" exists
      And group "team-with-roles" has role "editor" assigned
      When I attempt to delete group "team-with-roles"
      Then an error should indicate group "team-with-roles" has dependencies
      And the error should mention assigned roles
      And group "team-with-roles" should not be deleted

    @validation
    Scenario: Cannot create group with duplicate name
      Given a group "existing-group" exists
      When I attempt to create a group with name "existing-group"
      Then an error should indicate group name "existing-group" already exists
      And the group should not be created

    @validation
    Scenario: Cannot update group to duplicate name
      Given a group "group-a" exists
      And a group "group-b" exists
      When I attempt to update group "group-a" with name "group-b"
      Then an error should indicate group name "group-b" already exists
      And group "group-a" should retain its original name

    @edge-case
    Scenario: Search groups with no results
      Given no groups match query "nonexistent-group"
      When I search groups with query "nonexistent-group"
      Then I should receive an empty list of groups

    @non-functional
    Scenario: Group node exposes relationships
      Given a group "developers" exists
      And a role "developer" exists
      And user "alice" is a member of group "developers"
      And group "developers" has role "developer" assigned
      When I query the Group node for "developers"
      Then the response should include roles assigned to the group
      And the response should include users who are members of the group

  Rule: Role Management
    @happy-path
    Scenario: List all roles with pagination
      Given roles exist in the system
      When I list roles with first "10" and after "cursor"
      Then I should receive a paginated list of roles
      And the response should use Relay pagination format with edges, nodes, and pageInfo
      And roles should be ordered alphabetically by default

    @happy-path
    Scenario: Search roles by name
      Given a role "editor" exists
      And a role "viewer" exists
      When I search roles with query "editor"
      Then I should receive roles matching "editor"
      And role "editor" should be in the results
      And role "viewer" should not be in the results

    @happy-path
    Scenario: Create a new role
      Given a role "new-role" does not exist
      When I create a role with name "new-role"
      Then role "new-role" should be created
      And role "new-role" should appear in role listings

    @happy-path
    Scenario: Update an existing role
      Given a role "old-role" exists
      When I update role "old-role" with name "new-role"
      Then role "old-role" should be renamed to "new-role"
      And role "new-role" should appear in role listings
      And role "old-role" should not exist

    @happy-path
    Scenario: Delete a role without dependencies
      Given a role "unused-role" exists
      And role "unused-role" has no assigned users
      And role "unused-role" has no assigned groups
      And role "unused-role" has no associated permissions
      When I delete role "unused-role"
      Then role "unused-role" should be deleted
      And role "unused-role" should not appear in role listings

    @happy-path
    Scenario: List permissions associated with a role
      Given a role "editor" exists
      And permission "transaction:read" exists
      And permission "transaction:update" exists
      And role "editor" has permission "transaction:read" assigned
      And role "editor" has permission "transaction:update" assigned
      When I list permissions for role "editor"
      Then I should receive permissions "transaction:read" and "transaction:update"
      And permission "transaction:read" should be in the results
      And permission "transaction:update" should be in the results

    @happy-path
    Scenario: Assign permission to role
      Given a role "editor" exists
      And permission "transaction:delete" exists
      And role "editor" does not have permission "transaction:delete"
      When I assign permission "transaction:delete" to role "editor"
      Then role "editor" should have permission "transaction:delete"
      And the role-permission relationship should be updated in real time

    @happy-path
    Scenario: Remove permission from role
      Given a role "editor" exists
      And permission "transaction:update" exists
      And role "editor" has permission "transaction:update" assigned
      When I remove permission "transaction:update" from role "editor"
      Then role "editor" should not have permission "transaction:update"
      And the role-permission relationship should be updated in real time

    @validation
    Scenario: Cannot delete role with assigned users
      Given a role "assigned-role" exists
      And user "alice" has role "assigned-role" assigned
      When I attempt to delete role "assigned-role"
      Then an error should indicate role "assigned-role" has dependencies
      And the error should mention assigned users
      And role "assigned-role" should not be deleted

    @validation
    Scenario: Cannot delete role with assigned groups
      Given a role "group-role" exists
      And group "developers" has role "group-role" assigned
      When I attempt to delete role "group-role"
      Then an error should indicate role "group-role" has dependencies
      And the error should mention assigned groups
      And role "group-role" should not be deleted

    @validation
    Scenario: Cannot delete role with associated permissions
      Given a role "permission-role" exists
      And permission "transaction:read" exists
      And role "permission-role" has permission "transaction:read" assigned
      When I attempt to delete role "permission-role"
      Then an error should indicate role "permission-role" has dependencies
      And the error should mention associated permissions
      And role "permission-role" should not be deleted

    @validation
    Scenario: Cannot create role with duplicate name
      Given a role "existing-role" exists
      When I attempt to create a role with name "existing-role"
      Then an error should indicate role name "existing-role" already exists
      And the role should not be created

    @validation
    Scenario: Cannot update role to duplicate name
      Given a role "role-a" exists
      And a role "role-b" exists
      When I attempt to update role "role-a" with name "role-b"
      Then an error should indicate role name "role-b" already exists
      And role "role-a" should retain its original name

    @validation
    Scenario: Cannot assign non-existent permission to role
      Given a role "editor" exists
      And permission "nonexistent" does not exist
      When I attempt to assign permission "nonexistent" to role "editor"
      Then an error should indicate permission "nonexistent" does not exist
      And role "editor" should not have permission "nonexistent"

    @validation
    Scenario: Cannot remove permission that is not assigned
      Given a role "editor" exists
      And permission "transaction:read" exists
      And role "editor" does not have permission "transaction:read" assigned
      When I attempt to remove permission "transaction:read" from role "editor"
      Then an error should indicate permission "transaction:read" is not assigned to role "editor"

    @edge-case
    Scenario: List permissions for role with no permissions
      Given a role "empty-role" exists
      And role "empty-role" has no permissions assigned
      When I list permissions for role "empty-role"
      Then I should receive an empty list of permissions

    @non-functional
    Scenario: Role node exposes relationships
      Given a role "editor" exists
      And permission "transaction:read" exists
      And permission "transaction:update" exists
      And role "editor" has permission "transaction:read" assigned
      And role "editor" has permission "transaction:update" assigned
      When I query the Role node for "editor"
      Then the response should include permissions associated with the role

  Rule: Permission Management
    @happy-path
    Scenario: List all permissions with pagination
      Given permissions exist in the system
      When I list permissions with first "10" and after "cursor"
      Then I should receive a paginated list of permissions
      And the response should use Relay pagination format with edges, nodes, and pageInfo
      And permissions should be ordered alphabetically by default

    @happy-path
    Scenario: Search permissions by name
      Given a permission "transaction:read" exists
      And a permission "transaction:update" exists
      When I search permissions with query "read"
      Then I should receive permissions matching "read"
      And permission "transaction:read" should be in the results
      And permission "transaction:update" should not be in the results

    @edge-case
    Scenario: Search permissions with no results
      Given no permissions match query "nonexistent"
      When I search permissions with query "nonexistent"
      Then I should receive an empty list of permissions

    @non-functional
    Scenario: Permission node exposes relationships
      Given a permission "transaction:read" exists
      And a role "editor" exists
      And a role "viewer" exists
      And role "editor" has permission "transaction:read" assigned
      And role "viewer" has permission "transaction:read" assigned
      When I query the Permission node for "transaction:read"
      Then the response should include roles that include this permission
      And role "editor" should be in the results
      And role "viewer" should be in the results

  Rule: Default Initialization
    @non-functional
    Scenario: Default admin user is created during bootstrap
      Given the system is being initialized
      When the system bootstrap completes
      Then a default admin user should exist
      And the admin user should have username "admin"
      And the admin user should have password "admin"

    @non-functional
    Scenario: Default admin group is created during bootstrap
      Given the system is being initialized
      And a default admin user exists
      When the system bootstrap completes
      Then a default admin group should exist
      And the admin group should have name "admin"
      And the default admin user should be included in the admin group

    @non-functional
    Scenario: Default admin role is created during bootstrap
      Given the system is being initialized
      And a default admin group exists
      When the system bootstrap completes
      Then a default admin role should exist
      And the admin role should have name "admin"
      And the default admin role should be assigned to the admin group

    @non-functional
    Scenario: Default permissions are created during bootstrap
      Given the system is being initialized
      When the system bootstrap completes
      Then default permissions should exist
      And a permission should exist for each action described in this specification
      And the default admin role should receive all permissions

  Rule: Authorization Checks
    @validation
    Scenario: Non-admin cannot manage authorization entities
      Given a user "regular-user" exists
      And user "regular-user" does not have permission "authorization:manage"
      When user "regular-user" attempts to list users
      Then access should be denied
      And an error should indicate missing "authorization:manage" permission

    @validation
    Scenario: Non-admin cannot create groups
      Given a user "regular-user" exists
      And user "regular-user" does not have permission "authorization:manage"
      When user "regular-user" attempts to create a group
      Then access should be denied
      And an error should indicate missing "authorization:manage" permission
