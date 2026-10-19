# Data Model: Visualización de perfil de usuario

**Feature**: `002-user-profile` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Conclusión: modelo sin cambios (no-op)

Esta feature es de **solo lectura** y no introduce ni modifica entidades, tablas, columnas,
CHECKs, índices ni migraciones. La fuente de verdad del esquema sigue siendo la migración
`backend/src/main/resources/db/migration/V1__create_user_registration.sql` del feature
[001-user-registration](../001-user-registration/data-model.md) (`ddl-auto=validate` debe
seguir validando sin diferencias).

## Tablas consumidas (solo lectura)

| Tabla (`001`) | Uso en esta feature | Campos proyectados |
|---|---|---|
| `users` | Perfil del usuario en sesión vía `GET /api/users/me` | `id`, `first_name`, `last_name`, `email`, `cbu` (expuesto como `cbuCvu`) |
| `user_avatars` | Existe fila ⇒ `hasCustomAvatar=true` y `avatarUrl` apunta a `GET /api/users/{id}/avatar` | `content_type`, `data` (servidos por el endpoint binario) |
| `user_currencies` | `@ElementCollection` de códigos de moneda; sin filas ⇒ default ARS en el cliente (FR-011/Q1 de `001`) | `currency_code` |

## Reglas derivadas que conserva esta feature

- **Contraseña**: nunca sale del backend (`UserAccountResponse` no la transporta, FR-007 de `001`); el enmascaramiento en pantalla es presentación pura de la SPA.
- **Aislamiento**: `me` consulta siempre el principal de la sesión (id de usuario autenticado); no hay componente de esta feature que resuelva un id ajeno.
- **Opcionalidad**: `lastName` y `cbuCvu` pueden ser `null` (el JSON omite el campo); la UI muestra "No informado" (FR-004). `currencies` vacío ⇒ ARS (FR-006).
- **Avatar**: sin fila en `user_avatars` ⇒ cliente usa `/avatar-default.png`; respuesta 404 del endpoint binario ⇒ mismo fallback (FR-005).

## Cambios prohibidos en esta feature

- Ninguna migración nueva (`V2__*` no se crea).
- Ningún endpoint nuevo ni modificado.
- Ninguna mutación de datos desde la pantalla de perfil (edición diferida a historia futura).
