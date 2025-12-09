# **Feature Request: Hybrid RBAC + ABAC Authorization System**

## **1. Overview**

This feature introduces a **hybrid RBAC (Role-Based Access Control) + ABAC (Attribute-Based Access Control)** model to support fine-grained, enterprise-grade authorization across all modules of the application.

The goal is to combine:

* **RBAC** → defines *who* can perform certain *actions*.
* **ABAC** → defines *under which conditions* an action can be performed *on a specific resource*.

This design ensures flexibility, scalability, and strong security aligned with mature systems used by companies such as Google Workspace, Okta, AWS IAM, and Microsoft Entra.

---

## **2. Core Requirements**

### **2.1 RBAC Requirements**

* The system must support:

  * `USER`
  * `ROLE`
  * `PERMISSION`
  * `GROUP` (teams, squads, shared profiles)
* A user inherits:

  * All roles assigned directly to them.
  * All roles assigned to groups they belong to.
* A role contains a set of permissions (e.g., `transaction:update`, `account:read`).
* Roles can be assigned to users and groups.
* Role bindings must support:

  * Valid dates (`valid_from`, `valid_until`)
  * Subject types (`USER`, `GROUP`)

---

### **2.2 ABAC Requirements**

* Fine-grained authorization is evaluated via **attribute-based policies**, using attributes from:

  * The *subject* (user attributes, groups, tenant)
  * The *resource* (creator, owner_group, sensitivity, status)
  * The *context* (IP, request time, environment)
* Policies must support:

  * `allow` or `deny` effects
  * Logical rules (AND / OR)
  * Conditions over subject + resource + context attributes

Example ABAC use cases:

* A user may update a resource only if:

  * They created the resource, OR
  * The resource belongs to one of their groups.
* A user may view all transactions in a profile, but may only **edit** transactions they own.

---

## **3. Functional Use Cases**

### **3.1 Inherited Permissions via Groups**

**Requirement:**
Users must automatically inherit all permissions granted to their groups.

**Details:**

* If a group has a role, all its members receive the full permission set.
* Group membership must support:

  * Member types: `member`, `owner`, `manager`
  * Expirable access (`valid_until`)

---

### **3.2 Resource Ownership Editing Rules**

**Requirement:**
A user can only edit records they created or records created by their groups.

**ABAC Policy Example (conceptual):**

> *Allow `update` on a resource if:*
>
> * `request.user_id == resource.created_by_user_id`
>   **OR**
> * `resource.created_by_group_id IN request.user_group_ids`

---

### **3.3 Temporary Access**

**Requirement:**
Roles can be assigned with temporary access using expiry dates on role bindings.

---

### **3.4 Read-Global, Write-Limited Pattern**

Common enterprise requirement:

* User may **read** all resources in a profile.
* User may **write** (create/update/delete) only:

  * Owned records
  * Records assigned to their team/group
  * Records in specific statuses (e.g., `draft`)

This must be enforced through:

* RBAC for read/write permissions.
* ABAC for ownership/status restrictions.

---

### **3.5 Audit Logging Requirements**

Every authorization decision must generate a structured audit event:

* `user_id`
* `groups`
* `roles`
* `requested_action`
* `resource_type`
* `resource_id` (if applicable)
* `rbac_result`
* `abac_result`
* `final_decision`
* `timestamp`

This is essential for compliance and debugging.

---

### **3.6 Temporary / Just-in-Time Access**

The system must allow granting temporary elevated access:

* For incident response
* For operational support
* With automatic expiration

Role bindings must include:

* `valid_from`
* `valid_until`

---

### **3.7 Delegation / Acting-on-Behalf (Impersonation)**

The system must support controlled impersonation:

* A user may take actions “on behalf of” another user.
* Requires a special permission (e.g., `user:impersonate`).
* All impersonation actions must be logged with:

  * `acting_user_id`
  * `impersonated_user_id`

---

### **3.8 Resource Sharing (Optional but Recommended)**

The system should optionally support per-resource ACLs:

* Share a single resource with:

  * A user
  * A group
* With granular permissions such as:

  * `read`
  * `comment`
  * `update`

---

### **3.9 Service Accounts / API Tokens**

The system must support non-human actors:

* Service accounts authenticate via tokens/secrets.
* They can receive roles and policies like users.
* Should support scope-limited permissions:

  * Read-only
  * Write-only
  * Specific modules

---

### **3.10 Break-Glass (“Emergency Admin”) Access**

The system must support a restricted, highly audited emergency role:

* Gives broad access
* Only a small set of trusted admins have it
* All usage must generate a special audit log entry

---

### **3.11 Versioning and Simulation of Policies**

To avoid breaking production:

* Policies must be versioned.
* A “policy playground” or simulation mode should exist to test changes.
* Admins should evaluate:

  * “What would happen if I change this rule?”
  * “Who would gain/lose access?”

---

## **4. Non-Functional Requirements**

### **4.1 Performance**

* Authorization checks must be fast (<5 ms per evaluation).
* Implement caching layers for:

  * User groups
  * Role bindings
  * Permissions
  * Policy evaluations

### **4.2 Scalability**

Supports:

* Large number of users
* Thousands of resources
* Many role bindings

RBAC handles coarse-grained access, while ABAC scales fine-grained control.

### **4.3 Extensibility**

* New resource types must be easy to add.
* New policies should not require code changes (policy engine driven).

### **4.4 Security**

* Deny-by-default model.
* Policies must support explicit `deny` that overrides `allow`.
* All authorization operations must be logged.

---

## **5. Summary**

This feature introduces a **complete hybrid RBAC + ABAC authorization architecture** supporting:

* Group-based inheritance
* Scoped roles
* Ownership-based editing rules
* Fine-grained policy enforcement
* Delegation and temporary access
* Strong auditing
* High scalability
* Enterprise-grade flexibility

This document defines the high-level requirements needed to implement the authorization subsystem.
