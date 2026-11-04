# Data Model: groups-management

**Source Specification**: `specs/010-groups-management/spec.md`
**Related Research**: `research.md`
**Date**: 2026-10-23

## Reused Existing Entities

### User (com.amano.backend.user.User)
- **Table**: `users`
- **Source**: Feature 001 (existing), reused without modification
- **Relationship to groups**: Inverse side of `group_members` join table (`user_id` FK)
- **Reuse reason**: Existing user infrastructure; authentication already implemented
- **Fields used for groups**: `id` (primary key), `email`, `firstName`, `lastName` (for display in group member list)

### Currency (com.amano.backend.user.Currency)
- **Source**: Feature 007/009 (existing expense system)
- **Reuse reason**: Group expenses may need currency handling; existing currency converter used
- **Related**: `CurrencyConversion` converter already exists

### Expense (com.amano.backend.expense.Expense)
- **Table**: `expenses`
- **Source**: Feature 007/009 (existing)
- **Relationship to groups**: Groups track expenses; individual expenses belong to users, not groups directly (per spec FR-006: "Pure persistence mapping: normalization and business rules live in the service layer")
- **Note**: Group-level expense tracking is new; individual expenses remain user-scoped

### UserAvatar (com.amano.backend.user.UserAvatar)
- **Table**: `user_avatar`
- **Source**: Existing avatar mechanism
- **Relationship**: OneToOne with User; group photos will use a separate mechanism but can reuse the upload validator

### UserRepository (com.amano.backend.user.UserRepository)
- **Source**: Feature 008 (existing search)
- **Relationship**: Nuevo puerto `FriendshipPort` (sub-package `groups/port`; implementación `AcceptedFriendshipPort` sobre las amistades `ACCEPTED` de `friendship_requests` desde el fix del 2026-11-04 — antes, stub) usado para encontrar los **amigos** del usuario al agregar miembros — DECISION (2026-10-27): `searchVerified()` ya NO se usa para esto, porque busca en toda la base de usuarios y el spec exige solo amigos (FR-006)
- **Reuse reason**: Leverages existing user search infrastructure for "agregar miembros" functionality

## New Entities (groups-management)

### Group
**Table**: `groups`

| Field | Type | Constraints | Description |
|------|------|-------------|-------------|
| `id` | Long | PK, generated | Unique group identifier |
| `name` | String(255) | NOT NULL (solo requerido y no vacío; sin unicidad ni límite de longitud — decisión 2026-10-27) | Group name |
| `description` | String(200) | NULLable | Group description (optional) |
| `photo` | String(255) | NULLable | Group photo filename or URL (JPG/JPEG/PNG) |
| `created_at` | OffsetDateTime | NOT NULL | Creation timestamp |
| `updated_at` | OffsetDateTime | NOT NULL | Last update timestamp |

**Constraints**:
- Name validation is service-level only: required and non-empty (FR-001); no uniqueness constraint and no length limit beyond VARCHAR(255)
- Photo limited to JPG/JPEG/PNG (validated at upload time, reusing the user-avatar upload validation mechanism)
- Default gray avatar displayed when `photo` is NULL

### GroupMember (join table)
**Table**: `group_members`

| Field | Type | Constraints | Description |
|------|------|-------------|-------------|
| `user_id` | Long | PK + FK → users(id) | Registered user or simulated profile participant |
| `group_id` | Long | PK + FK → groups(id) | Group identifier |
| `role` | VARCHAR(20) + CHECK | NOT NULL ('ADMIN' o 'MEMBER'; sin ENUM nativo) | User's role within this group |
| `joined_at` | OffsetDateTime | NOT NULL | When the user joined the group |
| `left_at` | OffsetDateTime | NULLable | Baja lógica: la fila se conserva (gastos previos e historial mantienen trazabilidad); membresía activa = `left_at` IS NULL |

**Constraints**:
- PK: (`user_id`, `group_id`)
- A user can have only ONE role per group (ADMIN or MEMBER), valores unificados en todo el módulo
- La fila NO se elimina al abandonar/quitar miembro: se setea `left_at` (baja lógica)
- Los perfiles simulados NO tienen filas aquí (se modelan en `simulated_profiles`)
- Supports "rol por grupo": same user can be ADMIN in group A and MEMBER in group B

### SimulatedProfile
**Table**: `simulated_profiles`

| Field | Type | Constraints | Description |
|------|------|-------------|-------------|
| `id` | Long | PK, generated | Unique simulated profile identifier |
| `group_id` | Long | FK → groups(id) | Group this perfil simulado belongs to |
| `name` | String(100) | NOT NULL | Display name of the perfil simulado |
| `email` | String(191) | NOT NULL, unique per group, valid email | Contact email (not a registered account) |
| `created_at` | OffsetDateTime | NOT NULL | When the simulated profile was created |

**Constraints**:
- FK: `group_id` → `groups(id)`
- Unique combination: (`group_id`, `email`) — cannot have two perfiles simulados with same email in same group
- Email validated at creation time
- No associated user account (FK to users is NULL by design)
- **Member modeling**: un perfil simulado es miembro del grupo implícitamente por `group_id` (sin fila en `group_members`); se cuenta como miembro activo junto a las filas `group_members` con `left_at` IS NULL; su eliminación es un DELETE físico de la fila, registrado como `MEMBER_REMOVED`; no puede abandonar por sí mismo (no tiene cuenta)

### GroupPermissions
**Table**: `group_permissions`

| Field | Type | Constraints | Description |
|------|------|-------------|-------------|
| `id` | Long | PK, generated | Unique permissions identifier |
| `group_id` | Long | PK + FK → groups(id), UNIQUE | Group this configuration belongs to |
| `members_cannot_make_expenses` | boolean | NOT NULL, default: false | Members cannot record expenses |
| `members_cannot_invite` | boolean | NOT NULL, default: false | Members cannot invite new people |
| `members_cannot_edit_settings` | boolean | NOT NULL, default: false | Members cannot edit group settings |
| `members_can_manage_simulated_profiles` | boolean | NOT NULL, default: false | Members can administer simulated profiles |
| `members_can_generate_expenses_for_others` | boolean | NOT NULL, default: false | Members can generate expenses on behalf of others |
| `payments_require_validation` | boolean | NOT NULL, default: false | Payments require approval |

**Constraints**:
- FK: `group_id` → `groups(id)`
- One configuration per group (UNIQUE on group_id)
- **Defaults (decisión 2026-10-27)**: `spec.md` no define defaults para las casillas (FR-005 las lista sin valores) → todas en **false** (sin restricciones activas al crear el grupo); se rigen vía `GroupPermissionChecker`

### GroupActivity
**Table**: `group_activity`

| Field | Type | Constraints | Description |
|------|------|-------------|-------------|
| `id` | Long | PK, generated | Unique activity entry identifier |
| `group_id` | Long | FK → groups(id) | Group this activity belongs to |
| `event_type` | VARCHAR(50) + CHECK | NOT NULL | Type of event (see below) |
| `actor_user_id` | Long | FK → users(id), NULLABLE | User who performed the action (system for auto events) |
| `details_json` | Text | NULLable | Additional context in JSON format |
| `created_at` | OffsetDateTime | NOT NULL | When the activity was recorded |

**Event types**:
- `MEMBER_ADDED`: A user (or simulated profile) was added to the group
- `MEMBER_REMOVED`: A user (or simulated profile) was removed from the group
- `MEMBER_LEFT`: A registered user left the group voluntarily (US5)
- `PERMISSION_CHANGED`: Group permission configuration was modified
- `PHOTO_CHANGED`: Group photo was uploaded/replaced
- `EXPENSE_CREATED` *(punto de enganche, no implementado en este módulo)*: An expense was recorded in the group
- `PAYMENT_MADE` *(punto de enganche, no implementado en este módulo)*: A payment was recorded/validated

**Estado de implementación**: en este módulo se registran MemberAdded/MemberRemoved (desde Foundational) y, cuando se completen, MemberLeft (US5), PermissionsChanged (US2) y PhotoChanged (US8); los eventos de gastos y pagos son puntos de enganche para el futuro módulo de gastos por grupo.

**Preservation pattern**: Activity entries are standalone rows with their own PK. Even if the related expense, member, or other element is deleted/modified, the activity row persists because it only references `group_id` and has independent existence.

## New Relationships

### Group ↔ User (many-to-many via GroupMember)
- **One group** has **many members** (registered users + simulated profiles)
- **One user** can belong to **many groups** with different roles
- **Role is per group**: User can be ADMIN in one group and MEMBER in another
- `left_at` IS NULL defines active membership; entries preserve traceability of prior expenses
- **Implementation**: GroupMember join table with `role` VARCHAR(20) CHECK ('ADMIN','MEMBER')

### Group ↔ SimulatedProfile (one-to-many)
- **One group** has **many simulated profiles** (perfiles simulados without accounts)
- **SimulatedProfile** belongs to exactly one group
- **Deletion**: grupos sin disolución en esta versión; simulated profiles are never self-removed (no tienen cuenta). Los elimina un admin, o un miembro cuando la casilla "pueden administrar perfiles simulados" está activa, aplicando la regla de deudas pendientes (vía `DebtPort`, stub)

### Group ↔ GroupPermissions (one-to-one)
- **One group** has **one permissions configuration**
- **GroupPermissions** belongs to exactly one group
- **Default values (decisión 2026-10-27)**: todas las casillas en **false** (el spec no define defaults); cualquier restricción se activa explícitamente vía la UI del Administrador

### Group ↔ GroupActivity (one-to-many)
- **One group** has **many activity entries**
- **GroupActivity** belongs to exactly one group
- **Preservation**: Entries persist even when related elements are modified/deleted

### Expense ↔ Group (indirect)
- Individual expenses belong to a user (userId), not directly to a group
- Group-level expense tracking is handled through the group's permission configuration and activity history
- Members can "generar gastos en nombre de otras personas" per the permission configuration

## Entity-Relationship Diagram (summary)

```
User (1)─(N) GroupMember (N)─(1) Group
     │                       │
     │                       │ role (ADMIN/MEMBER)
     │                       │
     │                       (N) SimulatedProfile (via group_id)
     │
    (search) FriendshipPort → AcceptedFriendshipPort (friendship_requests ACCEPTED, 011–013)

Group (1)─(1) GroupPermissions
     │
     │ (1─0..1) GroupPhoto — bytes in `group_photos` (PK = group_id; 2 MB
     │          AvatarValidator limit w/ 413; `groups.photo` stores only
     │          the serving URL, NULLable → gray default)
     │
     │ (N) GroupActivity
     │
     │ (N) Expense (indirect - via user permissions)

SimulatedProfile (N)─(1) Group
   │
   │ name, email (unique per group)
```

## Database Migration Notes (Flyway)

New migrations required for:
1. `V###__Create_groups_table.sql` — Create Group entity table
2. `V###__Create_group_members_table.sql` — Create GroupMember join table
3. `V###__Create_simulated_profiles_table.sql` — Create SimulatedProfile entity
4. `V###__Create_group_permissions_table.sql` — Create GroupPermissions entity
5. `V###__Create_group_activity_table.sql` — Create GroupActivity entity
6. `V11__create_group_photos_table.sql` — Create `group_photos` (US8, added during implementation): one row per group, `group_id` BIGINT PK + FK → `groups(id)` ON DELETE CASCADE, `content_type` VARCHAR(50) NOT NULL CHECK IN ('image/jpeg','image/png'), `data` BYTEA NOT NULL CHECK (octet_length(data) <= 2097152 — the 2 MB `AvatarValidator` limit, 413 when exceeded), `updated_at` TIMESTAMPTZ NOT NULL DEFAULT NOW(). `groups.photo` keeps only a URL reference (`/api/groups/{id}/photo?v=<fingerprint>`, NULLable = no photo → gray default avatar); the bytes live exclusively in `group_photos`, mirroring the existing `user_avatars` mechanism.

Existing migrations should NOT be modified. New migrations follow the `V<version>__<description>.sql` naming convention used by the project.

## Index Recommendations

- `idx_groups_name` on `groups(name)` — for group lookup by name
- `idx_group_members_group_id` on `group_members(group_id)` — for listing group members
- `idx_group_members_user_id` on `group_members(user_id)` — for listing user's groups
- `idx_simulated_profiles_group_email` on `simulated_profiles(group_id, email)` — for unique constraint and lookups
- `idx_group_permissions_group_id` on `group_permissions(group_id)` — for permission lookups
- `idx_group_activity_group_id` on `group_activity(group_id)` — for activity history queries
- `idx_group_activity_event_type` on `group_activity(event_type)` — for filtering by event type