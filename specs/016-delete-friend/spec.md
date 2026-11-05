# Feature Specification: Eliminar amigo

**Feature Branch**: `feat/delete-friend`

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "Eliminar amigo. Como usuario quiero eliminar a un usuario de mi lista de amigos para mantener actualizada mi red de contactos. Criterios de aceptación: el sistema debe permitir seleccionar un amigo para eliminarlo; el sistema debe solicitar confirmación antes de eliminarlo; una vez confirmada la acción, el usuario debe dejar de figurar como amigo; el sistema debe informar que el amigo fue eliminado correctamente; la eliminación de la amistad no debe eliminar el historial de gastos compartidos previamente registrados; si existen deudas pendientes entre ambos usuarios, el sistema debe informar esta situación antes de completar la eliminación. Reglas adicionales: si un usuario elimina a un amigo, se elimina la amistad para ambos; no puede agregarlo directamente a nuevos grupos, pero los grupos previos permanecen; si el amigo tiene deuda directa (gasto compartido sin grupo mediante), no debe permitirse eliminar hasta saldar la deuda."

## Relación con otras especificaciones

- **Extiende 013 (Ver listado de amigos)**: la sección "Mis amigos" de `/friends` gana la acción "Eliminar amigo" en cada tarjeta y un diálogo de confirmación. El listado, los datos mostrados, el orden, el contador y el estado vacío de 013 no cambian.
- **Reutiliza 011 y 012 (solicitudes de amistad)**: la amistad sigue siendo una solicitud en estado `ACCEPTED` de `friendship_requests` (`V12`). Eliminar la amistad es borrar físicamente esa fila; no se crea una tabla de amigos ni una migración nueva.
- **La amistad es simétrica**: como la fila `ACCEPTED` es única por pareja (`uq_friendship_requests_accepted_pair`), borrarla hace que la persona deje de figurar como amiga para ambos usuarios de inmediato.
- **No modifica 010 (Gestión de grupos)**: la pertenencia a grupos (`group_members`) no se toca; los grupos previos permanecen. La regla de "solo se pueden agregar amigos a un grupo nuevo" ya existe y se apoya en `AcceptedFriendshipPort`, que consulta las amistades `ACCEPTED`; al dejar de ser amigos, la persona ya no puede ser agregada a grupos nuevos sin cambiar 010.
- **No modifica gastos ni deudas históricas**: la feature no escribe ni elimina filas de `expenses`, de gastos compartidos ni de deudas. El historial de gastos compartidos previamente registrados se conserva intacto.
- **Depende del futuro módulo de deudas directas**: hoy el repo no tiene gastos compartidos ni deudas. 016 define la regla de bloqueo a través de un puerto `DirectDebtPort` con una implementación stub que informa "sin deuda", igual que `DebtPort` en 010. Cuando exista el módulo real, se conecta el puerto sin cambiar la especificación ni la interfaz.
- **Las deudas de grupo no bloquean**: el bloqueo aplica únicamente a deuda directa (gasto compartido sin grupo). Las deudas contraídas dentro de un grupo no impiden eliminar la amistad y no se alteran por esta operación.
- **Numeración**: `016-delete-friend`. Se asume que `015` queda reservada para la historia de gastos compartidos/deudas directas.

Los identificadores FR-### y SC-### de esta especificación son propios de 016.

## Clarifications

### Session 2026-11-05

- Q: ¿Cómo se representa la eliminación de la amistad? → A: Borrado físico de la fila `ACCEPTED` de `friendship_requests`; no se agrega una tabla ni una migración.
- Q: ¿Qué deudas impiden eliminar? → A: Solo la deuda directa (gasto compartido sin grupo). Las deudas de grupo no bloquean.
- Q: ¿Qué pasa si hay deuda directa? → A: Se informa al intentar abrir la eliminación y no se permite completarla hasta saldarla. El backend también lo valida al confirmar.
- Q: ¿Cómo se informa la deuda directa? → A: Al pulsar "Eliminar amigo" se consulta la elegibilidad; si hay deuda, se muestra un aviso bloqueante y no se abre el diálogo de confirmación.
- Q: ¿Qué código devuelve el bloqueo? → A: `403 Forbidden` con `ProblemDetail` (RFC 7807), coherente con las deudas pendientes de grupos (010).
- Q: ¿Se puede volver a ser amigo después de eliminar? → A: Sí. Cualquiera de los dos puede enviar una nueva solicitud y, si se acepta, la amistad se restablece.
- Q: ¿Qué pasa con otras solicitudes de la pareja? → A: Solo se elimina la fila `ACCEPTED`; las solicitudes `REJECTED` previas se conservan como historial y no impiden un nuevo envío.
- Q: ¿Cómo se confirma? → A: Con un diálogo modal que muestra el nombre del amigo y las acciones "Cancelar" y "Eliminar".
- Q: ¿Desde dónde se elimina? → A: Solo desde la sección "Mis amigos" de `/friends`.
- Q: ¿Qué pasa si se repite la eliminación? → A: `404` neutro; la amistad ya no existe.
- Q: ¿Qué contrato tiene la operación? → A: `DELETE /api/users/me/friends/{friendId}`, con `204` sin cuerpo; y una consulta previa `GET /api/users/me/friends/{friendId}/deletion-eligibility`.
- Q: ¿Cómo se obtiene la deuda antes de abrir el diálogo? → A: Con el endpoint de elegibilidad, que no modifica el contrato de la lista de 013.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Eliminar un amigo con confirmación (Priority: P1) 🎯 MVP

Una persona autenticada y verificada abre "Amigos", ve a uno de sus amigos en "Mis amigos" y pulsa "Eliminar amigo". El sistema consulta que la eliminación sea posible y abre un diálogo con el nombre del amigo y las opciones "Cancelar" y "Eliminar". Al confirmar, la tarjeta desaparece, la persona deja de figurar como amiga para ambos y se muestra "Amigo eliminado correctamente". Si cancela, no cambia nada.

**Why this priority**: Es el valor central de la historia: poder dar de baja una amistad de forma segura y explícita.

**Independent Test**: Con dos cuentas verificadas y una amistad aceptada, abrir "Amigos" como A, pulsar "Eliminar amigo" en la tarjeta de B, confirmar y comprobar que B desaparece de "Mis amigos" de A, que A desaparece de "Mis amigos" de B y que aparece la confirmación exacta.

**Acceptance Scenarios**:

1. **Given** una persona con una amistad aceptada, **When** abre "Amigos", **Then** la tarjeta del amigo muestra la acción "Eliminar amigo".
2. **Given** una tarjeta de amigo, **When** se pulsa "Eliminar amigo", **Then** se consulta la elegibilidad y, si es posible, se abre un diálogo que muestra el nombre del amigo y las acciones "Cancelar" y "Eliminar".
3. **Given** el diálogo abierto, **When** se pulsa "Cancelar", **Then** el diálogo se cierra y la amistad permanece sin cambios.
4. **Given** el diálogo abierto, **When** se confirma "Eliminar", **Then** la tarjeta se retira de "Mis amigos" y se muestra exactamente "Amigo eliminado correctamente".
5. **Given** una eliminación exitosa, **When** la otra persona abre "Amigos", **Then** la persona que eliminó tampoco figura en su lista.
6. **Given** una eliminación exitosa, **When** se consulta la persistencia, **Then** ya no existe una fila `ACCEPTED` entre ambas cuentas.
7. **Given** una persona sin amigos tras eliminar al último, **When** finaliza la operación, **Then** la sección muestra el estado vacío de 013 y la confirmación de éxito permanece visible.

---

### User Story 2 - Deuda directa pendiente (Priority: P1)

Si entre la persona y el amigo existe una deuda directa pendiente (un gasto compartido sin grupo mediante), el sistema lo informa al intentar eliminar y bloquea la operación hasta que la deuda esté saldada. La amistad no se modifica.

**Why this priority**: Eliminar una amistad con deuda directa dejaría una obligación sin un contexto claro para saldarla; la historia exige explícitamente bloquear hasta saldar.

**Independent Test**: Con dos cuentas amigas y una deuda directa pendiente entre ellas, pulsar "Eliminar amigo" y comprobar que se muestra el aviso bloqueante, que no se abre el diálogo y que la amistad sigue existiendo; luego de saldar la deuda, poder eliminar normalmente.

**Acceptance Scenarios**:

1. **Given** una deuda directa pendiente con un amigo, **When** se pulsa "Eliminar amigo", **Then** se muestra el aviso bloqueante con el motivo y no se abre el diálogo de confirmación.
2. **Given** una deuda directa pendiente, **When** se intenta eliminar directamente por API, **Then** el servidor responde `403` con el mismo motivo y la amistad no se modifica.
3. **Given** una deuda directa que fue saldada, **When** se vuelve a intentar eliminar, **Then** la elegibilidad es positiva y la eliminación se puede confirmar.
4. **Given** una deuda contraída dentro de un grupo compartido, **When** se intenta eliminar la amistad, **Then** la deuda de grupo no bloquea la operación.
5. **Given** una deuda directa pendiente, **When** la operación se rechaza, **Then** no se elimina ninguna fila de amistad, gasto ni deuda.

---

### User Story 3 - Efectos en la red, acceso y errores (Priority: P2)

La eliminación solo afecta la amistad actual: los grupos previos y el historial de gastos se conservan, la persona eliminada no puede agregarse a grupos nuevos por no ser amiga, y después ambos pueden volver a ser amigos con una nueva solicitud aceptada. Solo una sesión verificada puede eliminar, y los errores de red, sesión, amistad inexistente y concurrencia se informan sin datos falsos.

**Why this priority**: Completa la experiencia y protege la integridad de la red y del historial; el flujo principal ya entrega el valor de la historia.

**Independent Test**: Eliminar una amistad que comparte un grupo y gastos previos; verificar que el grupo y el historial siguen; intentar agregar a la persona eliminada a un grupo nuevo; volver a enviar y aceptar una solicitud; probar 401, 404, 400 y dos eliminaciones simultáneas.

**Acceptance Scenarios**:

1. **Given** dos amigos que comparten grupos, **When** se elimina la amistad, **Then** ambos siguen siendo miembros de los grupos previos y el historial del grupo no cambia.
2. **Given** una amistad eliminada, **When** se intenta agregar a la persona eliminada a un grupo nuevo, **Then** el sistema aplica la regla existente de 010 y no permite agregarla por no ser amiga.
3. **Given** una amistad eliminada, **When** una de las personas envía una nueva solicitud y la otra la acepta, **Then** vuelven a figurar como amigos.
4. **Given** un intento de eliminar a alguien que no es amigo, **When** se procesa, **Then** se responde `404` con un mensaje neutro y sin cambios de datos.
5. **Given** un identificador de amigo inválido (no numérico o no positivo), **When** se procesa, **Then** se responde `400` sin acceso a datos.
6. **Given** dos eliminaciones simultáneas de la misma amistad, **When** ambas llegan al servidor, **Then** exactamente una tiene éxito y la otra recibe `404`, sin dejar estados inconsistentes.
7. **Given** una petición sin sesión válida o de una cuenta no verificada, **When** se procesa, **Then** se responde `401` y no se modifica ningún dato.
8. **Given** un error de red o del servidor durante la eliminación, **When** finaliza el intento, **Then** se muestra "No se pudo eliminar al amigo. Intentá nuevamente", la tarjeta permanece y no se muestra ninguna confirmación de éxito.
9. **Given** una sesión expirada durante la operación, **When** el servidor responde `401`, **Then** se cierra la sesión local y se redirige al acceso.
10. **Given** una eliminación que responde `404` porque la amistad ya no existe, **When** la interfaz recibe la respuesta, **Then** retira la tarjeta y muestra "No sos amigo de ese usuario".

---

### Edge Cases

- **Cancelar el diálogo**: no se envía ninguna petición ni se modifica la amistad.
- **Doble clic en "Eliminar"**: las acciones del diálogo se deshabilitan mientras la petición está en curso; si aun así llegan dos peticiones, solo una elimina y la otra recibe `404`.
- **Dos pestañas**: una eliminación gana; la otra recibe `404` y retira la tarjeta obsoleta.
- **Amistad eliminada por la otra persona mientras el diálogo está abierto**: al confirmar, el servidor responde `404`; la interfaz retira la tarjeta y no muestra un éxito falso.
- **Deuda directa contraída entre la consulta de elegibilidad y la confirmación**: el `DELETE` responde `403`, el diálogo se cierra y se muestra el aviso de deuda; la amistad no se modifica.
- **Solo solicitudes pendientes o rechazadas**: no hay amistad; la operación responde `404` neutro.
- **Identificador igual al de la sesión**: no existe una amistad consigo mismo; responde `404` neutro.
- **Identificador no numérico o menor o igual a cero**: `400` sin consultar la base.
- **Último amigo eliminado**: se muestra el estado vacío de 013 junto con la confirmación de éxito.
- **Error al cargar la lista de amigos**: la acción de eliminar no está disponible mientras no haya una lista válida.
- **Deudas de grupo**: no bloquean, no se modifican y no se eliminan.
- **Gastos pasados ya saldados**: permanecen en el historial aunque la amistad ya no exista.
- **Re-agregar**: una nueva solicitud entre las mismas cuentas es válida aunque existan filas `REJECTED` históricas.

## Requirements *(mandatory)*

### Functional Requirements

**Eliminación (US1)**

- **FR-001**: La sección "Mis amigos" MUST mostrar, en cada tarjeta de amigo, la acción "Eliminar amigo".
- **FR-002**: La persona MUST identificarse solo por su sesión; el amigo a eliminar MUST salir de la ruta. El endpoint MUST NOT aceptar un id de usuario emisor por parámetro ni permitir eliminar amistades ajenas.
- **FR-003**: Al pulsar "Eliminar amigo", la interfaz MUST consultar primero la elegibilidad de la operación.
- **FR-004**: Si la elegibilidad es positiva, la interfaz MUST abrir un diálogo de confirmación con el nombre del amigo y las acciones "Cancelar" y "Eliminar"; si es negativa por deuda directa, MUST mostrar el aviso bloqueante y MUST NOT abrir el diálogo.
- **FR-005**: "Cancelar" MUST cerrar el diálogo sin enviar la eliminación ni modificar la amistad.
- **FR-006**: Al confirmar, la interfaz MUST deshabilitar las acciones del diálogo y mostrar el estado en curso ("Eliminando…").
- **FR-007**: Una eliminación exitosa MUST borrar físicamente la fila `ACCEPTED` que representa la amistad; ambas personas MUST dejar de figurar como amigas entre sí de inmediato.
- **FR-008**: La interfaz MUST retirar la tarjeta de la lista y mostrar exactamente "Amigo eliminado correctamente".
- **FR-009**: La eliminación MUST afectar únicamente la fila `ACCEPTED` de la pareja; las solicitudes `PENDING` o `REJECTED` entre ambos MUST NOT borrarse.
- **FR-010**: Tras la eliminación, cualquiera de las dos personas MUST poder enviar una nueva solicitud de amistad; si se acepta, la amistad se restablece.

**Deuda directa (US2)**

- **FR-011**: Antes de completar la eliminación, el sistema MUST verificar si la persona autenticada tiene una deuda directa pendiente con el amigo (gasto compartido sin grupo mediante).
- **FR-012**: Si existe deuda directa pendiente, el sistema MUST informar la situación antes de completar la eliminación y MUST NOT eliminar la amistad.
- **FR-013**: El bloqueo MUST exponerse como `403 Forbidden` con un `ProblemDetail` (RFC 7807) y un mensaje comprensible que indique que debe saldar la deuda.
- **FR-014**: Las deudas contraídas dentro de grupos compartidos MUST NOT bloquear la eliminación ni modificarse por ella.
- **FR-015**: Una vez saldada la deuda directa, la eliminación MUST quedar habilitada sin pasos adicionales.
- **FR-016**: La consulta de deuda directa MUST realizarse a través de un puerto de dominio (`DirectDebtPort`) para que el futuro módulo de deudas lo implemente; mientras ese módulo no exista, la implementación stub MUST informar que no hay deuda.

**Efectos y alcance (US1, US3)**

- **FR-017**: La eliminación MUST NOT borrar ni modificar gastos, gastos compartidos, deudas ni el historial de gastos previamente registrados.
- **FR-018**: La eliminación MUST NOT modificar la pertenencia a grupos; los grupos previos y su historial MUST permanecer intactos.
- **FR-019**: Al dejar de ser amigos, la persona eliminada MUST NOT poder agregarse a grupos nuevos, por la regla existente de 010 que exige amistad; esta feature MUST NOT modificar esa regla.
- **FR-020**: La feature MUST NOT borrar la cuenta de ninguna persona ni modificar sus datos de perfil.

**Interfaz, acceso y errores (US3)**

- **FR-021**: La operación MUST requerir una sesión válida de una cuenta verificada y MUST responder `401` en caso contrario; MUST vivir bajo `/api/users/**` para heredar la regla de 003.
- **FR-022**: El sistema MUST exponer una consulta de elegibilidad `GET /api/users/me/friends/{friendId}/deletion-eligibility` que responda si se puede eliminar y, si no, el motivo.
- **FR-023**: El sistema MUST exponer `DELETE /api/users/me/friends/{friendId}`; una eliminación exitosa MUST responder `204` sin cuerpo.
- **FR-024**: Un identificador no numérico o menor o igual a cero MUST producir `400` sin acceso a datos.
- **FR-025**: Una amistad inexistente, ajena o ya eliminada MUST producir `404` con el mensaje neutro "No sos amigo de ese usuario", sin revelar información de otras relaciones.
- **FR-026**: Ante `404`, la interfaz MUST retirar la tarjeta obsoleta y mostrar el mensaje neutro informado por el servidor, sin confirmar un éxito.
- **FR-027**: Ante `403` por deuda directa, la interfaz MUST mostrar el motivo, MUST NOT retirar la tarjeta y MUST NOT mostrar ninguna confirmación de éxito.
- **FR-028**: Ante un error de red o del servidor, la interfaz MUST mostrar exactamente "No se pudo eliminar al amigo. Intentá nuevamente", mantener la tarjeta y permitir reintentar.
- **FR-029**: Ante un `401`, la interfaz MUST cerrar la sesión local y redirigir al acceso, sin dejar datos ni confirmaciones falsas.
- **FR-030**: El sistema MUST garantizar, incluso ante eliminaciones concurrentes, que una amistad se elimine como máximo una vez; las operaciones restantes MUST recibir `404` sin dejar estados inconsistentes.
- **FR-031**: La confirmación de éxito MUST permanecer visible hasta que la persona navegue o realice otra acción, incluso si la lista queda vacía.
- **FR-032**: La feature MUST NOT cambiar el contrato de `GET /api/users/me/friends` de 013 ni los contratos de 008, 010, 011 y 012.
- **FR-033**: Los errores MUST seguir el formato de errores del proyecto (RFC 7807), en español, con voseo y sin detalles internos.

### Key Entities *(include if feature involves data)*

- **Solicitud de amistad** (existente, 011/012): relación dirigida entre dos cuentas con estado (`PENDING`, `ACCEPTED`, `REJECTED`). La amistad es la fila `ACCEPTED`; 016 la elimina físicamente. No se agregan columnas ni tablas.
- **Amigo** (vista existente de 013): la otra cuenta de una amistad aceptada. 016 agrega la acción de eliminarlo, sin cambiar los datos que se muestran.
- **Cuenta de usuario** (existente, solo lectura): la sesión resuelve a la persona que elimina; la cuenta del amigo no se modifica.
- **Deuda directa** (futura, solo lectura): obligación pendiente entre dos personas por un gasto compartido sin grupo. Se consulta a través de `DirectDebtPort`; hoy el stub informa que no existe.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona elimina a un amigo en menos de 2 segundos en condiciones normales de red y recibe la confirmación exacta "Amigo eliminado correctamente".
- **SC-002**: El 100 % de las eliminaciones válidas deja de mostrar a la persona como amiga en las listas de ambos usuarios y elimina la fila `ACCEPTED` correspondiente.
- **SC-003**: El 100 % de los intentos con deuda directa pendiente se bloquea con `403`, sin modificar la amistad ni las deudas.
- **SC-004**: El 0 % de los gastos, deudas, historial de gastos y grupos previos se ve borrado o modificado por una eliminación de amistad.
- **SC-005**: El 0 % de las deudas de grupo bloquea una eliminación de amistad.
- **SC-006**: El 100 % de las peticiones sin sesión o con cuenta no verificada se rechaza con `401`.
- **SC-007**: Ante cualquier cantidad de eliminaciones concurrentes de la misma amistad, exactamente una tiene éxito y las restantes reciben `404` (0 estados inconsistentes).
- **SC-008**: El 100 % de las amistades eliminadas puede restablecerse mediante una nueva solicitud aceptada.
- **SC-009**: Ninguna respuesta ni pantalla confirma un éxito cuando la operación falló por red, sesión, deuda o amistad inexistente.

## Assumptions

- La amistad es la solicitud `ACCEPTED` de 011/012; no se agrega una tabla de amistades ni una migración.
- El borrado físico de la fila `ACCEPTED` es suficiente para que la amistad desaparezca para ambos usuarios; no se requiere historial de la amistad eliminada.
- El módulo de gastos compartidos y deudas directas todavía no existe. Hasta que exista, `DirectDebtPort` se implementa con un stub que informa que no hay deuda, por lo que el bloqueo por deuda directa no se podrá observar de punta a punta con datos reales; la regla y las pruebas unitarias quedan preparadas.
- Las deudas de grupo no se informan en esta entrega porque dependen del futuro módulo de deudas; lo verificable y exigible es que no bloquean ni se modifican (FR-014).
- Los gastos compartidos históricos no referencian la fila de amistad, por lo que borrarla no altera su trazabilidad.
- La regla de 010 que exige amistad para agregar a un grupo nuevo no cambia; los grupos previos conservan a sus miembros.
- No se notifica a la otra persona que fue eliminada; la historia no lo pide.
- La pantalla "Amigos" de 011/012/013 conserva su listado, orden, datos, contador y estado vacío; esta feature solo agrega la acción, el diálogo y sus estados.
- Los textos de la interfaz se escriben en español con voseo.
- No se agregan dependencias nuevas; se reutilizan los mecanismos de sesión, validación, errores y persistencia existentes.
