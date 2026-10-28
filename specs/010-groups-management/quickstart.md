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
2. `POST /api/groups` with body (con el stub activo, el amigo viene de `FriendshipPort` con datos de prueba):
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
1. `GET /api/groups/{groupId}/candidates?q=test` (búsqueda **solo entre amigos del caller**, vía `FriendshipPort`; con el stub activo se usan los amigos de prueba configurados para dev/tests)
2. `POST /api/groups/{groupId}/members` with body:
   ```json
   {"memberType": "REGISTERED", "identifier": "test@example.com"}
   ```
3. Verify response shows member added with `role: "MEMBER"`
4. Verify `GET /api/groups/{groupId}` shows updated `memberCount`

**Expected outcome**: Registered friend added as member; member count increases. Un usuario que NO es amigo del caller no puede agregarse (llega el motivo del rechazo).

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
1. `POST /api/groups/{groupId}/photo` with multipart/form-data containing a JPG or PNG file (≤5MB)
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
| Unauthenticated access | 401 | `error` or authentication header missing |
| Group not found | 404 | `error` or `not found` |
| Not an admin | 403 | `error: "No sos admin de este grupo"` |
| Validation error | 400 | `error` with field details |
| Pending debts blocking operation | 403 | `error: "No tenés saldos pendientes"` or similar |
| Last admin protection | 403 | `error: "Siempre debe quedar un admin"` |

## Running the Validations

These scenarios can be tested using:
- **curl** (JWT es cookie, mecanismo existente): `curl -X POST --cookie "jwt=<token>" -H "Content-Type: application/json" -d '{"name": "Test", "members": [{"memberType": "REGISTERED", "identifier": "amigo@ejemplo.com"}]}' http://localhost:8080/api/groups`
- **Postman**: Import the collection using the endpoints above
- **Frontend**: Use the groups-management UI components after frontend integration

All scenarios assume the backend is running with the groups-management Flyway migrations applied and the frontend is connected to the same API base URL.