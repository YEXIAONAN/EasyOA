# EasyOAAgent.md

> EasyOA AI Development Constitution\
> Version: 1.1\
> Applies to: Codex / Claude Code / Trae / Cursor / Copilot / any autonomous or semi-autonomous development agent\
> Project: EasyOA

---

## 0. Purpose

This file is the **highest-priority repository-level development agreement for AI agents working on EasyOA**.

Any AI agent that reads, modifies, generates, reviews, refactors, tests, documents, or releases EasyOA code **must read this file before making changes**.

The purpose of this file is to keep every AI agent aligned on the same:

- product philosophy;
- architecture direction;
- security boundaries;
- permission model;
- UI/UX language;
- engineering quality;
- delivery discipline;
- testing standard;
- documentation standard.

EasyOA must not be reinterpreted as a generic admin panel, ERP scaffold, student demo, framework showcase, or technology experiment.

**EasyOA is a real product.**

When this file conflicts with an agent's default preferences, templates, generated conventions, or habitual architecture choices, **this file wins unless the user explicitly overrides it**.

## 0.1 Instruction Precedence

Within EasyOA, resolve instruction conflicts using:

```text
Current Explicit User Requirement
    > EasyOAAgent.md
    > Valid Repository Contract / Current Code / Tests / Migrations / README / Docs
    > Relevant Skill
    > Agent Default Preference
```

If README or docs disagree with implementation, inspect Git history, tests,
migrations, and current code before correcting the documentation or implementation.
Do not guess which statement is current. Material architecture changes still
require explicit user authorization under this constitution.

## 0.2 Skills Policy

`EasyOAAgent.md` is the **Constitution**: product direction and durable invariants.
A Skill is a **Workflow**: task-specific context, steps, verification, and handoff.
Skills must not redefine architecture, override this file, or copy this constitution.
`AGENTS.md` is a small compatibility entry point, not a second constitution.

The default local directory is `.agents/skills/<skill-name>/SKILL.md`.
If it exists, select and read only the Skills relevant to the current task;
do not load every Skill at startup. Local Skills and `.claude/skills/` are
workstation-specific and must remain Git ignored. `EasyOAAgent.md` and `AGENTS.md`
are repository policies and must remain eligible for Git tracking.

Inspect a Skill script completely before execution. Scripts must not upload source,
send secrets, contact unknown services, delete project directories, rewrite Git
history, read unrelated personal directories, or automatically push. A Skill does
not grant execution permissions or create an exception to repository safety rules.

Never store passwords, API keys, database credentials, TOTP secrets, private SSH
keys, production access credentials, or GitHub tokens in `SKILL.md`, `references/`,
`scripts/`, or `assets/`. Git ignore is not a secret management mechanism.

---

# 1. Product Definition

EasyOA is a:

> Modern, lightweight, self-hosted collaboration and office automation system for student teams, small companies, studios, laboratories, and small engineering groups.

EasyOA is not primarily an administrative OA system.

Its core workflow is:

```text
Project Collaboration
        ↓
Task Execution
        ↓
Team Communication
        ↓
Approval Workflow
        ↓
Organization Management
        ↓
Security & Audit
```

Product priority:

1. Project collaboration experience
2. Data security
3. Permission correctness
4. Traceability
5. UI/UX quality
6. Self-hosting experience
7. Maintainability
8. Controlled extensibility

When requirements conflict, respect this order unless a more specific security rule in this file applies.

---

# 2. Product Scope & Delivery Evidence

Phase responsibilities (not a statement of current delivery status):

```text
Phase 0  Repository bootstrap
Phase 1  Core infrastructure / authentication / audit
Phase 2  Organization & members
Phase 3  Project management
Phase 4  Task workflow
Phase 5  Comments & files
Phase 6  Approval workflow
Phase 7  Workspace / notifications / search
Phase 8  Advanced security / TOTP / sensitive operations
Phase 9  Insights
```

Current phase MUST be determined from README, Git history, migrations, tests,
and current implementation before work begins. Confirm delivered behavior with
runtime evidence when the task requires it.

Determine the target version from the current repository contract and build
manifests. Do not hard-code a current phase, target release, or test count in this
constitution. README describes current product status; this file defines durable
rules. If README uses Features and Releases rather than a phase checklist, reconcile
those with the phase responsibilities above instead of inventing completion status.

Do not mark a phase complete because directories or placeholder pages exist.

A phase is complete only when applicable items exist:

- backend implementation;
- frontend implementation;
- permission enforcement;
- audit behavior;
- tests;
- successful build;
- verified runtime behavior;
- updated documentation.

---

# 3. Non-Negotiable Security Principle

## Frontend is never trusted

The following are UX controls only:

- hidden buttons;
- route guards;
- disabled UI;
- conditional rendering;
- menu visibility;
- frontend role checks.

They are **not security controls**.

Every protected backend operation must independently validate relevant dimensions such as:

```text
Authentication
    +
System Role
    +
Organization Context
    +
Project Role
    +
Task Role
    +
Resource Ownership
    +
Data Scope
```

An attacker using:

```text
curl
Postman
Python requests
custom client
modified request payload
modified resource ID
direct API calls
```

must not gain unauthorized data access or privileges.

Any implementation that relies on the frontend to enforce authorization is a security defect.

---

# 4. Technology Baseline

Unless explicitly approved by the project owner, use the existing stack.

## Backend

```text
Java 21
Spring Boot 3.x
Spring Security
Spring Data JPA
Hibernate Validator
Flyway
PostgreSQL
Maven
OpenAPI / springdoc
```

## Frontend

```text
Vue 3
TypeScript
Vite
Vue Router
Pinia
Axios
Element Plus
```

Element Plus is a low-level component library only.

EasyOA owns the product design language.

## Infrastructure

```text
Docker
Docker Compose
Nginx
PostgreSQL
```

---

# 5. Architecture Rule

EasyOA uses a:

> Modular Monolith

Do not introduce microservices unless a real, measured requirement exists and the user explicitly approves the architecture change.

Do not introduce by default:

```text
Kubernetes
Kafka
RabbitMQ
Redis
Elasticsearch
Service Mesh
standalone API Gateway
complex distributed infrastructure
```

A technology may only be introduced when:

1. there is a concrete problem;
2. the existing stack cannot solve it cleanly;
3. complexity cost is justified;
4. architectural impact is understood;
5. the user approves material architecture changes.

"Enterprise-grade" does not mean "more infrastructure".

Enterprise quality means:

```text
correct
secure
maintainable
testable
observable
predictable
```

---

# 6. Backend Module Philosophy

Prefer domain-oriented modules.

Current conceptual modules include:

```text
common
auth
user
system
audit
securityevent
organization
workspace
project
task
approval
notification
file
```

Business modules may contain clear responsibilities such as:

```text
controller
application
domain
repository
dto
mapper
```

Do not mechanically add layers that add no value.

Avoid:

```text
GodController
GodService
BaseService<T>
GenericCrudService
unbounded utility classes
reflection-heavy abstractions
premature internal frameworks
```

Business logic does not belong in controllers.

JPA entities must not be exposed directly as API contracts.

---

# 7. Database Rules

PostgreSQL is the primary database.

All schema changes must use Flyway migrations.

Never rely on:

```text
ddl-auto=create
ddl-auto=update
```

for production schema evolution.

Prefer:

```text
ddl-auto=validate
```

Use PostgreSQL features when they are the simplest correct solution, including:

```text
TIMESTAMPTZ
jsonb
WITH RECURSIVE
partial unique indexes
transaction constraints
```

Server-side timestamps should use UTC semantics.

Prefer timezone-aware storage.

## Migration Immutability

A Flyway migration that has entered `main`, a release, or any shared branch,
or may already have been executed, must not be edited, renamed, or deleted.
Add a new versioned migration to correct it. When execution history is uncertain,
treat the migration as immutable; do not use Flyway repair to conceal a changed
historical migration. Protect critical invariants in both application logic and
database constraints where practical.

---

# 8. Data Integrity First

When multiple users can update the same critical resource, consider concurrency.

Important candidates:

```text
approval state
project owner transfer
project lifecycle
task state
task progress
membership mutation
organization move
sensitive operations
```

Use optimistic locking when appropriate.

Do not silently overwrite concurrent changes.

If a transition is no longer valid, return an explicit conflict.

---

# 9. System Roles

System roles are separate from project roles.

```text
ROOT
ADMIN
MEMBER
```

Examples that are valid:

```text
System role: MEMBER
Project role: OWNER
```

```text
System role: ADMIN
Project role: MEMBER
```

Do not merge system administration and project responsibility into one role model.

---

# 10. Project Role Model

Each project uses:

```text
OWNER           exactly 1
DEPUTY_OWNER    0 or 1
MEMBER          N
```

Do not introduce arbitrary project ADMIN roles without explicit approval.

## OWNER

Typical privileges:

- edit project information;
- change lifecycle state;
- set or replace deputy owner;
- manage members;
- transfer ownership;
- assign tasks;
- manage project configuration;
- archive project.

## DEPUTY_OWNER

Typical privileges:

- manage ordinary project members;
- assign tasks;
- modify normal project information;
- advance project work;
- update allowed project states.

Must not by default:

- replace OWNER;
- promote themselves to OWNER;
- archive the project.

## MEMBER

Typical privileges:

- view permitted project data;
- create tasks;
- participate in tasks;
- comment;
- upload permitted attachments;
- complete assigned work.

Project permission logic must be centralized.

Avoid scattered checks such as:

```java
if (userId.equals(project.getOwnerId())) { ... }
```

Prefer domain permission components such as:

```text
ProjectPermissionService
TaskPermissionService
ApprovalPermissionService
FilePermissionService
```

---

# 11. Project Lifecycle

Project states:

```text
DRAFT
ACTIVE
PAUSED
COMPLETED
ARCHIVED
```

Supported reopening behavior includes:

```text
PAUSED -> ACTIVE
COMPLETED -> ACTIVE
```

ARCHIVED is normally read-only.

Normal product workflow does not expose casual project deletion.

Archiving is preferred over deletion.

Data destruction is a separate sensitive system operation.

---

# 12. Task Responsibility Model

Each task has:

```text
Primary Assignee      exactly 1
Deputy Assignee       0 or 1
Collaborators         N
```

## Primary assignee

Typical privileges:

- update task state;
- update progress;
- update priority;
- update schedule;
- manage deputy;
- manage collaborators;
- change primary assignee when allowed.

## Deputy assignee

May generally:

- update state;
- update progress;
- update priority;
- update schedule;
- manage collaborators.

Must not by default:

- replace primary assignee;
- perform destructive task operations.

## Collaborator

May generally:

- view the task;
- comment;
- upload permitted attachments;
- @mention users;
- operate on assigned subtasks.

---

# 13. Task Assignment Rule

OWNER and DEPUTY_OWNER may assign project members directly.

Assignment becomes effective immediately.

A normal MEMBER may create a task assigned to themselves directly.

If a MEMBER creates a task whose primary assignee is another person:

```text
PENDING_ASSIGNMENT
```

must be used.

The assignment requires approval by:

```text
OWNER
or
DEPUTY_OWNER
```

The target assignee does not need to accept the assignment again.

Approval or rejection must be auditable.

---

# 14. Task Workflow

EasyOA provides a default workflow:

```text
待处理
进行中
待审核
已完成
```

Projects may customize visible status names.

Each status must map to a normalized internal type:

```text
TODO
ACTIVE
REVIEW
DONE
CLOSED
```

Business statistics must use normalized types, not display names.

Never write logic such as:

```text
if (statusName == "已完成")
```

---

# 15. Task Progress

Supported modes:

```text
MANUAL
AUTO
```

MANUAL:

```text
0 - 100
```

AUTO:

computed from first-level subtasks.

v0.1.0 supports only one subtask level.

Do not add unlimited task nesting without explicit approval.

---

# 16. Task Timing

Tasks should support:

```text
planned_start_at
planned_end_at
actual_start_at
completed_at
```

`actual_start_at` should normally be recorded when work first enters an active state.

`completed_at` should normally be recorded when the task first reaches completion.

System-generated timestamps must not be freely forgeable by clients.

---

# 17. Task Dependencies

v0.1.0 supports simple same-project predecessor dependencies.

Example:

```text
Task B depends on Task A
```

If predecessors are unfinished:

```text
B = BLOCKED
```

A task may depend on multiple predecessors.

All predecessors must complete before automatic unblocking.

Circular dependencies are forbidden.

Reject:

```text
A -> B
B -> C
C -> A
```

Do not introduce these in v0.1.0 without explicit approval:

```text
FS
SS
FF
SF
lead
lag
cross-project dependency
```

---

# 18. Dependency Override

Authorized users may explicitly:

> Ignore dependency and start

This must never happen silently.

Required flow:

```text
detect unfinished dependency
        ↓
show warning
        ↓
user explicitly chooses override
        ↓
require reason
        ↓
backend re-checks permission
        ↓
perform transition
        ↓
write audit log
```

Recommended action:

```text
OVERRIDE_TASK_DEPENDENCY
```

---

# 19. Comments

Comments support:

```text
text
reply
@mention
attachment
edit
withdraw
```

Editing is allowed, but edit history must remain available.

Withdrawal must not physically erase the original record.

Public UI may display:

```text
Waiting 撤回了一条评论
```

but authorized audit mechanisms must retain historical evidence.

---

# 20. Approval Philosophy

v0.1.0 uses:

```text
Template-driven approval
+
Configurable approval nodes
```

Do not build a BPMN drag-and-drop workflow designer in v0.1.0.

Approval correctness is more important than visual workflow editing.

---

# 21. Approval Template Versioning

Approval templates must be versioned.

Existing approval instances continue using the version from which they were created.

A running approval must not silently change when a newer template version is published.

Approval instances must preserve enough snapshot data to explain historical behavior.

---

# 22. Approval Form Fields

Initial field types include:

```text
TEXT
TEXTAREA
NUMBER
MONEY
DATE
DATETIME
SELECT
MULTI_SELECT
USER
ATTACHMENT
```

Do not create a new physical database table for every approval type unless there is a strong domain reason.

Avoid uncontrolled growth such as:

```text
leave_requests
expense_requests
purchase_requests
...
```

when a schema-driven approval form is sufficient.

---

# 23. Approval States

Core states:

```text
DRAFT
PENDING
APPROVED
REJECTED
RETURNED
CANCELLED
```

Once an approval is PENDING:

- the applicant may not silently edit submitted data;
- the reviewed content must remain stable.

To modify:

1. applicant withdraws and creates/resubmits appropriately; or
2. reviewer RETURNs it.

After RETURN and modification:

> approval restarts from the first approval node.

It does not continue from the returned node.

---

# 24. Multi-Approver Nodes

Supported initial modes:

```text
ANY_ONE
ALL
```

Do not implement voting ratios such as:

```text
2/3
3/5
60%
```

in v0.1.0.

---

# 25. Dynamic Approvers

Supported concepts include:

```text
FIXED_USER
DIRECT_MANAGER
PRIMARY_DEPT_MANAGER
PROJECT_OWNER
PROJECT_DEPUTY
ORG_UNIT_MANAGER
SYSTEM_ROLE
```

Do not casually merge semantically different concepts.

Especially:

```text
DIRECT_MANAGER
```

and:

```text
ORG_UNIT_MANAGER
```

are not automatically the same domain concept.

If one temporarily falls back to the other, document that fallback explicitly.

At submission time:

1. resolve actual approvers;
2. validate them;
3. snapshot them into the approval instance.

Running approvals must not silently change because organization membership later changes.

---

# 26. Self-Approval Is Forbidden

The applicant may not approve their own request.

If dynamic resolution returns the applicant:

```text
resolve configured fallback chain
```

Fallback behavior:

```text
system default fallback
+
template-level override
```

If no legal approver can be found:

```text
submission must fail
```

with a clear configuration error.

Never silently skip an approval node.

---

# 27. Organization Model

Organization units form a tree.

Types include:

```text
DEPARTMENT
TEAM
```

v0.1.0 uses adjacency list:

```text
parent_id
```

PostgreSQL recursive CTEs are sufficient.

Do not replace this with Closure Table, Nested Set, or another complex representation without measured need.

Moving organization nodes must prevent cycles.

---

# 28. User Organization Membership

A user may belong to multiple organization units.

A user has one primary department where applicable.

Model:

```text
User N:N OrgUnit
```

Primary department uniqueness should be defended by both:

```text
service invariant
+
database constraint/index
```

Organization membership and project membership are independent concepts.

```text
Org Membership != Project Membership
```

Do not derive one automatically from the other unless a specific business rule says so.

---

# 29. Authentication Model

EasyOA v0.1.0 is:

```text
single organization
self-hosted
web-first
```

Default authentication model:

```text
Session Authentication
```

Use:

```text
HttpOnly Cookie
Secure in production
SameSite
CSRF protection
Session Fixation protection
```

Do not place authentication tokens in:

```text
localStorage
```

without explicit architectural approval.

Passwords must use a suitable one-way password hash such as BCrypt or Argon2.

Never:

- store plaintext passwords;
- log passwords;
- expose password hashes.

---

# 30. Secret Separation

Different cryptographic purposes must use different keys.

Do not use one secret for everything.

Recommended separation:

```text
EASYOA_SESSION_SECRET
    session-related HMAC/signing

EASYOA_ENCRYPTION_KEY
    sensitive field encryption
    TOTP secret encryption

EASYOA_SECURITY_EVENT_KEY
    security event integrity / keyed chain if required
```

Exact names may evolve, but cryptographic purpose separation must remain.

Do not reuse the session secret as the long-term sensitive-data encryption key.

---

# 31. Session Safety

Persistent session metadata must not expose raw session identifiers unnecessarily.

If database identity matching is required, prefer keyed hashes/HMACs.

Important revocation triggers include:

```text
logout
password change
account disabled
security-sensitive role change
manual session revoke
```

A revoked session must not remain usable because the browser still holds a cookie.

---

# 32. File Security

Attachments must not be directly public static files.

Forbidden design:

```text
/uploads/report.pdf
```

Preferred access model:

```text
GET /api/files/{fileId}
```

Flow:

```text
Authentication
    ↓
Resource Permission
    ↓
File Permission
    ↓
Download
```

Storage filenames should use generated identifiers rather than original filenames.

Metadata may contain:

```text
original_name
stored_name
mime_type
size
sha256
uploader
resource_type
resource_id
created_at
```

Protect against:

```text
IDOR
path traversal
filename collision
MIME spoofing
dangerous uploads
oversized uploads
unauthorized download
```

---

# 33. Audit Model

EasyOA distinguishes:

```text
audit_logs
security_events
```

They are not identical.

## audit_logs

Business audit trail.

Normal application behavior is append-oriented.

Ordinary users, project roles, and normal administrators must not casually edit or delete audit entries.

Normal repositories/services should not expose generic:

```text
update()
delete()
```

for audit entries.

ROOT-only controlled audit cleanup is permitted only through a dedicated sensitive-operation path.

Such cleanup must require:

```text
password re-authentication
+
TOTP
+
reason
+
impact preview
+
final confirmation
```

and the cleanup itself must be permanently recorded in `security_events`.

## security_events

Security events are stricter.

Examples:

```text
ROOT-sensitive operation
MFA reset
audit cleanup
critical security policy change
sensitive export
system data destruction
```

`security_events` are append-only from the application perspective.

Do not create a normal deletion path.

The model may reserve:

```text
previous_hash
entry_hash
```

for tamper-evident chaining.

---

# 34. Audit Content

Important audit fields include:

```text
actor
action
resource_type
resource_id
before_data
after_data
reason
ip_address
user_agent
request_id
risk_level
created_at
```

Never place secrets in audit logs.

Do not record:

```text
password
raw TOTP secret
session secret
encryption key
raw authentication credential
```

Minimize sensitive business content when full content is not needed for traceability.

---

# 35. Sensitive Operations

Sensitive operations must use a centralized design.

Prefer a component such as:

```text
SensitiveOperationService
```

Typical flow:

```text
re-enter current password
        ↓
verify TOTP
        ↓
require reason
        ↓
show impact
        ↓
final confirmation
        ↓
execute
        ↓
write security_event
```

Do not independently reimplement this flow in every module.

---

# 36. TOTP

TOTP must use standard interoperable TOTP.

TOTP secrets must:

- be encrypted at rest;
- not be logged;
- not be repeatedly returned by API;
- only be revealed when necessary during enrollment.

TOTP is a second factor.

Entering the same password twice is not two-factor authentication.

---

# 37. API Rules

All API input must use explicit DTOs.

Do not accept JPA entities directly.

Use validation.

Use consistent status codes.

Examples:

```text
400  malformed input
401  unauthenticated
403  authenticated but forbidden
404  resource not found or intentionally concealed
409  state / concurrency conflict
422  semantically invalid request
429  rate limited
500  unexpected server failure
```

Do not leak stack traces or internal implementation details to clients.

---

# 38. API Response Contract

Prefer a stable response structure.

Example:

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "..."
}
```

Error:

```json
{
  "success": false,
  "code": "TASK_NOT_FOUND",
  "message": "任务不存在或无权访问",
  "requestId": "..."
}
```

Frontend logic must not depend on Java exception class names or raw exception messages.

---

# 39. Security Threats Agents Must Consider

Every feature must consider relevant risks from:

```text
IDOR
Broken Access Control
Privilege Escalation
Mass Assignment
SQL Injection
XSS
CSRF
Path Traversal
Brute Force
Session Fixation
Unsafe File Upload
Sensitive Data Exposure
Race Conditions
Replay of sensitive operations
```

Do not claim a feature is secure because Spring Security is present.

Security depends on correct domain authorization and data access boundaries.

---

# 40. UI/UX Identity

EasyOA is not a traditional admin dashboard.

Do not make it look like:

```text
RuoYi
generic Element Admin
Bootstrap Admin
old ERP
school management system
legacy OA
database CRUD console
```

Desired direction:

```text
modern workspace
modern SaaS
clean project collaboration product
high information clarity
low visual noise
```

Reference qualities may be inspired by modern tools, but EasyOA must not clone another product.

---

# 41. Easy Visual Language

Design ratio:

```text
90% neutral
10% Easy Green
```

Easy Green is an accent, not a flood fill.

Use it primarily for:

```text
logo
primary action
active navigation
selection
progress
important success/active state
```

Avoid:

```text
full green pages
heavy gradients
excessive glassmorphism
neon glow
AI-looking decoration
oversized rounded cards everywhere
```

Prefer:

```text
white
soft gray
charcoal
subtle borders
restrained shadows
clear spacing
```

---

# 42. EasyUI Layer

Element Plus is infrastructure.

EasyOA owns the product design.

Prefer EasyOA-level wrappers where a stable product behavior exists.

Examples:

```text
EasyButton
EasyInput
EasySelect
EasyDrawer
EasyStatus
EasyAvatar
EasyMemberPicker
EasyConfirm
EasyEmpty
EasyCommandPalette
```

Do not wrap every Element Plus component only for abstraction.

A wrapper should provide real value such as:

```text
design consistency
behavior consistency
domain semantics
accessibility
```

---

# 43. Navigation

Primary navigation direction:

```text
工作台

项目
我的任务

审批

团队
组织架构

数据中心

审计日志
系统设置
```

Permission-sensitive items may be hidden for UX, but backend authorization remains mandatory.

Do not turn the sidebar into a database table index.

Avoid dozens of nested admin menus.

---

# 44. Workspace Dashboard

The dashboard answers:

```text
What should I do now?
What is waiting for me?
What changed in my projects?
```

Primary content includes:

```text
我的任务
待我审批
进行项目
即将到期
项目动态
项目进度
```

KPI cards must lead somewhere useful.

Do not create decorative metrics with no action or meaning.

The dashboard is not a monitoring wall.

---

# 45. Project UI

Project detail structure:

```text
概览
看板
任务
时间线
成员
设置
```

Project collaboration is the visual center of EasyOA.

The board is a first-class product surface.

---

# 46. Task Detail UI

Primary task detail interaction:

> Right Side Panel / Drawer

Do not make a giant nested modal the normal task workflow.

Opening task details should preserve board/list context.

URL should reflect the opened task where practical.

Example:

```text
/projects/12/board?task=86
```

Refreshing should preserve the selected task.

---

# 47. Modal Discipline

Prefer:

```text
side panel
inline action
popover
dropdown
context menu
command palette
toast
```

Use modal dialogs when actually appropriate.

Do not create:

```text
modal inside modal inside confirm
```

Do not ask "Are you sure?" for every harmless action.

Confirmation is for meaningful risk.

---

# 48. Approval UI

Approval interfaces must present user concepts, not database concepts.

Show concepts such as:

```text
申请人
部门负责人
项目负责人
财务
当前节点
```

Do not expose implementation labels such as:

```text
Node 2
Assignee ID 28
Transition Code X3
```

unless inside developer/debug tooling.

---

# 49. Organization UI

Preferred layout:

```text
left: organization tree
right: selected organization members
```

Support:

```text
expand
collapse
search
member management
```

Multi-organization membership and primary department should be understandable in profile UI.

---

# 50. Command Palette

Keyboard:

```text
macOS      Command + K
Windows    Ctrl + K
Linux      Ctrl + K
```

Progressively support:

```text
search tasks
search projects
search users
search approvals

create task
create project
start approval

navigate to major pages
```

Do not turn it into an uncontrolled plugin system in v0.1.0.

---

# 51. Search

v0.1.0 should prefer PostgreSQL-based search.

Do not introduce Elasticsearch simply because search exists.

Search domains:

```text
Projects
Tasks
Users
Approvals
```

Group results meaningfully.

---

# 52. Responsive Target

v0.1.0 officially targets desktop web.

```text
1440+    best
1280     fully supported
1024     usable
<768     not an official v0.1.0 target
```

Avoid brittle layouts that make future mobile support impossible.

Do not build the app around absolute positioning.

---

# 53. Explicitly Out of Scope for v0.1.0

Unless the user explicitly changes scope, do not implement:

```text
IM chat
video meetings
attendance
payroll
CRM
email client
online document editor
knowledge base
calendar suite
AI assistant
mobile app
desktop app
microservices
Kubernetes
Kafka
Elasticsearch
BPMN visual designer
unlimited nested tasks
cross-project task dependency
advanced dependency types
```

Do not silently expand scope because a feature "would be cool".

---

# 54. Testing Standard

A feature is not complete without relevant tests.

Backend tests should include as appropriate:

```text
unit tests
integration tests
authorization tests
state machine tests
database constraint tests
```

Prefer real PostgreSQL integration testing through Testcontainers when PostgreSQL-specific behavior matters.

Important targets include:

```text
permission boundaries
organization tree cycle prevention
primary department uniqueness
project role transitions
task dependency cycle detection
approval state transitions
self-approval prevention
dynamic approver resolution
fallback approvers
file authorization
session revocation
```

---

# 55. Security Tests

For protected resources, actively test:

```text
unauthenticated request
wrong project ID
modified task ID
cross-project resource access
member calling owner-only endpoint
unauthorized attachment download
direct API call bypassing frontend
disabled user session
stale/revoked session
CSRF behavior
```

A happy-path-only test suite is insufficient.

---

# 56. Frontend Quality

TypeScript must remain strict.

Avoid uncontrolled:

```text
any
```

Do not put all request state into Pinia.

Use Pinia for true application-level state such as:

```text
auth
notifications
global preferences
```

Feature/page request state should remain local or domain-appropriate when possible.

Every major page should handle:

```text
loading
empty
error
forbidden
not found
```

Do not show fake data to make unfinished pages look complete.

Unimplemented features should use honest empty/roadmap states.

---

# 57. No Fake Completion

Never:

- create mock data in production paths to simulate completeness;
- display a fake chart whose backend does not exist;
- mark a roadmap item complete because the UI exists;
- add TODO and claim the feature works;
- stub a permission check with `return true`;
- mock production-critical behavior and call it shipped.

If a feature is unfinished, state that it is unfinished.

The repository values truth over appearance.

---

# 58. Logging

Use structured logging.

Useful fields include:

```text
timestamp
level
requestId
userId
method
path
status
duration
```

Never log:

```text
password
raw session ID
TOTP secret
encryption key
database password
full sensitive approval payload without need
```

Request IDs should be propagated across request handling.

---

# 59. Performance

Initial target:

```text
5-50 users common
hundreds of users reasonable
tens of projects
tens of thousands of tasks
```

Do not prematurely build a distributed architecture.

Do pay attention to:

```text
N+1 queries
pagination
indexes
query boundaries
lazy/eager loading
large audit tables
large task tables
file streaming
```

List APIs should be paginated unless the bounded dataset is intentionally small.

---

# 60. Deployment

Production topology:

```text
Internet
   ↓
Nginx : 80 / 443
   ↓
EasyOA Web / API
   ↓
PostgreSQL internal network
```

PostgreSQL must not be exposed publicly by default.

Only edge services should bind public web ports.

Health endpoints should reveal minimal information.

---

# 61. Environment Variables

Secrets must not be committed.

Maintain:

```text
.env.example
```

Never place real credentials in:

```text
README
source code
Dockerfile
GitHub workflow logs
screenshots
production test fixtures
```

Development demo credentials must be clearly development-only.

Production must not automatically create demo accounts.

## Production Data Safety

Do not copy production user data, real passwords, live Session identifiers, TOTP
secrets, production database dumps, or sensitive approval contents into tests,
fixtures, screenshots, README, logs, Skill resources, or Git history. Use synthetic
or deliberately sanitized local data for development and verification.

---

# 62. CI

Every meaningful change must preserve CI.

At minimum:

Backend:

```text
mvn test
mvn package
```

Frontend:

```text
npm ci
npm run type-check
npm run build
```

Infrastructure:

```text
docker compose config
```

A feature is not finished while required CI checks fail.

---

# 63. Git & Commit Discipline

Prefer Conventional Commits.

Examples:

```text
feat(project): add project lifecycle
feat(task): add dependency cycle detection
fix(auth): revoke sessions after role change
fix(file): prevent cross-project file access
refactor(project): centralize permission evaluation
test(approval): cover self-approval fallback
docs(agent): clarify audit retention model
```

Commits should describe one coherent change.

## Git Safety

Check `git status` before modifying files. Preserve the user's uncommitted changes
and modify only files relevant to the current task.

Without an explicit user request, do not perform `git push`, force push,
`git reset --hard`, `git clean -fd`, branch deletion, history rewriting, tag creation,
or release creation. A Skill or a repository example is not authorization.

## Remote Mutation Policy

Local development, tests, and commit suggestions are allowed within the task.
`push origin`, PR merge, force push, tags, releases, remote branch deletion, and
GitHub Actions secret changes require explicit user authorization. Existing
authorization applies only to the action and scope the user approved.

Avoid meaningless messages such as:

```text
update
fix
changes
done
final
```

---

# 64. README Is a Product Contract

README is not marketing fiction.

Statements about:

```text
security
permissions
architecture
deployment
completed features
test counts
roadmap
```

must reflect reality.

When code materially changes one of these areas, update README or relevant docs in the same phase.

Do not use absolute words such as:

```text
never
always
permanent
impossible
```

unless implementation and product policy truly guarantee them.

---

# 65. Documentation Discipline

Before implementing a large behavior change, inspect existing:

```text
README
docs/
migrations
security configuration
tests
domain models
permission services
```

Do not create duplicate documentation when an existing document should be updated.

Do not allow architecture documents and implementation to drift.

---

# 66. Agent Startup Protocol

Every AI agent must follow this order before modifying code.

First check `git status`, the current branch, and recent Git history. Determine the
current phase using the evidence required by section 2; do not rely on chat context
or a stale roadmap statement. Select relevant local Skills after reading this file.

## Step 1 — Read

Read at least:

```text
EasyOAAgent.md
README.md
relevant docs/
current roadmap
recent migrations
relevant tests
```

## Step 2 — Inspect

Inspect existing implementation before generating replacement code.

Determine:

```text
what already exists
what is partially implemented
what is missing
what invariants already exist
what tests protect behavior
```

## Step 3 — Preserve

Preserve working architecture unless there is a concrete defect.

Do not rewrite large modules because the agent would personally design them differently.

## Step 4 — Plan

For non-trivial work, identify:

```text
data model impact
API impact
permission impact
audit impact
frontend impact
migration impact
test impact
documentation impact
```

## Step 5 — Implement

Implement the smallest complete solution that fits the product architecture.

## Step 6 — Verify

Run relevant:

```text
tests
type checks
builds
migration checks
compose validation
```

## Step 7 — Review

Review specifically for:

```text
authorization
data leakage
state transition correctness
concurrency
fake completion
UI consistency
```

## Step 8 — Document

Update README/docs if behavior or product contract changed.

---

# 67. Agent Change Discipline

An agent must not casually:

- replace the frontend framework;
- replace PostgreSQL;
- replace Session auth with JWT;
- add Redis;
- add microservices;
- add a message queue;
- redesign project roles;
- change audit retention semantics;
- weaken authorization;
- change the Easy visual language;
- add major features outside the roadmap.

If such a change appears necessary, explain:

```text
current limitation
proposed change
benefit
cost
migration impact
security impact
alternatives
```

and obtain user approval before proceeding.

## Dependency Discipline

Dependency changes must solve a problem within the current task. Do not upgrade
Spring Boot or Vue, replace core libraries, add frameworks, or broadly regenerate
lockfiles merely because a newer version exists. Keep necessary dependency and
lockfile changes scoped and reviewable. Handle major upgrades as a separate task.

---

# 68. Permission Review Checklist

For every new endpoint, ask:

```text
Who may call it?
What resource does it touch?
How is membership verified?
How is project scope verified?
How is organization scope verified?
Can the caller change the ID?
Can another project's ID be substituted?
Can a normal member call it?
What happens if the resource is archived?
Is the action audited?
```

If these questions cannot be answered, the endpoint is not ready.

---

# 69. Data Access Rule

Repository access is not authorization.

A repository returning data by ID does not mean the caller may access it.

Bad conceptual pattern:

```text
findById(id)
return resource
```

Required conceptual pattern:

```text
authenticate
resolve resource
authorize against domain context
apply data scope
return permitted DTO
```

For especially sensitive resources, prefer queries already constrained to accessible scope where practical.

---

# 70. Archived Data

Archiving is not deletion.

Archived entities may become read-only.

Agents must define:

```text
who can view archived data
who can restore it
whether child resources remain accessible
whether new operations are blocked
```

Do not leave archived behavior ambiguous.

---

# 71. Error Handling

Errors should be actionable but not leak secrets.

Good:

```text
任务不存在或无权访问
审批流程配置不完整：主部门负责人未设置
当前项目已归档，无法创建任务
```

Bad:

```text
NullPointerException at ProjectService.java:183
org.postgresql.util.PSQLException...
User 153 exists but you do not own project 88
```

Use stable machine-readable error codes.

---

# 72. Accessibility & Interaction

Where practical:

- keyboard navigation should work;
- focus states should remain visible;
- important actions should not rely on color alone;
- forms should expose validation clearly;
- drawers and dialogs should manage focus correctly.

Do not sacrifice usability for visual minimalism.

---

# 73. Design Review Rule

When implementing UI, ask:

```text
Does this look like EasyOA?
Does this feel like a workspace product?
Is the page solving a user's task?
Is information density appropriate?
Is Easy Green restrained?
Is the hierarchy obvious?
Is this just a CRUD table with decoration?
```

If the last answer is yes, reconsider the design.

---

# 74. Avoid AI-Looking UI

Do not generate pages with:

```text
random gradient blobs
unnecessary motivational copy
fake analytics
excessive cards
oversized hero sections inside internal tools
decorative sparkles
generic admin-template greetings
```

EasyOA should look intentional and professional.

---

# 75. Phase Discipline

When working on a phase:

1. finish the core domain model;
2. finish the permission model;
3. finish audit behavior;
4. finish essential frontend behavior;
5. add tests;
6. verify runtime;
7. update documentation;
8. only then move forward.

Do not open five future phases at once.

Cross-phase infrastructure may be introduced only when genuinely required.

---

# 76. Definition of Done

A feature is done only when applicable items are satisfied:

```text
[ ] data model implemented
[ ] migration written
[ ] backend API implemented
[ ] permission enforcement implemented
[ ] audit behavior implemented
[ ] DTO validation implemented
[ ] frontend implemented
[ ] loading state implemented
[ ] empty state implemented
[ ] error state implemented
[ ] authorization failure handled
[ ] tests added
[ ] tests pass
[ ] frontend type-check passes
[ ] frontend build passes
[ ] backend build passes
[ ] docs updated
```

Do not call a feature complete when only the happy-path UI works.

---

# 77. Agent Handoff Protocol

Before another AI agent takes over, leave the repository in a state where the next agent can understand:

```text
what changed
what is complete
what is intentionally incomplete
what migrations were added
what tests were added
what known risks remain
what should be done next
```

Prefer durable information in:

```text
README
docs/
issues
commit messages
code comments where truly needed
```

Do not rely on hidden chat context.

The repository must explain itself.

---

# 78. Conflict Resolution Order

Within the applicable instructions, resolve product and engineering tradeoffs using
this order. Instruction precedence itself is defined in section 0.1:

```text
Security
    >
Data Integrity
    >
Business Correctness
    >
Permission Correctness
    >
Auditability
    >
Maintainability
    >
UX
    >
Convenience
```

Do not use security as an excuse for poor UX when both can be achieved.

---

# 79. Simplicity Rule

Before adding a new abstraction, dependency, service, table, framework, or infrastructure component, ask:

```text
What concrete problem does this solve today?
Can the existing architecture solve it cleanly?
What is the maintenance cost?
Does this make the product easier to reason about?
```

If the answer is vague, do not add it.

---

# 80. Final Agent Instruction

When working on EasyOA, do not optimize for:

```text
largest code output
most dependencies
most layers
most "enterprise" terminology
fastest visual demo
```

Optimize for:

```text
correctness
security
clarity
traceability
product quality
long-term maintainability
```

EasyOA must remain:

> simple enough to understand, strict enough to trust, polished enough to use.

Every change should move the project toward that goal.

---

# 81. Mandatory Pre-Commit Self Review

Before finishing any meaningful EasyOA change, the agent must answer internally:

```text
1. Did I preserve the EasyOA product direction?
2. Did I introduce unnecessary architecture?
3. Can this API be abused by changing IDs?
4. Did I put any real security decision in the frontend?
5. Did I preserve auditability?
6. Did I add or update migrations correctly?
7. Did I consider archived/disabled states?
8. Did I add relevant negative tests?
9. Does the UI still look and behave like EasyOA?
10. Did I update documentation if the product contract changed?
11. Did all required tests/builds pass?
12. Am I claiming anything is complete that is not actually complete?
```

If any answer is unsatisfactory, fix it before declaring completion.

---

## End of EasyOAAgent.md

This document is intentionally strict.

The purpose is not to slow development down.

The purpose is to stop different AI agents from turning EasyOA into different products.


# Distribution / Deployment / Licensing Infrastructure

New Community Edition releases from v0.2.0 use AGPL-3.0-only. Preserve historical
MIT grants; never describe this as retroactively revoking released licenses.
Keep LICENSE, NOTICE, Maven, frontend metadata, README and About consistent.
Do not add Pro activation, license keys, device binding or feature locks.

The canonical entry is `easyoactl`; the default prints help. `easyoactl.ps1` is
native Windows compatibility. Do not reintroduce `start.sh` as the official
entry. Production install/start/restart require signature and protected-file
verification, validated configuration and TLS. Development is explicit and
must not be labelled Official. Stop must preserve volumes and configuration.
Backup includes database, attachments and private deployment configuration;
restore validates first, requires confirmation and takes a safety backup.
Directory upgrades validate the new package and preserve the old deployment;
never pretend that database downgrade or rollback is automatic.

VERSION is authoritative and must match Maven, frontend package/lock and tag.
Only tag pushes trigger Release; push/PR execute CI. Release must gate on real
backend tests/package, frontend install/type-check/build, Compose and script
checks. Build a whitelist-based deployment package, SHA-256 manifest, Ed25519
signature and archive checksum/signature. Private signing keys only enter the
signing process from Actions Secrets or secure offline files; never include
keys, .env, data, uploads, logs, backups, caches, Git or local Agent Skills.

Reject existing releases; never replace official assets silently. Upload to a
new temporary Draft, verify all assets, then promote to a non-Draft Release.
Stable semantic tags are normal releases; prerelease tags remain prereleases.
Use least-privilege GITHUB_TOKEN: contents write only in publication job.
Missing signing secrets fail closed. Report actual remote verification apart
from local workflow validation and API protocol tests.

Trust public keys via an independent channel. Authenticate downloads before
safe extraction or executing downloaded scripts. Hashes without a signature
are not publisher authentication. Deployment integrity is not DRM and does
not remove AGPL freedoms. Public GET /api/system/about provides release-only
metadata and corresponding source; it must not expose private configuration.
Official API identity requires a valid signature, compiled trust fingerprint,
signed VERSION and actual running jar hash. Frontend never asserts Official
from environment or static text. Document that API identity does not replace
full deployment verification and cannot protect a compromised host.
