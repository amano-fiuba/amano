---
description: "Task list for groups-management feature implementation"
---

# Tasks: groups-management

**Input**: Design documents from `specs/010-groups-management/` (plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md)

**Tests explicitly requested**: JUnit 5 + Spring Boot tests (`*Test` fast, `*IT` Testcontainers) under `backend/src/test/java/`; Vitest `.test.jsx` colocated under `frontend/src/`.

**Organization**: Tasks grouped by user story so each story is independently implementable and testable. Phases ordered by priority (P1 first).

**Corrections applied (decisions from user, 2026-10-27)**:
1. The repo already exists (Java 17, Spring Boot, Maven, JWT, Security, Flyway, PostgreSQL): setup tasks for existing infrastructure are removed; only verification tasks are included. Authentication, avatar upload validation and user infrastructure are **existentes** — their tasks only plan the difference.
2. Group name: only required and non-empty (FR-001) — **YA SINCRONIZADO** (2026-10-27): `data-model.md` y `contracts/api-contracts.md` ya no definen unicidad ni límite 2-50 (columna VARCHAR(255)); no uniqueness check or forced-length validation is implemented.
3. Member removal uses `DELETE` and admin assign/revoke are **separate operations** (`POST`/`DELETE /api/groups/{id}/members/{userId}/admin`) — **YA SINCRONIZADO** en `contracts/api-contracts.md` (la POST de baja y la combinada quedaron reemplazadas).
4. Search for adding members is restricted to the **user's friends** (friendship relation) — **YA SINCRONIZADO**: `research.md` y `data-model.md` ya no proponen `UserRepository.searchVerified()` (que busca en toda la base) y definen `FriendshipPort` con stub configurable (T020).
5. A **port** (`DebtPort`) queries pending debts from the debts module, with a stub until that module exists.
6. Last admin is protected on **both** leaving (US5) and role revocation (US10).
7. Simulated profiles cannot abandon themselves; they are removed by an admin or by a member when "miembros pueden administrar perfiles simulados" is active; the pending-debt rule applies.
8. The activity event service lives in **Foundational** so US3/US4/US5 can record events without depending on US9. Only `MEMBER_ADDED` and `MEMBER_REMOVED` are implemented; `EXPENSE_CREATED`/`PAYMENT_MADE` are reserved hook points (not implemented); `PERMISSION_CHANGED` (US2) and `PHOTO_CHANGED` (US8) hook in from their stories.
9. Frontend tasks cover web **and mobile-responsive views** within the existing React app (no new mobile codebase).
10. A `GroupPermissionChecker` centralizes authorization by role + casillas (settings editing, member addition, simulated-profile removal); the old authz-review Polish task was folded into that checker task.
11. `spec.md` does not define default values for the casillas → all "cannot/require" casillas default to **false** (overriding the defaults written in `data-model.md`; synced in the Polish sync task). Roles use `ADMIN`/`MEMBER` uniformly; the `group_members`/`event_type` columns are `VARCHAR` + `CHECK` instead of native ENUM. Registered memberships are soft-removed via `left_at`; simulated profiles are rows in `simulated_profiles` (not in `group_members`) and their removal is a physical DELETE.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story label `[US1]`…`[US10]`; NOT used in Setup, Foundational or Polish phases
- All task IDs are unique and consecutive; every line follows the checklist format

## Path Conventions

- **Backend main**: `backend/src/main/java/com/amano/backend/groups/` (new package `groups`)
- **Backend tests**: `backend/src/test/java/com/amano/backend/groups/`
- **Flyway migrations**: `backend/src/main/resources/db/migration/` (next versions: V6–V11; existing tail is V5)
- **Frontend views**: `frontend/src/pages/groups/` and `frontend/src/components/groups/`
- **Frontend tests**: `.test.jsx` next to each component

---

## Phase 1: Setup (Repository Verification Only)

**Purpose**: Verify existing infrastructure — nothing new to initialize.

- [X] T001 Verify Flyway migration numbering: current tail is `V5__expense_recurrence.sql`; confirm new migrations must use V6–V11 (6 migrations for this module, including `V11__create_group_photos_table.sql` for US8 bytes) in `backend/src/main/resources/db/migration/` and existing migrations are not modified
- [X] T002 [P] Verify existing JWT cookie authentication and user resolution (existente in `com.amano.backend.auth`) exposes the current authenticated user to new group endpoints without new configuration
- [X] T003 [P] Verify frontend test setup (Vitest, `.test.jsx`, `npm run test:coverage` in `frontend/package.json`) and backend test commands (`mvn -B verify`, JaCoCo ≥85% in `backend/pom.xml`)

**Checkpoint**: Existing infrastructure confirmed; new work is limited to the new `com.amano.backend.groups` package and V6+ migrations.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Entities, migrations, repositories, ports and the activity event service. MUST be complete before ANY user story.

**⚠️ CRITICAL**: No user story can start until this phase is complete.

- [X] T004 Write Flyway migration `V6__create_groups_table.sql` in `backend/src/main/resources/db/migration/`: table `groups` with `id` BIGSERIAL PK, `name` VARCHAR(255) NOT NULL (no invented length limit — decision 7; only required and non-empty, enforced in the service layer), `description` VARCHAR(200) NULLable, `photo` VARCHAR(255) NULLable, `created_at`/`updated_at` TIMESTAMP/OffsetDateTime NOT NULL; index `idx_groups_name` on `name`(per data-model.md)
- [X] T005 Write Flyway migration `V7__create_group_members_table.sql`: table `group_members` with PK (`user_id`,`group_id`), FK `user_id` → `users(id)`, FK `group_id` → `groups(id)`, `role` VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','MEMBER')) — VARCHAR+CHECK instead of native ENUM (same value set everywhere in the module), `joined_at` NOT NULL, `left_at` NULLable (soft baja: the row is preserved so prior expenses and history entries stay traceable; active membership = `left_at` IS NULL); indexes `idx_group_members_group_id` and `idx_group_members_user_id`. NOTE: simulated profiles are NOT stored in `group_members` — they are modeled and counted separately (see T006)
- [X] T006 Write Flyway migration `V8__create_simulated_profiles_table.sql`: table `simulated_profiles` with `id` PK, FK `group_id` → `groups(id)`, `name` VARCHAR(100) NOT NULL, `email` VARCHAR(191) NOT NULL, `created_at` NOT NULL; UNIQUE (`group_id`,`email`) with index `idx_simulated_profiles_group_email`; no FK to `users`. Member modeling: simulated profiles are members of the group implicitly via `group_id` — they never get a `group_members` row (no user account); they count as active members alongside `group_members` rows with `left_at` IS NULL, and their removal is a physical DELETE of the row (recorded as `MEMBER_REMOVED` in the activity history)
- [X] T007 Write Flyway migration `V9__create_group_permissions_table.sql`: table `group_permissions` with `id` PK, `group_id` PK+FK+UNIQUE; columns NOT NULL. Defaults: **`spec.md` does not define default values for the casillas (FR-005 lists them without defaults) → per decision 11, all "cannot/require" casillas default to false**: `members_cannot_make_expenses` = false, `members_cannot_invite` = false, `members_cannot_edit_settings` = false, `members_can_manage_simulated_profiles` = false, `members_can_generate_expenses_for_others` = false, `payments_require_validation` = false (`data-model.md` states different defaults — conflicts are synced in the Polish sync task)
- [X] T008 Write Flyway migration `V10__create_group_activity_table.sql`: table `group_activity` with `id` PK, FK `group_id` → `groups(id)`, `event_type` VARCHAR(50) NOT NULL CHECK (event_type IN ('MEMBER_ADDED','MEMBER_REMOVED','MEMBER_LEFT','PERMISSION_CHANGED','PHOTO_CHANGED','EXPENSE_CREATED','PAYMENT_MADE')) — VARCHAR+CHECK instead of native ENUM (values defined in T021), `actor_user_id` FK → `users(id)` NULLable, `details_json` TEXT NULLable, `created_at` NOT NULL; indexes `idx_group_activity_group_id` and `idx_group_activity_event_type`
- [X] T009 [P] Create entity `Group` in `backend/src/main/java/com/amano/backend/groups/Group.java` mapped to `groups` (fields per T004: `id`, `name`, `description` nullable, `photo` nullable, `createdAt`, `updatedAt`)
- [X] T010 [P] Create entity `GroupMember` in `backend/src/main/java/com/amano/backend/groups/GroupMember.java` with composite key (`userId`,`groupId`) and `role` ∈ {ADMIN, MEMBER} — unified ADMIN/MEMBER values across the module (same as T005), no GROUP_ADMIN variant; role lives in the user-group relation, not on the User
- [X] T011 [P] Create entity `SimulatedProfile` in `backend/src/main/java/com/amano/backend/groups/SimulatedProfile.java` (fields per T006; no user account)
- [X] T012 [P] Create entity `GroupPermissions` in `backend/src/main/java/com/amano/backend/groups/GroupPermissions.java` with the 6 boolean permission fields (one row per group, defaults per T007)
- [X] T013 [P] Create entity `GroupActivity` in `backend/src/main/java/com/amano/backend/groups/GroupActivity.java` (standalone event row per preservation pattern: references only `group_id`, persists even if the related member/expense/payment is later modified or deleted)
- [X] T014 [P] Create `GroupRepository.java` in `backend/src/main/java/com/amano/backend/groups/` including query for the user's active groups (join `group_members` by `user_id`)
- [X] T015 [P] Create `GroupMemberRepository.java` with `findByGroupId`, `findByGroupIdAndUserId`, `existsByGroupIdAndUserId`, `countByGroupIdAndRole(ADMIN)` (for the last-admin guard), and active-member counting: active members = `group_members` rows with `left_at` IS NULL (helpers `findByGroupIdAndLeftAtIsNull`, `countActiveByGroupId`) + simulated profiles of the group via `SimulatedProfileRepository` (see T005/T006 model)
- [X] T016 [P] Create `SimulatedProfileRepository.java` with `existsByGroupIdAndEmail` and `findByGroupAndEmail`
- [X] T017 [P] Create `GroupPermissionsRepository.java` with `findByGroupId` (one configuration per group)
- [X] T018 [P] Create `GroupActivityRepository.java` with paginated `findByGroupIdOrderByCreatedAtDesc` and optional `eventType` filter
- [X] T019 Create port `DebtPort.java` in `backend/src/main/java/com/amano/backend/groups/port/` defining "consultar deudas pendientes del grupo para un miembro" (returns whether the member has pending debts with other group members) and stub `NoOpDebtPort.java` (always reports zero debts) so local development works before the debts module exists
- [X] T020 Create port `FriendshipPort.java` in `backend/src/main/java/com/amano/backend/groups/port/` defining the friendship relation queries (member user IDs of the user's friends, search filtered candidate list) and stub `NoOpFriendshipPort.java` (empty list by default) configurable via properties or test fixtures so it can return sample friends for dev and automated tests (US1 requires an additional member to create a group) until the friendship module exists — ⚠️ conflict: `research.md` proposes `UserRepository.searchVerified()` over all users; the user decided search must be restricted to friends
- [X] T021 Create `ActivityEventService.java` in `backend/src/main/java/com/amano/backend/groups/` that records `GroupActivity` rows (actor, timestamp, `details_json`) with the event enum including `MEMBER_ADDED`, `MEMBER_REMOVED`, `MEMBER_LEFT`, `PERMISSION_CHANGED`, `PHOTO_CHANGED`, `EXPENSE_CREATED`, `PAYMENT_MADE`; this task implements recording of `MEMBER_ADDED` and `MEMBER_REMOVED` only (called by US3 additions and US4 removals); `MEMBER_LEFT` is recorded by US5, `PERMISSION_CHANGED` by US2 and `PHOTO_CHANGED` by US8; `EXPENSE_CREATED`/`PAYMENT_MADE` remain reserved hook points without implementation; rows are standalone (preservation pattern)
- [X] T022 Create `GroupPermissionChecker.java` in `backend/src/main/java/com/amano/backend/groups/`: single decision point combining the caller's role and the group's casillas — settings editing allowed for ADMIN always and for MEMBER only when `members_cannot_edit_settings` = false; adding members allowed for ADMIN always and for MEMBER only when `members_cannot_invite` = false; simulated-profile removal allowed for ADMIN always and for MEMBER only when `members_can_manage_simulated_profiles` = true; all group actions consult this checker instead of duplicating role/checkbox logic; server-side enforcement means permission rules hold even when endpoints are called directly

**Checkpoint**: Foundation ready — user stories can now start; US3/US4/US5 can already record member events without depending on US9, and all stories can authorize through `GroupPermissionChecker`.

---

## Phase 3: User Story 1 — Crear un nuevo grupo (Priority: P1) 🎯 MVP

**Goal**: Un usuario crea un grupo proporcionando un nombre obligatorio (y opcionalmente descripción y foto) y al menos un miembro adicional (un amigo registrado). El sistema lo convierte automáticamente en Administrador. Si falla la creación, no queda ningún grupo o rol a medias.

**Independent Test**: Given a registered user with a friend, when they create a group with a valid name and at least one additional member, then the group is created with the user as ADMIN and appears in their groups list. When they attempt creation without a name or with no additional member, then creation fails and no group is created.

### Tests for User Story 1

- [X] T023 [P] [US1] Write `GroupServiceTest.java` in `backend/src/test/java/com/amano/backend/groups/`: creator becomes ADMIN, requires at least one additional member — a registered friend from `FriendshipPort` OR a simulated profile (name + email) —, name required and non-empty (no uniqueness/length assertions per decision 2), atomic failure (no partial rows)
- [X] T024 [P] [US1] Write `GroupControllerIT.java`: `POST /api/groups` 201/400 cases and `GET /api/groups` list; unauthenticated → 401
- [X] T025 [P] [US1] Write `CreateGroupView.test.jsx` in `frontend/src/`: name required error, member picking (friend or simulated profile), success message

### Implementation for User Story 1

- [X] T026 [US1] Create DTOs `CreateGroupRequest` and `GroupResponse` in `backend/src/main/java/com/amano/backend/groups/dto/` (`name` required; `description` optional)
- [X] T027 [US1] Implement `GroupService.createGroup()` in `backend/src/main/java/com/amano/backend/groups/GroupService.java`: validate name not null/not empty (decision 2), insert creator as `ADMIN` and the additional member as `MEMBER` in one transaction (no halfway states). The additional member may be a registered friend — chosen ONLY among the creator's friends via `FriendshipPort` — or a simulated profile (name + email, stored in `simulated_profiles` with the same modeling as US3/T006); create `GroupPermissions` row with defaults (T007, all casillas false)
- [X] T028 [US1] Implement `POST /api/groups` endpoint in `backend/src/main/java/com/amano/backend/groups/GroupController.java` returning 201 with creator/role/membersCount and 400 on invalid input
- [X] T029 [US1] Implement `GET /api/groups` (listado de grupos del usuario) in `GroupController.java`: groups where the user is an active member, with per-group role; no story-specific storage beyond `GroupRepository`
- [X] T030 [US1] Implement `GET /api/groups/{id}` detail endpoint with group info, `currentUserRole`, and 404 when not found/not member
- [X] T031 [US1] Create `frontend/src/pages/groups/CreateGroupView.jsx`: form with name (required), optional description, member selector (friends search fed by the friendship candidates or simulated-profile form); inline validation and error messages in the UI; mobile-responsive layout
- [X] T032 [US1] Create `frontend/src/pages/groups/GroupListView.jsx`: listado de grupos del usuario with name, photo (or default gray avatar) and role per group; responsive for mobile
- [X] T033 [US1] Create `frontend/src/pages/groups/GroupInfoView.jsx` (base): principal group information shown after creation, with success/failure messages in the UI; responsive

**Checkpoint**: Story 1 is independently testable — a user can create a group and see it listed; group persisted with creator as ADMIN.

---

## Phase 4: User Story 2 — Configurar permisos del grupo (Priority: P1)

**Goal**: El Administrador configura las casillas de permisos que determinan qué pueden hacer los Miembros: que no puedan hacer gastos, que no puedan invitar gente, que no puedan editar ajustes, que puedan administrar perfiles simulados, que puedan generar gastos en nombre de otros, y que los pagos requieran validación.

**Independent Test**: Given a group with an ADMIN, when they toggle a permission checkbox, then the setting is saved and applied to members. Given a member, when they attempt a restricted action, then it is denied with an appropriate message.

### Tests for User Story 2

- [X] T034 [P] [US2] Write `GroupSettingsServiceTest.java`: authorization via `GroupPermissionChecker` (ADMIN always; MEMBER only when `members_cannot_edit_settings` = false), the 6 checkboxes changed independently, assumptions for member-denied case, defaults created at group creation (all casillas false per T007)
- [X] T035 [P] [US2] Write `GroupSettingsUpdateIT.java` for `PUT /api/groups/{id}`: 200 with updated state, 403 for a MEMBER when `members_cannot_edit_settings` = true, 404 unknown group
- [X] T036 [P] [US2] Write `GroupPermissionsPanel.test.jsx` in `frontend/src/`: renders 6 checkboxes with current values, submits changes, shows confirmation

### Implementation for User Story 2

- [X] T037 [US2] Implement `GroupService.updateGroupSettings()` in `GroupService.java`: authorization through `GroupPermissionChecker` (ADMIN siempre puede; MEMBER solo si `members_cannot_edit_settings` = false); persists the 6 permission booleans with the defaults from T007 (all false); no new persistence structures
- [X] T038 [US2] Implement `PUT /api/groups/{id}` endpoint in `GroupController.java` (name/description/permissions partial update; 200/403/404; name still only required-if-present and non-empty)
- [X] T039 [US2] Record `PERMISSION_CHANGED` event via `ActivityEventService` in `GroupService.updateGroupSettings()`
- [X] T040 [US2] Create `frontend/src/components/groups/GroupPermissionsPanel.jsx`: 6 casillas ("No podrán hacer gastos", "No podrán invitar gente", "No podrán editar ajustes del grupo", "Pueden administrar perfiles simulados", "Pueden generar gastos en nombre de otras personas", "Los pagos requieren validación"), saved with confirmation message in the UI; responsive

**Checkpoint**: Story 2 is independently testable — an ADMIN can reconfigure the group's permission checkboxes and members are affected.

---

## Phase 5: User Story 3 — Agregar miembros al grupo (Priority: P1)

**Goal**: Quien tenga permiso agrega nuevos miembros al grupo: el ADMIN siempre; un miembro solo si la casilla "No podrán invitar gente" = false (decisión vía `GroupPermissionChecker`). Puede elegir entre amigos registrados del usuario o crear un perfil simulado (nombre + correo). No se puede agregar a quien ya es miembro. Se informa el éxito o el motivo del rechazo y se registra en el historial.

**Independent Test**: Given an ADMIN and a group, when they add a registered friend who is not a member, then the friend becomes MEMBER and `MEMBER_ADDED` is recorded. When they add someone already in the group or outside the friendship relation, the action is rejected with the reason. When they create a simulated profile (name + email), it becomes MEMBER.

### Tests for User Story 3

- [X] T041 [P] [US3] Write `GroupMemberServiceTest.java`: add registered friend (via `FriendshipPort`, returns empty friends for non-friends), duplicate rejection with reason, simulated profile created with `MEMBER` role, and authorization via `GroupPermissionChecker`: MEMBER can add when `members_cannot_invite` = false and is rejected when the casilla is true
- [X] T042 [P] [US3] Write `MemberAdditionIT.java` for `POST /api/groups/{id}/members` and `GET /api/groups/{id}/candidates`
- [X] T043 [P] [US3] Write `AddMemberView.test.jsx` in `frontend/src/`: search limited to friends, simulated form validation, duplicate rejection message

### Implementation for User Story 3

- [X] T044 [US3] Implement friend candidates search in `GroupController.java`: `GET /api/groups/{id}/candidates?q=` returns ONLY the ADMIN's friends (via `FriendshipPort`, filtered server-side; never the whole user base — decision 8; note at Polish task to sync research.md)
- [X] T045 [US3] Implement `GroupMemberService.addRegisteredMember()` in `backend/src/main/java/com/amano/backend/groups/GroupMemberService.java`: authorization through `GroupPermissionChecker` (ADMIN siempre puede; MEMBER solo si `members_cannot_invite` = false); identifier is the friend's email, must be in the caller's friendship relation, not already a member (exists check), role `MEMBER` assigned on incorporation
- [X] T046 [US3] Implement `GroupMemberService.addSimulatedProfile()` in `GroupMemberService.java`: `name` required (per T006 String(100) NOT NULL), `email` valid and required (VARCHAR(191)), UNIQUE (`group_id`,`email`), stored in `simulated_profiles`, becomes MEMBER of the group
- [X] T047 [US3] Implement `POST /api/groups/{id}/members` endpoint in `GroupController.java` with `memberType` = `REGISTERED | SIMULATED`; returns 201 with the member info or 400/403 with the rejection reason
- [X] T048 [US3] Record `MEMBER_ADDED` via `ActivityEventService` in both addition paths (event service already exists from Foundational)
- [X] T049 [US3] Create `frontend/src/pages/groups/AddMemberView.jsx`: friend search limited to the user's friends, form for simulated profile (name + email), success and rejection messages in the UI; responsive

**Checkpoint**: Story 3 is independently testable — members can be added and appear in the history.

---

## Phase 6: User Story 9 — Ver historial de actividad del grupo (Priority: P1)

**Goal**: Se muestra el historial de actividad (todos los miembros): altas y bajas de miembros (gastos y pagos quedan como puntos de enganche, sin implementar); cada entrada indica quién la hizo y cuándo; orden cronológico descendente; las entradas se conservan aunque el elemento relacionado se modifique o elimine.

**Independent Test**: Given a group with recorded member events, when any member views the history, then entries appear in descending chronological order showing actor and timestamp. When a member is added/removed, an entry appears. If the related member is later removed, prior entries persist.

### Tests for User Story 9

- [X] T050 [P] [US9] Write `GroupActivityControllerIT.java` for `GET /api/groups/{id}/activity`: pagination, `eventType` filter, descending order, actor and timestamp present, entries preserved after member removal
- [X] T051 [P] [US9] Write `GroupHistoryView.test.jsx` in `frontend/src/`: renders entries with actor name, humanized date and event description; warning message shown when no entries

### Implementation for User Story 9

- [X] T052 [US9] Implement `GroupActivityService.java` read layer in `backend/src/main/java/com/amano/backend/groups/`: paginated descending queries with optional `eventType` filter (repository from T018 already provides this)
- [X] T053 [US9] Implement `GET /api/groups/{id}/activity` endpoint with `page`/`size` (default 20) and `eventType` parameters returning content, `totalElements`, `totalPages`; 404/401 handled
- [X] T054 [US9] Create `frontend/src/pages/groups/GroupHistoryView.jsx`: descending list "quién la hizo y cuándo", accessible from group info, tablet/mobile responsive; shows hook-point note that expenses/payments entries will be recorded when the expense integration exists

**Checkpoint**: Story 9 is independently testable — the history shows preserved, dated, attributed member events (the write side was completed in Foundational + US3/US4 events).

---

## Phase 7: User Story 4 — Eliminar miembros del grupo (Priority: P2)

**Goal**: El Administrador elimina miembros confirmado previamente. No se permite si el miembro tiene deudas pendientes con otros miembros. Los gastos previos se conservan. Se registra en el historial. Un admin no puede eliminar directamente a otro admin: primero se le quita el rol. Los perfiles simulados no pueden darse de baja por sí mismos; los elimina un admin, o un miembro cuando la casilla "pueden administrar perfiles simulados" está activa, aplicando la misma regla de deudas.

**Independent Test**: Given an ADMIN and a non-admin member without pending debts, `DELETE /api/groups/{id}/members/{userId}` removes them and records `MEMBER_REMOVED`. If the member has pending debts (`DebtPort`), removal is blocked with the reason. If the target is another ADMIN, removal is blocked with "revoke the role first". Simulated profiles are removed via the profile-specific endpoint by an admin or an enabled member, blocked by the debt rule.

### Tests for User Story 4

- [X] T055 [P] [US4] Write `MemberRemovalServiceTest.java` in `backend/src/test/java/com/amano/backend/groups/`: removal of member, blocked when `DebtPort` reports pending debts, blocked when target is ADMIN (role revocation required first), simulated removal by admin and by enabled member, simulated removal blocked by debts
- [X] T056 [P] [US4] Write `MemberRemovalIT.java` for `DELETE /api/groups/{id}/members/{userId}` and `DELETE /api/groups/{id}/simulated-profiles/{profileId}`
- [X] T057 [P] [US4] Write `RemoveMemberDialog.test.jsx` in `frontend/src/`: confirmation step, rejection reasons rendered, debt-related message

### Implementation for User Story 4

- [X] T058 [US4] Implement `GroupMemberService.removeMember()` in `GroupMemberService.java`: ADMIN-only; rejects targets whose role is ADMIN with "primero quitale el rol" (no direct admin-to-admin removal); consults `DebtPort` for pending debts from T019 and blocks the removal with the reason; preserves prior expenses (they belong to users, not the group); records `MEMBER_REMOVED`
- [X] T059 [US4] Implement `GroupMemberService.removeSimulatedProfile()` in `GroupMemberService.java`: authorization through `GroupPermissionChecker` (ADMIN siempre puede; MEMBER solo si `members_can_manage_simulated_profiles` = true); applies the same debt rule via `DebtPort`; physical DELETE of the `simulated_profiles` row (per T006 model); records `MEMBER_REMOVED`; by design simulated profiles expose no self-removal flow
- [X] T060 [US4] Implement `DELETE /api/groups/{id}/members/{userId}` endpoint in `GroupController.java` (verb agreed with the user; contract in `contracts/api-contracts.md` still documents POST — sync in Polish task); 200/400/403/404
- [X] T061 [US4] Implement `DELETE /api/groups/{id}/simulated-profiles/{profileId}` endpoint in `GroupController.java`
- [X] T062 [US4] Create `frontend/src/components/groups/RemoveMemberDialog.jsx`: confirmation dialog before removal, distinct handling for other admin ("revoke the role first"), pending-debts and simulated-profile cases; messages in the UI; responsive

**Checkpoint**: Story 4 is independently testable — removals respect debts, admin protection and permissions, and are recorded in the history.

---

## Phase 8: User Story 5 — Abandonar el grupo (Priority: P2)

**Goal**: Un usuario puede abandonar un grupo con confirmación. No se permite si tiene saldo pendiente con otros miembros ni si es el último admin — se informa el motivo. El grupo deja de figurar activo para él; sus gastos previos y su historial se conservan; el grupo sigue existiendo mientras quede al menos un miembro activo (no hay disolución en esta versión).

**Independent Test**: Given a member without pending balances who is not the last admin, when they confirm leaving, then they leave the group and `MEMBER_REMOVED` is recorded. If they have pending balances (`DebtPort`) or are the last admin, leaving is blocked with the reason and the UI suggests assigning another admin before leaving.

### Tests for User Story 5

- [X] T063 [P] [US5] Write `GroupLeaveServiceTest.java`: success path, blocked by pending balances, blocked by last-admin guard, group persists for remaining members, simulated profiles cannot leave (there is no path for them)
- [X] T064 [P] [US5] Write `LeaveGroupIT.java` for `POST /api/groups/{id}/leave`
- [X] T065 [P] [US5] Write `LeaveGroupPanel.test.jsx` in `frontend/src/`: confirmation, last-admin hint rendered, debts message rendered

### Implementation for User Story 5

- [X] T066 [US5] Implement `GroupService.leaveGroup()` in `GroupService.java`: blocks on pending balances via `DebtPort` (from T019) and blocks when `GroupMemberRepository.countByGroupIdAndRole(ADMIN)` would reach zero (last-admin guard per decision 6); sets `left_at` on the `group_members` row (soft baja per T005 — the row is preserved so prior expenses and activity entries remain traceable) instead of dissolving the group (no dissolution in this version); active-members counting excludes the `left_at` row and keeps counting simulated profiles (T005/T006); records `MEMBER_LEFT` via `ActivityEventService`
- [X] T067 [US5] Implement `POST /api/groups/{id}/leave` endpoint in `GroupController.java` returning message + `groupLeft` boolean (200), 403 with reason
- [X] T068 [US5] Create `frontend/src/components/groups/LeaveGroupPanel.jsx`: leave button with confirmation; when the user is the last admin show guidance "asigná otro administrador antes de salir" (linking to US10 grant UI) and the debts message; responsive

**Checkpoint**: Story 5 is independently testable — leaving respects debts and last-admin rules without dissolving the group.

---

## Phase 9: User Story 10 — Management of admin roles (Priority: P2)

**Goal**: Los Administradores asignan el rol de Administrador a un miembro (sin modificar admins existentes) y se lo quitan a otro admin (que pasa a Miembro y conserva su pertenencia), como operaciones separadas. Solo admins pueden hacerlo, afecta solo a ese grupo, y si la operación falla se conserva la asignación anterior. Todo usuario que se incorpora ya recibe automáticamente el rol Miembro (implementado en US3).

**Independent Test**: Given an ADMIN and a member, `POST /api/groups/{id}/members/{userId}/admin` turns the member into ADMIN; existing admins are untouched. Given an ADMIN and another admin, `DELETE /api/groups/{id}/members/{userId}/admin` turns them into MEMBER keeping membership; the revocation is blocked if they are the last admin. On failure the previous role is preserved.

### Tests for User Story 10

- [X] T069 [P] [US10] Write `AdminRoleServiceTest.java` in `backend/src/test/java/com/amano/backend/groups/`: GRANT member→admin, REVOKE admin→member keeps membership, last-admin revocation blocked, failure preserves prior role, role scope is per group only
- [X] T070 [P] [US10] Write `AdminRoleIT.java` for the two separate endpoints
- [X] T071 [P] [US10] Write `AdminRolePanel.test.jsx` in `frontend/src/`: grant and revoke actions, last-admin disabled with explanation

### Implementation for User Story 10

- [X] T072 [US10] Implement `GroupMemberService.grantAdminRole()` in `GroupMemberService.java`: caller must be ADMIN; target must be an active MEMBER; sets role ADMIN without touching other admins; transactional so a failure preserves the previous assignment
- [X] T073 [US10] Implement `GroupMemberService.revokeAdminRole()` in `GroupMemberService.java`: caller must be ADMIN; target must be ADMIN → becomes MEMBER retaining membership; blocked when target is the last admin (guard per decision 6); transactional failure preserves the prior role
- [X] T074 [US10] Implement the two separate endpoints `POST /api/groups/{id}/members/{userId}/admin` (assign) and `DELETE /api/groups/{id}/members/{userId}/admin` (revoke) in `GroupController.java` (contract currently combines them in `POST /api/groups/{id}/admin` — sync in Polish task)
- [X] T075 [US10] Create `frontend/src/components/groups/AdminRolePanel.jsx`: grant/revoke actions per member, action disabled for the last admin with explanatory message, success/failure messages in the UI; responsive

**Checkpoint**: Story 10 is independently testable — roles can be granted/revoked with last-admin protection and per-group scope.

---

## Phase 10: User Story 6 — Ver listado de miembros activos (Priority: P3)

**Goal**: Se muestra el listado de miembros activos del grupo con nombre y avatar (si lo tiene); el usuario actual se distingue visualmente; se actualiza con altas y bajas; es accesible desde la información del grupo.

**Independent Test**: Given a group, when the member list view is opened, all active members appear with names and avatars (or default), the current user is visually distinguished, and after adding/removing a member (via US3/US4) the list reflects the change.

### Tests for User Story 6

- [X] T076 [P] [US6] Write `MembersListIT.java` for `GET /api/groups/{id}/members`: active registered members + simulated profiles with name, avatar, role
- [X] T077 [P] [US6] Write `MemberListView.test.jsx` in `frontend/src/`: renders all members, distinguishes current user, default avatar when photo is NULL

### Implementation for User Story 6

- [X] T078 [US6] Implement `GET /api/groups/{id}/members` endpoint in `GroupController.java` returning active members (registered + simulated) with `name`, `avatar`, `role` (read from `GroupMemberRepository` + `UserRepository` / `SimulatedProfileRepository`)
- [X] T079 [US6] Create `frontend/src/components/groups/MemberListView.jsx`: name + avatar (gray default avatar when the member has none, same mechanism as user avatars — existente), current user visually distinguished ("Vos"), list refreshes on info changes; accessible from `GroupInfoView`; responsive

**Checkpoint**: Story 6 is independently testable — the member list reflects reality and is refreshed with member changes.

---

## Phase 11: User Story 7 — Ver descripción del grupo (Priority: P3)

**Goal**: Se muestra la descripción del grupo junto con la información principal; si el grupo no tiene descripción, se indica que no tiene.

**Independent Test**: Given a group with a description, the description is displayed next to the group info. Given a group without a description, the message "no tiene descripción" is displayed.

### Tests for User Story 7

- [X] T080 [P] [US7] Write `GroupDescriptionPanel.test.jsx` in `frontend/src/`: renders description when present and the "no tiene descripción" message when NULL

### Implementation for User Story 7

- [X] T081 [US7] Create `frontend/src/components/groups/GroupDescriptionPanel.jsx` shown next to the principal group info (uses existing `GET /api/groups/{id}` from US1; only UI work is needed); responsive

**Checkpoint**: Story 7 is independently testable — pure UI read of the existing description field.

---

## Phase 12: User Story 8 — Cargar/reemplazar foto del grupo (Priority: P3)

**Goal**: Se puede cargar o reemplazar la foto del grupo admitiendo solo JPG/JPEG/PNG; sin foto se muestra un avatar gris por defecto; si la carga falla o el archivo es inválido, se informa y se conserva la foto anterior.

**Independent Test**: Given an ADMIN uploading a valid JPG/PNG ≤2MB, the photo is saved and displayed replacing the previous one. Given an oversize file, 413. Given an invalid file (e.g. GIF/PDF), the system informs the invalid file type, preserves the previous photo and no change is applied.

### Tests for User Story 8

- [X] T082 [P] [US8] Write `GroupPhotoControllerIT.java` for `POST /api/groups/{id}/photo`: 200 with valid JPG/PNG ≤2MB, 400 for invalid type — the validator reuses the existente user-avatar upload validation mechanism — and for oversize 413, 403 non-admin, previous photo preserved on failure
- [X] T083 [P] [US8] Write `GroupPhotoPanel.test.jsx` in `frontend/src/`: upload UI, rejection messages, gray default avatar rendering

### Implementation for User Story 8

- [X] T084 [US8] Implement `GroupService.uploadPhoto()` in `GroupService.java`: ADMIN-only; multipart JPG/JPEG/PNG ≤2MB (413 al superarlo) reusing the existente avatar upload validator (no new validation pattern); stores the new filename/URL in `groups.photo` keeping the old one on failure; informative message on failure
- [X] T085 [US8] Record `PHOTO_CHANGED` via `ActivityEventService` on every successful upload/replace
- [X] T086 [US8] Create `frontend/src/components/groups/GroupPhotoPanel.jsx`: photo upload/replace with file picker, explicit file-type and size errors, and the gray default avatar when the photo is NULL; responsive

**Checkpoint**: Story 8 is independently testable — photos respect type/size rules and failures conserve the previous photo.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [X] T087 Verify consistency across the design artifacts against the decisions in this task list (`specs/010-groups-management/data-model.md`, `contracts/api-contracts.md`, `research.md`, `quickstart.md`): group name without uniqueness/length limit, permission casillas defaults all false, member removal via DELETE, separate admin grant/revoke endpoints, friend search restricted to `FriendshipPort`, terminology "perfil simulado" — the sync already happened during `/speckit.plan` (2026-10-27); this task only re-checks and fixes any drift before implementation
- [X] T088 [P] Run backend coverage: `mvn -B verify` in `backend/` ensuring JaCoCo ≥85% and all `*Test`/`*IT` suites pass
- [X] T089 [P] Run frontend coverage: `npm run test:coverage` in `frontend/` ensuring all `.test.jsx` suites pass
- [X] T090 Run the 12 scenarios in `specs/010-groups-management/quickstart.md` end-to-end and document results
- [X] T091 Review the `com.amano.backend.groups` package for leftovers and consistency with existing patterns (`User`, `Expense` entities and controllers as reference)

**Checkpoint**: All stories validated independently and together.

---

## Fix 2026-11-04 — Amigos reales al agregar miembros (rama `fix/agregar-amigos-al-grupo`)

**Problema**: "Agregar amigo" y "Crear grupo con un amigo" fallaban con amistades reales; el stub `NoOpFriendshipPort` ignoraba `friendship_requests` (research.md §Fix 2026-11-04).

- [X] T092 Crear `groups/port/AcceptedFriendshipPort.java` sobre `FriendshipRequestRepository.findFriendsOf` y eliminar `NoOpFriendshipPort.java` — FR-006/FR-007
- [X] T093 [P] `AcceptedFriendshipPortTest`: correos de amistades aceptadas, comparación sin mayúsculas/espacios, entradas nulas
- [X] T094 [P] `GroupRealFriendshipIT`: sin amistad → 404; pendiente → 404 y sin candidatos; aceptada → candidatos y alta 201; ambas direcciones; crear grupo con amigo; rechazada → 404
- [X] T095 Reemplazar la propiedad `groups.friendship.stub-friend-emails` en los 9 ITs de grupos por amistades `ACCEPTED` reales (`FriendshipFixtures`)
- [X] T096 `./mvnw -B verify` en verde con JaCoCo ≥ 85 %
- [ ] T097 Validación manual: enviar y aceptar una solicitud entre dos cuentas y agregar a la otra cuenta a un grupo (quickstart.md escenario 2/3)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — verification tasks start immediately
- **Foundational (Phase 2)**: depends on Setup (T001 numbering first, then T004–T008 migrations, then T009–T018 entities/repos, with T019–T022 independent infra and checker) — **BLOCKS all user stories**
- **User Stories (Phases 3–12)**: all depend only on Foundational; recommended execution order puts P1 first: US1 → US2 → US3 → US9 → (P2) US4 → US5 → US10 → (P3) US6 → US7 → US8
- Story-level soft dependencies inside that fixed order: US3 benefits from US1 (group exists) in the real flow, but only depends on Foundational; US5's last-admin guidance references US10's UI but US5 is independently testable with backend rules; US6/US7/US8 read existing data
- **Polish (Final)**: depends on all desired user stories

### User Story Dependencies (for parallel work by team)

- **US1 (P1)**: only Foundational
- **US2 (P1)**: only Foundational (touches the group settings created in US1's service, but no story logic is imported)
- **US3 (P1)**: uses `FriendshipPort`/`ActivityEventService` from Foundational
- **US9 (P1)**: write side exists from Foundational; US3/US4 events enrich history but are not required
- **US4 (P2)**: uses `DebtPort` + `ActivityEventService` from Foundational
- **US5 (P2)**: uses `DebtPort`; last-admin guard mirrors US10's guard (same repository helper)
- **US10 (P2)**: pure `GroupMember` role updates on Foundational tables
- **US6/US7/US8 (P3)**: read-only UI over Foundational tables (US6 uses its new `GET /members` endpoint; US8 records `PHOTO_CHANGED`)

### Parallel Opportunities

- Setup verification tasks T002/T003
- All Flyway migrations T004–T008 (separate files, pure SQL)
- All entities T009–T013 and all repositories T014–T018 (different files)
- T019–T021 (ports and event service, each in a different file)
- All test tasks marked [P] in each story (service test, IT and component test are different files)
- Once Foundational is complete, any subset of stories in Phases 3–12 can proceed in parallel if staffed; the fixed phase order below is the recommended solo sequence by priority

---

## Parallel Execution Example: User Story 1

```bash
# All three tests can be written at the same time (different files):
Task: "GroupServiceTest.java  in backend/src/test/java/com/amano/backend/groups/"
Task: "GroupControllerIT.java (POST + GET list) in backend/src/test/java/com/amano/backend/groups/"
Task: "CreateGroupView.test.jsx  in frontend/src/"

# After the services are done, DTOs/Views run in parallel:
Task: "CreateGroupRequest & GroupResponse in backend/src/main/java/com/amano/backend/groups/dto/"
Task: "CreateGroupView.jsx + GroupListView.jsx + GroupInfoView.jsx in frontend/src/pages/groups/"
```

---

## Implementation Strategy

### MVP first (user story 1 only)

1. Complete Phase 1 (verification) + Phase 2 (Foundational) — migrations + entities + ports + event service
2. Complete Phase 3 (US1): creating a group with friends + list
3. **STOP and VALIDATE**: the creation flow works end-to-end
4. Deploy/demo if ready

### Incremental delivery

1. Foundation → US1 (MVP) → US2 → US3 → US9 → US4 → US5 → US10 → US6 → US7 → US8 → Polish
2. Every story adds value without breaking prior ones; each phase has its checkpoint

### Parallel team strategy

1. Team completes Setup + Foundational together (migrations first)
2. Once Foundational lands: one developer per story (or pairs): A=US1, B=US2, C=US3, D=US9, E=US4, F=US5, G=US10, H=US6, I=US7, J=US8
3. Each story integrates independently; the only cross-story touchpoints are atomicity (`DebtPort`) and history events (`ActivityEventService`) — both defined in Foundational

---

## Notes

- [P] tasks: different files, no dependencies on incomplete tasks
- Each story phase ends with a checkpoint for independent validation
- Fixtures: `*Test` = fast (Mockito/JPA), `*IT` = Testcontainers against real PostgreSQL
- Artifacts/UI strings in Spanish; code identifiers and file names in English
- Do not modify existing migrations; do not reuse them beyond reading — feature only adds V6–V11
