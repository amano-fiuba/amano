# Contract: Expenses API (registro de gasto personal)

**Feature**: `007-register-expense` | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

Endpoint autenticado con la cookie `amano_auth` (ver
[auth-api.md](../../001-user-registration/contracts/auth-api.md) de `001`). Formato de errores
idéntico al vigente: ProblemDetail RFC 7807 (`application/problem+json`). El frontend consume el
endpoint vía el proxy unificado `/api/*` (origen único, ver `vite.config.js` y `nginx.conf`).

El creador del gasto es **siempre** el principal de la sesión (FR-003/FR-004): ninguna
petición lleva un id de usuario de destino. El gasto es estrictamente personal: no hay campos
de participantes, divisiones, deudas, amigos ni grupos (FR-006).

## `POST /api/expenses`

Registra un gasto personal (US1). Requiere sesión **verificada**, como `/api/users/**`.

### Cabeceras de la petición

| Cabecera | Obligatoria | Reglas |
|---|---|---|
| `Content-Type` | Sí | `application/json` |
| `Idempotency-Key` | Sí | UUID. Identifica **la operación de guardado** (FR-027): un reintento de la misma operación reutiliza el mismo valor; un gasto nuevo deliberado usa uno nuevo. Ausente o inválido ⇒ `400`. Reutilizarla con un **payload distinto** ⇒ `422` (ver Semántica de idempotencia) |

### Petición (application/json)

```json
{
  "amount": "10.10",
  "currency": "ARS",
  "date": "2026-10-21",
  "category": "FOOD",
  "type": "ONE_TIME",
  "paymentMethod": "CASH",
  "description": "Almuerzo",
  "deviceTimeZone": "America/Argentina/Buenos_Aires"
}
```

| Campo | Tipo | Obligatorio | Reglas / Normalización |
|---|---|---|---|
| `amount` | string | Sí | Decimal **canónico** con punto y hasta 2 decimales (`"10.10"`), entre `"0.01"` y `"999999999.99"`; el backend completa a 2 sin redondear. El cliente envía el valor ya parseado del formato argentino; el backend **no** acepta `,`, separadores de miles ni punto decimal como entrada del usuario (FR-013/FR-014/FR-022/FR-023) |
| `currency` | string | Sí | Uno de `ARS`, `USD`, `EUR` (FR-011) |
| `date` | string | Sí | ISO `yyyy-MM-dd`; no posterior al día actual según `deviceTimeZone` (FR-009/FR-016) |
| `category` | string | Sí | Uno de `FOOD`, `TRANSPORT`, `HOUSING`, `SERVICES`, `HEALTH`, `EDUCATION`, `LEISURE`, `SHOPPING`, `PETS`, `OTHER` (FR-011) |
| `type` | string | Sí | `RECURRING` o `ONE_TIME` (FR-010) |
| `paymentMethod` | string | Sí | Uno de `CASH`, `DEBIT_CARD`, `CREDIT_CARD`, `TRANSFER`, `VIRTUAL_WALLET`, `OTHER` (FR-011) |
| `description` | string \| ausente | No | Máx. 200 caracteres por code points **después** del recorte Unicode y de normalizar CRLF/CR→LF; vacío o solo espacios/tabs/saltos ⇒ ausente (`null`) (FR-012/FR-017) |
| `deviceTimeZone` | string | No | Identificador IANA (`Intl.DateTimeFormat().resolvedOptions().timeZone`). **No se persiste**; se usa solo para validar que `date` no sea futura. Si falta o es inválido, el backend aplica una tolerancia de un día (FR-022) |

Propiedades reservadas: si la petición incluye `userId`, `creatorId`, `creator`, `user`,
`participants`, `participantIds`, `splits`, `divisions`, `shares`, `debts`, `friends` o
`groups`, el backend responde `400` y no persiste nada (FR-005/FR-006).

### Respuesta 201 (gasto creado)

```json
{
  "id": 42,
  "amount": "10.10",
  "currency": "ARS",
  "date": "2026-10-21",
  "category": "FOOD",
  "type": "ONE_TIME",
  "paymentMethod": "CASH",
  "description": "Almuerzo",
  "createdAt": "2026-10-21T18:03:11.482-03:00"
}
```

`amount` viaja como string decimal con 2 decimales (SC-004): ni el backend ni el cliente
involucran punto flotante. `description` se omite cuando es ausente (política `NON_NULL` ya
usada en `UserAccountResponse`).

### Respuesta 200 (replay idempotente)

Si ya existe un gasto del mismo usuario con la misma `Idempotency-Key` **y el payload
normalizado coincide**, el backend **no inserta de nuevo** y responde `200` con la **misma
representación** del gasto ya persistido (FR-027/SC-003). Para comparar, el payload entrante se
valida y normaliza igual que en la creación; un payload inválido sigue respondiendo `400`. El cliente trata `200` y
`201` como éxito.

Se comparan los campos normalizados que el gasto persistido ya conserva
(`amount`, `currency`, `expense_date`, `category`, `expense_type`, `payment_method`,
`description`); no se requiere una columna extra. La comparación de `amount` usa `compareTo` y
la de `description` usa el `null` canónico.

### Respuesta 422 (clave reutilizada con payload distinto)

Si existe un gasto con la misma `Idempotency-Key` pero el payload normalizado **difiere**, la
solicitud se rechaza con `422 Unprocessable Content` (convención del borrador IETF de
`Idempotency-Key`); **no se persiste** un segundo gasto ni se aplica el nuevo payload, y el
gasto original queda intacto. Caso típico: un timeout con persistencia seguido de una edición
del monto.

El mensaje es claro y accionable (p. ej. `detail`: "La operación ya fue registrada con datos
distintos. Reintentá el guardado."), sin exponer internos (SC-006). El frontend, ante `422`,
**renueva la clave** e informa al usuario para que confirme de nuevo (ver contrato del
frontend), de modo que el usuario nunca quede atascado.

Formato: `application/problem+json`; puede incluir `properties.code = "IDEMPOTENCY_KEY_REUSED"`.

### Respuestas de fallo

| Situación | Código | `title` | `detail` / `properties` |
|---|---|---|---|
| Campos inválidos (monto, fecha, catálogos, descripción) | `400` | Validación fallida | mensajes por campo en `properties.fields` (español) |
| `Idempotency-Key` ausente o inválida | `400` | Validación fallida | mensaje claro; sin detalle interno |
| Propiedad reservada (creador/participantes/divisiones/…) | `400` | Validación fallida | campo ofensor en `properties.fields` (FR-005/FR-006) |
| JSON malformado | `400` | Validación fallida | mensaje genérico (nuevo handler; FR-026) |
| Misma `Idempotency-Key` con payload distinto | `422` | Operación ya registrada | mensaje accionable; `properties.code` si aplica; sin datos sensibles |
| Sin cookie / cookie inválida o expirada | `401` | No autorizado | "Necesitas iniciar sesión para continuar"; el gate redirige al login de 004 |
| Cuenta no verificada | `401` | No autorizado | bloqueo del ámbito `verifiedAccount` (coherente con `/api/users/**`) |
| `Content-Type` no soportado | `415` | — | respuesta estándar de Spring |
| Error interno inesperado | `500` | Error interno | mensaje genérico; **ninguna escritura parcial** (transacción) |

Ningún error expone trazas, nombres de clases/paquetes, SQL, identificadores internos, tokens,
cookies ni datos de otros usuarios (FR-026/SC-006).

### Semántica de idempotencia (resumen operativo)

| Estado de `(user_id, Idempotency-Key)` | Payload entrante | Resultado |
|---|---|---|
| No existe | cualquiera válido | `201`, se persiste |
| Existe | igual al persistido (normalizado) | `200` con el gasto original |
| Existe | distinto del persistido | `422`, no se persiste |
| En curso (otra solicitud sin confirmar) | igual | espera a que termine el ganador y responde `200` |
| En curso (otra solicitud sin confirmar) | distinto | espera al ganador y responde `422` |

Reglas:

1. El frontend genera la clave con `crypto.randomUUID()` al primer confirmar.
2. Un reintento por timeout o fallo de red reutiliza la misma clave.
3. Tras un guardado exitoso (y el reseteo de FR-032), la clave se renueva.
4. Dos gastos idénticos en operaciones distintas usan claves distintas ⇒ dos gastos.
5. Carrera de dos solicitudes simultáneas con la misma clave: PostgreSQL serializa por el
   índice único; la perdedora espera al ganador, relee y resuelve por payload (`200` si
   coincide, `422` si difiere). El índice único es el punto de serialización, por lo que **no
   se usa `409`** (ver research.md §D1).

## Contrato del frontend con este endpoint

- **Ruta**: `/expenses/new`, protegida con `ProtectedRoute` (FR-001/FR-002). Sin sesión ⇒
  redirección a `/login` con retorno al destino intentado (comportamiento existente de 004).
- **Precarga**:
  - Fecha: día actual en la zona del dispositivo vía `Intl` (FR-009); se actualiza al cruzar la
    medianoche solo si el usuario no la modificó.
  - Moneda: `me()` (reuso de 001/004). Orden fijo ARS → USD → EUR; ausente/vacía/inválida ⇒
    ARS; perfil no consultable ⇒ ARS + aviso no bloqueante (FR-011).
  - Categoría, tipo y medio de pago: sin selección (FR-032).
- **Guardar** (US1): `POST /api/expenses` con la clave del intento. Éxito (`201`/`200`) ⇒
  notificación de éxito que se cierra a los 5 s o antes si el usuario la cierra, reseteo exacto
  de FR-032 y permanencia en la pantalla (sin redirección ni listado). `400` ⇒ mensajes por
  campo en `properties.fields` sin limpiar el formulario. `422` ⇒ la clave se **renueva** y se
  informa que la operación anterior ya quedó registrada con datos distintos; el usuario vuelve
  a confirmar (sin quedar atascado) y sus datos se conservan. `401` ⇒ `ProtectedRoute`.
  Red/`5xx` ⇒ mensaje fijo, sin notificación de éxito, datos conservados (FR-029/FR-030).
- **Estado de envío** (FR-027): botón deshabilitado con texto "Guardando…" o indicador
  equivalente; el formulario permanece visible; los controles no permiten un segundo envío.
- **Sesión expirada** (FR-028): los datos se conservan solo mientras el formulario sigue
  visible; sin borradores ni restauración posterior al reautenticarse.
- **Accesibilidad** (FR-033): operable con teclado, etiquetas asociadas, foco visible y errores
  asociados al campo (`aria-invalid` + `aria-describedby`, patrón de `TextField`).
- **Móvil** (FR-034): sin desplazamiento horizontal.
