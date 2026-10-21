# Contrato: API de recuperación de contraseña (006)

**Fecha**: 2026-10-21 | **Spec**: [spec.md](../spec.md)

Contrato de los dos endpoints públicos agregados por esta feature bajo `/api/auth`, ambos en `PasswordResetController`. Todo el frontend consume `/api/*` a través del proxy de Vite (`/api` → `:8080`) o de nginx en compose.

**Convenciones comunes** (definidas en `specs/001-user-registration/contracts/auth-api.md`):

- Éxito: `application/json;charset=UTF-8`.
- Error: `application/problem+json` (RFC 7807) con `type`, `title`, `status`, `detail` y, cuando aplica, `properties`.
- Ninguna respuesta expone contraseñas ni hashes, tokens, stacktraces ni si un correo está registrado.

**Relación con otros contratos**: la política de contraseñas y sus mensajes de campo son los de 001; el criterio de respuesta neutra replica `POST /api/auth/resend-verification` de 003 (`specs/003-email-verification/contracts/email-verification-api.md`); el login posterior usa `POST /api/auth/login` de 004 sin cambios (`specs/004-user-login/contracts/auth-api.md`).

---

## POST /api/auth/forgot-password

Solicita el enlace de recuperación. Si el correo corresponde a una cuenta **verificada**, invalida sus enlaces anteriores, emite uno nuevo y lo envía por correo de forma asíncrona.

**Visibilidad**: pública (`permitAll`).

**Request**: `Content-Type: application/json`

```json
{ "email": "ana@example.com" }
```

| Campo | Tipo | Reglas |
|-------|------|--------|
| `email` | string | Recortado y pasado a minúsculas en el servidor. Sin validación de formato: un valor vacío o inválido se trata como cuenta inexistente |

Cuerpo ausente o `email` `null`: se tratan como cuenta inexistente.

### Respuestas

| Código | Condición | Cuerpo |
|--------|-----------|--------|
| **200 OK** | **Siempre**: cuenta verificada, pendiente, inexistente, cuerpo vacío o falla del SMTP | Mensaje neutro, idéntico byte a byte en todos los casos |

```json
{ "message": "Si el correo existe, recibirás un enlace para restablecer tu contraseña" }
```

> No existen respuestas 400/404/429 para este endpoint. Un 5xx solo puede deberse a una falla inesperada del servidor (por ejemplo, base de datos caída), nunca a datos de la cuenta.

### Efectos internos (no reflejados en la respuesta)

| Situación | Efecto |
|-----------|--------|
| Cuenta verificada | Tokens vigentes de la cuenta → `used_at = now()`; token nuevo con `expires_at = now() + 1 h`; correo asíncrono |
| Cuenta pendiente o inexistente | Ninguno |
| Falla del SMTP | Warning en el log sin token ni credenciales |

### Correo enviado

- **Asunto**: `Restablece tu contraseña - A Mano`
- **Remitente**: `MAIL_USERNAME` (si está configurado)
- **Cuerpo** (texto plano, UTF-8):

```text
Hola Ana,

Recibimos una solicitud para restablecer la contraseña de tu cuenta en A Mano. Para elegir una nueva, haz clic en el siguiente enlace (válido durante 1 hora):

${APP_BASE_URL}/reset-password?token=<token de 43 caracteres Base64URL>

Si tú no solicitaste este cambio, puedes ignorar este mensaje: tu contraseña actual seguirá funcionando.
```

---

## POST /api/auth/reset-password

Canjea el enlace y reemplaza la contraseña. No abre sesión.

**Visibilidad**: pública (`permitAll`): el token del enlace es la credencial.

**Request**: `Content-Type: application/json`

```json
{ "token": "q3Xb...43 caracteres...", "password": "NuevaClave123!", "confirmPassword": "NuevaClave123!" }
```

| Campo | Tipo | Reglas |
|-------|------|--------|
| `token` | string | Valor en claro tomado de `?token=` del enlace; el servidor lo hashea (SHA-256) para buscarlo |
| `password` | string | Obligatoria; política de 001: 8–64 caracteres con al menos una mayúscula, una minúscula, un número y un símbolo |
| `confirmPassword` | string | Obligatoria; debe ser igual a `password` |

Cuerpo ausente o campos `null`: el token se trata como no válido (400).

**Orden de evaluación**: token no válido → token vencido → campos → éxito.

### Respuestas

| Código | Condición | Cuerpo | Efecto sobre los tokens |
|--------|-----------|--------|-------------------------|
| **200 OK** | Token vigente y campos válidos | `{ "message": ... }` (sin `Set-Cookie`) | Se eliminan **todos** los de la cuenta |
| **400 Bad Request** | Token inexistente, manipulado, ya canjeado o invalidado por una solicitud más nueva | ProblemDetail "Enlace no válido" | Ninguno |
| **410 Gone** | Token vigente pero vencido | ProblemDetail "Enlace expirado" | Se elimina ese token |
| **400 Bad Request** | Token vigente; contraseña fuera de política o confirmación distinta | ProblemDetail con `properties.fields` | Ninguno (el enlace sigue sirviendo) |

**200 — contraseña actualizada**:

```json
{ "message": "Tu contraseña fue actualizada. Ya puedes iniciar sesión" }
```

**400 — enlace no válido** (el mismo para todos los motivos; no revela si la cuenta existe):

```json
{
  "type": "about:blank",
  "title": "Enlace no válido",
  "status": 400,
  "detail": "El enlace para restablecer la contraseña no es válido"
}
```

**410 — enlace vencido**:

```json
{
  "type": "about:blank",
  "title": "Enlace expirado",
  "status": 410,
  "detail": "El enlace para restablecer la contraseña ha expirado"
}
```

**400 — errores de campo** (se reportan todos juntos):

```json
{
  "type": "about:blank",
  "title": "Validación fallida",
  "status": 400,
  "detail": "La solicitud contiene campos inválidos",
  "properties": {
    "fields": {
      "password": "La contraseña debe incluir una mayúscula, una minúscula, un número y un símbolo",
      "confirmPassword": "Las contraseñas no coinciden"
    }
  }
}
```

Mensajes posibles por campo:

| Campo | Mensaje |
|-------|---------|
| `password` | "La contraseña es obligatoria" |
| `password` | "La contraseña debe tener al menos 8 caracteres" |
| `password` | "La contraseña no puede superar los 64 caracteres" |
| `password` | "La contraseña debe incluir una mayúscula, una minúscula, un número y un símbolo" |
| `confirmPassword` | "Las contraseñas no coinciden" (también si falta) |

> Para distinguir los dos 400, el cliente mira `properties.fields`: si trae campos, es un error de formulario; si no, el enlace no es válido.

---

## Endpoints existentes sin cambios

| Endpoint | Relación |
|----------|----------|
| `POST /api/auth/login` (004) | Tras el canje, la nueva contraseña se usa aquí; la anterior responde el 401 genérico |
| `POST /api/auth/resend-verification` (003) | Camino para cuentas pendientes, que no reciben enlace de recuperación |
| `POST /api/auth/register` (001/003) | Sin cambios; su política de contraseña es la misma que aplica el canje |

---

## Comportamiento del cliente (contrato de interfaz)

### `forgotPassword(email)` y `ForgotPasswordPage` (`/forgot-password`)

| Situación | Comportamiento |
|-----------|----------------|
| Correo vacío o con formato inválido | Error de campo en el cliente ("El correo electrónico es obligatorio" / "Ingresá un correo electrónico válido"); sin llamada |
| Envío en curso | Botón deshabilitado con "Enviando…" |
| 200 | `{ ok: true, message }` → se reemplaza el formulario por "Revisa tu bandeja de entrada" y el mensaje del servidor (`role="status"`) |
| Cualquier otro resultado o fallo de red | `{ ok: false, network: true }` → "No se pudo enviar la solicitud. Intenta nuevamente"; formulario conservado |

El correo se envía normalizado (`normalizeEmail`).

### `resetPassword(token, password, confirmPassword)` y `ResetPasswordPage` (`/reset-password?token=...`)

| Situación | Comportamiento |
|-----------|----------------|
| URL sin `token` | "El enlace para restablecer la contraseña no es válido" + "Solicitar un nuevo enlace" (`/forgot-password`); sin formulario ni llamada |
| Campos inválidos en el cliente | Errores de campo con los mensajes de 001 ("Debes confirmar la contraseña" si falta la confirmación); sin llamada |
| Envío en curso | Botón deshabilitado con "Guardando…" |
| 200 | `{ ok: true, message }` → `navigate('/login', { replace: true, state: { notice: message } })`; `LoginPage` muestra el aviso (`data-testid="login-notice"`) |
| 410 | `{ ok: false, status: 410, ... }` → "…ha expirado" + "Solicitar un nuevo enlace"; sin formulario |
| 400 con `fields` | `setError` por campo; formulario conservado |
| 400 sin `fields` (u otro 4xx) | "…no es válido" + "Solicitar un nuevo enlace"; sin formulario |
| ≥ 500 o fallo de red | `{ ok: false, network: true }` → "No se pudo actualizar la contraseña. Intenta nuevamente"; formulario conservado |

### `LoginPage` (`/login`)

| Situación | Comportamiento |
|-----------|----------------|
| Siempre | Enlace "¿Olvidaste tu contraseña?" a `/forgot-password` |
| `location.state.notice` presente | Se muestra en `role="status"` sobre el formulario |
