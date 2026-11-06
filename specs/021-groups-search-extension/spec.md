# Feature Specification: Extensión de grupos y buscador de usuarios

**Feature Branch**: `fix/ui-grupos`

**Created**: 2026-11-06

**Status**: Implemented (retroactiva)

**Input**: User description: "Eliminar la foto del grupo y volver al default. Configuración 'Pueden editar datos del grupo': los miembros pueden editar foto, nombre y descripción sin ser administradores; los administradores pueden por default. Botón Editar con ícono de lápiz, visible para administradores y, si el checkbox está activo, para miembros; permite subir una foto nueva, eliminarla, cambiar el nombre y la descripción, y guardar o cancelar. El perfil simulado lleva la etiqueta Simulado además de Miembro. Si Ana le envía una solicitud a Bruno y Bruno la busca en el buscador, debe decir Pendiente y no Agregar."

## Relación con otras especificaciones

Esta spec documenta cambios ya implementados en el commit `551a2f0` y la limpieza posterior de la casilla reemplazada (`V16`). Sigue la costumbre del repo (018 §Constitution Check): las specs anteriores no se editan y sus extensiones se documentan acá. **Donde esta spec y una anterior difieran, vale esta.**

| Spec | Punto | Qué decía | Qué vale ahora |
|---|---|---|---|
| 008 | FR-007, FR-014, entidad "Resultado de Búsqueda" | Cada resultado tiene únicamente nombre, apellido, correo y avatar por defecto | Además incluye el estado de la relación con quien busca (FR-001) |
| 008 | `contracts/users-search-api.md` | Sin estado de relación | Suma `friendshipStatus` (cambio aditivo) |
| 011 | Edge case "Recarga de la pantalla de búsqueda" | Tras recargar, "Agregar" vuelve a mostrarse | La fila conserva "Solicitud enviada" (FR-002) |
| 011 | Assumptions, `research.md`, `plan.md` | Extender el contrato de búsqueda queda fuera de alcance | Extendido por esta spec |
| 010 | FR-005 y US2, casilla "no pueden editar ajustes del grupo" | Casilla negativa: por defecto los miembros podían editar | Eliminada y reemplazada por "Pueden editar datos del grupo": por defecto no pueden (FR-006 a FR-008, FR-019) |
| 010 | US8 | La foto se carga o reemplaza; la gestiona un administrador | También se elimina, y un miembro puede gestionarla si la casilla está activa (FR-009, FR-010) |
| 010 | US6 | Fila de miembro con nombre, avatar y rol | Los perfiles simulados suman la etiqueta "Simulado" (FR-017) |

No modifica 012, 013 ni 020. Los identificadores FR-### y SC-### de esta especificación son propios de 021.

## Clarifications

### Session 2026-11-06

- Q: ¿Qué ve quien recibió una solicitud? → A: "Pendiente", deshabilitado. Acepta o rechaza desde "Solicitudes"; el buscador no ofrece esas acciones.
- Q: ¿Qué ve quien envió la solicitud? → A: "Solicitud enviada", deshabilitado, también después de recargar.
- Q: ¿Qué ve si ya son amigos? → A: "Amigos", deshabilitado.
- Q: ¿Qué cubre "datos del grupo"? → A: Foto, nombre y descripción. Los permisos no: siguen siendo solo de administradores.
- Q: ¿Se aplican los cambios al instante? → A: No. Se aplican todos juntos al tocar "Guardar cambios"; "Cancelar" los descarta.
- Q: ¿Qué pasa con la casilla "No podrán editar ajustes del grupo"? → A: Se elimina por completo: de la pantalla, de la API y de la base (`V16`).

## Fuera de alcance

- Aceptar o rechazar solicitudes desde el buscador.
- Registrar en el historial los cambios de nombre y descripción.
- Guardar datos y foto en una única operación atómica.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El buscador muestra la relación real (Priority: P1) 🎯 MVP

Quien busca a otra persona ve en qué estado está la relación con ella, sin ofrecerle "Agregar" cuando no corresponde.

**Why this priority**: Es el error reportado: Bruno veía "Agregar" teniendo una solicitud de Ana.

**Independent Test**: Ana le envía una solicitud a Bruno. Bruno busca a Ana y ve "Pendiente" deshabilitado. Ana recarga, busca a Bruno y ve "Solicitud enviada".

**Acceptance Scenarios**:

1. **Given** Ana le envió una solicitud a Bruno, **When** Bruno la busca, **Then** ve "Pendiente" deshabilitado y no ve "Agregar".
2. **Given** Ana le envió una solicitud a Bruno, **When** Ana recarga y lo busca, **Then** ve "Solicitud enviada" deshabilitado.
3. **Given** son amigos, **When** cualquiera busca al otro, **Then** ve "Amigos" deshabilitado.
4. **Given** no tienen relación, **When** se buscan, **Then** ven "Agregar" habilitado.
5. **Given** una solicitud rechazada o una amistad eliminada, **When** se buscan, **Then** ven "Agregar" habilitado.

---

### User Story 2 - Editar los datos del grupo (Priority: P1)

Un administrador, o un miembro si la casilla está activa, toca "Editar" y cambia foto, nombre y descripción, guardando o cancelando todo junto.

**Why this priority**: Antes no había ninguna pantalla para editar nombre y descripción.

**Independent Test**: Como administrador, tocar "Editar", cambiar nombre y descripción, guardar y ver los datos nuevos. Repetir como miembro con la casilla activa y comprobar que sin ella no ve el botón.

**Acceptance Scenarios**:

1. **Given** soy administrador, **When** abro el grupo, **Then** veo "Editar" con el ícono de lápiz.
2. **Given** soy miembro y la casilla está activa, **Then** veo "Editar"; **Given** está apagada, **Then** no lo veo.
3. **Given** el modo edición, **Then** puedo subir una foto, eliminarla, cambiar nombre y descripción, y "Guardar cambios" o "Cancelar".
4. **Given** hice cambios, **When** toco "Cancelar", **Then** se descartan sin llamar a la API.
5. **Given** dejo el nombre vacío, **When** guardo, **Then** veo "El nombre del grupo es obligatorio" y no se guarda nada.
6. **Given** guardo, **Then** veo "Los datos del grupo se actualizaron" y los datos nuevos.
7. **Given** soy miembro sin la casilla, **When** llamo a la API, **Then** recibo 403 y no cambia nada.
8. **Given** soy miembro con la casilla, **When** intento cambiar permisos por API, **Then** recibo 403.

---

### User Story 3 - Eliminar la foto del grupo (Priority: P2)

En el modo edición se puede quitar la foto y el grupo vuelve al avatar por defecto.

**Why this priority**: Hasta ahora una foto solo podía reemplazarse.

**Independent Test**: En un grupo con foto, tocar "Editar", "Eliminar foto" y "Guardar cambios", y ver el avatar por defecto.

**Acceptance Scenarios**:

1. **Given** el grupo tiene foto, **When** entro a editar, **Then** veo "Eliminar foto"; **Given** no tiene, **Then** no la veo.
2. **Given** toco "Eliminar foto", **Then** veo el avatar por defecto, pero nada se borra hasta guardar.
3. **Given** guardo, **Then** el grupo queda sin foto y se registra `PHOTO_CHANGED`.
4. **Given** la foto ya no existe, **When** repito la eliminación por API, **Then** responde 204 sin registrar otro evento.

---

### User Story 4 - Etiqueta "Simulado" (Priority: P3)

En la lista de miembros, los perfiles simulados se distinguen de las cuentas reales.

**Why this priority**: Es una mejora de lectura, sin efecto en las reglas.

**Independent Test**: En un grupo con un perfil simulado, ver las etiquetas "Simulado" y "Miembro" en su fila.

**Acceptance Scenarios**:

1. **Given** un perfil simulado, **Then** su fila muestra "Simulado" y "Miembro".
2. **Given** un miembro registrado, **Then** su fila no muestra "Simulado".

---

### Edge Cases

- **La casilla se apaga con el formulario abierto**: el servidor rechaza al guardar y el formulario muestra el motivo.
- **Falla parcial al guardar**: se guardan primero nombre y descripción, y después la foto. Si falla la foto, los datos ya quedaron guardados y el formulario muestra el error (FR-015).
- **Archivo inválido**: el cliente rechaza lo que no sea JPG o PNG y conserva la foto mostrada.
- **Filas con más de una solicitud**: si hay amistad y otra solicitud entre las mismas personas, prevalece "Amigos".

## Requirements *(mandatory)*

### Functional Requirements

**Buscador**

- **FR-001**: Cada resultado de `GET /api/users/search` MUST incluir `friendshipStatus`: `NONE`, `PENDING_SENT`, `PENDING_RECEIVED` o `FRIENDS`, calculado respecto de la persona de la sesión, en cualquier dirección.
- **FR-002**: La pantalla MUST mostrar "Agregar" (habilitado) para `NONE`, "Solicitud enviada" para `PENDING_SENT`, "Pendiente" para `PENDING_RECEIVED` y "Amigos" para `FRIENDS`; estos tres últimos deshabilitados.
- **FR-003**: Si hay una amistad, el estado MUST ser `FRIENDS` aunque exista otra solicitud entre las mismas personas.
- **FR-004**: El estado MUST obtenerse con una sola consulta por búsqueda, no una por resultado.
- **FR-005**: Las solicitudes rechazadas MUST NOT afectar el estado: el resultado es `NONE`.

**Permiso**

- **FR-006**: El grupo MUST tener la casilla "Pueden editar datos del grupo" (`membersCanEditGroupData`), desactivada por defecto.
- **FR-007**: Editar foto, nombre o descripción MUST estar permitido a los administradores siempre y a los miembros solo con la casilla activa.
- **FR-008**: Cambiar permisos MUST ser exclusivo de administradores, sin casilla. La casilla "No podrán editar ajustes del grupo" MUST NOT existir.

**Foto**

- **FR-009**: `DELETE /api/groups/{id}/photo` MUST eliminar la foto y dejar `groups.photo` nulo, con 204. Si no había foto, MUST responder 204 sin cambios ni evento.
- **FR-010**: Eliminar una foto existente MUST registrar `PHOTO_CHANGED`, igual que cargarla.

**Pantalla de edición**

- **FR-011**: "Editar", con ícono de lápiz, MUST mostrarse solo a quien pueda editar los datos y MUST ocultarse durante la edición.
- **FR-012**: Fuera del modo edición, la foto MUST ser de solo lectura para todos.
- **FR-013**: Los cambios MUST quedar pendientes hasta "Guardar cambios"; "Cancelar" MUST descartarlos sin llamar a la API.
- **FR-014**: El formulario MUST enviar solo los campos que cambiaron, validar el nombre obligatorio y el formato JPG o PNG, y mostrar "Los datos del grupo se actualizaron" al terminar.
- **FR-015**: Ante un error, el formulario MUST seguir abierto y mostrar el motivo. Los pasos ya completados pueden quedar guardados.
- **FR-016**: Los mensajes de rechazo del servidor MUST ser los del [contrato](./contracts/groups-search-api.md).

**Miembros**

- **FR-017**: Cada perfil simulado MUST mostrar la etiqueta "Simulado" además de "Miembro".

**Esquema**

- **FR-018**: La migración `V15` MUST agregar `members_can_edit_group_data` con `DEFAULT FALSE`. Una migración aplicada no se edita.
- **FR-019**: La migración `V16` MUST eliminar la columna `members_cannot_edit_settings`. `membersCannotEditSettings` MUST NOT figurar en la API: las respuestas no lo incluyen y, si un cliente lo envía, se ignora.

### Key Entities

- **Resultado de Búsqueda**: suma `friendshipStatus`.
- **Permisos del grupo**: suman `membersCanEditGroupData` y pierden `membersCannotEditSettings`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Quien tiene una solicitud pendiente, en cualquier dirección, nunca ve "Agregar" para esa persona, ni siquiera tras recargar.
- **SC-002**: Sin la casilla, un miembro no puede cambiar foto, nombre ni descripción, ni por pantalla ni por API.
- **SC-003**: Con la casilla, un miembro no puede cambiar permisos.
- **SC-004**: "Cancelar" nunca genera solicitudes de escritura.
- **SC-005**: Todo perfil simulado se distingue de un miembro registrado en la lista.

## Assumptions

- **Cambio de comportamiento en grupos existentes**: antes un miembro podía editar por API, salvo que la casilla vieja lo prohibiera. Con esta spec, nadie que no sea administrador puede editar hasta que un administrador active la casilla nueva, y la configuración vieja no se migra. `V16` borra los valores guardados de la casilla vieja: no se pueden recuperar.
- Antes un miembro con permiso de ajustes también podía cambiar los permisos del grupo. Eso se elimina a propósito.
- Los cambios de nombre y descripción no generan eventos en el historial, igual que antes.
