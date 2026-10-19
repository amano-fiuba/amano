# Contract: Auth API

**Feature**: `001-user-registration` | **Spec**: [../spec.md](../spec.md)

Respuestas en `application/json;charset=UTF-8`; errores estandarizados con ProblemDetail
(RFC 7807, `application/problem+json`) — ver sección **Errores** al final. El frontend
consume estos endpoints vía el proxy unificado `/api/*` (origen único).

## `POST /api/auth/register`

Crea la cuenta, inicia sesión automáticamente (FR-016) asignando la cookie de sesión JWT y
retorna el perfil recién creado. Envío único atómico `multipart/form-data`.

### Petición (multipart/form-data)

| Parte | Campo | Tipo | Obligatorio | Reglas / Normalización |
|---|---|---|---|---|
| form-data | `firstName` | text | Sí | 2–50 caracteres tras trim |
| form-data | `lastName` | text | No (se puede omitir o enviar vacío) | 2–50 caracteres tras trim |
| form-data | `email` | text | Sí | formato e-mail; trim + lowercase en el servidor |
| form-data | `password` | text | Sí | 8–64 chars, ≥1 mayúscula, ≥1 minúscula, ≥1 dígito, ≥1 carácter especial |
| form-data | `confirmPassword` | text | No (se puede omitir en el backend; se valida en el cliente) | 8–64 chars; si se envía, debe coincidir con `password` |
| form-data | `acceptTerms` | text | Sí | `"true"` obligatorio (FR-012) |
| form-data | `currencies` | text (repetido 0..N veces) | No | valores de {`ARS`, `USD`, `EUR`}; si ningún valor ⇒ ARS por defecto (Q1) |
| form-data | `cbuCvu` | text | No | 22 dígitos; se eliminan espacios y guiones separadores antes de validar (Q3) |
| file | `avatar` | binary | No | debe ser `image/jpeg` o `image/png` con magic bytes correctos; máx. 2 MB (FR-010) |

Si algún campo viola su regla, el backend responde `400` con ProblemDetail. Si no se adjunta
archivo de avatar, se omite la parte `avatar` y no se crea fila en `user_avatars` (el frontend
resuelve el default FR-010/D5). `confirmPassword` se valida principalmente en el cliente (FR-013): según la **Asunción de la spec** ("La confirmación de contraseña (contraseña y confirmación deben coincidir) es un campo de entrada obligatorio, adicional a la contraseña propiamente dicha"), el requisito de obligatoriedad aplica al formulario de registro en la UI; el backend la trata como parte opcional del multipart y solo verifica la coincidencia si se envía (FR-006).

### Respuesta 201 Created

Encabezados:

- `Set-Cookie: amano_auth=<jwt>; Path=/; HttpOnly; SameSite=Strict; Max-Age=86400` (más `Secure` cuando del despliegue HTTPS)
- `Location: /api/users/me` (informativo)

Cuerpo (`application/json`) — representación pública del usuario creado:

```json
{
  "id": 1,
  "firstName": "Ana",
  "lastName": "Pérez",
  "email": "ana.perez@example.com",
  "currencies": ["USD", "EUR"],
  "cbuCvu": "0000000000000000000000",
  "hasCustomAvatar": true,
  "avatarUrl": "/api/users/1/avatar"
}
```

- `currencies` vacío o ausente → el cliente muestra `["ARS"]` como default (FR-011).
- `hasCustomAvatar: false` → no existe `avatarUrl` personalizado; el cliente resuelve `/avatar-default.png` (FR-010/D5).
- `cbuCvu` es visible en esta respuesta para el propio usuario (FR-009/FR-021).
- Nunca se devuelve la contraseña ni su hash, ni en texto plano, ni en forma derivada (FR-007).

### Respuestas de fallo

| Situación | Código | `title` | `detail` (visible al usuario) |
|---|---|---|---|
| Campos inválidos (por campo: ver `properties.fields`) | `400` | Validación fallida | mensaje por campo, en español (FR-018) |
| Términos no aceptados (`acceptTerms` != `"true"`) | `400` | Validación fallida | mensaje del checkbox (FR-012) |
| Correo ya registrado (normalizado, incluye carrera concurrente) | `409` | Conflicto | "El correo electrónico ya se encuentra registrado" |
| Avatar no JPG/PNG real (magic bytes) | `400` | Validación fallida | mensaje de formato de imagen |
| Avatar > 2 MB | `413` | Payload demasiado grande | mensaje de tamaño (o `400` si se agrupa con la validación general; decisión de tasks; el frontend ya lo valida en vivo FR-013) |
| Error interno inesperado | `500` | Error interno | mensaje genérico |

### Notas de comportamiento

- **FR-015**: el cliente deshabilita el botón durante el envío y rehabilita a la llegada de cualquier
  respuesta de error (`400`, `409`, `413`, `500`) o fallo de red; solo se procesa una solicitud a la vez.
- **FR-019**: timeout de red o 5xx muestran "No se pudo completar el registro. Intenta nuevamente",
  conservando los datos del formulario y rehabilitando el botón.
- **FR-020**: si el usuario abandona la pantalla sin confirmar, no se envía nada ni se persiste algo.

## `Set-Cookie` de sesión (contrato entre módulos)

| Atributo | Valor |
|---|---|
| `Name` | `amano_auth` |
| `HttpOnly` | sí (no accesible por JavaScript) |
| `SameSite` | `Strict` |
| `Max-Age` | `86400` (24 h, variable `JWT_EXPIRATION`) |
| `Secure` | presente en HTTPS; no en `http://localhost` (compose local) |
| JWT | firmado con `JWT_SECRET` (env); claims: `sub` = id de usuario, `iat`, `exp` |

## Errores — formato común (RFC 7807)

```json
{
  "type": "about:blank",
  "title": "Conflicto",
  "status": 409,
  "detail": "El correo electrónico ya se encuentra registrado",
  "properties": {
    "fields": {
      "email": "El correo electrónico ya se encuentra registrado"
    }
  }
}
```

- `properties.fields` (opcional) contiene el mensaje por campo (`campo → mensaje`).
- El backend nunca expone stacktraces, SQL ni credenciales (constitución VIII).
- El frontend publica `detail` y `properties.fields` como mensajes de UI, con el mensaje fijo
  "No se pudo completar el registro. Intenta nuevamente" para errores de red o 5xx (FR-019).
