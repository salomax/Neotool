@security @authorization @admin @settings
Feature: Authorization Management (Users, Groups, and Roles)
  As an Administrator
  I want to manage Users, Groups, and Roles through the Settings page
  So that I can maintain a secure, organized, and scalable access-control structure across the entire platform

  Background:
    Given I am authenticated as an administrator
    And I have permission "security:user:read" or "security:group:read" or "security:role:read"
    And I navigate to the Settings page
    And the authorization system is configured

  Rule: Settings Page Navigation
    @happy-path
    Scenario: Settings page displays three tabs
      Given I am on the Settings page
      Then I should see a "Users" tab
      And I should see a "Groups" tab
      And I should see a "Roles" tab
      And the "Users" tab should be selected by default

    @happy-path
    Scenario: Switch between tabs
      Given I am on the Settings page
      When I click on the "Groups" tab
      Then the Groups management interface should be displayed
      When I click on the "Roles" tab
      Then the Roles management interface should be displayed
      When I click on the "Users" tab
      Then the Users management interface should be displayed

  Rule: User Management
    @happy-path
    Scenario: List all users with pagination
      Given users exist in the system
      When I navigate to the Users tab
      Then I should see a paginated list of users
      And the response should use Relay pagination format with edges, nodes, and pageInfo
      And users should be displayed in a table format
      And I should see user email, display name, and status

    @happy-path
    Scenario: List users without pagination parameters
      Given users exist in the system
      When I navigate to the Users tab
      Then I should receive a paginated list of users
      And the response should include pageInfo with hasNextPage and hasPreviousPage
      And users should be ordered by name ascending by default

    @happy-path
    Scenario: Search users by name or email
      Given a user "Alice Smith" exists with email "alice@example.com"
      And a user "Bob Johnson" exists with email "bob@example.com"
      When I search users with query "Alice"
      Then I should receive users matching "Alice"
      And user "Alice Smith" should be in the results
      And user "Bob Johnson" should not be in the results

    @happy-path
    Scenario: Search users by email address
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
      When I click the enable button for user "disabled-user"
      Then user "disabled-user" should be enabled
      And the status change should be persisted
      And user "disabled-user" should appear as enabled in listings
      And I should see a success message

    @happy-path
    Scenario: Disable user account
      Given a user "active-user" exists
      And user "active-user" is enabled
      When I click the disable button for user "active-user"
      Then user "active-user" should be disabled
      And the status change should be persisted
      And user "active-user" should appear as disabled in listings
      And I should see a success message

    @happy-path
    Scenario: View user details in drawer
      Given a user "alice" exists
      When I click on user "alice" to view details
      Then a drawer should open showing user details
      And I should see the user's email
      And I should see the user's display name
      And I should see the user's status (enabled/disabled)
      And I should see the user's groups
      And I should see the user's roles (readonly)
      And I should see the user's permissions (readonly)

    @happy-path
    Scenario: Edit user display name
      Given a user "alice" exists with display name "Alice"
      When I open the user drawer for "alice"
      And I change the display name to "Alice Smith"
      And I click "Save Changes"
      Then the display name should be updated to "Alice Smith"
      And I should see a success message

    @happy-path
    Scenario: Assign group to user
      Given a user "alice" exists
      And a group "developers" exists
      And user "alice" is not a member of group "developers"
      When I open the user drawer for "alice"
      And I assign group "developers" to user "alice"
      And I click "Save Changes"
      Then user "alice" should be a member of group "developers"
      And the group should appear in the user's groups list

    @happy-path
    Scenario: Remove group from user
      Given a user "alice" exists
      And a group "developers" exists
      And user "alice" is a member of group "developers"
      When I open the user drawer for "alice"
      And I remove group "developers" from user "alice"
      And I click "Save Changes"
      Then user "alice" should not be a member of group "developers"
      And the group should not appear in the user's groups list

    @happy-path
    Scenario: View user roles (inherited from groups)
      Given a user "alice" exists
      And a group "developers" exists
      And user "alice" is a member of group "developers"
      And group "developers" has role "developer" assigned
      When I open the user drawer for "alice"
      Then I should see role "developer" in the user's roles list
      And the roles section should be readonly

    @happy-path
    Scenario: View user permissions (inherited from roles)
      Given a user "alice" exists
      And a role "developer" exists with permission "transaction:read"
      And user "alice" has role "developer" (via group membership)
      When I open the user drawer for "alice"
      Then I should see permission "transaction:read" in the user's permissions list
      And the permissions section should be readonly

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

    @edge-case
    Scenario: Search users with no results
      Given no users match query "nonexistent-user"
      When I search users with query "nonexistent-user"
      Then I should see a message indicating no users found
      And the user list should be empty

    @non-functional
    Scenario: User list supports sorting
      Given users exist in the system
      When I navigate to the Users tab
      Then I should be able to sort users by name
      And sorting should update the list order

    @non-functional
    Scenario: User list supports pagination navigation
      Given more than 10 users exist in the system
      When I navigate to the Users tab
      Then I should see pagination controls
      And I should be able to navigate to the next page
      And I should be able to navigate to the previous page
      And I should be able to go to the first page

  Rule: Group Management
    @happy-path
    Scenario: List all groups with pagination
      Given groups exist in the system
      When I navigate to the Groups tab
      Then I should see a paginated list of groups
      And the response should use Relay pagination format with edges, nodes, and pageInfo
      And groups should be displayed in a table format
      And I should see group name and description

    @happy-path
    Scenario: Search groups by name
      Given a group "finance-team" exists
      And a group "sales-team" exists
      When I navigate to the Groups tab
      And I search groups with query "finance"
      Then I should receive groups matching "finance"
      And group "finance-team" should be in the results
      And group "sales-team" should not be in the results

    @happy-path
    Scenario: Create a new group
      Given a group "new-team" does not exist
      When I navigate to the Groups tab
      And I click the "New" button
      Then a drawer should open for creating a group
      When I enter group name "new-team"
      And I enter description "New team description"
      And I click "Create"
      Then group "new-team" should be created
      And group "new-team" should appear in group listings
      And I should see a success message

    @happy-path
    Scenario: Create group with roles assigned
      Given a group "new-team" does not exist
      And a role "editor" exists
      When I navigate to the Groups tab
      And I click the "New" button
      And I enter group name "new-team"
      And I assign role "editor" to the group
      And I click "Create"
      Then group "new-team" should be created
      And group "new-team" should have role "editor" assigned

    @happy-path
    Scenario: Update an existing group
      Given a group "old-name" exists
      When I navigate to the Groups tab
      And I click to edit group "old-name"
      Then a drawer should open showing group details
      When I change the name to "new-name"
      And I click "Save Changes"
      Then group "old-name" should be renamed to "new-name"
      And group "new-name" should appear in group listings
      And I should see a success message

    @happy-path
    Scenario: Update group description
      Given a group "test-group" exists with description "Old description"
      When I navigate to the Groups tab
      And I click to edit group "test-group"
      And I change the description to "New description"
      And I click "Save Changes"
      Then group "test-group" should have description "New description"

    @happy-path
    Scenario: Assign users to group
      Given a group "developers" exists
      And a user "alice" exists
      And user "alice" is not a member of group "developers"
      When I navigate to the Groups tab
      And I click to edit group "developers"
      And I assign user "alice" to group "developers"
      And I click "Save Changes"
      Then user "alice" should be a member of group "developers"
      And user "alice" should appear in the group's members list

    @happy-path
    Scenario: Remove users from group
      Given a group "developers" exists
      And a user "alice" exists
      And user "alice" is a member of group "developers"
      When I navigate to the Groups tab
      And I click to edit group "developers"
      And I remove user "alice" from group "developers"
      And I click "Save Changes"
      Then user "alice" should not be a member of group "developers"
      And user "alice" should not appear in the group's members list

    @happy-path
    Scenario: Assign role to group
      Given a group "developers" exists
      And a role "developer" exists
      And group "developers" does not have role "developer" assigned
      When I navigate to the Groups tab
      And I click to edit group "developers"
      And I assign role "developer" to group "developers"
      And I click "Save Changes"
      Then group "developers" should have role "developer" assigned
      And role "developer" should appear in the group's roles list

    @happy-path
    Scenario: Remove role from group
      Given a group "developers" exists
      And a role "developer" exists
      And group "developers" has role "developer" assigned
      When I navigate to the Groups tab
      And I click to edit group "developers"
      And I remove role "developer" from group "developers"
      And I click "Save Changes"
      Then group "developers" should not have role "developer" assigned
      And role "developer" should not appear in the group's roles list

    @happy-path
    Scenario: Delete a group without dependencies
      Given a group "empty-group" exists
      And group "empty-group" has no assigned users
      And group "empty-group" has no assigned roles
      When I navigate to the Groups tab
      And I click the delete button for group "empty-group"
      And I confirm the deletion
      Then group "empty-group" should be deleted
      And group "empty-group" should not appear in group listings
      And I should see a success message

    @validation
    Scenario: Cannot delete group with assigned users
      Given a group "team-with-users" exists
      And user "alice" is a member of group "team-with-users"
      When I navigate to the Groups tab
      And I attempt to delete group "team-with-users"
      Then an error should indicate group "team-with-users" has dependencies
      And the error should mention assigned users
      And group "team-with-users" should not be deleted

    @validation
    Scenario: Cannot delete group with assigned roles
      Given a group "team-with-roles" exists
      And group "team-with-roles" has role "editor" assigned
      When I navigate to the Groups tab
      And I attempt to delete group "team-with-roles"
      Then an error should indicate group "team-with-roles" has dependencies
      And the error should mention assigned roles
      And group "team-with-roles" should not be deleted

    @validation
    Scenario: Cannot create group with duplicate name
      Given a group "existing-group" exists
      When I navigate to the Groups tab
      And I attempt to create a group with name "existing-group"
      Then an error should indicate group name "existing-group" already exists
      And the group should not be created

    @validation
    Scenario: Cannot update group to duplicate name
      Given a group "group-a" exists
      And a group "group-b" exists
      When I navigate to the Groups tab
      And I attempt to update group "group-a" with name "group-b"
      Then an error should indicate group name "group-b" already exists
      And group "group-a" should retain its original name

    @validation
    Scenario: Group name is required
      Given I am creating a new group
      When I attempt to create a group without a name
      Then an error should indicate name is required
      And the group should not be created

    @edge-case
    Scenario: Search groups with no results
      Given no groups match query "nonexistent-group"
      When I navigate to the Groups tab
      And I search groups with query "nonexistent-group"
      Then I should see a message indicating no groups found
      And the group list should be empty

    @non-functional
    Scenario: Group list supports sorting
      Given groups exist in the system
      When I navigate to the Groups tab
      Then I should be able to sort groups by name
      And sorting should update the list order

    @non-functional
    Scenario: Group list supports pagination navigation
      Given more than 10 groups exist in the system
      When I navigate to the Groups tab
      Then I should see pagination controls
      And I should be able to navigate to the next page
      And I should be able to navigate to the previous page
      And I should be able to go to the first page

  Rule: Role Management
    @happy-path
    Scenario: List all roles with pagination
      Given roles exist in the system
      When I navigate to the Roles tab
      Then I should see a paginated list of roles
      And the response should use Relay pagination format with edges, nodes, and pageInfo
      And roles should be displayed in a table format
      And I should see role name

    @happy-path
    Scenario: Search roles by name
      Given a role "editor" exists
      And a role "viewer" exists
      When I navigate to the Roles tab
      And I search roles with query "editor"
      Then I should receive roles matching "editor"
      And role "editor" should be in the results
      And role "viewer" should not be in the results

    @happy-path
    Scenario: Create a new role
      Given a role "new-role" does not exist
      When I navigate to the Roles tab
      And I click the "New" button
      Then a drawer should open for creating a role
      When I enter role name "new-role"
      And I click "Create"
      Then role "new-role" should be created
      And role "new-role" should appear in role listings
      And I should see a success message

    @happy-path
    Scenario: Create role with permissions assigned
      Given a role "new-role" does not exist
      And a permission "transaction:read" exists
      When I navigate to the Roles tab
      And I click the "New" button
      And I enter role name "new-role"
      And I assign permission "transaction:read" to the role
      And I click "Create"
      Then role "new-role" should be created
      And role "new-role" should have permission "transaction:read" assigned

    @happy-path
    Scenario: Create role with users and groups assigned
      Given a role "new-role" does not exist
      And a user "alice" exists
      And a group "developers" exists
      When I navigate to the Roles tab
      And I click the "New" button
      And I enter role name "new-role"
      And I assign user "alice" to the role
      And I assign group "developers" to the role
      And I click "Create"
      Then role "new-role" should be created
      And role "new-role" should be assigned to user "alice"
      And role "new-role" should be assigned to group "developers"

    @happy-path
    Scenario: Update an existing role
      Given a role "old-role" exists
      When I navigate to the Roles tab
      And I click to edit role "old-role"
      Then a drawer should open showing role details
      When I change the name to "new-role"
      And I click "Save Changes"
      Then role "old-role" should be renamed to "new-role"
      And role "new-role" should appear in role listings
      And I should see a success message

    @happy-path
    Scenario: Assign permission to role
      Given a role "editor" exists
      And a permission "transaction:delete" exists
      And role "editor" does not have permission "transaction:delete"
      When I navigate to the Roles tab
      And I click to edit role "editor"
      And I assign permission "transaction:delete" to role "editor"
      And I click "Save Changes"
      Then role "editor" should have permission "transaction:delete"
      And permission "transaction:delete" should appear in the role's permissions list

    @happy-path
    Scenario: Remove permission from role
      Given a role "editor" exists
      And a permission "transaction:update" exists
      And role "editor" has permission "transaction:update" assigned
      When I navigate to the Roles tab
      And I click to edit role "editor"
      And I remove permission "transaction:update" from role "editor"
      And I click "Save Changes"
      Then role "editor" should not have permission "transaction:update"
      And permission "transaction:update" should not appear in the role's permissions list

    @happy-path
    Scenario: Assign users to role
      Given a role "editor" exists
      And a user "alice" exists
      And user "alice" does not have role "editor"
      When I navigate to the Roles tab
      And I click to edit role "editor"
      And I assign user "alice" to role "editor"
      And I click "Save Changes"
      Then user "alice" should have role "editor" assigned
      And user "alice" should appear in the role's users list

    @happy-path
    Scenario: Remove users from role
      Given a role "editor" exists
      And a user "alice" exists
      And user "alice" has role "editor" assigned
      When I navigate to the Roles tab
      And I click to edit role "editor"
      And I remove user "alice" from role "editor"
      And I click "Save Changes"
      Then user "alice" should not have role "editor" assigned
      And user "alice" should not appear in the role's users list

    @happy-path
    Scenario: Assign groups to role
      Given a role "developer" exists
      And a group "developers" exists
      And group "developers" does not have role "developer" assigned
      When I navigate to the Roles tab
      And I click to edit role "developer"
      And I assign group "developers" to role "developer"
      And I click "Save Changes"
      Then group "developers" should have role "developer" assigned
      And group "developers" should appear in the role's groups list

    @happy-path
    Scenario: Remove groups from role
      Given a role "developer" exists
      And a group "developers" exists
      And group "developers" has role "developer" assigned
      When I navigate to the Roles tab
      And I click to edit role "developer"
      And I remove group "developers" from role "developer"
      And I click "Save Changes"
      Then group "developers" should not have role "developer" assigned
      And group "developers" should not appear in the role's groups list

    @happy-path
    Scenario: Delete a role without dependencies
      Given a role "unused-role" exists
      And role "unused-role" has no assigned users
      And role "unused-role" has no assigned groups
      And role "unused-role" has no associated permissions
      When I navigate to the Roles tab
      And I click the delete button for role "unused-role"
      And I confirm the deletion
      Then role "unused-role" should be deleted
      And role "unused-role" should not appear in role listings
      And I should see a success message

    @validation
    Scenario: Cannot delete role with assigned users
      Given a role "assigned-role" exists
      And user "alice" has role "assigned-role" assigned
      When I navigate to the Roles tab
      And I attempt to delete role "assigned-role"
      Then an error should indicate role "assigned-role" has dependencies
      And the error should mention assigned users
      And role "assigned-role" should not be deleted

    @validation
    Scenario: Cannot delete role with assigned groups
      Given a role "group-role" exists
      And group "developers" has role "group-role" assigned
      When I navigate to the Roles tab
      And I attempt to delete role "group-role"
      Then an error should indicate role "group-role" has dependencies
      And the error should mention assigned groups
      And role "group-role" should not be deleted

    @validation
    Scenario: Cannot create role with duplicate name
      Given a role "existing-role" exists
      When I navigate to the Roles tab
      And I attempt to create a role with name "existing-role"
      Then an error should indicate role name "existing-role" already exists
      And the role should not be created

    @validation
    Scenario: Cannot update role to duplicate name
      Given a role "role-a" exists
      And a role "role-b" exists
      When I navigate to the Roles tab
      And I attempt to update role "role-a" with name "role-b"
      Then an error should indicate role name "role-b" already exists
      And role "role-a" should retain its original name

    @validation
    Scenario: Role name is required
      Given I am creating a new role
      When I attempt to create a role without a name
      Then an error should indicate name is required
      And the role should not be created

    @edge-case
    Scenario: Search roles with no results
      Given no roles match query "nonexistent-role"
      When I navigate to the Roles tab
      And I search roles with query "nonexistent-role"
      Then I should see a message indicating no roles found
      And the role list should be empty

    @edge-case
    Scenario: List permissions for role with no permissions
      Given a role "empty-role" exists
      And role "empty-role" has no permissions assigned
      When I navigate to the Roles tab
      And I click to edit role "empty-role"
      Then I should see an empty permissions list
      And I should see a message indicating no permissions assigned

    @non-functional
    Scenario: Role list supports sorting
      Given roles exist in the system
      When I navigate to the Roles tab
      Then I should be able to sort roles by name
      And sorting should update the list order

    @non-functional
    Scenario: Role list supports pagination navigation
      Given more than 10 roles exist in the system
      When I navigate to the Roles tab
      Then I should see pagination controls
      And I should be able to navigate to the next page
      And I should be able to navigate to the previous page
      And I should be able to go to the first page

  Rule: Permission Management (Readonly)
    @happy-path
    Scenario: View permissions assigned to role
      Given a role "editor" exists
      And permission "transaction:read" exists
      And permission "transaction:update" exists
      And role "editor" has permission "transaction:read" assigned
      And role "editor" has permission "transaction:update" assigned
      When I navigate to the Roles tab
      And I click to edit role "editor"
      Then I should see permissions "transaction:read" and "transaction:update" in the permissions list
      And permission "transaction:read" should be in the results
      And permission "transaction:update" should be in the results

    @happy-path
    Scenario: Search available permissions when assigning to role
      Given a role "editor" exists
      And permission "transaction:read" exists
      And permission "transaction:update" exists
      When I navigate to the Roles tab
      And I click to edit role "editor"
      And I search for available permissions with query "read"
      Then I should see permission "transaction:read" in the available permissions
      And permission "transaction:update" should not be in the filtered results

  Rule: Authorization Checks
    @validation
    Scenario: Non-admin cannot access settings page
      Given a user "regular-user" exists
      And user "regular-user" does not have permission "security:user:read"
      And user "regular-user" does not have permission "security:group:read"
      And user "regular-user" does not have permission "security:role:read"
      When user "regular-user" attempts to navigate to the Settings page
      Then access should be denied
      And an error should indicate missing required permissions

    @validation
    Scenario: User without create permission cannot create groups
      Given a user "regular-user" exists
      And user "regular-user" has permission "security:group:read"
      And user "regular-user" does not have permission "security:group:save"
      When user "regular-user" navigates to the Groups tab
      Then the "New" button should not be visible or should be disabled

    @validation
    Scenario: User without save permission cannot edit users
      Given a user "regular-user" exists
      And user "regular-user" has permission "security:user:read"
      And user "regular-user" does not have permission "security:user:save"
      When user "regular-user" navigates to the Users tab
      And user "regular-user" attempts to edit a user
      Then the save button should not be visible or should be disabled

    @validation
    Scenario: User without save permission cannot edit roles
      Given a user "regular-user" exists
      And user "regular-user" has permission "security:role:read"
      And user "regular-user" does not have permission "security:role:save"
      When user "regular-user" navigates to the Roles tab
      And user "regular-user" attempts to edit a role
      Then the save button should not be visible or should be disabled

  Rule: User Interface Behavior
    @happy-path
    Scenario: Drawer opens and closes correctly
      Given I am on the Settings page
      When I click to view/edit an item
      Then a drawer should open from the right side
      When I click the close button
      Then the drawer should close
      And any unsaved changes should be discarded

    @happy-path
    Scenario: Drawer shows loading state
      Given I am on the Settings page
      When I click to view/edit an item
      Then I should see a loading indicator while data is being fetched
      And once data is loaded, the loading indicator should disappear

    @happy-path
    Scenario: Drawer shows error state
      Given I am on the Settings page
      And an error occurs while loading item details
      When I click to view/edit an item
      Then I should see an error message
      And I should have an option to retry loading the data

    @happy-path
    Scenario: Save button is disabled when no changes are made
      Given I am on the Settings page
      When I open a drawer to edit an item
      Then the save button should be disabled
      When I make a change
      Then the save button should be enabled

    @happy-path
    Scenario: Cancel button discards changes
      Given I am on the Settings page
      When I open a drawer to edit an item
      And I make changes
      And I click the cancel button
      Then the drawer should close
      And the changes should not be saved

    @happy-path
    Scenario: Success toast appears after successful operations
      Given I am on the Settings page
      When I successfully create a new item
      Then I should see a success toast message
      When I successfully update an item
      Then I should see a success toast message
      When I successfully delete an item
      Then I should see a success toast message

    @happy-path
    Scenario: Error toast appears after failed operations
      Given I am on the Settings page
      When an operation fails
      Then I should see an error toast message
      And the error message should be descriptive
