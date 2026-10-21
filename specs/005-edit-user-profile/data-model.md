# Data Model: Modificar perfil de usuario

**Feature**: `005-edit-user-profile` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Conclusión: mutación sin cambios de esquema

Esta feature **muta datos pero no modifica entidades, tablas, columnas, CHECKs, índices
ni migraciones**. La fuente de verdad del esquema sigue siendo la migración
`backend/src/main/resources/db/migration/V1__create_user_registration.sql` del feature
[001-user-registration](../001-user-registration/data-model.md): `ddl-auto=validate` debe
seguir validando sin diferencias y no se crea ningún script `V2__*`.

## Tablas mutadas (esquema de 001, escritura por el principal de sesión)

| Tabla (`001`) | Mutación en esta feature | Campos afectados | Reglas |
|---|---|---|---|
| `users` | `UPDATE` (por `id` del principal) | `first_name`, `last_name`, `cbu` | `first_name` obligatorio 2–50 tras trim (FR-002 de `001`); `last_name` opcional 2–50 tras trim (FR-003) o `NULL`; `cbu` opcional 22 dígitos (FR-009) o `NULL` — al limpiar el campo vuelve a `NULL` |
| `users` | `UPDATE` (solo cambio de contraseña) | `password_hash` | Re-hash BCrypt de la contraseña nueva (política FR-006 de `001`) tras verificar la actual (FR-013/FR-014 de esta spec) |
| `user_currencies` | `DELETE` + `INSERT` (reposición completa) | `currency_code` | Lista destino ⊆ {ARS, USD, EUR}, sin duplicados (FR-011 de `001`); lista vacía ⇒ se eliminan todas las filas (representación "sin preferencias" ⇒ ARS en el cliente, FR-011/Q1 de `001`) |
| `user_avatars` | `UPSERT` (reemplazo) o `DELETE` (eliminación) | `data`, `content_type` | Reemplazo: la imagen nueva (JPG/JPEG/PNG validada por magic bytes, ≤ 2 MB) sustituye el binario (o crea la fila si no existía); eliminación: la fila se quita ⇒ `hasCustomAvatar=false` |

## Columnas y restricciones intactas (invariantes de esta feature)

- `users.email`: **sin mutación posible** vía esta feature — ningún endpoint la toca y la
  petición de actualización no contempla el campo (FR-007/FR-020; SC-003: 0 cambios).
- `users.password_hash`: solo cambiable con la contraseña actual verificada (FR-013/FR-014).
- `users.id`, `created_at`, datos de consentimiento legal: inalterados.

## Reglas derivadas que mantiene esta feature

- **Aislamiento**: toda mutación resuelve el usuario por el principal de la sesión (cookie
  `amano_auth`); ninguna petición lleva id de usuario de destino, así que no existe vía para
  alterar datos de terceros (FR-017, SC-007).
- **Contraseña**: la actual y la nueva nunca se registran ni se devuelven; solo el hash se
  persiste (FR-007 de `001`).
- **CBU/CVU privado del dueño**: sigue expuesto solo por `GET /api/users/me` al propio
  usuario (FR-009 de `001`); esta feature no agrega lecturas nuevas.
- **Avatar**: `GET /api/users/{id}/avatar` de `001`/`002` sirve el binario vigente; tras el
  reemplazo sirve la imagen nueva, tras la eliminación responde 404 (⇒ default en el cliente).
- **Opcionalidad**: `last_name`/`cbu` `NULL` ⇒ "No informado" en la visualización (FR-004 de
  `002`); `user_currencies` sin filas ⇒ ARS (FR-006 de `002`).

## Cambios prohibidos en esta feature

- Ninguna migración nueva (`V2__*` no se crea).
- Ninguna vía (endpoint, campo o parámetro) para modificar `users.email`.
- Ningún endpoint de edición que resuelva un usuario distinto del principal.
- Ningún almacenamiento de contraseñas en claro (ni logs, ni respuestas API).
