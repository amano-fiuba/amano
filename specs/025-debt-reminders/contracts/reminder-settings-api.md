# Contrato REST: configuración de recordatorios del grupo

**Feature**: `025-debt-reminders` | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

Endpoints aditivos. No cambian los contratos existentes de grupos, gastos, deudas ni pagos.

## `GET /api/groups/{id}/reminder-settings`

- **Autenticación**: cookie `amano_auth`.
- **Acceso**: solo un `ADMIN` activo del grupo. Un miembro no administrador recibe 403; un no miembro o un grupo inexistente recibe 404 neutral.
- **Descripción**: devuelve la configuración vigente de recordatorios del grupo.

### Respuesta 200

```json
{
  "sendDays": 5,
  "message": null,
  "defaultSendDays": 5,
  "allowedSendDays": [7, 5, 3, 1]
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `sendDays` | `int` | Días antes del vencimiento en que se envían los recordatorios. |
| `message` | `string` o `null` | Mensaje personalizado; `null` significa que se usa el predeterminado. |
| `defaultSendDays` | `int` | Valor predeterminado (5), informativo. |
| `allowedSendDays` | `int[]` | Lista fija de opciones válidas, en orden descendente. |

### Respuesta 401

Sin sesión: `{"detail":"Debés iniciar sesión para continuar"}`.

### Respuesta 403

```json
{
  "type": "about:blank",
  "title": "Operación no permitida",
  "status": 403,
  "detail": "Solo un administrador puede ver la configuración de recordatorios"
}
```

### Respuesta 404

```json
{
  "type": "about:blank",
  "title": "Grupo no encontrado",
  "status": 404,
  "detail": "No existe el grupo solicitado o no sos miembro"
}
```

## `PUT /api/groups/{id}/reminder-settings`

- **Autenticación**: cookie `amano_auth`.
- **Acceso**: solo un `ADMIN` activo del grupo; mismas respuestas 401/403/404 que el `GET`.
- **Descripción**: actualiza el tiempo de envío y/o el mensaje. Es una actualización parcial: los campos ausentes conservan su valor. Un `message` en blanco borra el mensaje personalizado y vuelve al predeterminado.

### Request

```json
{
  "sendDays": 7,
  "message": "¡Hola! Te recuerdo que falta poco para el vencimiento."
}
```

| Campo | Tipo | Obligatorio | Reglas |
|-------|------|-------------|--------|
| `sendDays` | `int` | No | Si viene, debe ser `7`, `5`, `3` o `1`. |
| `message` | `string` o `null` | No | Se recorta; vacío o `null` → sin mensaje; máximo 500 caracteres. |

Un cuerpo vacío `{}` es válido y no cambia nada.

### Respuesta 200

Misma forma que el `GET`, con los valores ya guardados.

```json
{
  "sendDays": 7,
  "message": "¡Hola! Te recuerdo que falta poco para el vencimiento.",
  "defaultSendDays": 5,
  "allowedSendDays": [7, 5, 3, 1]
}
```

### Respuesta 400 — validación

```json
{
  "type": "about:blank",
  "title": "Validación fallida",
  "status": 400,
  "detail": "La solicitud contiene campos inválidos",
  "properties": {
    "fields": {
      "sendDays": "El tiempo de envío debe ser 7, 5, 3 o 1 días antes"
    }
  }
}
```

```json
{
  "type": "about:blank",
  "title": "Validación fallida",
  "status": 400,
  "detail": "La solicitud contiene campos inválidos",
  "properties": {
    "fields": {
      "message": "El mensaje no puede superar los 500 caracteres"
    }
  }
}
```

Si hay varios errores, `fields` trae una entrada por campo. La configuración anterior se conserva.

### Respuesta 403

```json
{
  "type": "about:blank",
  "title": "Operación no permitida",
  "status": 403,
  "detail": "Solo un administrador puede modificar la configuración de recordatorios"
}
```

### Respuesta 404

Igual que el `GET`: mensaje neutral de grupo inexistente o no miembro.

## Efectos secundarios de `PUT`

- Actualiza `groups.reminder_send_days` y/o `groups.reminder_message`.
- Reprograma las filas `SCHEDULED` no intentadas (`claimed_at IS NULL`) del grupo con el nuevo `sendDays`: `send_date = dueDate - sendDays`.
- Si la nueva fecha de envío ya pasó y la fecha límite no, el próximo job envía.
- Si la fecha límite ya pasó, las filas reprogramadas se cancelan en el próximo job.
- Las filas `SENT` y `FAILED` no cambian.
- El cambio de mensaje no modifica envíos ya realizados; los programados usan el mensaje vigente al momento de enviarse.

## Errores transversales

| Situación | Código | Cuerpo |
|-----------|--------|--------|
| Sin sesión | 401 | `{"detail":"Debés iniciar sesión para continuar"}` |
| Miembro no administrador | 403 | ProblemDetail con `detail` explicativo |
| Grupo inexistente o no miembro | 404 | ProblemDetail neutral |
| `sendDays` inválido | 400 | `properties.fields.sendDays` |
| `message` de más de 500 caracteres | 400 | `properties.fields.message` |
| Error inesperado | 500 | ProblemDetail sin detalles internos |
