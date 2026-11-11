# Contrato: Abandonar grupo (020)

Autenticación: sesión verificada (cookie `amano_auth`). Errores en `ProblemDetail` (RFC 7807), como en 010.

## `POST /api/groups/{id}/leave`

Sin cuerpo. Sin cambios de ruta respecto de 010. `groupDeleted` es un campo aditivo.

### 200 — quedan otros miembros

```json
{ "message": "Se abandonó el grupo. Tus gastos previos se conservan.", "groupLeft": true, "groupDeleted": false }
```

### 200 — era el último miembro

```json
{ "message": "Se eliminó el grupo.", "groupLeft": true, "groupDeleted": true }
```

`groupDeleted` indica que el grupo dejó de existir para la persona. No se borra ningún registro.

### Errores

| Código | `code` | Caso | `detail` |
|---|---|---|---|
| 401 | — | Sin sesión | — |
| 403 | `PENDING_DEBTS` | Saldos pendientes | "No podés abandonar el grupo: tenés saldos pendientes con otros miembros" |
| 403 | `LAST_ADMIN` | Único administrador con otros miembros | "Sos el último administrador: asigná otro administrador antes de salir" |
| 404 | — | Grupo inexistente o sin membresía activa | "No existe el grupo solicitado o no sos miembro" |

## `GET /api/groups/{id}` (010, cambio aditivo)

```json
{ "isLastAdmin": false, "isLastMember": true }
```

- `isLastAdmin`: la persona es ADMIN y la única administradora activa. **Nuevo.**
- `isLastMember`: la persona es la única miembro registrada activa. Ya publicado.

## Modos del modal (frontend)

| `isLastMember` | `isLastAdmin` | Texto | Acciones |
|---|---|---|---|
| true | cualquiera | "Sos el último miembro: si confirmas, el grupo se eliminará" | Confirmar, Cancelar, equis |
| false | true | "Sos el último administrador: asigná otro administrador antes de salir" | Solo equis |
| false | false | "¿Seguro que querés abandonar el grupo?" | Confirmar, Cancelar, equis |
