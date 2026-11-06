# Feature Specification: Abandonar grupo

**Feature Branch**: `fix/ui-grupos`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "Al presionar Abandonar grupo se abre un modal. Si hay otros usuarios además de mí y soy solo miembro, el modal pregunta si estoy seguro de abandonar el grupo. Si hay otros usuarios además de mí y soy el único admin, el modal dice «Sos el último administrador: asigná otro administrador antes de salir» y solo muestra la equis para cerrarlo, sin botones. Si no hay más usuarios además de mí, el modal dice «Sos el último miembro: si confirmas, el grupo se eliminará». Al abandonar un grupo no se eliminan los registros del grupo de la base de datos, pero no se le muestra más al usuario entre sus grupos."

## Relación con otras especificaciones

- **Refina la historia US5 de 010 (Gestión de grupos)**: se mantienen la confirmación, el bloqueo por saldos pendientes, la baja lógica con `left_at`, el evento `MEMBER_LEFT`, la conservación de los gastos y la navegación al listado.
- **Una excepción acotada a 010**: 010 bloquea siempre al último administrador. 020 aplica ese bloqueo solo si quedan otros miembros registrados activos: si no queda nadie, no hay a quién asignarle el rol. Es la única regla de 010 que cambia.
- **No introduce disolución**: 010 dejó la disolución de grupos fuera de esta versión y 020 lo respeta. El grupo no se borra: queda en la base sin miembros activos. "Se eliminará" es lo que percibe la persona, no una operación sobre los datos.
- **Reutiliza `POST /api/groups/{id}/leave` (010)** y `DebtPort` / `LedgerDebtPort` (010, 017): los saldos pendientes siguen bloqueando la salida. Un pago que salda la deuda (018) la desbloquea.
- **No cambia el esquema**: sin migraciones.
- **No modifica 014, 017 ni 018**: gastos, deudas, pagos e historial del grupo se conservan intactos.

Los identificadores FR-### y SC-### de esta especificación son propios de 020.

## Clarifications

### Session 2026-11-06

- Q: ¿Se borran registros del grupo cuando se va el último miembro? → A: No. Ningún registro se borra: el grupo, sus gastos, deudas, pagos, perfiles simulados, permisos, foto e historial quedan en la base. Reemplaza el borrado físico que se había planteado en un primer borrador.
- Q: ¿Qué ve la persona después? → A: El grupo deja de figurar entre sus grupos y deja de ser accesible, como en cualquier baja de 010.
- Q: ¿Qué cuenta como "otros usuarios"? → A: Los miembros registrados activos (`left_at` nulo). Los perfiles simulados no cuentan.
- Q: ¿Qué modal ve un administrador que no es el único? → A: El de confirmación, igual que un miembro.
- Q: ¿Los saldos pendientes bloquean al último miembro? → A: Sí, sin cambios.
- Q: ¿Se puede volver a un grupo sin miembros activos? → A: No. Solo un miembro activo puede agregar personas, y ya no hay ninguno.

## Fuera de alcance

- Borrar físicamente registros de un grupo.
- Disolver o eliminar un grupo sin abandonarlo.
- Reactivar o recuperar un grupo sin miembros activos.
- Limpiar periódicamente los grupos sin miembros activos.
- Cambiar las reglas de quitar miembros (010), de deudas (017) o de pagos (018).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Abandonar el grupo con otras personas (Priority: P1) 🎯 MVP

Una persona que no es la única administradora y no tiene saldos pendientes pulsa "Abandonar grupo". Se abre un modal que pregunta si está segura. Si confirma, deja de pertenecer al grupo, se muestra "Se abandonó el grupo" y vuelve a "Mis grupos". El grupo sigue igual para las demás personas.

**Why this priority**: Es el caso más frecuente y la base del resto.

**Independent Test**: Con un grupo de dos miembros registrados, entrar como el que no es administrador, pulsar "Abandonar grupo", comprobar el modal, confirmar y verificar que el grupo desaparece de "Mis grupos" de esa persona y sigue visible para la otra.

**Acceptance Scenarios**:

1. **Given** hay otro miembro registrado activo y soy solo miembro, **When** pulso "Abandonar grupo", **Then** se abre un modal que pregunta "¿Seguro que querés abandonar el grupo?" con "Confirmar", "Cancelar" y la equis.
2. **Given** el modal abierto, **When** pulso "Cancelar" o la equis, **Then** el modal se cierra y no cambia nada.
3. **Given** el modal abierto, **When** pulso "Confirmar", **Then** dejo de ser miembro activo, se muestra "Se abandonó el grupo" y voy a `/groups` sin poder volver al grupo con "atrás".
4. **Given** soy administrador y hay otro administrador activo, **When** pulso "Abandonar grupo", **Then** veo el mismo modal de confirmación.
5. **Given** tengo saldos pendientes, **When** confirmo, **Then** el modal muestra el motivo del servidor y sigo siendo miembro.
6. **Given** abandoné el grupo, **When** las otras personas lo abren, **Then** el grupo sigue intacto, con `MEMBER_LEFT` en el historial.

---

### User Story 2 - Único administrador con otras personas (Priority: P1)

Una persona que es la única administradora y deja a otros miembros en el grupo pulsa "Abandonar grupo". El modal le pide asignar otro administrador y no ofrece salir: solo la equis para cerrarlo.

**Why this priority**: Evita dejar un grupo con miembros y sin administradores (regla de 010).

**Independent Test**: Con un grupo de dos miembros y un solo administrador, entrar como ese administrador, pulsar "Abandonar grupo" y comprobar el texto exacto, la ausencia de botones y que la equis cierra el modal.

**Acceptance Scenarios**:

1. **Given** hay otro miembro registrado activo y soy el único administrador, **When** pulso "Abandonar grupo", **Then** el modal muestra exactamente "Sos el último administrador: asigná otro administrador antes de salir".
2. **Given** ese modal, **Then** no hay "Confirmar" ni "Cancelar"; solo la equis.
3. **Given** ese modal, **When** pulso la equis o Escape, **Then** se cierra y no cambia nada.
4. **Given** llamo a la API directamente en esa situación, **Then** el servidor responde 403 `LAST_ADMIN` y no cambia nada.
5. **Given** asigno otro administrador, **When** vuelvo a pulsar "Abandonar grupo", **Then** veo el modal de confirmación.
6. **Given** soy el único administrador, **When** abro la pantalla del grupo sin pulsar "Abandonar grupo", **Then** el aviso no aparece en ningún lado, tampoco arriba del listado de miembros.

---

### User Story 3 - Último miembro (Priority: P1)

Una persona que es la única miembro registrada activa pulsa "Abandonar grupo". El modal dice "Sos el último miembro: si confirmas, el grupo se eliminará". Al confirmar, se le da de baja, se muestra "Se eliminó el grupo" y vuelve a "Mis grupos". El grupo ya no le aparece ni puede abrirlo, pero sus registros siguen en la base.

**Why this priority**: Es el caso que 010 no resolvía: sin esta regla, la persona queda atrapada en el grupo.

**Independent Test**: Con un grupo de una sola persona, abandonar, confirmar y verificar que `/groups` ya no lo lista, que `GET /api/groups/{id}` responde 404 y que en la base siguen las filas del grupo, con `left_at` completo.

**Acceptance Scenarios**:

1. **Given** no hay otros miembros registrados activos, **When** pulso "Abandonar grupo", **Then** el modal muestra exactamente "Sos el último miembro: si confirmas, el grupo se eliminará" con "Confirmar", "Cancelar" y la equis.
2. **Given** soy además el único administrador, **Then** veo este modal y no el de último administrador.
3. **Given** quedo solo con perfiles simulados, **Then** veo este modal.
4. **Given** el modal abierto, **When** pulso "Confirmar", **Then** se completa mi `left_at`, se registra `MEMBER_LEFT`, se muestra "Se eliminó el grupo" y voy a `/groups`.
5. **Given** confirmé, **When** abro "Mis grupos", el panel de grupos recientes o el buscador, **Then** el grupo no aparece.
6. **Given** confirmé, **When** consulto `GET /api/groups/{id}`, **Then** responde el 404 neutral.
7. **Given** confirmé, **When** se inspecciona la base, **Then** el grupo y todos sus registros siguen existiendo sin cambios, salvo mi `left_at` y el evento `MEMBER_LEFT`.
8. **Given** tengo saldos pendientes, **When** confirmo, **Then** el modal muestra el motivo y sigo siendo miembro.

---

### User Story 4 - Modal accesible y sin dobles envíos (Priority: P2)

Los tres modos se comportan como un diálogo: foco al abrir, cierre con Escape y la equis, y no se puede confirmar dos veces.

**Why this priority**: Calidad de la interacción en una acción difícil de revertir.

**Independent Test**: Abrir cada modo, usar el teclado y verificar foco, Escape, doble clic en "Confirmar" y error de red.

**Acceptance Scenarios**:

1. **Given** abro el modal, **Then** el foco queda dentro y tiene `role="dialog"` con nombre accesible.
2. **Given** el modal, **When** pulso Escape, **Then** se cierra, salvo que haya una solicitud en curso.
3. **Given** pulso "Confirmar", **Then** se deshabilita mientras la solicitud está en curso y solo se envía una.
4. **Given** un error de red, **Then** el modal muestra el mensaje de red, no navega y permite reintentar.
5. **Given** una respuesta 401, **Then** se cierra la sesión como en el resto de la aplicación.

---

### Edge Cases

- **El modal quedó desactualizado**: si alguien se fue o entró después de cargar la pantalla, el servidor decide con el estado real y el modal muestra su respuesta (por ejemplo, 403 `LAST_ADMIN`).
- **Los dos últimos miembros confirman a la vez**: ambos quedan dados de baja y el grupo queda sin miembros activos. El resultado para cada persona es correcto y no se pierden datos; solo el mensaje puede decir "Se abandonó el grupo" en lugar de "Se eliminó el grupo".
- **Perfiles simulados con deudas con el último miembro**: cuentan como saldos pendientes y bloquean.
- **Repetir la operación tras abandonar**: responde el 404 neutral.
- **Grupo sin miembros activos**: nadie puede abrirlo ni agregar personas; sus datos solo existen en la base.

## Requirements *(mandatory)*

### Functional Requirements

**Modal (frontend)**

- **FR-001**: El botón "Abandonar grupo" MUST abrir un modal y no ejecutar ninguna acción por sí mismo.
- **FR-002**: Si `isLastMember` e `isLastAdmin` son falsos, el modal MUST preguntar "¿Seguro que querés abandonar el grupo?" y ofrecer "Confirmar" y "Cancelar".
- **FR-003**: Si `isLastAdmin` es verdadero e `isLastMember` falso, el modal MUST mostrar exactamente "Sos el último administrador: asigná otro administrador antes de salir" y MUST NOT mostrar botones de acción; solo la equis.
- **FR-004**: Si `isLastMember` es verdadero, el modal MUST mostrar exactamente "Sos el último miembro: si confirmas, el grupo se eliminará" y ofrecer "Confirmar" y "Cancelar". Este caso MUST tener prioridad sobre FR-003.
- **FR-005**: Todos los modos MUST tener la equis y cerrarse con Escape. Con una solicitud en curso, "Confirmar" MUST estar deshabilitado y el modal MUST NOT cerrarse.
- **FR-006**: Si el servidor rechaza la operación, el modal MUST mostrar el motivo. Un error de red MUST mostrar el mensaje de red y permitir reintentar. Un 401 MUST cerrar la sesión.
- **FR-007**: Al confirmar con éxito, la pantalla MUST mostrar "Se eliminó el grupo" si `groupDeleted` es verdadero, o "Se abandonó el grupo" en otro caso, y navegar a `/groups` reemplazando la entrada del historial.
- **FR-008**: El modo del modal MUST salir de `isLastMember` e `isLastAdmin` del detalle del grupo, no de deducciones sobre `memberCount`.
- **FR-016**: El aviso "Sos el último administrador: asigná otro administrador antes de salir" MUST mostrarse solamente en el modal. MUST NOT aparecer en la pantalla del grupo, incluido el listado de miembros.

**Servidor**

- **FR-009**: `POST /api/groups/{id}/leave` MUST rechazar con `PENDING_DEBTS` cuando la persona tenga deudas sin saldar en el grupo, incluso si es el último miembro (010, 017).
- **FR-010**: Si hay otros miembros registrados activos y la persona es la única administradora activa, el servidor MUST rechazar con `LAST_ADMIN`.
- **FR-011**: Si no aplica FR-009 ni FR-010, el servidor MUST completar `left_at` y registrar `MEMBER_LEFT`, también para el último miembro.
- **FR-012**: La respuesta MUST informar `groupDeleted: true` cuando la persona era la única miembro registrada activa, y `false` en otro caso. Este campo refleja lo que percibe la persona: MUST NOT implicar ningún borrado.
- **FR-013**: La operación MUST NOT borrar ni modificar registros del grupo, salvo `left_at` de la persona y el evento `MEMBER_LEFT`.
- **FR-014**: `GET /api/groups/{id}` MUST informar `isLastAdmin` (la persona es ADMIN y la única administradora activa) e `isLastMember` (la persona es la única miembro registrada activa).
- **FR-015**: El listado de grupos y los endpoints del grupo MUST seguir exigiendo una membresía activa, de modo que el grupo deje de mostrarse y responda el 404 neutral para quien lo abandonó (sin cambios respecto de 010).

### Key Entities

- **Pertenencia (`group_members`)**: siempre baja lógica con `left_at`; la fila se conserva.
- **Grupo sin miembros activos**: un grupo cuyas pertenencias tienen todas `left_at`. Sigue en la base y no es accesible para nadie.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En los tres casos, el modal muestra el texto y las acciones especificadas, verificado por pruebas de componente.
- **SC-002**: En el caso de único administrador con otros miembros, el modal no contiene ningún botón de acción, solo la equis.
- **SC-003**: Tras abandonar, el grupo no aparece en ningún listado de la persona y no puede abrirlo.
- **SC-004**: Abandonar no borra ninguna fila: la cantidad de filas de las tablas del grupo no cambia, salvo un evento `MEMBER_LEFT` más.
- **SC-005**: Tras confirmar, la persona llega a `/groups` y no puede volver al grupo con "atrás".
- **SC-006**: La pantalla del grupo no muestra el aviso de último administrador fuera del modal.

## Assumptions

- Los perfiles simulados no cuentan como "otros usuarios".
- Un grupo sin miembros activos permanece en la base de forma indefinida.
- El texto "se eliminará" y el campo `groupDeleted` describen lo que percibe la persona; ya están publicados y se mantienen.
