# Contrato: API de inicio de sesión y cierre de sesión (004)

**Fecha**: 2026-10-20 | **Spec**: [spec.md](../spec.md)

Contrato de los dos endpoints públicos de autenticación agregados por esta feature bajo `/api/auth`. Todo el frontend consume `/api/*` a través del proxy de Vite (`/api` → `:8080`).

**Convenciones comunes** (definidas en `specs/001-user-registration/contracts/auth-api.md`):

- Éxito: `application/json;charset=UTF-8`.
- Error: `application/problem+json` (RFC 7807) con `type`, `title`, `status`, `detail` y, cuando aplica, `properties`.
- Ninguna respuesta expone el password ni su hash, ni stacktraces, ni si un correo está registrado.

**Relación con otros contratos**: la cookie `amano_auth` y el perfil con forma de `GET /api/users/me` siguen el contrato de 001; el rechazo de cuentas no verificadas y `POST /api/auth/resend-verification` siguen `specs/003-email-verification/contracts/email-verification-api.md`. `POST /api/auth/register` permanece definido en 001.

---

## POST /api/auth/login

Autentica correo + contraseña y, si son válidos sobre una cuenta **verificada**, establece la sesión.

**Visibilidad**: pública (`permitAll`).

**Request**: `Content-Type: application/json`

```json
{ "email": "ana@example.com", "password": "Secreto123!" }
```

| Campo | Tipo | Reglas |
|-------|------|--------|
| `email` | string | Recortado y pasado a minúsculas en el servidor antes de buscar la cuenta |
| `password` | string | Se compara tal cual contra el hash; nunca se devuelve |

Cuerpo ausente o campos `null`: se tratan como vacíos (ver errores 400).

### Respuestas

| Código | Condición | Encabezados / cuerpo |
|--------|-----------|----------------------|
| **200 OK** | Credenciales correctas sobre cuenta verificada | `Set-Cookie: amano_auth=<jwt>; Path=/; HttpOnly; SameSite=Strict; Max-Age=86400` (+ `Secure` en HTTPS) y cuerpo = perfil |
| **400 Bad Request** | `email` o `password` vacíos | ProblemDetail con `properties.fields` (sin cookie) |
| **401 Unauthorized** | Cuenta inexistente **o** contraseña incorrecta (indistinguibles) | ProblemDetail genérico (sin cookie) |
| **403 Forbidden** | Contraseña correcta pero cuenta no verificada | ProblemDetail de 003 con `properties.action = "RESEND_VERIFICATION"` (sin cookie) |

**200 — cuerpo (perfil, forma de `GET /api/users/me`)**:

```json
{
  "id": 42,
  "firstName": "Ana",
  "lastName": "Pérez",
  "email": "ana@example.com",
  "currencies": ["ARS"],
  "cbuCvu": null,
  "hasCustomAvatar": false,
  "avatarUrl": null
}
```

**400 — campos vacíos** (ambos errores se reportan juntos):

```json
{
  "type": "about:blank",
  "title": "Validación fallida",
  "status": 400,
  "detail": "La solicitud contiene campos inválidos",
  "properties": {
    "fields": {
      "email": "El correo electrónico es obligatorio",
      "password": "La contraseña es obligatoria"
    }
  }
}
```

**401 — credenciales inválidas** (idéntico para cuenta inexistente y contraseña incorrecta; el servidor iguala el tiempo de respuesta comparando contra un hash BCrypt ficticio cuando la cuenta no existe):

```json
{
  "type": "about:blank",
  "title": "No autorizado",
  "status": 401,
  "detail": "El correo electrónico o la contraseña son incorrectos"
}
```

**403 — cuenta no verificada** (conserva FR-014 de 003; solo se emite a quien ya posee la contraseña correcta):

```json
{
  "type": "about:blank",
  "title": "Cuenta no verificada",
  "status": 403,
  "detail": "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico",
  "properties": { "action": "RESEND_VERIFICATION" }
}
```

> El 403 es la señal para que la pantalla de login ofrezca el botón de reenvío de 003 (`POST /api/auth/resend-verification`), que mantiene su contrato neutro de 200.

---

## POST /api/auth/logout

Cierra la sesión: emite la cookie de sesión con `Max-Age=0` para que el navegador la elimine.

**Visibilidad**: pública (`permitAll`) e **idempotente**: sin sesión (o repetida) sigue siendo un cierre inofensivo.

**Request**: sin cuerpo. No requiere autenticación ni cookie válida.

### Respuestas

| Código | Condición | Encabezados / cuerpo |
|--------|-----------|----------------------|
| **204 No Content** | Siempre (única respuesta posible) | `Set-Cookie: amano_auth=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0` (+ `Secure` en HTTPS); cuerpo vacío |

---

## Comportamiento del cliente (contrato de interfaz)

| Situación | Comportamiento de `authClient` / `LoginForm` |
|-----------|----------------------------------------------|
| 2xx en login | `login()` devuelve `{ ok: true, profile }`; se guarda la sesión y se navega a la ruta intentada (`state.from`) o a `/dashboard` |
| 400 | `{ ok: false, status, fields }` → error por campo en el formulario |
| 401 | `{ ok: false, status, detail }` → mensaje genérico, **se limpia la contraseña y se conserva el correo** |
| 403 con `action = "RESEND_VERIFICATION"` | Aviso de cuenta no verificada + `ResendCountdown` de 003 |
| ≥ 500 o fallo de red | `{ ok: false, network: true }` → "No se pudo iniciar sesión. Intenta nuevamente"; botón rehabilitado |
| Logout | `logout()` siempre devuelve `{ ok }` (no lanza excepción); la UI limpia el estado local y navega a `/login` aunque la red falle |
| Sesión activa en `/login` | Redirección al destino sin renderizar el formulario |
| Ruta protegida sin sesión | `ProtectedRoute` redirige a `/login` con `state = { from: location }` |
