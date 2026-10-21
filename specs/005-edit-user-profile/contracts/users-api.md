# Contract: Users API (edición de perfil)

**Feature**: `005-edit-user-profile` | **Spec**: [spec.md](spec.md)

Endpoints autenticados con la cookie `amano_auth` (ver
[auth-api.md](../../001-user-registration/contracts/auth-api.md) de `001`). Formato de
errores idéntico: ProblemDetail RFC 7807 (`application/problem+json`). El frontend consume
estos endpoints vía el proxy unificado `/api/*` (origen único).

Todos los endpoints de mutación operan **exclusivamente sobre el principal de la sesión**
(FR-017): ninguna petición lleva id de usuario de destino. Ningún endpoint de esta feature
modifica `email` (FR-007/FR-020): el campo, si aparece en una petición, se ignora
(decisión de tasks: ignorar; un cambio activo se respondería 400, pero el default es
tolerante para no exponer diferencias).

## `GET /api/users/me` (reuso de 001/002)

Mantenido sin cambios: es la fuente de la **precarga** de la edición (FR-002). Respuesta
idéntica a la documentada en [002 contracts](../../002-user-profile/contracts/users-api.md):
`id`, `firstName`, `lastName` (opcional, omitido si `null`), `email`, `currencies`,
`cbuCvu` (opcional, omitido si `null`), `hasCustomAvatar`, `avatarUrl` (solo si
`hasCustomAvatar=true`). Nunca expone la contraseña (FR-007 de `001`).

## `PUT /api/users/me`

Actualiza los datos editables del perfil (US1): nombre, apellido, CBU/CVU y monedas.
El correo electrónico **no forma parte** del contrato de la petición.

### Petición (application/json)

```json
{
  "firstName": "Ana",
  "lastName": "Pérez Gómez",
  "currencies": ["ARS", "USD"],
  "cbuCvu": "0000003100010000000001"
}
```

| Campo | Tipo | Obligatorio | Reglas / Normalización |
|---|---|---|---|
| `firstName` | text | Sí | 2–50 caracteres tras trim (FR-002 de `001`); se persiste el texto recortado |
| `lastName` | text | No | 2–50 caracteres tras trim (FR-003 de `001`); vacío/ausente ⇒ `NULL` (limpiar el campo) |
| `currencies` | array de text | No | valores de {`ARS`, `USD`, `EUR`}, sin duplicados; vacío/ausente ⇒ sin filas (default ARS en el cliente, FR-011/Q1 de `001`) |
| `cbuCvu` | text | No | exactamente 22 dígitos; se eliminan espacios y guiones separadores antes de validar (Q3 de `001`); vacío/ausente ⇒ `NULL` |

El servidor **revalida todo** (validación en fronteras, FR-022 de `001`) y es la única
fuente de verdad: la aplicación de atención termina en el `PUT` atómico del recurso (una
transacción; no hay guardado parcial — FR-019).

### Respuesta 200 (actualización exitosa)

Cuerpo: representación pública actualizada, con las mismas semánticas de `GET /api/users/me`
(persistencia confirmada por el mismo servicio que leerá la visualización de `002`).

### Respuestas de fallo

| Situación | Código | `title` | `detail` (visible al usuario) |
|---|---|---|---|
| Campos inválidos (por campo, en `properties.fields`) | `400` | Validación fallida | mensajes por campo, en español (ej. "El nombre debe tener entre 2 y 50 caracteres", "El CBU/CVU debe tener 22 dígitos") |
| Monedas fuera de {ARS, USD, EUR} o duplicadas | `400` | Validación fallida | mensaje del campo `currencies` (decisión de tasks: rechazar, no silenciar) |
| Sin cookie / cookie inválida/expirada | `401` | No autorizado | mensaje de sesión requerida; el gate redirige |
| Cuenta no verificada | `403` | Prohibido | bloqueo de recurso protegido según `003` |
| Error interno inesperado | `500` | Error interno | mensaje genérico; **ningún cambio parcial queda aplicado** (transacción) |

## `PUT /api/users/me/password`

Cambio de contraseña (US2). Requiere acreditar la contraseña actual (clarificación
2026-10-20; FR-013/FR-014).

### Petición (application/json)

```json
{
  "currentPassword": "S3gura!2026",
  "newPassword": "N3uva#Clave",
  "confirmPassword": "N3uva#Clave"
}
```

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `currentPassword` | text | Sí | comparación contra el hash vigente (BCrypt, verificación constante); erróneo ⇒ 400 |
| `newPassword` | text | Sí | política FR-006 de `001`: 8–64 caracteres, ≥1 mayúscula, ≥1 minúscula, ≥1 dígito, ≥1 carácter especial |
| `confirmPassword` | text | Sí | debe coincidir con `newPassword` |

### Comportamiento

- **200 / 204**: credencial re-hasheada y persistida; la sesión activa (cookie vigente)
  **sigue siendo válida** (no se rota el JWT, Assumption de la spec). Respuesta: representación
  pública del usuario (o 204 — decisión de tasks).
- El campo `email` no existe en esta petición.

### Respuestas de fallo

| Situación | Código | `detail` |
|---|---|---|
| Contraseña actual incorrecta | `400` | "La contraseña actual no es correcta" (FR-014) |
| `newPassword` no cumple la política / confirmación no coincide | `400` | mensajes por campo de FR-006 de `001` |
| Sin sesión | `401` | mensaje de sesión requerida |
| Error interno | `500` | mensaje genérico; credencial intacta (transacción) |

## `PUT /api/users/me/avatar` (reemplazo, US3)

Sustituye el avatar propio por una imagen nueva (mismas reglas de FR-010 de `001`). Si el
usuario no tenía avatar propio, crea la fila (US3 scenario 3).

### Petición (multipart/form-data)

| Parte | Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|---|
| file | `avatar` | binary | Sí | `image/jpeg` o `image/png` con magic bytes correctos; máx. 2 MB |

### Respuestas

| Situación | Código | Comportamiento |
|---|---|---|
| Imagen válida | `200` | fila `user_avatars` creada o sobrescrita (el binario anterior deja de servirse); cuerpo: representación pública del usuario con `hasCustomAvatar=true` y `avatarUrl` actualizado |
| Formato no JPG/PNG real (magic bytes) | `400` | mensaje de formato de imagen; avatar vigente intacto |
| Imagen > 2 MB | `413` | mensaje de tamaño; avatar vigente intacto |
| Sin sesión | `401` | ProblemDetail |
| Error interno | `500` | avatar vigente intacto |

## `DELETE /api/users/me/avatar` (eliminación, US3)

Quita el avatar propio y devuelve la cuenta al estado "sin avatar propio".

### Respuestas

| Situación | Código | Comportamiento |
|---|---|---|
| Avatar propio existe | `204` | fila eliminada; `GET /api/users/{id}/avatar` pasa a responder `404` (⇒ cliente muestra `/avatar-default.png`, FR-005 de `002`) |
| Sin avatar propio (idempotente) | `204` | no hay nada que eliminar; el resultado es el mismo estado |
| Sin sesión | `401` | ProblemDetail |
| Error interno | `500` | estado sin cambios |

## Contrato del frontend con estos endpoints

- **Precarga** (FR-002): `GET /api/users/me` fresco al montar la edición (patrón
  `DashboardPage`/`ProfilePage` de `002`); 401 → redirección vía `ProtectedRoute`.
- **Guardar** (US1): `PUT /api/users/me` con todos los campos editables; éxito ⇒ mensaje
  "Los cambios se guardaron correctamente" (u equivalente) y datos frescos en pantalla;
  `400` ⇒ mensajes por campo de `properties.fields` sin limpiar el formulario (FR-010);
  fallo de red/`5xx` ⇒ "No se pudo completar la actualización. Intenta nuevamente"
  (FR-019) con botón rehabilitado (FR-011).
- **Cancelar** (FR-008): navegación sin `PUT`; ningún cambio persistido.
- **Contraseña** (US2): `PUT /api/users/me/password`; `400` con `currentPassword` ⇒ mensaje
  específico en el campo, sin limpiar el resto del formulario.
- **Avatar** (US3): validación en vivo de formato/tamaño en el cliente antes de enviar
  (patrón FR-013 de `001`); `PUT` para reemplazar, `DELETE` para volver al default; ante
  error, reconsiderar `me()` para restablecer el estado visual real.
- **Correo** (FR-007): el campo `email` de la precarga se renderiza siempre deshabilitado/
  solo lectura; el cliente no lo envía en ninguna petición.
- **Doble envío** (FR-011): botones deshabilitados durante cada mutación; rehabilitados ante
  cualquier respuesta o fallo.
