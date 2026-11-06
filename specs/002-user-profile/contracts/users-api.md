# Contract: Users API (reuso)

**Feature**: `002-user-profile` | **Spec**: [spec.md](spec.md)

**No hay endpoints nuevos ni modificados en esta feature.** La pantalla "Mi perfil" consume
exclusivamente los dos contratos ya activados por [001-user-registration](../../001-user-registration/contracts/users-api.md):

- `GET /api/users/me` — datos y preferencias del usuario en sesión (cookie `amano_auth`).
- `GET /api/users/{id}/avatar` — binario del avatar. Accesible para el dueño, sus amigos (solicitud `ACCEPTED`), quienes comparten con él un grupo activo y quien recibió una solicitud pendiente suya (solo en ese sentido); para cualquier otro id responde `404`, igual que una cuenta sin avatar.

Este documento fija las **cláusulas de consumo** que la SPA debe respetar al renderizar el perfil.

## `GET /api/users/me` — consumo para el perfil

### Respuesta 200 (sesión válida)

```json
{
  "id": 1,
  "firstName": "Ana",
  "lastName": "Pérez",
  "email": "ana.perez@ejemplo.com",
  "currencies": ["ARS", "USD"],
  "cbuCvu": "0123456789012345678901",
  "hasCustomAvatar": true,
  "avatarUrl": "/api/users/1/avatar"
}
```

Reglas de render obligatorias (tabla campo a campo):

| Campo | Semántica | Render en "Mi perfil" |
|---|---|---|
| `firstName` | obligatorio | texto directo |
| `email` | único, normalizado por el backend | texto directo (no editable en esta feature) |
| `password` / hash | **nunca transportado** (FR-007 de `001`) | siempre enmascarado `●●●●●●●●` + leyenda "Por seguridad, la contraseña no se muestra" |
| `lastName` | opcional; `null` se omite del JSON | texto o "No informado" si está ausente |
| `cbuCvu` | opcional; privado del dueño (FR-009 de `001`) | texto o "No informado" si está ausente |
| `currencies` | lista; vacía ⇒ default del cliente (FR-011/Q1 de `001`) | lista; si `[]` ⇒ mostrar `ARS` |
| `hasCustomAvatar` | `true` ⇔ existe fila en `user_avatars` | resuelve fuente de avatar (ver tabla siguiente) |
| `avatarUrl` | presente solo si `hasCustomAvatar=true` | URL del `<img>` del avatar propio |

### Respuestas de fallo (referencia)

| Situación | Código | Comportamiento del perfil en la SPA |
|---|---|---|
| Sin cookie / inválida / expirada | `401` | redirige al flujo de registro/acceso (gate existente `ProtectedRoute`); jamás renderiza datos |
| Fallo de red o `5xx` | — | mensaje de error en la pantalla; sin datos incompletos ni ficticios (FR-009) |

## `GET /api/users/{id}/avatar` — consumo para el perfil

| Situación | Código | Render |
|---|---|---|
| Avatar propio existe | `200` binario (`image/jpeg`/`image/png`, `nosniff`, `Cache-Control: private, max-age=3600`) | `<img>` con la URL de `avatarUrl` |
| Sin fila de avatar | `404` | estrictamente `<img src="/avatar-default.png">` (estático del frontend; no hay binario por defecto en BD) |
| Sin sesión válida | `401` | no aplica en pantalla protegida; el gate redirige antes |

## Cláusulas adicionales de esta feature

1. **Solo lectura**: ningún método distinto de `GET` contra estos endpoints desde la pantalla (la edición queda diferida; FR-011).
2. **Sin invento de datos**: ante `me()` con `ok:false` por red, mostrar estado de error; nunca placeholders que parezcan datos reales.
3. **Coincidencia si oferta**: si un `me()` de reintento devuelve `ok:true` después de un fallo, la pantalla puede renderizar los datos frescos (y solo el mensaje de error en el estado intermedio).
