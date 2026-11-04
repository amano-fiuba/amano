# Research: groups-management Feature Integration

**Source Specification**: `specs/010-groups-management/spec.md`
**Feature**: groups-management - gestión de grupos de gastos compartidos, sus miembros, roles y permisos
**Date**: 2026-10-23

## Overview

This research documents the integration of the groups-management feature with the existing amano codebase. The feature is entirely new (no existing Group entity, group-related REST endpoints, or group UI components found in the codebase), but it must integrate with the existing user, friendship, and expense infrastructure.

## Existing Codebase Analysis

### Stack & Architecture
- **Backend**: Java 17 + Spring Boot 3, Maven, PostgreSQL with JPA/Hibernate, Flyway migrations
- **Frontend**: React 18 + Vite, React Testing Library, Vitest, JavaScript/JSX
- **Authentication**: JWT via cookies (`JwtCookieAuthenticationFilter`), `SecurityConfig`, `JwtService`, `JwtProperties`
- **API**: REST endpoints under `/api/`, with DTOs and controllers per feature
- **Services**: Layered architecture with service layer containing business rules, repository layer for persistence
- **Testing**: JUnit + Spring Test + Testcontainers (backend), Vitest + React Testing Library (frontend)

### Existing Entities & Models

#### User (`com.amano.backend.user.User`)
- Table: `users`
- Fields: `id`, `firstName`, `lastName`, `email`, `passwordHash`, `cbu`, `termsAcceptedAt`, `createdAt`, `updatedAt`, `emailVerifiedAt`
- Relationships: 
  - OneToOne with `UserAvatar` (optional avatar)
  - ElementCollection `user_currencies` (currency codes)
  - Repository: `UserRepository` extending `JpaRepository<User, Long>`
  - Services: `UserAccountService`, `UserSearchService` (search by name/email, excludes current user)

#### Expense (`com.amano.backend.expense.Expense`)
- Table: `expenses`
- Fields: `id`, `userId`, `amount`, `currency`, `expenseDate`, `category`, `type`, `paymentMethod`, `description`, `idempotencyKey`, `recurrence*` fields
- Belongs to a single creator (userId)
- No participants, splits, or groups (FR-006 per Expense.java comment)
- Managed by `ExpenseService`

#### UserAvatar (`com.amano.backend.user.UserAvatar`)
- Table: user_avatar (inferred)
- OneToOne with User (mappedBy = "user")
- Stores avatar image file reference
- Validated by `AvatarValidator`

#### No existing Group, GroupMember, or Friendship entities found

### Existing API Endpoints (relevant)
- `GET /api/users/search?q=...` — search verified users (excluding current user)
- `GET /api/users/me` — current user profile
- `POST /api/expenses` — create expense
- `GET /api/expenses` — list expenses
- Authentication: `POST /api/auth/login`, `POST /api/auth/register`

### Existing UI Components (relevant)
- `AvatarUploader.jsx.html` — user photo upload (JPG/PNG, `AvatarValidator` content-based limit 2MB)
- `AuthContext.jsx.html` — authentication context
- `AuthPage.jsx.html` — login/register page
- `SiteNav.jsx.html` — site navigation
- Expense-related forms and lists

## Integration Points & Design Decisions

### 1. Group Entity (NEW)
No existing Group entity found. Must be created from scratch.

**Design options:**
- **Option A**: Create `Group` entity with `id`, `name`, `description`, `photo`, `createdAt`, and a join table `group_members` with `user_id`, `group_id`, `role` (ADMIN/MEMBER), `joinedAt`
- **Option B**: Reuse existing join table pattern from other features

**Decision**: Option A — dedicated Group entity with role-per-group design as specified. The spec states "El rol es por grupo: un usuario puede ser admin en uno y miembro en otro," which confirms per-group role storage.

**Fields for Group entity**:
- `id` (Long, generated)
- `name` (String/VARCHAR(255), required and non-empty — **sin unicidad ni límite 2-50**: decisión 2026-10-27, el spec solo exige obligatorio/no vacío)
- `description` (String, optional, 200 chars)
- `photo` (String, URL or file reference, JPG/JPEG/PNG only)
- `createdAt` (OffsetDateTime)
- `lastModifiedAt` (OffsetDateTime)

**Group-Membership join table** (`group_members`):
- `user_id` (Long, FK to users)
- `group_id` (Long, FK to groups)
- `role` (VARCHAR + CHECK: ADMIN, MEMBER — no native ENUM)
- `joinedAt` (OffsetDateTime)
- `leftAt` (OffsetDateTime, nullable — baja lógica: la fila se conserva para trazabilidad de gastos previos e historial; membresía activa = `left_at` IS NULL)

### 2. Simulated Profile (Perfil Simulado)
The spec defines "perfil simulado" as a person within a group without a registered account (has name and email, no full user account). **Terminología acordada: "perfil simulado" (no "invitado").**

**Design options:**
- **Option A**: Create `SimulatedProfile` entity linked to Group, with `name`, `email`, `createdAt`
- **Option B**: Use a flag on the group-members table (e.g., `is_simulated = true`) with name/email columns

**Decision**: Option A — separate `SimulatedProfile` entity. This cleanly separates simulated profiles from registered users and matches the spec's distinction between "usuarios registrados" and "perfiles simulados." The spec says simulated profiles have "nombre y mail, sin registrarse." Simulated profiles do **not** get a `group_members` row (no user account); they are members of the group implicitly via `group_id`, counted as active members alongside `group_members` rows with `left_at` IS NULL, and their removal is a physical DELETE of the row recorded as `MEMBER_REMOVED`.

**Fields for SimulatedProfile**:
- `id` (Long, generated)
- `group_id` (Long, FK to groups)
- `name` (String, required)
- `email` (String, required, validated format)
- `createdAt` (OffsetDateTime)

### 3. Group Photo Upload Integration
The spec says: "Cargar/reemplazar la foto del grupo: solo JPG, JPEG o PNG; sin foto se muestra un avatar gris por defecto."

**Integration with existing mechanism**:
- The frontend already has `AvatarUploader.jsx.html` for user avatars
- Backend has `UserAvatar` entity and `AvatarValidator`
- **Decision**: Reuse the image validation mechanism (file type, size) but create a separate upload endpoint/path for group photos. The group photo is distinct from the user avatar.

**Backend changes needed**:
- New `GroupPhoto` entity or add `photo` field to Group entity
- New `POST /api/groups/{id}/photo` endpoint
- File type validation (JPG/JPEG/PNG only)
- Size limit (2 MB, same content-based `AvatarValidator` limit as user avatars; oversize → 413 — decision finalized in T087/review, replacing the originally sketched 5MB default)

**Frontend changes needed**:
- Group photo upload component (similar to AvatarUploader but for groups)
- Preview of group photo in group view
- Default gray avatar when no photo

### 4. Permission Configuration
The spec defines these permission checkboxes for members:
- Miembros no pueden hacer gastos
- Miembros no pueden invitar gente
- Miembros no pueden editar ajustes del grupo
- Miembros pueden administrar perfiles simulados
- Miembros pueden generar gastos en nombre de otras personas
- Pagos requieren validación

**Design decision**: Store group-level permission configuration as a separate entity or as a JSON/column on the Group entity.

**Decision**: Separate `GroupPermissions` entity with boolean fields for each permission. This allows future per-member permission overrides if needed, and keeps the group configuration clean.

**Fields for GroupPermissions**:
- `id` (Long, generated)
- `group_id` (Long, FK to groups, unique)
- `members_cannot_make_expenses` (boolean, default: false — `spec.md` no define defaults; decisión 2026-10-27: todas las casillas en false)
- `members_cannot_invite` (boolean, default: false)
- `members_cannot_edit_settings` (boolean, default: false)
- `members_can_manage_simulated_profiles` (boolean, default: false)
- `members_can_generate_expenses_for_others` (boolean, default: false)
- `payments_require_validation` (boolean, default: false)

### 5. Activity History Design
The spec says: "Ver el historial de actividad del grupo (todos los miembros): altas y bajas de miembros, gastos y pagos; cada entrada indica quién la hizo y cuándo; orden cronológico descendente; se conserva aunque el elemento relacionado luego se modifique o elimine."

**Design decision**: Separate `GroupActivity` entity with a self-referencing or polymorphic design that records events but doesn't dependency-link to the modified deleted element in a way that breaks preservation.

**Fields for GroupActivity**:
- `id` (Long, generated)
- `group_id` (Long, FK to groups)
- `event_type` (VARCHAR + CHECK: MEMBER_ADDED, MEMBER_REMOVED, MEMBER_LEFT, PERMISSION_CHANGED, PHOTO_CHANGED, EXPENSE_CREATED, PAYMENT_MADE — gastos y pagos quedan como puntos de enganche **sin implementarse en este módulo**)
- `actor_user_id` (Long, FK to users who performed the action, nullable for system actions)
- `details_json` (JSON or text for additional context)
- `createdAt` (OffsetDateTime)

**Preservation pattern**: The activity entries are standalone rows; even if the related expense or member is deleted, the activity row persists because it has its own primary key and the group_id foreign key alone preserves the reference context.

### 5. Role Assignment (Admin/Member per group)
The spec says: "A todo usuario que se incorpora se le asigna automáticamente el rol Miembro." and "Solo admins pueden [assign/remove admin role], afecta únicamente a ese grupo."

**Design decision**: The role is stored in the `group_members` join table as an `role` ENUM field (ADMIN or MEMBER). When a user joins a group, they get MEMBER role automatically. Admins can promote other members to ADMIN.

**Key constraint**: "El rol es por grupo: un usuario puede ser admin en uno y miembro en otro." This means the same user can have different roles in different groups, stored in the join table.

### 6. Friend/Lookup Integration
The spec says: "elegir entre los amigos del usuario (debe ser usuario registrado) o crear un perfil simulado con nombre y mail."

**Integration with existing search**:
- Backend has `UserRepository.searchVerified()` which searches by firstName, lastName, or email (excluding current user) — **pero busca en TODA la base de usuarios, no solo amigos**
- No existe un módulo/entidad de amistades en el repo (verificado en Fase 0)
- **Decision (corregida 2026-10-27, reemplaza la decisión previa de `searchVerified()`)**: FR-006 exige elegir entre **amigos registrados del usuario**; buscar en toda la base es más amplio de lo que permite el spec. Se define el puerto `FriendshipPort.java` (sub-package `com.amano.backend.groups.port`) para consultar la relación de amistad del usuario, con stub `NoOpFriendshipPort.java` (lista vacía por default, configurable vía properties/fixtures para dev/tests) hasta que exista el módulo de amistades. Toda búsqueda de candidatos para agregar miembros pasa por este puerto (nunca por la base completa).

**Frontend integration**: When "agregar miembros" is triggered, show a searchable list of the user's friends (registered users) plus an option to "crear perfil simulado."

### 7. Data Migration & Flyway
The existing Flyway migrations manage database schema. New migrations will be needed for:
- `V###__Create_Group_entity.sql`
- `V###__Create_Group_Members_join_table.sql`
- `V###__Create_Simulated_Profile_entity.sql`
- `V###__Create_Group_Permissions_entity.sql`
- `V###__Create_Group_Activity_entity.sql`

**Check existing migration naming**: Flyway migrations are in the standard Spring Boot location and follow `V<version>__<description>.sql` naming convention.

## NEEDS CLARIFICATION & Resolved Questions

The following questions were resolved during the clarification phase (3 questions):

1. **Group dissolution**: No explicit dissolution operation; groups persist with ≥1 active member
2. **Last admin scenario**: Operation blocked if it would leave group without admin; new admin must be explicitly promoted
3. **Simulated profile permissions**: Admin removes simulated profiles; same debt rules as registered users; admin cannot directly remove another admin's role

## Research Outcomes Summary

| Research Area | Decision | Rationale |
|---|---|---|
| Group entity design | NEW entity with join table | No existing group infrastructure; spec clearly defines group as first-class entity |
| Simulated profile design | Separate SimulatedProfile entity | Clean separation from registered users; matches spec's "sin registrarse" definition |
| Group photo upload | Separate mechanism from user avatar | Image validation reuse, but distinct entity/endpoint per spec scope |
| Permission configuration | GroupPermissions entity | Clean separation from member roles; extensible for future needs |
| Activity history design | GroupActivity entity with standalone rows | Preserves entries even when related elements are modified/deleted, as spec requires |
| Role assignment | ENUM in group-members join table | Supports "rol por grupo" (different roles in different groups) as spec requires |
| Friend lookup | `FriendshipPort` (port). ~~Stub configurable hasta que exista el módulo de amistades~~ → desde 2026-11-04, `AcceptedFriendshipPort` sobre las amistades `ACCEPTED` de 011–013 (ver "Fix 2026-11-04") | FR-006 exige "amigos del usuario"; `searchVerified()` busca en toda la base y no distingue amistades |

## Fix 2026-11-04 — `FriendshipPort` sobre las amistades reales

- **Problema**: con 011–013 ya mergeadas, "Agregar miembros" y "Crear grupo" con un amigo registrado fallaban siempre (404 "no está entre tus amigos") porque `NoOpFriendshipPort` devolvía una lista fija de correos de prueba (`groups.friendship.stub-friend-emails`, por defecto `test@example.com`) e ignoraba `friendship_requests`. Los perfiles simulados no consultan amistades, por eso sí funcionaban.
- **Decisión**: se elimina `NoOpFriendshipPort` y se agrega `groups/port/AcceptedFriendshipPort`, que implementa `friendEmailsOf` con `FriendshipRequestRepository.findFriendsOf(userId)` (la consulta de "Mis amigos", 013 research.md D3) e `isFriend` comparando correos sin espacios ni mayúsculas.
- **Rationale**: una sola definición de amistad en todo el sistema (solicitud `ACCEPTED` en cualquier dirección). El puerto sigue aislando a `groups` del módulo de amistades (principio I); no cambian contratos ni esquema.
- **Alternativas consideradas**: mantener el stub activable por propiedad (deja una trampa en producción: el bug fue justamente ese); consulta nueva en el repositorio solo con correos (duplica la de 013 sin beneficio con el volumen del proyecto).
- **Pruebas**: `AcceptedFriendshipPortTest` (unitario) y `GroupRealFriendshipIT` (flujo real 011 → 012 → agregar/crear grupo; pendiente y rechazada no cuentan; ambas direcciones). Los 9 ITs de grupos que usaban la propiedad del stub ahora crean amistades `ACCEPTED` reales con el helper de pruebas `FriendshipFixtures`.
