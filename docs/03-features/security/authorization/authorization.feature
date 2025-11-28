@security @authorization
Feature: Authorization Access Checks
  As a system
  I want to evaluate user access to resources and actions
  So that I can enforce fine-grained security policies using RBAC and ABAC

  Background:
    Given the authorization system is configured
    And RBAC and ABAC policies are defined

  Rule: RBAC - Direct Role Assignment
    @happy-path
    Scenario: User has direct role assignment
      Given a user "alice" exists
      And a role "editor" exists with permission "transaction:update"
      And user "alice" has role "editor" assigned directly
      When I check if user "alice" has permission "transaction:update"
      Then access should be granted
      And an audit log entry should be created with decision "allowed"

    @happy-path
    Scenario: User has multiple direct roles
      Given a user "bob" exists
      And a role "viewer" exists with permission "transaction:read"
      And a role "editor" exists with permission "transaction:update"
      And user "bob" has roles "viewer" and "editor" assigned directly
      When I check if user "bob" has permission "transaction:read"
      Then access should be granted
      When I check if user "bob" has permission "transaction:update"
      Then access should be granted

    @validation
    Scenario: Permission denied when user lacks required role
      Given a user "charlie" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "charlie" has role "viewer" assigned directly
      When I check if user "charlie" has permission "transaction:update"
      Then access should be denied
      And an audit log entry should be created with decision "denied"

  Rule: RBAC - Group Inheritance
    @happy-path
    Scenario: User inherits role from group
      Given a user "diana" exists
      And a group "finance-team" exists
      And user "diana" is a member of group "finance-team"
      And a role "accountant" exists with permission "account:read"
      And group "finance-team" has role "accountant" assigned
      When I check if user "diana" has permission "account:read"
      Then access should be granted
      And the permission should be inherited from group "finance-team"

    @happy-path
    Scenario: User inherits multiple roles from multiple groups
      Given a user "eve" exists
      And a group "sales-team" exists
      And a group "support-team" exists
      And user "eve" is a member of groups "sales-team" and "support-team"
      And a role "sales-rep" exists with permission "customer:read"
      And a role "support-agent" exists with permission "ticket:read"
      And group "sales-team" has role "sales-rep" assigned
      And group "support-team" has role "support-agent" assigned
      When I check if user "eve" has permission "customer:read"
      Then access should be granted
      When I check if user "eve" has permission "ticket:read"
      Then access should be granted

    @happy-path
    Scenario: User has direct role and inherits role from group
      Given a user "frank" exists
      And a group "managers" exists
      And user "frank" is a member of group "managers"
      And a role "admin" exists with permission "user:manage"
      And a role "manager" exists with permission "team:view"
      And user "frank" has role "admin" assigned directly
      And group "managers" has role "manager" assigned
      When I check if user "frank" has permission "user:manage"
      Then access should be granted
      When I check if user "frank" has permission "team:view"
      Then access should be granted

    @edge-case
    Scenario: User in group with expired membership
      Given a user "grace" exists
      And a group "temp-team" exists
      And user "grace" was a member of group "temp-team" until "2024-01-01"
      And a role "temp-role" exists with permission "temp:access"
      And group "temp-team" has role "temp-role" assigned
      When I check if user "grace" has permission "temp:access" on "2024-01-02"
      Then access should be denied
      And the reason should indicate expired group membership

  Rule: RBAC - Scoped Permissions
    @happy-path
    Scenario: User has profile-level permission
      Given a user "henry" exists
      And a role "profile-editor" exists with permission "transaction:update"
      And user "henry" has role "profile-editor" assigned at profile scope "profile-123"
      When I check if user "henry" has permission "transaction:update" on profile "profile-123"
      Then access should be granted
      When I check if user "henry" has permission "transaction:update" on profile "profile-456"
      Then access should be denied

    @happy-path
    Scenario: User has resource-level permission
      Given a user "iris" exists
      And a role "resource-owner" exists with permission "transaction:update"
      And user "iris" has role "resource-owner" assigned at resource scope "transaction-789"
      When I check if user "iris" has permission "transaction:update" on resource "transaction-789"
      Then access should be granted
      When I check if user "iris" has permission "transaction:update" on resource "transaction-999"
      Then access should be denied

    @happy-path
    Scenario: User has project-level permission
      Given a user "jack" exists
      And a role "project-manager" exists with permission "project:manage"
      And user "jack" has role "project-manager" assigned at project scope "project-abc"
      When I check if user "jack" has permission "project:manage" on project "project-abc"
      Then access should be granted
      When I check if user "jack" has permission "project:manage" on project "project-xyz"
      Then access should be denied

  Rule: ABAC - Policy Evaluation
    @happy-path
    Scenario: ABAC policy allows based on subject attributes
      Given a user "karen" exists
      And user "karen" has attribute "department" = "finance"
      And an ABAC policy exists:
        | effect | condition                          |
        | allow  | subject.department == "finance"    |
      When I check if user "karen" can perform action "report:view"
      Then access should be granted
      And ABAC evaluation should return "allowed"

    @happy-path
    Scenario: ABAC policy allows based on resource attributes
      Given a user "larry" exists
      And a resource "document-1" exists with attribute "sensitivity" = "public"
      And an ABAC policy exists:
        | effect | condition                              |
        | allow  | resource.sensitivity == "public"      |
      When I check if user "larry" can perform action "document:read" on resource "document-1"
      Then access should be granted

    @happy-path
    Scenario: ABAC policy allows based on context attributes
      Given a user "mary" exists
      And the request context has attribute "ip_address" = "192.168.1.100"
      And an ABAC policy exists:
        | effect | condition                              |
        | allow  | context.ip_address IN ["192.168.1.0/24"] |
      When I check if user "mary" can perform action "admin:access"
      Then access should be granted

    @happy-path
    Scenario: ABAC policy with AND condition
      Given a user "nancy" exists
      And user "nancy" has attribute "department" = "finance"
      And user "nancy" has attribute "level" = "senior"
      And a resource "report-1" exists with attribute "type" = "financial"
      And an ABAC policy exists:
        | effect | condition                                                                    |
        | allow  | subject.department == "finance" AND subject.level == "senior" AND resource.type == "financial" |
      When I check if user "nancy" can perform action "report:view" on resource "report-1"
      Then access should be granted

    @happy-path
    Scenario: ABAC policy with OR condition
      Given a user "oscar" exists
      And user "oscar" has attribute "role" = "manager"
      And a resource "budget-1" exists with attribute "owner" = "oscar"
      And an ABAC policy exists:
        | effect | condition                                                      |
        | allow  | subject.role == "manager" OR resource.owner == subject.user_id |
      When I check if user "oscar" can perform action "budget:edit" on resource "budget-1"
      Then access should be granted

    @validation
    Scenario: ABAC policy denies access
      Given a user "paul" exists
      And user "paul" has attribute "department" = "sales"
      And an ABAC policy exists:
        | effect | condition                          |
        | deny   | subject.department == "sales"     |
      When I check if user "paul" can perform action "finance:access"
      Then access should be denied
      And ABAC evaluation should return "denied"

  Rule: Hybrid RBAC + ABAC Evaluation
    @happy-path
    Scenario: RBAC allows and ABAC allows
      Given a user "quinn" exists
      And a role "editor" exists with permission "transaction:update"
      And user "quinn" has role "editor" assigned directly
      And user "quinn" has attribute "status" = "active"
      And an ABAC policy exists:
        | effect | condition                      |
        | allow  | subject.status == "active"     |
      When I check if user "quinn" has permission "transaction:update"
      Then access should be granted
      And RBAC evaluation should return "allowed"
      And ABAC evaluation should return "allowed"

    @validation
    Scenario: RBAC allows but ABAC denies
      Given a user "rachel" exists
      And a role "editor" exists with permission "transaction:update"
      And user "rachel" has role "editor" assigned directly
      And user "rachel" has attribute "status" = "suspended"
      And an ABAC policy exists:
        | effect | condition                        |
        | deny   | subject.status == "suspended"   |
      When I check if user "rachel" has permission "transaction:update"
      Then access should be denied
      And RBAC evaluation should return "allowed"
      And ABAC evaluation should return "denied"
      And the final decision should be "denied"

    @validation
    Scenario: RBAC denies (short-circuit evaluation)
      Given a user "sam" exists
      And user "sam" has no roles assigned
      And an ABAC policy exists:
        | effect | condition                  |
        | allow  | subject.user_id != null    |
      When I check if user "sam" has permission "transaction:update"
      Then access should be denied
      And RBAC evaluation should return "denied"
      And ABAC evaluation should not be performed

    @validation
    Scenario: Explicit deny overrides allow
      Given a user "tina" exists
      And a role "editor" exists with permission "transaction:update"
      And user "tina" has role "editor" assigned directly
      And an ABAC policy exists:
        | effect | condition                      |
        | allow  | subject.user_id != null        |
      And an ABAC policy exists:
        | effect | condition                      |
        | deny   | subject.user_id == "tina"      |
      When I check if user "tina" has permission "transaction:update"
      Then access should be denied
      And the deny policy should override the allow policy

  Rule: Resource Ownership
    @happy-path
    Scenario: User can edit own resources
      Given a user "uma" exists
      And a resource "transaction-1" exists created by user "uma"
      And an ABAC policy exists:
        | effect | condition                                          |
        | allow  | resource.created_by_user_id == subject.user_id     |
      When I check if user "uma" can perform action "transaction:update" on resource "transaction-1"
      Then access should be granted

    @happy-path
    Scenario: User can edit group resources
      Given a user "victor" exists
      And a group "team-alpha" exists
      And user "victor" is a member of group "team-alpha"
      And a resource "transaction-2" exists created by group "team-alpha"
      And an ABAC policy exists:
        | effect | condition                                                      |
        | allow  | resource.created_by_group_id IN subject.group_ids              |
      When I check if user "victor" can perform action "transaction:update" on resource "transaction-2"
      Then access should be granted

    @validation
    Scenario: User cannot edit others' resources
      Given a user "wendy" exists
      And a user "xavier" exists
      And a resource "transaction-3" exists created by user "xavier"
      And an ABAC policy exists:
        | effect | condition                                          |
        | allow  | resource.created_by_user_id == subject.user_id     |
      When I check if user "wendy" can perform action "transaction:update" on resource "transaction-3"
      Then access should be denied

  Rule: Read-Global, Write-Limited Pattern
    @happy-path
    Scenario: User can read all resources in profile
      Given a user "yara" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "yara" has role "viewer" assigned at profile scope "profile-123"
      And resources "transaction-1", "transaction-2", "transaction-3" exist in profile "profile-123"
      When I check if user "yara" has permission "transaction:read" on resource "transaction-1"
      Then access should be granted
      When I check if user "yara" has permission "transaction:read" on resource "transaction-2"
      Then access should be granted
      When I check if user "yara" has permission "transaction:read" on resource "transaction-3"
      Then access should be granted

    @happy-path
    Scenario: User can write only owned resources
      Given a user "zoe" exists
      And a role "editor" exists with permission "transaction:update"
      And user "zoe" has role "editor" assigned at profile scope "profile-123"
      And a resource "transaction-1" exists in profile "profile-123" created by user "zoe"
      And a resource "transaction-2" exists in profile "profile-123" created by user "alice"
      And an ABAC policy exists:
        | effect | condition                                          |
        | allow  | resource.created_by_user_id == subject.user_id     |
      When I check if user "zoe" has permission "transaction:update" on resource "transaction-1"
      Then access should be granted
      When I check if user "zoe" has permission "transaction:update" on resource "transaction-2"
      Then access should be denied

    @happy-path
    Scenario: User can write draft resources
      Given a user "adam" exists
      And a role "editor" exists with permission "transaction:update"
      And user "adam" has role "editor" assigned at profile scope "profile-123"
      And a resource "transaction-1" exists in profile "profile-123" with status "draft"
      And an ABAC policy exists:
        | effect | condition                                  |
        | allow  | resource.status == "draft"                 |
      When I check if user "adam" has permission "transaction:update" on resource "transaction-1"
      Then access should be granted

  Rule: Temporary Access
    @happy-path
    Scenario: Valid access within date range
      Given a user "bella" exists
      And a role "temp-editor" exists with permission "transaction:update"
      And user "bella" has role "temp-editor" assigned with valid_from "2024-01-01" and valid_until "2024-12-31"
      When I check if user "bella" has permission "transaction:update" on "2024-06-15"
      Then access should be granted

    @validation
    Scenario: Expired access denied
      Given a user "charlie" exists
      And a role "temp-editor" exists with permission "transaction:update"
      And user "charlie" has role "temp-editor" assigned with valid_from "2024-01-01" and valid_until "2024-06-30"
      When I check if user "charlie" has permission "transaction:update" on "2024-07-01"
      Then access should be denied
      And the reason should indicate expired role binding

    @validation
    Scenario: Future-dated access denied
      Given a user "diana" exists
      And a role "temp-editor" exists with permission "transaction:update"
      And user "diana" has role "temp-editor" assigned with valid_from "2025-01-01" and valid_until "2025-12-31"
      When I check if user "diana" has permission "transaction:update" on "2024-12-15"
      Then access should be denied
      And the reason should indicate future-dated role binding

  Rule: Delegation and Impersonation
    @happy-path
    Scenario: User can impersonate with permission
      Given a user "eve" exists
      And a user "frank" exists
      And a role "admin" exists with permission "user:impersonate"
      And user "eve" has role "admin" assigned directly
      When user "eve" impersonates user "frank"
      And I check if the impersonated user "frank" has permission "transaction:read"
      Then access should be granted
      And an audit log entry should be created with acting_user_id "eve" and impersonated_user_id "frank"

    @validation
    Scenario: User cannot impersonate without permission
      Given a user "grace" exists
      And a user "henry" exists
      And user "grace" has no roles assigned
      When user "grace" attempts to impersonate user "henry"
      Then impersonation should be denied
      And an error should indicate missing "user:impersonate" permission

  Rule: Service Accounts
    @happy-path
    Scenario: Service account has access with token
      Given a service account "api-service" exists
      And a role "api-reader" exists with permission "transaction:read"
      And service account "api-service" has role "api-reader" assigned
      When I check if service account "api-service" authenticated with valid token has permission "transaction:read"
      Then access should be granted

    @validation
    Scenario: Service account access denied with invalid token
      Given a service account "api-service" exists
      When I check if service account "api-service" authenticated with invalid token has permission "transaction:read"
      Then access should be denied

  Rule: Break-Glass Emergency Access
    @happy-path
    Scenario: Emergency admin has broad access
      Given a user "emergency-admin" exists
      And a role "break-glass" exists with broad permissions
      And user "emergency-admin" has role "break-glass" assigned directly
      When I check if user "emergency-admin" has permission "user:manage"
      Then access should be granted
      And a special audit log entry should be created with type "break-glass"

    @validation
    Scenario: Regular admin cannot use break-glass permissions
      Given a user "regular-admin" exists
      And a role "admin" exists with permission "user:view"
      And user "regular-admin" has role "admin" assigned directly
      When I check if user "regular-admin" has permission "break-glass:access"
      Then access should be denied

  Rule: Edge Cases
    @edge-case
    Scenario: Missing user
      Given a user "nonexistent" does not exist
      When I check if user "nonexistent" has permission "transaction:read"
      Then access should be denied
      And an error should indicate user not found

    @edge-case
    Scenario: Missing resource
      Given a user "alice" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "alice" has role "viewer" assigned directly
      When I check if user "alice" has permission "transaction:read" on resource "nonexistent"
      Then access should be denied
      And an error should indicate resource not found

    @edge-case
    Scenario: Invalid policy
      Given a user "bob" exists
      And an invalid ABAC policy exists
      When I check if user "bob" can perform action "transaction:read"
      Then access should be denied
      And an error should indicate invalid policy

    @edge-case
    Scenario: Cache invalidation on role change
      Given a user "charlie" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "charlie" has role "viewer" assigned directly
      And the authorization cache is populated
      When I check if user "charlie" has permission "transaction:read"
      Then access should be granted
      When role "viewer" is revoked from user "charlie"
      And the authorization cache is invalidated
      When I check if user "charlie" has permission "transaction:read"
      Then access should be denied

  Rule: Performance Requirements
    @non-functional
    Scenario: Authorization check completes within performance threshold
      Given a user "diana" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "diana" has role "viewer" assigned directly
      When I check if user "diana" has permission "transaction:read"
      Then access should be granted
      And the authorization check should complete within 5 milliseconds

    @non-functional
    Scenario: Cached authorization check is faster
      Given a user "eve" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "eve" has role "viewer" assigned directly
      When I check if user "eve" has permission "transaction:read" (first call)
      Then access should be granted
      When I check if user "eve" has permission "transaction:read" (cached call)
      Then access should be granted
      And the cached authorization check should be faster than the first call

