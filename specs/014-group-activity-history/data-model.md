# Data Model: Ver historial de actividad del grupo (014)

**Fecha**: 2026-11-03 | **Spec**: [spec.md](./spec.md)

**Alcance de datos**: la feature **no crea ni modifica tablas, columnas, índices ni migraciones**, y **no modifica filas existentes**. Escribe filas nuevas en `group_activity` (`V10`, 010) con un formato de `details_json` definido acá.

## Entidades

### GroupActivity (existente — 010)

| Columna | Uso en 014 |
|---------|------------|
| `group_id` | Grupo del evento |
| `event_type` | Uno de los 7 valores del CHECK de `V10`; 014 empieza a usar `EXPENSE_CREATED` y `PAYMENT_MADE` mediante el enganche |
| `actor_user_id` | Quién hizo la acción (nullable) |
| `details_json` | JSON generado por Jackson con el formato de abajo |
| `created_at` | Fecha del evento; orden `created_at DESC, id DESC` |

Índices existentes que usa la consulta: `idx_group_activity_group_id`, `idx_group_activity_event_type`.

## Formato de `details_json` por tipo

Las claves con valor `null` se omiten.

### `MEMBER_ADDED`

| Clave | Tipo | Cuándo |
|-------|------|--------|
| `type` | `"REGISTERED"` \| `"SIMULATED"` | Siempre |
| `userId` | número | Registrado |
| `simulatedProfileId` | número | Simulado |
| `name` | texto | Nombre visible al momento del alta |
| `email` | texto | Correo al momento del alta |
| `atCreation` | `true` | Solo para el miembro inicial al crear el grupo |

```json
{"type":"REGISTERED","userId":15,"name":"Juan Pérez","email":"juan@example.com","atCreation":true}
{"type":"SIMULATED","simulatedProfileId":4,"name":"Abuela \"Tita\"","email":"tita@example.com"}
```

### `MEMBER_REMOVED`

Mismas claves que `MEMBER_ADDED` (sin `atCreation`).

```json
{"type":"REGISTERED","userId":15,"name":"Juan Pérez","email":"juan@example.com"}
```

### `MEMBER_LEFT`, `PHOTO_CHANGED`

`{}` (el afectado es el actor). Sin cambios respecto de 010.

### `PERMISSION_CHANGED`

`{"fields":["permissions"]}`. Sin cambios respecto de 010.

### `EXPENSE_CREATED` (enganche)

| Clave | Tipo | Obligatoria |
|-------|------|-------------|
| `expenseId` | número | No |
| `amount` | texto decimal (`"1500.00"`) | Sí, > 0 |
| `currency` | texto (ISO 4217, mayúsculas) | Sí |
| `description` | texto | No |

### `PAYMENT_MADE` (enganche)

| Clave | Tipo | Obligatoria |
|-------|------|-------------|
| `paymentId` | número | No |
| `amount` | texto decimal | Sí, > 0 |
| `currency` | texto | Sí |
| `fromName` | texto | No |
| `toName` | texto | No |

## Formatos anteriores a 014 (solo lectura)

| Tipo | Formato viejo | Cómo se muestra |
|------|---------------|-----------------|
| `MEMBER_ADDED` registrado | `{"type":"REGISTERED","email":"…"}` | "agregó a {email}" |
| `MEMBER_REMOVED` registrado | `{"userId":15}` | "quitó a un miembro del grupo" |
| `MEMBER_REMOVED` simulado | `{"simulatedProfileId":4,"name":"…","email":"…"}` | "quitó el perfil simulado {name}" |
| Cualquiera con JSON inválido | texto mal formado | `details: null` → frase genérica |

## Records Java nuevos

| Record | Campos |
|--------|--------|
| `ExpenseActivityDetails` | `Long expenseId`, `BigDecimal amount`, `String currency`, `String description` |
| `PaymentActivityDetails` | `Long paymentId`, `BigDecimal amount`, `String currency`, `String fromName`, `String toName` |

## Reglas de negocio

1. Los eventos se escriben en la transacción del cambio que los produce (`Propagation.MANDATORY`).
2. Las filas nunca se modifican ni borran (patrón de preservación).
3. Los nombres son copia al momento del evento.
4. Monto > 0 y moneda no vacía en gastos y pagos.

## Cambios de esquema

Ninguno.
