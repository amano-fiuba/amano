# Data Model: Inicio de sesión de usuario (004)

**Fecha**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

**Alcance de datos**: la feature **no crea entidades nuevas ni migraciones**. Solo consulta y transforma datos existentes (esquema de 001/002/003) y emite/invalida la cookie de sesión definida en 001.

## Entidades

### User (existente — 001/002/003, solo lectura en esta feature)

Representa a una cuenta registrada. Campos relevantes para el login:

| Campo | Regla de uso en 004 |
|-------|---------------------|
| `email` | Identificador de búsqueda. Se compara contra el correo **normalizado** (trim + minúsculas, `Locale.ROOT`) tanto en cliente como en servidor |
| `passwordHash` | Hash BCrypt del password; nunca se expone ni se devuelve. Comparación con `PasswordEncoder.matches` |
| `emailVerifiedAt` | `null` = cuenta no verificada (bloquea el login según FR-014 de 003); con valor = cuenta verificada, login permitido |
| Perfil (`id`, `firstName`, `lastName`, `currencies`, `cbuCvu`, `hasCustomAvatar`, `avatarUrl`) | Se devuelve en el 200 del login con la misma forma de `GET /api/users/me` (contrato de 001) |

**Reglas de validación aplicadas por el login** (FR-003/FR-005 de la spec):

- `email` vacío (tras normalizar) → error de campo `"El correo electrónico es obligatorio"`; sin consulta a BD.
- `password` nulo o vacío → error de campo `"La contraseña es obligatoria"`; sin consulta a BD.
- Ambos errores se reportan juntos en una sola respuesta 400.
- No hay validación de formato de correo en el servidor: formato inválido ⇒ cuenta no encontrada ⇒ 401 genérico (decisión D3 de research).

### Credenciales de login (dato de solicitud, no persistido)

- `email` (string) y `password` (string) enviados como JSON a `POST /api/auth/login`.
- El `password` solo existe durante la comparación: no se almacena, registra ni devuelve. La respuesta de error 401 no lo incluye, y la interfaz lo limpia del formulario conservando el correo.

### Sesión de usuario (cookie `amano_auth` — contrato de 001)

| Atributo | Valor |
|----------|-------|
| Nombre | `amano_auth` |
| Contenido | JWT firmado (`JWT_SECRET`); claims `sub` = id de usuario, `iat`, `exp` |
| `Max-Age` | `JWT_EXPIRATION` (86400 s por defecto) al iniciar sesión; **0** al cerrar sesión |
| `HttpOnly` | sí (inalcanzable para JavaScript) |
| `SameSite` | `Strict` |
| `Path` | `/` |
| `Secure` | solo cuando la petición llega por HTTPS |

Estados: **inexistente → activa (login 200) → expirada (logout 204 con `Max-Age=0` o vencimiento `exp`)**. El filtro de cookie existente la convierte en el principal de seguridad; si es inválida o falta, las rutas protegidas responden 401 y el gate de rutas redirige a `/login`.

### Estado de verificación de cuenta (existente — 003)

No es un campo nuevo: es la condición `emailVerifiedAt == null` sobre `User`.

Transición relevante para esta feature:

```text
[no verificada] --login con contraseña correcta--> 403 + RESEND_VERIFICATION (sin cookie)
[no verificada] --login con contraseña incorrecta--> 401 genérico (sin cookie)
[no verificada] --verificación de correo (003)--> [verificada]
[verificada]    --login correcto--> 200 + cookie amano_auth + perfil
```

La transición a `[verificada]` pertenece a 003 y no cambia aquí; 004 solo la consulta.

## Relaciones

- `User` 1 — 1 `Sesión` activa por navegador (la sesión no persiste en BD: es el JWT de la cookie; un mismo usuario puede tener varias cookies válidas en distintos dispositivos, ya que no hay revocación por sesión — fuera de alcance).
- `User` 1 — 0..1 `token de verificación` (entidad de 003, usada indirectamente por el botón de reenvío).

## Cambios de esquema

**Ninguno.** Sin tablas nuevas, sin columnas nuevas, sin migraciones Flyway (la feature solo lee datos existentes).
