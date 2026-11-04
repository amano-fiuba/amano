# Quickstart: groups-management Validation

**Source Specification**: `specs/010-groups-management/spec.md`
**Data Model**: `data-model.md`
**API Contracts**: `contracts/api-contracts.md`
**Date**: 2026-10-23

This quickstart guide provides runnable validation scenarios to prove the groups-management feature works end-to-end. Each scenario can be executed after the backend and frontend are running.

## Prerequisites

- Backend server running (Spring Boot, port 8080 by default)
- Frontend development server running (Vite, typically port 5173)
- User authenticated (have a valid session/token)
- Database migrated with groups-management tables (Flyway migrations applied)

## Validation Scenarios

### Scenario 1: Create a Group

**Goal**: Verify that a registered user can create a new group with a name and at least one additional member (amigo registrado o perfil simulado).

**Steps**:
1. Authenticate as a registered user (use existing login or `POST /api/auth/login`)
2. `POST /api/groups` with body (el amigo tiene que tener una amistad aceptada con quien crea el grupo: enviala desde "Buscar usuarios" y aceptala desde "Amigos"; ver 011/012):
   ```json
   {"name": "Amigos para viajar", "member": {"memberType": "REGISTERED", "identifier": "test@example.com"}}
   ```
3. Verify response has `id`, `name: "Amigos para viajar"`, `creatorRole: "ADMIN"`, `membersCount: 2`
4. Verify the authenticated user appears as a member with ADMIN role and the additional member as MEMBER
5. Verify `GET /api/groups/{id}` returns the created group

**Expected outcome**: Group created successfully with the seed member; user is automatically Admin; group appears in user's group list. Sin el miembro adicional la creación falla con 400 (FR-001).

### Scenario 2: Add Registered Members to Group

**Goal**: Verify an Admin can add registered friends to the group.

**Prerequisite**: Group created in Scenario 1 (que ya trae al menos un miembro semilla de la creación) — aquí se agregan miembros adicionales

**Steps**:
1. `GET /api/groups/{groupId}/candidates?q=test` (búsqueda **solo entre amigos del caller**, vía `FriendshipPort`; solo aparecen las cuentas con amistad aceptada)
2. `POST /api/groups/{groupId}/members` with body:
   ```json
   {"memberType": "REGISTERED", "identifier": "test@example.com"}
   ```
3. Verify response shows member added with `role: "MEMBER"`
4. Verify `GET /api/groups/{groupId}` shows updated `memberCount`

**Expected outcome**: Registered friend added as member; member count increases. Un usuario que NO es amigo del caller no puede agregarse: se responde con un **404 neutral** ("La persona que querés agregar no está entre tus amigos") que informa el motivo del rechazo sin confirmar si el usuario existe.

### Scenario 3: Add Simulated Profile (Perfil Simulado)

**Goal**: Verify an Admin can add a simulated profile (perfil simulado) to the group.

**Prerequisite**: Group created in Scenario 1 (que ya trae un miembro semilla; aquí se agrega un perfil simulado adicional)

**Steps**:
1. `POST /api/groups/{groupId}/members` with body:
   ```json
   {"memberType": "SIMULATED", "identifier": "simulado@ejemplo.com", "name": "Juan Simulado"}
   ```
2. Verify response shows simulated profile added with `type: "SIMULATED"` and `name: "Juan Simulado"`
3. Verify the perfil simulado appears in the group member list

**Expected outcome**: Simulated profile added to group; perfil simulado appears in member list with name.

### Scenario 4: Configure Group Permissions

**Goal**: Verify an Admin can configure member permissions.

**Prerequisite**: Group created in Scenario 1

**Steps**:
1. `PUT /api/groups/{groupId}` with body:
   ```json
   {
     "permissions": {
       "membersCannotMakeExpenses": false,
       "membersCannotInvite": true,
       "membersCannotEditSettings": true,
       "membersCanManageSimulatedProfiles": true,
       "membersCanGenerateExpensesForOthers": false,
       "paymentsRequireValidation": true
     }
   }
   ```
2. Verify response shows updated permission configuration

**Expected outcome**: Permissions updated; group configuration reflects the new settings.

### Scenario 5: Assign Admin Role

**Goal**: Verify an Admin can assign the Admin role to another member.

**Prerequisite**: Group with at least 2 members (created in Scenario 1, add another user in Scenario 2)

**Steps**:
1. `POST /api/groups/{groupId}/members/{userIdOfSecondMember}/admin` (asignar rol — operación separada de la quita)
2. Verify response shows `newRole: "ADMIN"` for the target user
3. Verify the target user now has admin capabilities in the group

**Expected outcome**: Target user promoted to Admin; can now perform admin actions.

### Scenario 6: Member Cannot Make Expenses (Permission Restriction)

**Goal**: Verify that when "Miembros no pueden hacer gastos" is enabled, members cannot record expenses.

**Prerequisite**: Group with permissions set to restrict members (from Scenario 4)

**Steps**:
1. Log in as a member (not admin) of the group
2. Attempt a group expense recording action as that member (ej. desde la UI del grupo)
3. Verify the action is rejected with an appropriate message

**Expected outcome**: Member cannot record expenses; appropriate error message shown.

### Scenario 7: Abandon Group (No Pending Debts)

**Goal**: Verify a user can leave a group when they have no pending balances.

**Prerequisite**: Group created; user has no debts with other members

**Steps**:
1. `POST /api/groups/{groupId}/leave`
2. Verify response shows success
3. Verify user no longer appears in group member list
4. Verify group still exists and other members remain

**Expected outcome**: User leaves group successfully; group remains active for other members.

### Scenario 8: abandon Group Blocked (Pending Debts)

**Goal**: Verify a user cannot leave a group when they have pending debts.

**Prerequisite**: Group created; user has pending debts with other members

**Steps**:
1. `POST /api/groups/{groupId}/leave`
2. Verify response shows error: "No se permite abandonar si tenés saldos pendientes con otros miembros"
3. Verify user remains in group member list

**Expected outcome**: User informed of pending debt reason; cannot leave until debts are resolved.

### Scenario 9: Remove Member with Pending Debts

**Goal**: Verify an Admin cannot remove a member with pending debts.

**Prerequisite**: Group with members who have pending debts

**Steps**:
1. `DELETE /api/groups/{groupId}/members/{userId}` (as Admin)
2. Verify response shows error or prevents removal

**Expected outcome**: Admin informed cannot remove member with debts; member remains in group.

### Scenario 10: Last Admin Protection

**Goal**: Verify the system blocks operations that would leave the group without an Admin.

**Prerequisite**: Group with only 1 Admin and no other members

**Steps**:
1. Attempt `DELETE /api/groups/{groupId}/members/{lastAdminId}/admin` to revoke the last Admin's role
2. Or attempt to leave the group as the last Admin

**Expected outcome**: Operation blocked; user informed that at least one admin must remain and that puede asignar otro admin antes de salir/quitar el rol.

### Scenario 11: Group Photo Upload

**Goal**: Verify an Admin can upload a group photo (JPG/PNG).

**Prerequisite**: Group created

**Steps**:
1. `POST /api/groups/{groupId}/photo` with multipart/form-data containing a JPG or PNG file (≤2MB; oversize → 413)
2. Verify response shows `photo` field with filename/URL
3. Verify the group photo appears in `GET /api/groups/{groupId}`
4. Test with invalid file type (GIF) — should return 400 error

**Expected outcome**: Photo uploaded and displayed; invalid file types rejected.

### Scenario 12: Group Activity History

**Goal**: Verify activity entries are preserved even when related elements are modified.

**Prerequisite**: Group with activity entries (from previous scenarios: member additions, photo changes, etc.)

**Steps**:
1. `GET /api/groups/{groupId}/activity` — verify history is accessible
2. Modify or delete a related element (e.g., remove a member, delete an expense entry referenced in activity)
3. `GET /api/groups/{groupId}/activity` again — verify previous entries are still present

**Expected outcome**: Activity history preserved; entries remain even after related elements are modified/deleted.

## Error Handling Quick Reference

| Scenario | Expected HTTP Status | Expected Response Key |
|---|---|---|
| Unauthenticated access | 401 | `detail` (ProblemDetail authentication) |
| Group not found | 404 | `detail: "No existe el grupo solicitado o no sos miembro"` (neutral) |
| Not an admin | 403 | `detail: "No sos admin de este grupo"` |
| Validation error | 400 | `fields` with field details |
| Pending debts blocking a baja (US4) | 403 | `detail: "No se permite la baja: la persona tiene deudas pendientes con otros miembros del grupo"` |
| Pending debts blocking leaving (US5) | 403 | `detail: "No podés abandonar el grupo: tenés saldos pendientes con otros miembros"` |
| Last admin protection (US5/US10) | 403 | `detail: "Sos el último administrador: asigná otro administrador antes de salir"` |
| Removing another ADMIN (US4) | 403 | `detail: "No podés eliminar directamente a otro administrador: primero quitale el rol"` |

## Running the Validations

These scenarios can be tested using:
- **curl** (La sesión viva es la cookie HttpOnly `amano_auth`, mecanismo existente — first login to obtain it):
  `curl -c cookies.txt -X POST -H "Content-Type: application/json" -d '{"email":"...","password":"..."}' http://localhost:8081/api/auth/login`
  `curl -X POST --cookie cookies.txt -H "Content-Type: application/json" -d '{"name": "Test", "member": {"memberType": "REGISTERED", "identifier": "amigo@ejemplo.com"}}' http://localhost:8081/api/groups`
- **Postman**: Import the collection using the endpoints above
- **Frontend**: Use the groups-management UI components after frontend integration

All scenarios assume the backend is running with the groups-management Flyway migrations applied and the frontend is connected to the same API base URL.

## Validation Results (T090, executed 2026-10-28 against the dockerized stack: backend image built from this branch, Flyway schema v11, API via `http://localhost:8081`)

Sesiones: cookies `amano_auth` HttpOnly (`ana/beto/carla/dana@amano.dev`, `Passw0rd!`). Grupo de prueba creado en S1: **id=2 "Amigos para viajar"**. Seed real: group id=1 "Departamento compartido 2026" con ana/beto ADMIN y carla dada de baja.

| # | Escenario | Resultado | Cómo se validó |
|---|-----------|-----------|----------------|
| 1 | Create a group | ✅ PASS | `POST /api/groups` (ana): 201 con `id:2`, `creatorRole:ADMIN`, `membersCount:2` (ana + dana del stub). |
| 2 | Add a friend (Registered) | ✅ PASS | `GET candidates?q=car` devolvió solo amigos del stub (carla); `POST members` agregó a beto (201/ADDED); outsider no-amigo (`ifabregat@fi.uba.ar`) → **404 neutral** `"La persona que querés agregar no está entre tus amigos"` (la respuesta no confirma si el usuario existe; ver Motivos de rechazo en spec.md — esta redacción reemplaza la anotación previa que señalaba ambigüedad). |
| 3 | Add a simulated profile | ✅ PASS | `POST members` `memberType:SIMULATED` (sin cuenta): 201, aparece en la lista sin fila activa en `group_members` (perfil creado con nombre "Juan Simulado"). |
| 4 | Casillas (permissions) | ✅ PASS | `PUT /api/groups/2` con todas las casillas: persistidas y visibles en `GET /api/groups/2` (`memberCount` recalculado, `membersCannotInvite` on). Casilla `members_cannot_invite` **enforced**: MEMBER (carla) → 403 `"No podés invitar gente a este grupo"`; ADMIN ignora la casilla. Casilla `members_cannot_make_expenses`: **sin endpoint de gastos todavía** — enforced sólo en la tabla + hook-point del módulo futuro (validado vía unit tests T063). |
| 5 | Assign admin role | ✅ PASS | `POST /api/groups/2/members/2/admin` → `{"newRole":"ADMIN","previousRole":"MEMBER","success":true}`. |
| 6 | Permissions restrict members | ✅ PASS (parcial) | Ver S4: la casilla de invitación bloquea al MEMBER end-to-end. La casilla de gastos queda **sin verificar end-to-end** (módulo de gastos no existe). |
| 7 | Leave group (member) | ✅ PASS | `POST/DELETE leave` de dana → `{groupLeft:true}`, `left_at` set en DB, el grupo persiste (`memberCount` baja de 5 a 4) y ana/beto siguen. **Nota**: con el stub `NoOpDebtPort` (deudas siempre 0) el caso "no puede salir por deudas" no dispara end-to-end; cubierto por unit tests T063/T055 (403 `"No podés abandonar el grupo: tenés saldos pendientes con otros miembros"`). |
| 8 | Remove member | ✅ PASS | `DELETE /api/groups/2/members/3` (beto ADMIN): `{removed:true}`, `left_at` de carla seteado, actividad `MEMBER_REMOVED` registrada. |
| 9 | Pending debts block removal | ⚠️ Stub only | La ruta de deudas > 0 no es alcanzable end-to-end con `NoOpDebtPort` (siempre 0). Cubierto con unit tests mockeando `DebtPort` (403 `"No se permite la baja: la persona tiene deudas pendientes con otros miembros del grupo"`). |
| 10 | Last admin protection | ✅ PASS | Con dos ADMIN: revocar a ana OK (quedó MEMBER). Intentos siguientes: revocación por un no-admin → 403 `"No sos admin de este grupo"`; auto-revocación del último ADMIN → 403 `"Sos el último administrador: asigná otro administrador antes de salir"` (`code:LAST_ADMIN`). |
| 11 | Group photo | ✅ PASS | `POST /api/groups/2/photo` PNG (magic bytes reales): 200 `{photo:"/api/groups/2/photo?v=...",updatedAt}`, `GET photo` → 200 `image/png`. GIF (magic `GIF`) → 400 `"El archivo debe ser una imagen JPG o PNG válida"` con `fields`. MEMBER activo → 403 `"Solo un administrador puede cambiar la foto del grupo"`; ex-miembro → 404 neutral. |
| 12 | Activity history persistence | ✅ PASS | `GET /api/groups/2/activity`: 7 entradas en orden descendente (`MEMBER_REMOVED`, `PHOTO_CHANGED`, `MEMBER_LEFT`, `PERMISSION_CHANGED`, 3× `MEMBER_ADDED`); tras remover a carla las entradas previas se preservan. `eventType` filter + paginación via unit/IT. |

**Sin verificar end-to-end por stubs**: escenarios 9 y parte del 6 (deudas, vía `NoOpDebtPort`), cubiertos por tests unitarios que mockean el port. El flujo real de amistades ya no es un stub desde el fix del 2026-11-04 (`AcceptedFriendshipPort`): lo cubre `GroupRealFriendshipIT` de punta a punta.