# API Contracts: groups-management

**Source Specification**: `specs/010-groups-management/spec.md`
**Research**: `research.md`
**Date**: 2026-10-23

This module defines the API contracts for the groups-management feature. All endpoints are RESTful under the `/api/groups` base path, consistent with the existing amano API pattern.

## Base URL
`GET/POST/PUT/DELETE /api/groups[/{id}]`

## Contract: Create Group

**Method**: `POST`
**Endpoint**: `/api/groups`

**Request**:
```json
{
  "name": "string (required, non-empty — sin unicidad ni límite de longitud)",
  "description": "string (optional, 0-200 chars)",
  "member": {
    "memberType": "REGISTERED | SIMULATED",
    "identifier": "string (email del amigo registrado o del perfil simulado)",
    "name": "string (required if memberType=SIMULATED)"
  }
}
```

**Response (201 Created)**:
```json
{
  "id": "long",
  "name": "string",
  "description": "string or null",
  "photo": "string or null",
  "createdAt": "string (ISO-8601)",
  "updatedAt": "string (ISO-8601)",
  "creator": {
    "id": "long",
    "firstName": "string",
    "lastName": "string",
    "email": "string"
  },
  "membersCount": "int",
  "creatorRole": "ADMIN"
}
```

**Errors**:
- 400: Invalid name (missing or empty — el spec FR-001 solo exige obligatorio/no vacío)
- 400: Missing additional member (al menos un amigo registrado — vía `FriendshipPort` — o un perfil simulado)
- 404: Selected friend no existe en la relación de amistad del caller (404 neutral: la respuesta no confirma si el usuario existe)
- 401: Unauthenticated

## Contract: Get Group

**Method**: `GET`
**Endpoint**: `/api/groups/{id}`

**Request**: No query parameters required

**Response (200 OK)**:
```json
{
  "id": "long",
  "name": "string",
  "description": "string or null",
  "photo": "string or null",
  "createdAt": "string (ISO-8601)",
  "updatedAt": "string (ISO-8601)",
  "isActiveForUser": "boolean",
  "memberCount": "int",
  "currentUserRole": "ADMIN | MEMBER",
  "permissions": {
    "membersCannotMakeExpenses": "boolean",
    "membersCannotInvite": "boolean",
    "membersCannotEditSettings": "boolean",
    "membersCanManageSimulatedProfiles": "boolean",
    "membersCanGenerateExpensesForOthers": "boolean",
    "paymentsRequireValidation": "boolean"
  },
  "lastActivity": {
    "eventType": "STRING",
    "actor": {
      "id": "long",
      "firstName": "string",
      "lastName": "string"
    } or null,
    "createdAt": "string (ISO-8601)"
  },
  "expensesCount": "int"
}
```

**Errors**:
- 404: Group not found
- 401: Unauthenticated

## Contract: Update Group (authz vía GroupPermissionChecker: ADMIN siempre; MEMBER solo si members_cannot_edit_settings = false)

**Method**: `PUT`
**Endpoint**: `/api/groups/{id}`

**Request**:
```json
{
  "name": "string (optional)",
  "description": "string (optional)",
  "permissions": {
    "membersCannotMakeExpenses": "boolean (optional)",
    "membersCannotInvite": "boolean (optional)",
    "membersCannotEditSettings": "boolean (optional)",
    "membersCanManageSimulatedProfiles": "boolean (optional)",
    "membersCanGenerateExpensesForOthers": "boolean (optional)",
    "paymentsRequireValidation": "boolean (optional)"
  }
}
```

**Response (200 OK)**: Updated group JSON (same shape as Get Group response)

**Errors**:
- 403: Not an admin of the group
- 400: Invalid name or permission values
- 404: Group not found
- 401: Unauthenticated

## Contract: Leave Group

**Method**: `POST`
**Endpoint**: `/api/groups/{id}/leave`

**Request**: No body required

**Response (200 OK)**:
```json
{
  "message": "string",
  "groupLeft": "boolean"
}
```

**Errors**:
- 403: User has pending debts with group members (cannot leave)
- 404: Group not found
- 401: Unauthenticated

## Contract: Add Members

**Method**: `POST`
**Endpoint**: `/api/groups/{id}/members`

**Request**:
```json
{
  "memberType": "REGISTERED | SIMULATED",
  "identifier": "string (email for registered, email for simulated)",
  "name": "string (required if memberType=SIMULATED)"
}
```

**Response (201 Created)**:
```json
{
  "member": {
    "id": "long or null (for simulated)",
    "type": "REGISTERED | SIMULATED",
    "name": "string",
    "email": "string",
    "role": "MEMBER"
  },
  "groupMemberRole": "ADMIN | MEMBER",
  "action": "ADDED",
  "notified": "boolean"
}
```

**Errors**:
- 400: Invalid identifier, user already member
- 403: Caller no tiene permiso (ADMIN siempre; MEMBER solo si la casilla "no pueden invitar gente" = false)
- 404: REGISTERED identifier is not a friend of the caller (via `FriendshipPort`) — 404 neutral: se responde "no está entre tus amigos" sin confirmar si el usuario existe
- 400: simulated profile: email inválido o ya existe otro perfil simulado con ese email en el grupo (UNIQUE `group_id`,`email`)
- 404: Group not found
- 401: Unauthenticated

**Notes**: los miembros se agregan SOLO entre los amigos del usuario (via `FriendshipPort`; o perfiles simulados con nombre+email); rol asignado automáticamente: MEMBER.

## Contract: Remove Member

**Method**: `DELETE`
**Endpoint**: `/api/groups/{id}/members/{userId}`

**Request**: No body required

**Response (200 OK)**:
```json
{
  "removed": "boolean",
  "wasAdmin": "boolean",
  "groupMemberWasAdmin": "boolean",
  "message": "string"
}
```

**Errors**:
- 403: Cannot remove last admin; user has pending debts; target is another ADMIN (el rol debe revocarse primero — no hay baja directa admin→admin)
- 404: Group not found or user not a member
- 401: Unauthenticated

**Removed member state**: baja lógica (`left_at` en `group_members`), la fila se conserva (gastos previos e historial mantienen trazabilidad).

## Contract: Remove Simulated Profile

**Method**: `DELETE`
**Endpoint**: `/api/groups/{id}/simulated-profiles/{profileId}`

**Request**: No body required

**Response (200 OK)**:
```json
{
  "removed": "boolean",
  "message": "string"
}
```

**Errors**:
- 403: Caller is not an ADMIN and the casilla "miembros pueden administrar perfiles simulados" no está activa; pending debts
- 404: Group or simulated profile not found
- 401: Unauthenticated

**Nota**: los perfiles simulados no pueden abandonar por sí mismos (sin cuenta); su baja es un DELETE físico de la fila en `simulated_profiles`, registrado como `MEMBER_REMOVED`.

## Contract: Assign Admin Role

**Method**: `POST`
**Endpoint**: `/api/groups/{id}/members/{userId}/admin`

**Request**: No body required (el target se define en la URL)

**Response (200 OK)**:
```json
{
  "targetUserId": "long",
  "newRole": "ADMIN",
  "previousRole": "MEMBER",
  "success": "boolean"
}
```

**Errors**:
- 403: Caller is not an ADMIN of the group; target is not an active MEMBER
- 404: Group not found or target not a member
- 401: Unauthenticated

**Notes**: la operación afecta únicamente a ese grupo; no modifica a los administradores existentes; si falla, se conserva la asignación anterior.

## Contract: Revoke Admin Role

**Method**: `DELETE`
**Endpoint**: `/api/groups/{id}/members/{userId}/admin`

**Request**: No body required

**Response (200 OK)**:
```json
{
  "targetUserId": "long",
  "newRole": "MEMBER",
  "previousRole": "ADMIN",
  "success": "boolean"
}
```

**Errors**:
- 403: Caller is not an ADMIN of the group; **403/400: target is the LAST admin** (bloqueado; el usuario debe asignar otro admin antes de perder el rol)
- 404: Group not found or target not a member
- 401: Unauthenticated

**Notes**: el ex-admin pasa a Miembro y conserva su pertenencia; si falla, se conserva la asignación anterior.

## Contract: Group Photo Upload

**Method**: `POST`
**Endpoint**: `/api/groups/{id}/photo`

**Request**: `multipart/form-data`
- `file`: file (JPG, JPEG, or PNG only). Size limit: reused from the shared
  `AvatarValidator` (2 MB, content-based via magic bytes; oversize → 413,
  same as the user-avatar flow) — decision D6 makes the existing validator
  the single gate, so the originally sketched "5 MB / 400" is superseded.

**Response (200 OK)**:
```json
{
  "photo": "string (filename or URL)",
  "updatedAt": "string (ISO-8601)"
}
```

**Errors**:
- 400: Invalid file type (not JPG/JPEG/PNG; magic-byte check, never the declared MIME)
- 413: File too large (existing `MaxUploadSize` handler, reused)
- 403: Not an admin
- 404: Group not found
- 401: Unauthenticated

## Contract: Group Activity History

> **Actualizado por 014** (`specs/014-group-activity-history/contracts/activity-api.md`): `details` es un objeto JSON, `eventType` acepta varios tipos separados por coma (400 si alguno no existe) y `size` se limita a 100.

**Method**: `GET`
**Endpoint**: `/api/groups/{id}/activity`

**Request**: Optional query parameters
- `eventType`: FILTER by event type
- `page`: int (default 1)
- `size`: int (default 20)

**Response (200 OK)**:
```json
{
  "content": [
    {
      "id": "long",
      "eventType": "STRING",
      "actor": {
        "id": "long",
        "firstName": "string",
        "lastName": "string"
      } or null,
      "details": "string (nullable)",
      "createdAt": "string (ISO-8601)"
    }
  ],
  "totalElements": "int",
  "totalPages": "int",
  "page": "int",
  "size": "int"
}
```

**Errors**:
- 404: Group not found
- 401: Unauthenticated

## Contract: Search Friend Candidates (for adding members)

**Method**: `GET`
**Endpoint**: `/api/groups/{id}/candidates?q=`

**Request**:
- `q`: string (required, min 2 chars)

**Response (200 OK)**: solo amigos del caller (via `FriendshipPort` — NUNCA toda la base de usuarios) que aún no son miembros del grupo
```json
{
  "content": [
    {
      "id": "long",
      "firstName": "string",
      "lastName": "string",
      "email": "string"
    }
  ]
}
```

**Errors**:
- 400: Search term too short (<2)
- 403: Caller no tiene permiso de agregar miembros
- 404: Group not found
- 401: Unauthenticated

**Nota de implementación**: mientras no exista el módulo de amistades, `FriendshipPort` es un stub (lista de amigos de prueba para dev/tests). `UserRepository.searchVerified()` NO se usa para este flujo (busca en toda la base).

## Summary of API Contracts

| Endpoint | Method | Purpose | Auth |
|---|---|---|---|
| `/api/groups` | POST | Create group | Required |
| `/api/groups/{id}` | GET | Get group details | Required |
| `/api/groups/{id}` | PUT | Update group (Admin) | Required |
| `/api/groups/{id}/leave` | POST | User leaves group (bloqueado por deudas o último admin) | Required |
| `/api/groups/{id}/members` | POST | Add members (quien tenga permiso vía GroupPermissionChecker) | Required |
| `/api/groups/{id}/members/{userId}` | DELETE | Remove member (ADMIN; baja lógica `left_at`) | Required |
| `/api/groups/{id}/simulated-profiles/{profileId}` | DELETE | Remove simulated profile (ADMIN o miembro con casilla activa) | Required |
| `/api/groups/{id}/members/{userId}/admin` | POST | Assign admin role (solo ADMIN) | Required |
| `/api/groups/{id}/members/{userId}/admin` | DELETE | Revoke admin role (solo ADMIN; bloqueado el último admin) | Required |
| `/api/groups/{id}/photo` | POST | Upload group photo (ADMIN; JPG/JPEG/PNG ≤2MB via the shared `AvatarValidator`; oversize → 413) | Required |
| `/api/groups/{id}/activity` | GET | Get activity history | Required |
| `/api/groups/{id}/candidates?q=` | GET | Search friend candidates (solo amigos via FriendshipPort) | Required |

All endpoints return appropriate HTTP error codes (400, 401, 403, 404) with consistent error format following the amano API pattern.