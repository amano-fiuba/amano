# Data Model: Buscador de usuarios registrados

**Feature**: `008-user-search` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Alcance de datos

La feature es de **solo lectura**. No crea ni modifica tablas, columnas, índices ni
migraciones. Reutiliza la entidad `User` y la tabla `users` de `001-user-registration` y
`003-email-verification`.

## Entidad reutilizada: `User` (tabla `users`)

| Campo | Columna | Tipo | Uso en la búsqueda |
|---|---|---|---|
| `id` | `id` | BIGINT | Identificador del resultado; exclusión del propio usuario y desempate de orden. No se muestra en la lista. |
| `firstName` | `first_name` | VARCHAR(50) NOT NULL | Coincidencia parcial insensible a mayúsculas; orden principal; se muestra. |
| `lastName` | `last_name` | VARCHAR(50) NULL | Coincidencia parcial insensible a mayúsculas; orden secundario; se muestra (o "No informado" si es `null`). |
| `email` | `email` | VARCHAR(255) NOT NULL | Coincidencia parcial insensible a mayúsculas; se muestra. Almacenado normalizado (trim + minúsculas) por 001. |
| `emailVerifiedAt` | `email_verified_at` | TIMESTAMPTZ NULL | Filtro: solo cuentas con valor no nulo (`emailVerifiedAt IS NOT NULL`). |
| `passwordHash` | `password_hash` | VARCHAR(255) NOT NULL | **Nunca** se consulta para la respuesta ni se expone. |
| `cbu` | `cbu` | VARCHAR(22) NULL | **Nunca** se expone. |
| monedas | `user_currencies` | tabla relacionada | **Nunca** se exponen. |
| avatar | `user_avatars` | tabla relacionada | **Nunca** se expone en la búsqueda; el frontend usa el avatar por defecto. |

## DTO de salida: `UserSearchResult`

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `id` | Long | Sí | Identificador de la cuenta; no se renderiza. |
| `firstName` | String | Sí | Nombre de la cuenta. |
| `lastName` | String | No | Apellido de la cuenta; puede ser `null`. La UI muestra "No informado" en ese caso. |
| `email` | String | Sí | Correo electrónico de la cuenta. |

El DTO no contiene CBU/CVU, monedas, contraseña ni hash, avatar, fecha de verificación ni
cualquier otro dato privado. La respuesta HTTP es un arreglo de estos objetos.

## Reglas derivadas y validaciones

1. **Término de búsqueda (`q`)**:
   - Se recorta al inicio y al final.
   - Se compara en minúsculas (`Locale.ROOT`).
   - Debe tener al menos 2 caracteres después del recorte.
   - Si no cumple, se lanza `FieldValidationException` con `fields.q` y no se consulta la
     base de datos.
2. **Universo de resultados**:
   - Solo cuentas con `emailVerifiedAt != null`.
   - Nunca la cuenta cuyo `id` coincide con el principal de la sesión.
3. **Coincidencia**:
   - Parcial sobre `firstName`, `lastName` o `email`.
   - Insensible a mayúsculas/minúsculas.
   - Literal: `%` y `_` no actúan como comodines.
   - `lastName` nulo no coincide por apellido, pero la cuenta puede aparecer por nombre o
     correo.
4. **Orden**:
   - Ascendente por nombre (`lower(firstName)`).
   - Luego por apellido (`lower(lastName)`, con nulos tratados de forma estable).
   - Desempate final por `id` para un orden determinista.
5. **Límite**:
   - Máximo 20 resultados por búsqueda.
   - Sin paginación ni acceso a resultados adicionales.
6. **Privacidad**:
   - La respuesta solo contiene los cuatro campos del DTO.
   - El avatar por defecto es una decisión de presentación del frontend; no forma parte del
     modelo ni de la respuesta.

## Relaciones

La búsqueda no crea ni modifica relaciones. No hay amistades, solicitudes, redes de
contactos, membresías de grupos ni entidades nuevas.

## Estados

No hay transiciones de estado nuevas. El único estado consultado es el de verificación de
la cuenta (`email_verified_at`), definido por 003.
