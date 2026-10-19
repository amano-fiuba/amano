# Contrato API: Verificación de correo electrónico

**Feature**: `002-email-verification` | **Spec**: [../spec.md](../spec.md)

Contratos nuevos y cambios sobre los endpoints de 001 que produce esta feature. El formato
de errores es el mismo de 001: RFC 7807 (`application/problem+json`) con la propiedad
personalizada `properties.fields` cuando corresponde. La sesión continúa siendo JWT en cookie
`amano_auth` (HttpOnly, `SameSite=Strict`, `Secure` sobre HTTPS, emisión/reuso vía
`JwtService` y `AuthCookie` de 001).

## Cambio sobre contrato de 001: `POST /api/auth/register`

Reemplaza el comportamiento de FR-016/SC-002 de 001 (sin sesión automática).

- **Request**: sin cambios (multipart: campos de 001, mismas validaciones).
- **201 Created** (registro nuevo o re-registro sobre cuenta no verificada):
  - **Sin** header `Set-Cookie` y sin header `Location` (no hay sesión).
  - Body: `{"email": "<correo normalizado>"}`.
  - Efectos: cuenta creada/actualizada como "no verificada", token emitido (invalida el
    anterior si lo hubiera) y correo enviado `@Async` si corresponde por el límite de reenvío
    (FR-015, Q2: con límite vigente no se envía hasta que venza).
- **409 Conflict**: el correo ya está registrado **y verificada** (FR-016 de 002) — mismo ProblemDetail
  de 001: `title: "Conflicto"`, `detail: "El correo electrónico ya se encuentra registrado"`.
- **400 / 413 / 500**: idénticos a 001 (validación, avatar, error interno).

## Nuevo: `POST /api/auth/verify-email`

Canjea un token de verificación. Es un POST (D3) para que los escáneres de correo no consuman
el token; el frontend llama a este endpoint desde la página `/verify-email`.

- **Request**: `Content-Type: application/json`, body `{"token": "<token en claro del enlace>"}`.
- **200 OK**: token válido, vigente y sin usar.
  - Efectos: cuenta marcada "verificada" (`email_verified_at`), token marcado como usado,
    sesión iniciada.
  - Headers: `Set-Cookie: amano_auth=<jwt>; Path=/; HttpOnly; SameSite=Strict; Max-Age=86400`
    (+ `Secure` sobre HTTPS) — idéntico al contrato de cookie de 001.
  - Body: perfil del usuario (mismo shape que `GET /api/users/me` de 001: `{id, firstName,
    lastName, email, currencies, cbuCvu, hasCustomAvatar, avatarUrl}`).
- **400 Bad Request**: token inválido, alterado o inexistente.
  - `title: "Enlace no válido"`, `detail: "El enlace de verificación no es válido"` (FR-008).
- **409 Conflict**: token ya utilizado.
  - `title: "Enlace ya utilizado"`, `detail: "Este enlace de verificación ya ha sido utilizado. Por favor, inicia sesión"` (FR-007).
  - **Sin** `Set-Cookie`.
- **410 Gone**: token vencido (más de 24 h desde `created_at`).
  - `title: "Enlace expirado"`, `detail: "El enlace de verificación ha expirado"` (FR-006).
  - El frontend, ante 410, navega a la pantalla de enlace vencido (que conduce a la de
    reenvío).
- **500**: error interno (ProblemDetail genérico de 001, sin filtrar detalles).

## Nuevo: `POST /api/auth/resend-verification`

Solicita un nuevo correo de verificación. **La respuesta es idéntica exista o no la cuenta,
esté o no verificada, y con límite vigente o no** (FR-012, SC-005) — la única variación es
`remainingSeconds`.

- **Request**: `Content-Type: application/json`, body `{"email": "<correo a reenviar>"}`.
  El correo se normaliza igual que en 001 (trim + minúsculas).
- **200 OK** (siempre, en todos los casos):
  ```json
  {
    "message": "Si el correo existe y no ha sido verificado, se enviará un nuevo enlace de confirmación",
    "remainingSeconds": 127
  }
  ```
  - `remainingSeconds`: segundos restantes del límite de 5 minutos desde el momento de la
    respuesta; `0` cuando no hay límite vigente (FR-012).
  - Efectos internos, **no reflejados en la respuesta**:
    - Sin límite vigente y con cuenta no verificada → se emite token nuevo (invalida el
      anterior) y se envía el correo; `last_resend_at = now()`.
    - Sin límite vigente y sin cuenta o cuenta verificada → no se envía nada, pero
      `last_resend_at = now()` igualmente (el clic inicia el plazo, FR-011).
    - Con límite vigente → no se envía nada y `last_resend_at` no se modifica (el plazo no
      se extiende).
- **429 / 400 / 404 nunca se usan en este endpoint** (romperían la identidad de la respuesta,
  SC-005). Errores reales de infraestructura devuelven 500 ProblemDetail genérico sin
  detalles internos (FR-019).

## Nuevo (stub provisorio): `POST /api/auth/login`

Implementado por `SimulatedLoginController`, **claramente marcado como provisorio** y a
reemplazar por la feature real de inicio de sesión (fuera de alcance). Existe para validar
FR-014.

- **Request**: `Content-Type: application/json`, body `{"email": "...", "password": "..."}`.
- **200 OK**: credenciales válidas y cuenta **verificada** → sesión iniciada; `Set-Cookie`
  idéntico al contrato de 001; body = perfil (shape de `GET /api/users/me`).
- **401 Unauthorized**: credenciales inválidas (cuenta inexistente o contraseña incorrecta)
  — mismo ProblemDetail que el filtro de 001 (`title: "No autorizado"`).
- **403 Forbidden**: credenciales válidas pero cuenta **no verificada** (FR-014):
  - `title: "Cuenta no verificada"`,
    `detail: "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico"`.
  - Además, el ProblemDetail incluye un indicador legible por máquina en `properties`
    (`{"action": "RESEND_VERIFICATION"}`) para que la feature de login real ofrezca el enlace
    de reenvío. La interfaz de login queda fuera de alcance de esta feature.

## Endpoints existentes sin cambios

- `GET /api/users/me`, `GET /api/users/{id}/avatar`, `GET /api/health`.
- **Regla de acceso reforzada** (FR-004): `SecurityConfig` exige que el usuario autenticado
  tenga la cuenta **verificada** para acceder a `/api/users/**`; una cookie emitida antes de
  verificar (por ejemplo, simulada en un test) recibe 401 con el ProblemDetail de
  "No autorizado" de 001. El stub de login jamás emite cookie para cuentas no verificadas.
- **Herencia de la regla (FR-004)**: todo endpoint protegido, presente o futuro, hereda la
  exigencia de cuenta verificada. La comprobación se aplica en el servidor sobre el conjunto
  autenticado, no por enumeración de rutas, de modo que cualquier ruta nueva bajo un ámbito
  protegido queda cubierta sin cambios adicionales en el contrato.

## Consideraciones de comportamiento compartidas

- **Plazos** (centralizados en properties, valores por defecto): token 24 h; límite de
  reenvío 5 min (300 s); caducidad de cuenta no verificada 3 días.
- **Mensajes exactos en español** los define la spec: "El enlace de verificación no es
  válido", "Este enlace de verificación ya ha sido utilizado. Por favor, inicia sesión",
  "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico" y el
  mensaje neutro de reenvío. Los textos de las pantallas (vencido, revisa tu correo) se
  detallan en quickstart.md.
