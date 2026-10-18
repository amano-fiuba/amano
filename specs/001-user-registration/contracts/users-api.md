# Contract: Users API

**Feature**: `001-user-registration` | **Spec**: [../spec.md](../spec.md)

Endpoints autenticados con la cookie `amano_auth` (ver [auth-api.md](./auth-api.md)).
Formato de errores idéntico al descrito en `auth-api.md` (ProblemDetail RFC 7807).
El frontend consume estos endpoints vía el proxy unificado `/api/*` (origen único).

## `GET /api/users/me`

Retorna la información y preferencias del usuario en sesión. Sustituto requerido por la
spec para cumplir **FR-016** (consultar de inmediato su información tras el registro) y el
renderizado del dashboard (SC-002).

### Respuesta 200 (si hay sesión)

```json
{
  "id": 1,
  "firstName": "Ana",
  "lastName": "Pérez",
  "email": "ana.perez@ejemplo.com",
  "currencies": ["ARS"],
  "cbuCvu": "0123456789012345678901",
  "hasCustomAvatar": true,
  "avatarUrl": "/api/users/1/avatar"
}
```

Mismas semánticas del cuerpo que en el registro:

- `currencies` vacío → default ARS (FR-011/Q1). Persistencia sin fila ⇒ representación propia.
- `cbuCvu` solo visible para el dueño (FR-009/FR-021: este endpoint siempre es consultado por el propio usuario por diseño).
- `hasCustomAvatar: false` → `avatarUrl` personalizado; el cliente resuelve `/avatar-default.png` (FR-010/D5).
- Nunca se expone la contraseña ni su hash (FR-007).

### Respuestas de fallo

| Situación | Código | `title` | `detail` |
|---|---|---|---|
| Sin cookie o cookie inválida/expirada | `401` | No autorizado | mensaje de sesión requerida |
| Error interno inesperado | `500` | Error interno | mensaje genérico |

## `GET /api/users/{id}/avatar`

Sirve el avatar propio, en binario, cuando existe fila en `user_avatars`. En este feature se
accede siempre al usuario de la sesión; la exposición a otros usuarios se define en la
funcionalidad de gestión de grupos (fuera de alcance de esta spec).

### Respuesta 200 (si el usuario tiene avatar propio)

- `Content-Type: image/jpeg` o `image/png` (exacto al MIME almacenado, correspondiente a los magic bytes validados D6)
- `X-Content-Type-Options: nosniff` (obligatorio, FR-010/D5)
- `Cache-Control: private, max-age=3600` (recomendado; evita descargas repetidas del binario pesado en la misma sesión)
- Cuerpo: bytes del binario (≤ 2 MB)

### Respuestas de fallo

| Situación | Código | Nota |
|---|---|---|
| Sin sesión válida | `401` | ProblemDetail |
| Usuario en sesión sin avatar propio (0 filas en BD) | `404` | El cliente debe entonces usar `/avatar-default.png` (default estático FR-010/D5; no hay binario por defecto en BD) |
| Error interno inesperado | `500` | ProblemDetail |

## Contrato del frontend con estos endpoints

- `GET /api/users/me`: render del dashboard con datos del perfil; 401 → redirigir al flujo de
  registro (o pantalla de acceso cuando exista). Sustituye el comportamiento placeholder.
- `GET /api/users/{id}/avatar`: URL usada en `avatarUrl` cuando `hasCustomAvatar` es `true`;
  `fetch`/`<img src>` con la cookie SameSite en la sesión misma que en la SPA (cookies automáticas).
- No hay endpoints de mutación de perfil en este feature (edición posterior queda fuera del alcance).
