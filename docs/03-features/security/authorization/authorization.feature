@security @authorization @rbac @abac
Feature: Authorization Access Checks (RBAC and ABAC)
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
      And the reason should indicate RBAC allowed

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
      And the reason should indicate user does not have permission

    @validation
    Scenario: Permission denied when user has no roles
      Given a user "diana" exists
      And user "diana" has no roles assigned
      When I check if user "diana" has permission "transaction:read"
      Then access should be denied
      And the reason should indicate no roles assigned

  Rule: RBAC - Group Inheritance
    @happy-path
    Scenario: User inherits role from group
      Given a user "eve" exists
      And a group "finance-team" exists
      And user "eve" is a member of group "finance-team"
      And a role "accountant" exists with permission "account:read"
      And group "finance-team" has role "accountant" assigned
      When I check if user "eve" has permission "account:read"
      Then access should be granted
      And the permission should be inherited from group "finance-team"

    @happy-path
    Scenario: User inherits multiple roles from multiple groups
      Given a user "frank" exists
      And a group "sales-team" exists
      And a group "support-team" exists
      And user "frank" is a member of groups "sales-team" and "support-team"
      And a role "sales-rep" exists with permission "customer:read"
      And a role "support-agent" exists with permission "ticket:read"
      And group "sales-team" has role "sales-rep" assigned
      And group "support-team" has role "support-agent" assigned
      When I check if user "frank" has permission "customer:read"
      Then access should be granted
      When I check if user "frank" has permission "ticket:read"
      Then access should be granted

    @happy-path
    Scenario: User has direct role and inherits role from group
      Given a user "grace" exists
      And a group "managers" exists
      And user "grace" is a member of group "managers"
      And a role "admin" exists with permission "user:manage"
      And a role "manager" exists with permission "team:view"
      And user "grace" has role "admin" assigned directly
      And group "managers" has role "manager" assigned
      When I check if user "grace" has permission "user:manage"
      Then access should be granted
      When I check if user "grace" has permission "team:view"
      Then access should be granted

    @edge-case
    Scenario: User in group with expired membership
      Given a user "henry" exists
      And a group "temp-team" exists
      And user "henry" was a member of group "temp-team" until "2024-01-01"
      And a role "temp-role" exists with permission "temp:access"
      And group "temp-team" has role "temp-role" assigned
      When I check if user "henry" has permission "temp:access" on "2024-01-02"
      Then access should be denied
      And the reason should indicate expired group membership

  Rule: ABAC - Policy Evaluation
    @happy-path
    Scenario: ABAC policy allows based on subject attributes
      Given a user "irene" exists
      And user "irene" has attribute "department" = "finance"
      And an ABAC policy exists:
        | effect | condition                          |
        | allow  | subject.department == "finance"    |
      When I check if user "irene" can perform action "report:view"
      Then access should be granted
      And RBAC evaluation should return "allowed"
      And ABAC evaluation should return "allowed"
      And an audit log entry should include both RBAC and ABAC results

    @happy-path
    Scenario: ABAC policy allows based on resource attributes
      Given a user "jack" exists
      And a role "viewer" exists with permission "document:read"
      And user "jack" has role "viewer" assigned directly
      And a resource "document-1" exists with attribute "sensitivity" = "public"
      And an ABAC policy exists:
        | effect | condition                              |
        | allow  | resource.sensitivity == "public"      |
      When I check if user "jack" can perform action "document:read" on resource "document-1"
      Then access should be granted
      And RBAC evaluation should return "allowed"
      And ABAC evaluation should return "allowed"

    @happy-path
    Scenario: ABAC policy allows based on context attributes
      Given a user "karen" exists
      And a role "admin" exists with permission "admin:access"
      And user "karen" has role "admin" assigned directly
      And the request context has attribute "ip_address" = "192.168.1.100"
      And an ABAC policy exists:
        | effect | condition                              |
        | allow  | context.ip_address IN ["192.168.1.0/24"] |
      When I check if user "karen" can perform action "admin:access"
      Then access should be granted
      And ABAC evaluation should return "allowed"

    @happy-path
    Scenario: ABAC policy with AND condition
      Given a user "larry" exists
      And a role "senior" exists with permission "report:view"
      And user "larry" has role "senior" assigned directly
      And user "larry" has attribute "department" = "finance"
      And user "larry" has attribute "level" = "senior"
      And a resource "report-1" exists with attribute "type" = "financial"
      And an ABAC policy exists:
        | effect | condition                                                                    |
        | allow  | subject.department == "finance" AND subject.level == "senior" AND resource.type == "financial" |
      When I check if user "larry" can perform action "report:view" on resource "report-1"
      Then access should be granted
      And ABAC evaluation should return "allowed"

    @happy-path
    Scenario: ABAC policy with OR condition
      Given a user "mary" exists
      And a role "manager" exists with permission "budget:edit"
      And user "mary" has role "manager" assigned directly
      And user "mary" has attribute "role" = "manager"
      And a resource "budget-1" exists with attribute "owner" = "mary"
      And an ABAC policy exists:
        | effect | condition                                                      |
        | allow  | subject.role == "manager" OR resource.owner == subject.user_id |
      When I check if user "mary" can perform action "budget:edit" on resource "budget-1"
      Then access should be granted
      And ABAC evaluation should return "allowed"

    @validation
    Scenario: ABAC policy denies access
      Given a user "nancy" exists
      And a role "editor" exists with permission "finance:access"
      And user "nancy" has role "editor" assigned directly
      And user "nancy" has attribute "department" = "sales"
      And an ABAC policy exists:
        | effect | condition                          |
        | deny   | subject.department == "sales"     |
      When I check if user "nancy" can perform action "finance:access"
      Then access should be denied
      And RBAC evaluation should return "allowed"
      And ABAC evaluation should return "denied"
      And the final decision should be "denied"
      And the reason should indicate ABAC policy explicitly denies access

    @edge-case
    Scenario: No ABAC policies match
      Given a user "oscar" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "oscar" has role "viewer" assigned directly
      And no ABAC policies match the request
      When I check if user "oscar" has permission "transaction:read"
      Then access should be granted
      And RBAC evaluation should return "allowed"
      And ABAC evaluation should return null (no policies matched)
      And the final decision should be "allowed"

  Rule: Hybrid RBAC + ABAC Evaluation
    @happy-path
    Scenario: RBAC allows and ABAC allows
      Given a user "paul" exists
      And a role "editor" exists with permission "transaction:update"
      And user "paul" has role "editor" assigned directly
      And user "paul" has attribute "status" = "active"
      And an ABAC policy exists:
        | effect | condition                      |
        | allow  | subject.status == "active"     |
      When I check if user "paul" has permission "transaction:update"
      Then access should be granted
      And RBAC evaluation should return "allowed"
      And ABAC evaluation should return "allowed"
      And an audit log entry should include both RBAC and ABAC results

    @validation
    Scenario: RBAC allows but ABAC denies
      Given a user "quinn" exists
      And a role "editor" exists with permission "transaction:update"
      And user "quinn" has role "editor" assigned directly
      And user "quinn" has attribute "status" = "suspended"
      And an ABAC policy exists:
        | effect | condition                        |
        | deny   | subject.status == "suspended"   |
      When I check if user "quinn" has permission "transaction:update"
      Then access should be denied
      And RBAC evaluation should return "allowed"
      And ABAC evaluation should return "denied"
      And the final decision should be "denied"
      And the reason should indicate ABAC policy explicitly denies access

    @validation
    Scenario: RBAC denies (short-circuit evaluation)
      Given a user "rachel" exists
      And user "rachel" has no roles assigned
      And an ABAC policy exists:
        | effect | condition                  |
        | allow  | subject.user_id != null    |
      When I check if user "rachel" has permission "transaction:update"
      Then access should be denied
      And RBAC evaluation should return "denied"
      And ABAC evaluation should not be performed
      And an audit log entry should indicate RBAC denied

    @validation
    Scenario: Explicit deny overrides allow
      Given a user "sam" exists
      And a role "editor" exists with permission "transaction:update"
      And user "sam" has role "editor" assigned directly
      And an ABAC policy exists:
        | effect | condition                      |
        | allow  | subject.user_id != null        |
      And an ABAC policy exists:
        | effect | condition                      |
        | deny   | subject.user_id == "sam"      |
      When I check if user "sam" has permission "transaction:update"
      Then access should be denied
      And the deny policy should override the allow policy
      And ABAC evaluation should return "denied"

  Rule: Resource Ownership
    @happy-path
    Scenario: User can edit own resources
      Given a user "tina" exists
      And a role "editor" exists with permission "transaction:update"
      And user "tina" has role "editor" assigned directly
      And a resource "transaction-1" exists created by user "tina"
      And an ABAC policy exists:
        | effect | condition                                          |
        | allow  | resource.created_by_user_id == subject.user_id     |
      When I check if user "tina" can perform action "transaction:update" on resource "transaction-1"
      Then access should be granted
      And RBAC evaluation should return "allowed"
      And ABAC evaluation should return "allowed"

    @happy-path
    Scenario: User can edit group resources
      Given a user "uma" exists
      And a role "editor" exists with permission "transaction:update"
      And user "uma" has role "editor" assigned directly
      And a group "team-alpha" exists
      And user "uma" is a member of group "team-alpha"
      And a resource "transaction-2" exists created by group "team-alpha"
      And an ABAC policy exists:
        | effect | condition                                                      |
        | allow  | resource.created_by_group_id IN subject.group_ids              |
      When I check if user "uma" can perform action "transaction:update" on resource "transaction-2"
      Then access should be granted
      And ABAC evaluation should return "allowed"

    @validation
    Scenario: User cannot edit others' resources
      Given a user "victor" exists
      And a role "editor" exists with permission "transaction:update"
      And user "victor" has role "editor" assigned directly
      And a user "wendy" exists
      And a resource "transaction-3" exists created by user "wendy"
      And an ABAC policy exists:
        | effect | condition                                          |
        | allow  | resource.created_by_user_id == subject.user_id     |
      When I check if user "victor" can perform action "transaction:update" on resource "transaction-3"
      Then access should be denied
      And RBAC evaluation should return "allowed"
      And ABAC evaluation should return "denied" or null (no matching policy)

  Rule: Read-Global, Write-Limited Pattern
    @happy-path
    Scenario: User can read all resources
      Given a user "xavier" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "xavier" has role "viewer" assigned directly
      And resources "transaction-1", "transaction-2", "transaction-3" exist
      When I check if user "xavier" has permission "transaction:read" on resource "transaction-1"
      Then access should be granted
      When I check if user "xavier" has permission "transaction:read" on resource "transaction-2"
      Then access should be granted
      When I check if user "xavier" has permission "transaction:read" on resource "transaction-3"
      Then access should be granted

    @happy-path
    Scenario: User can write only owned resources
      Given a user "yara" exists
      And a role "editor" exists with permission "transaction:update"
      And user "yara" has role "editor" assigned directly
      And a resource "transaction-1" exists created by user "yara"
      And a resource "transaction-2" exists created by user "alice"
      And an ABAC policy exists:
        | effect | condition                                          |
        | allow  | resource.created_by_user_id == subject.user_id     |
      When I check if user "yara" has permission "transaction:update" on resource "transaction-1"
      Then access should be granted
      When I check if user "yara" has permission "transaction:update" on resource "transaction-2"
      Then access should be denied

    @happy-path
    Scenario: User can write draft resources
      Given a user "zoe" exists
      And a role "editor" exists with permission "transaction:update"
      And user "zoe" has role "editor" assigned directly
      And a resource "transaction-1" exists with status "draft"
      And an ABAC policy exists:
        | effect | condition                                  |
        | allow  | resource.status == "draft"                 |
      When I check if user "zoe" has permission "transaction:update" on resource "transaction-1"
      Then access should be granted
      And ABAC evaluation should return "allowed"

  Rule: Temporary Access
    @happy-path
    Scenario: Valid access within date range
      Given a user "adam" exists
      And a role "temp-editor" exists with permission "transaction:update"
      And user "adam" has role "temp-editor" assigned with valid_from "2024-01-01" and valid_until "2024-12-31"
      When I check if user "adam" has permission "transaction:update" on "2024-06-15"
      Then access should be granted
      And the role binding should be considered valid

    @validation
    Scenario: Expired access denied
      Given a user "bella" exists
      And a role "temp-editor" exists with permission "transaction:update"
      And user "bella" has role "temp-editor" assigned with valid_from "2024-01-01" and valid_until "2024-06-30"
      When I check if user "bella" has permission "transaction:update" on "2024-07-01"
      Then access should be denied
      And the reason should indicate expired role binding

    @validation
    Scenario: Future-dated access denied
      Given a user "charlie" exists
      And a role "temp-editor" exists with permission "transaction:update"
      And user "charlie" has role "temp-editor" assigned with valid_from "2025-01-01" and valid_until "2025-12-31"
      When I check if user "charlie" has permission "transaction:update" on "2024-12-15"
      Then access should be denied
      And the reason should indicate future-dated role binding

  Rule: GraphQL Authorization Queries
    @happy-path
    Scenario: Check permission via GraphQL query
      Given a user "diana" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "diana" has role "viewer" assigned directly
      When I query checkPermission with userId "diana" and permission "transaction:read"
      Then the response should indicate allowed = true
      And the response should include a reason

    @happy-path
    Scenario: Get user permissions via GraphQL query
      Given a user "eve" exists
      And a role "viewer" exists with permission "transaction:read"
      And a role "editor" exists with permission "transaction:update"
      And user "eve" has roles "viewer" and "editor" assigned directly
      When I query getUserPermissions with userId "eve"
      Then the response should include permission "transaction:read"
      And the response should include permission "transaction:update"

    @happy-path
    Scenario: Get user roles via GraphQL query
      Given a user "frank" exists
      And a role "viewer" exists
      And a role "editor" exists
      And user "frank" has roles "viewer" and "editor" assigned directly
      When I query getUserRoles with userId "frank"
      Then the response should include role "viewer"
      And the response should include role "editor"

    @validation
    Scenario: Check permission with invalid user ID
      Given a user "nonexistent" does not exist
      When I query checkPermission with userId "nonexistent" and permission "transaction:read"
      Then the response should indicate allowed = false
      And the response should include an error reason

    @validation
    Scenario: Check permission with invalid permission format
      Given a user "grace" exists
      When I query checkPermission with userId "grace" and permission "invalid-permission-format"
      Then the response should indicate allowed = false
      And the response should include an error indicating invalid permission format

  Rule: Frontend Authorization
    @happy-path
    Scenario: PermissionGate allows access when user has permission
      Given a user "henry" exists
      And user "henry" has permission "security:user:read"
      When the frontend checks if user "henry" has permission "security:user:read"
      Then PermissionGate should render the protected content
      And the content should be visible

    @validation
    Scenario: PermissionGate denies access when user lacks permission
      Given a user "irene" exists
      And user "irene" does not have permission "security:user:save"
      When the frontend checks if user "irene" has permission "security:user:save"
      Then PermissionGate should not render the protected content
      And the content should be hidden

    @happy-path
    Scenario: PermissionGate handles loading state
      Given a user "jack" exists
      When the frontend is checking permissions for user "jack"
      Then PermissionGate should show a loading state
      And once permissions are loaded, the appropriate content should be rendered

  Rule: Audit Logging
    @happy-path
    Scenario: Authorization decision is logged
      Given a user "karen" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "karen" has role "viewer" assigned directly
      When I check if user "karen" has permission "transaction:read"
      Then an audit log entry should be created
      And the audit log should include userId "karen"
      And the audit log should include requestedAction "transaction:read"
      And the audit log should include rbacResult "allowed"
      And the audit log should include finalDecision "allowed"

    @happy-path
    Scenario: Hybrid authorization decision is logged with both RBAC and ABAC results
      Given a user "larry" exists
      And a role "editor" exists with permission "transaction:update"
      And user "larry" has role "editor" assigned directly
      And an ABAC policy exists that matches
      When I check if user "larry" has permission "transaction:update"
      Then an audit log entry should be created
      And the audit log should include rbacResult
      And the audit log should include abacResult
      And the audit log should include finalDecision
      And the audit log should include matchedPolicies

  Rule: Edge Cases
    @edge-case
    Scenario: Missing user
      Given a user "mary" does not exist
      When I check if user "mary" has permission "transaction:read"
      Then access should be denied
      And an error should indicate user not found

    @edge-case
    Scenario: Missing resource
      Given a user "nancy" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "nancy" has role "viewer" assigned directly
      When I check if user "nancy" has permission "transaction:read" on resource "nonexistent"
      Then access should be denied or allowed based on RBAC (resource may not be required for permission check)
      And if denied, an error should indicate resource not found

    @edge-case
    Scenario: Invalid policy
      Given a user "oscar" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "oscar" has role "viewer" assigned directly
      And an invalid ABAC policy exists
      When I check if user "oscar" can perform action "transaction:read"
      Then access should be granted (invalid policies are ignored)
      And an error should be logged for the invalid policy

    @edge-case
    Scenario: Cache invalidation on role change
      Given a user "paul" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "paul" has role "viewer" assigned directly
      And the authorization cache is populated
      When I check if user "paul" has permission "transaction:read"
      Then access should be granted
      When role "viewer" is revoked from user "paul"
      And the authorization cache is invalidated
      When I check if user "paul" has permission "transaction:read"
      Then access should be denied
      And the cache should reflect the updated permissions

  Rule: Performance Requirements
    @non-functional
    Scenario: Authorization check completes within performance threshold
      Given a user "quinn" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "quinn" has role "viewer" assigned directly
      When I check if user "quinn" has permission "transaction:read"
      Then access should be granted
      And the authorization check should complete within 5 milliseconds

    @non-functional
    Scenario: Cached authorization check is faster
      Given a user "rachel" exists
      And a role "viewer" exists with permission "transaction:read"
      And user "rachel" has role "viewer" assigned directly
      When I check if user "rachel" has permission "transaction:read" (first call)
      Then access should be granted
      When I check if user "rachel" has permission "transaction:read" (cached call)
      Then access should be granted
      And the cached authorization check should be faster than the first call

    @non-functional
    Scenario: Batch permission checks are optimized
      Given multiple users exist with various roles
      When I check permissions for multiple users in a batch
      Then the batch operation should be more efficient than individual checks
      And all permission checks should complete within acceptable time

  Rule: HTTP Interceptor Authorization
    @happy-path
    Scenario: HTTP request is authorized via interceptor
      Given a user "sam" exists
      And user "sam" has permission "security:user:read"
      When user "sam" makes an HTTP request to an endpoint requiring "security:user:read"
      Then the request should be allowed
      And the endpoint should process the request

    @validation
    Scenario: HTTP request is denied when user lacks permission
      Given a user "tina" exists
      And user "tina" does not have permission "security:user:save"
      When user "tina" makes an HTTP request to an endpoint requiring "security:user:save"
      Then the request should be denied
      And an AuthorizationDeniedException should be thrown
      And the response should indicate missing permission

  Rule: GraphQL Resolver Authorization
    @happy-path
    Scenario: GraphQL resolver enforces authorization
      Given a user "uma" exists
      And user "uma" has permission "security:user:view"
      When user "uma" queries checkPermission
      Then the query should be allowed
      And the resolver should process the query

    @validation
    Scenario: GraphQL resolver denies access without permission
      Given a user "victor" exists
      And user "victor" does not have permission "security:user:view"
      When user "victor" queries checkPermission
      Then the query should be denied
      And an AuthorizationDeniedException should be thrown
      And the GraphQL error should indicate missing permission





