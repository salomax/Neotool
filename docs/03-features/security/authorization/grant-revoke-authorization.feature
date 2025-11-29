@security @authorization @admin
Feature: Grant and Revoke Authorization
  As an administrator
  I want to manage user and group authorizations
  So that I can control access to resources and actions

  Background:
    Given I am authenticated as an administrator
    And I have permission "authorization:manage"
    And the authorization system is configured

  Rule: Role Management - Users
    @happy-path
    Scenario: Grant role to user
      Given a user "alice" exists
      And a role "editor" exists with permissions "transaction:read", "transaction:update"
      When I grant role "editor" to user "alice"
      Then user "alice" should have role "editor"
      And user "alice" should have permission "transaction:read"
      And user "alice" should have permission "transaction:update"
      And an audit log entry should be created for the role grant

    @happy-path
    Scenario: Grant multiple roles to user
      Given a user "bob" exists
      And a role "viewer" exists with permission "transaction:read"
      And a role "editor" exists with permission "transaction:update"
      When I grant roles "viewer" and "editor" to user "bob"
      Then user "bob" should have role "viewer"
      And user "bob" should have role "editor"
      And user "bob" should have permission "transaction:read"
      And user "bob" should have permission "transaction:update"

    @happy-path
    Scenario: Revoke role from user
      Given a user "charlie" exists
      And a role "editor" exists with permission "transaction:update"
      And user "charlie" has role "editor" assigned
      When I revoke role "editor" from user "charlie"
      Then user "charlie" should not have role "editor"
      And user "charlie" should not have permission "transaction:update"
      And an audit log entry should be created for the role revocation

    @happy-path
    Scenario: Revoke one role while keeping others
      Given a user "diana" exists
      And a role "viewer" exists with permission "transaction:read"
      And a role "editor" exists with permission "transaction:update"
      And user "diana" has roles "viewer" and "editor" assigned
      When I revoke role "editor" from user "diana"
      Then user "diana" should not have role "editor"
      And user "diana" should still have role "viewer"
      And user "diana" should not have permission "transaction:update"
      And user "diana" should still have permission "transaction:read"

    @validation
    Scenario: Cannot grant non-existent role
      Given a user "eve" exists
      When I attempt to grant role "nonexistent" to user "eve"
      Then an error should indicate role "nonexistent" does not exist
      And user "eve" should not have role "nonexistent"

    @validation
    Scenario: Cannot revoke role that is not assigned
      Given a user "frank" exists
      And a role "editor" exists
      And user "frank" does not have role "editor" assigned
      When I attempt to revoke role "editor" from user "frank"
      Then an error should indicate role "editor" is not assigned to user "frank"

  Rule: Role Management - Groups
    @happy-path
    Scenario: Grant role to group
      Given a group "finance-team" exists
      And a role "accountant" exists with permission "account:read"
      When I grant role "accountant" to group "finance-team"
      Then group "finance-team" should have role "accountant"
      And all members of group "finance-team" should inherit permission "account:read"

    @happy-path
    Scenario: Revoke role from group
      Given a group "sales-team" exists
      And a role "sales-rep" exists with permission "customer:read"
      And group "sales-team" has role "sales-rep" assigned
      And user "alice" is a member of group "sales-team"
      When I revoke role "sales-rep" from group "sales-team"
      Then group "sales-team" should not have role "sales-rep"
      And user "alice" should no longer inherit permission "customer:read"
      And an audit log entry should be created for the role revocation

    @validation
    Scenario: Cannot grant non-existent role to group
      Given a group "marketing-team" exists
      When I attempt to grant role "nonexistent" to group "marketing-team"
      Then an error should indicate role "nonexistent" does not exist
      And group "marketing-team" should not have role "nonexistent"

  Rule: Group Membership Management
    @happy-path
    Scenario: Add user to group
      Given a user "grace" exists
      And a group "developers" exists
      And a role "developer" exists with permission "code:write"
      And group "developers" has role "developer" assigned
      When I add user "grace" to group "developers" as "member"
      Then user "grace" should be a member of group "developers"
      And user "grace" should inherit permission "code:write"
      And an audit log entry should be created for the membership addition

    @happy-path
    Scenario: Add user to group as owner
      Given a user "henry" exists
      And a group "managers" exists
      When I add user "henry" to group "managers" as "owner"
      Then user "henry" should be an owner of group "managers"
      And user "henry" should have permission to manage group "managers"

    @happy-path
    Scenario: Add user to group as manager
      Given a user "iris" exists
      And a group "team-alpha" exists
      When I add user "iris" to group "team-alpha" as "manager"
      Then user "iris" should be a manager of group "team-alpha"
      And user "iris" should have permission to manage group "team-alpha" members

    @happy-path
    Scenario: Remove user from group
      Given a user "jack" exists
      And a group "support-team" exists
      And a role "support-agent" exists with permission "ticket:read"
      And group "support-team" has role "support-agent" assigned
      And user "jack" is a member of group "support-team"
      When I remove user "jack" from group "support-team"
      Then user "jack" should not be a member of group "support-team"
      And user "jack" should no longer inherit permission "ticket:read"
      And an audit log entry should be created for the membership removal

    @happy-path
    Scenario: Change group membership type
      Given a user "karen" exists
      And a group "finance-team" exists
      And user "karen" is a member of group "finance-team" as "member"
      When I change user "karen" membership in group "finance-team" to "manager"
      Then user "karen" should be a manager of group "finance-team"
      And user "karen" should have permission to manage group "finance-team" members

    @validation
    Scenario: Cannot add user to non-existent group
      Given a user "larry" exists
      When I attempt to add user "larry" to group "nonexistent"
      Then an error should indicate group "nonexistent" does not exist
      And user "larry" should not be a member of group "nonexistent"

    @validation
    Scenario: Cannot remove user from group they are not in
      Given a user "mary" exists
      And a group "sales-team" exists
      And user "mary" is not a member of group "sales-team"
      When I attempt to remove user "mary" from group "sales-team"
      Then an error should indicate user "mary" is not a member of group "sales-team"

  Rule: Temporary Role Bindings
    @happy-path
    Scenario: Create temporary role binding with expiry
      Given a user "sam" exists
      And a role "temp-editor" exists with permission "transaction:update"
      When I grant role "temp-editor" to user "sam" with valid_from "2024-01-01" and valid_until "2024-12-31"
      Then user "sam" should have role "temp-editor" with valid_from "2024-01-01" and valid_until "2024-12-31"
      And user "sam" should have permission "transaction:update" on "2024-06-15"
      And user "sam" should not have permission "transaction:update" on "2025-01-01"

    @happy-path
    Scenario: Create temporary role binding with future start date
      Given a user "tina" exists
      And a role "temp-viewer" exists with permission "transaction:read"
      When I grant role "temp-viewer" to user "tina" with valid_from "2025-01-01" and valid_until "2025-12-31"
      Then user "tina" should have role "temp-viewer" with valid_from "2025-01-01" and valid_until "2025-12-31"
      And user "tina" should not have permission "transaction:read" on "2024-12-31"
      And user "tina" should have permission "transaction:read" on "2025-01-01"

    @happy-path
    Scenario: Update temporary role binding expiry
      Given a user "uma" exists
      And a role "temp-editor" exists
      And user "uma" has role "temp-editor" with valid_from "2024-01-01" and valid_until "2024-06-30"
      When I update role binding for user "uma" and role "temp-editor" to valid_until "2024-12-31"
      Then user "uma" should have role "temp-editor" with valid_until "2024-12-31"
      And user "uma" should have permission "transaction:update" on "2024-07-01"

    @validation
    Scenario: Cannot create binding with invalid date range
      Given a user "victor" exists
      And a role "temp-editor" exists
      When I attempt to grant role "temp-editor" to user "victor" with valid_from "2024-12-31" and valid_until "2024-01-01"
      Then an error should indicate invalid date range
      And user "victor" should not have role "temp-editor"

  Rule: ABAC Policy Management
    @happy-path
    Scenario: Create ABAC policy
      Given an ABAC policy does not exist
      When I create an ABAC policy:
        | name        | effect | condition                          |
        | finance-only | allow  | subject.department == "finance"    |
      Then the ABAC policy "finance-only" should exist
      And the policy should have effect "allow"
      And the policy should have condition "subject.department == \"finance\""

    @happy-path
    Scenario: Create ABAC policy with deny effect
      Given an ABAC policy does not exist
      When I create an ABAC policy:
        | name        | effect | condition                          |
        | block-sales | deny   | subject.department == "sales"      |
      Then the ABAC policy "block-sales" should exist
      And the policy should have effect "deny"

    @happy-path
    Scenario: Create ABAC policy with complex condition
      Given an ABAC policy does not exist
      When I create an ABAC policy:
        | name           | effect | condition                                                                    |
        | senior-finance | allow  | subject.department == "finance" AND subject.level == "senior"              |
      Then the ABAC policy "senior-finance" should exist
      And the policy should have the complex condition

    @happy-path
    Scenario: Update ABAC policy
      Given an ABAC policy "finance-only" exists with effect "allow" and condition "subject.department == \"finance\""
      When I update ABAC policy "finance-only":
        | effect | condition                                                      |
        | allow  | subject.department == "finance" AND subject.status == "active" |
      Then the ABAC policy "finance-only" should have the updated condition
      And the policy should have effect "allow"

    @happy-path
    Scenario: Delete ABAC policy
      Given an ABAC policy "finance-only" exists
      When I delete ABAC policy "finance-only"
      Then the ABAC policy "finance-only" should not exist
      And an audit log entry should be created for the policy deletion

    @validation
    Scenario: Cannot create policy with invalid condition
      When I attempt to create an ABAC policy:
        | name        | effect | condition                    |
        | invalid     | allow  | invalid syntax condition     |
      Then an error should indicate invalid policy condition
      And the ABAC policy "invalid" should not exist

    @validation
    Scenario: Cannot update non-existent policy
      Given an ABAC policy "nonexistent" does not exist
      When I attempt to update ABAC policy "nonexistent"
      Then an error should indicate policy "nonexistent" does not exist

  Rule: Permission Management
    @happy-path
    Scenario: Create permission
      Given a permission "transaction:create" does not exist
      When I create permission "transaction:create"
      Then permission "transaction:create" should exist

    @happy-path
    Scenario: Add permission to role
      Given a role "editor" exists
      And a permission "transaction:delete" exists
      When I add permission "transaction:delete" to role "editor"
      Then role "editor" should have permission "transaction:delete"
      And all users with role "editor" should have permission "transaction:delete"

    @happy-path
    Scenario: Remove permission from role
      Given a role "editor" exists with permissions "transaction:read", "transaction:update"
      When I remove permission "transaction:update" from role "editor"
      Then role "editor" should not have permission "transaction:update"
      And role "editor" should still have permission "transaction:read"
      And all users with role "editor" should no longer have permission "transaction:update"

    @validation
    Scenario: Cannot add non-existent permission to role
      Given a role "editor" exists
      When I attempt to add permission "nonexistent" to role "editor"
      Then an error should indicate permission "nonexistent" does not exist
      And role "editor" should not have permission "nonexistent"

  Rule: Policy Versioning and Simulation
    @happy-path
    Scenario: Version ABAC policy
      Given an ABAC policy "finance-only" exists with version 1
      When I create a new version of ABAC policy "finance-only":
        | effect | condition                                                      |
        | allow  | subject.department == "finance" AND subject.status == "active" |
      Then the ABAC policy "finance-only" should have version 2
      And version 1 of policy "finance-only" should still exist
      And version 2 should be the active version

    @happy-path
    Scenario: Simulate policy change
      Given an ABAC policy "finance-only" exists with condition "subject.department == \"finance\""
      When I simulate updating policy "finance-only" with condition "subject.department == \"finance\" AND subject.level == \"senior\""
      Then the simulation should show:
        | user_id | current_access | simulated_access |
        | alice   | allowed        | denied           |
        | bob     | allowed        | allowed          |
      And no actual policy changes should be made

    @happy-path
    Scenario: Rollback to previous policy version
      Given an ABAC policy "finance-only" exists with version 2 as active
      And version 1 of policy "finance-only" exists
      When I rollback policy "finance-only" to version 1
      Then version 1 of policy "finance-only" should be active
      And version 2 should still exist but not be active

  Rule: Validations
    @validation
    Scenario: Non-admin cannot manage authorizations
      Given a user "wendy" exists
      And user "wendy" does not have permission "authorization:manage"
      When user "wendy" attempts to grant role "editor" to user "alice"
      Then access should be denied
      And an error should indicate missing "authorization:manage" permission

    @validation
    Scenario: Cannot create circular group dependencies
      Given a group "group-a" exists
      And a group "group-b" exists
      And group "group-a" contains group "group-b"
      When I attempt to add group "group-a" to group "group-b"
      Then an error should indicate circular dependency
      And group "group-b" should not contain group "group-a"

    @validation
    Scenario: Cannot create duplicate role binding
      Given a user "xavier" exists
      And a role "editor" exists
      And user "xavier" has role "editor" assigned
      When I attempt to grant role "editor" to user "xavier" again
      Then an error should indicate duplicate role binding
      And user "xavier" should have role "editor" only once

    @validation
    Scenario: Cannot create duplicate group membership
      Given a user "yara" exists
      And a group "developers" exists
      And user "yara" is a member of group "developers"
      When I attempt to add user "yara" to group "developers" again
      Then an error should indicate duplicate membership
      And user "yara" should be a member of group "developers" only once

  Rule: Audit Logging
    @non-functional
    Scenario: All authorization changes are logged
      Given a user "zoe" exists
      And a role "editor" exists
      When I grant role "editor" to user "zoe"
      Then an audit log entry should be created with:
        | field           | value                    |
        | action          | role_granted             |
        | admin_user_id   | current_admin_user_id    |
        | target_user_id  | zoe                      |
        | role            | editor                   |
        | timestamp       | current_timestamp        |

    @non-functional
    Scenario: Policy changes are logged
      Given an ABAC policy "test-policy" exists
      When I update ABAC policy "test-policy"
      Then an audit log entry should be created with:
        | field           | value                    |
        | action          | policy_updated           |
        | admin_user_id   | current_admin_user_id    |
        | policy_name     | test-policy              |
        | timestamp       | current_timestamp        |

