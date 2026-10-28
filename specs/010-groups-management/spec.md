# Feature Specification: groups-management

**Feature Branch**: `feat/groups-management` (integración a `sprint-n` por PR, según constitución)

**Created**: 2026-10-23

**Last Updated**: 2026-10-27

**Status**: Aprobada — lista para implementación (plan.md completo; artifacts Fase 0/1 y tasks.md alineados)

**Input**: User description: "Módulo 'groups-management': gestión de grupos de gastos compartidos, sus miembros, roles y permisos, dentro de una app web y móvil de gestión de gastos personales y compartidos (herramienta para organizar gastos, acordar quién debe a quién y saldar deudas sin fricción; no es billetera, banco ni red social/chat)."

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crear un nuevo grupo
**Priority**: P1

**Describe this user journey in plain language**: Un usuario crea un nuevo grupo de gastos compartidos, proporcionando un nombre obligatorio y opcionalmente una descripción y foto. El sistema automáticamente lo convierte en Administrador y requiere al menos un miembro adicional para completar la creación. Si falla la creación, no debe quedar ningún grupo o rol a medias.

**Why this priority**: This is the fundamental entry point for group functionality. Without the ability to create groups, no other group operations are possible.

**Independent Test**: Given a registered user, when they create a group with a valid name and at least one additional member, then the group is created with the user as Administrator and the group becomes accessible in their view. Given a registered user, when they attempt to create a group without providing a name, then the creation fails and no group is created. Given a registered user, when they attempt to create a group with no additional members, then the creation fails and no group is created.

### User Story 2 - Configurar permisos del grupo
**Priority**: P1

**Describe this user journey in plain language**: El Administrador del grupo configura las casillas de permisos que determinan qué pueden hacer los Miembros. Las opciones incluyen: si los miembros pueden hacer gastos, invitar gente, editar ajustes del grupo, administrar perfiles simulados, generar gastos en nombre de otras personas, y si los pagos requieren validación.

**Why this priority**: Permissions control the core access model and ensure the group can enforce its desired level of collaboration or restriction.

**Independent Test**: Given a group with an Administrator, when the Administrator toggles a permission setting, then the setting is saved and applied to all group members. Given a member, when they attempt an action restricted by the group's permission settings, then the action is denied with an appropriate message. Given a group with all permission toggles disabled for members, when a member attempts any restricted action, then all such actions are blocked.

### User Story 3 - Agregar miembros al grupo
**Priority**: P1

**Describe this user journey in plain language**: Quien tenga permiso (el Administrador) puede agregar nuevos miembros al grupo, eligiendo entre amigos registrados del usuario o creando un perfil simulado con nombre y correo electrónico. No se puede agregar a quien ya es miembro. El sistema informa el éxito o el motivo del rechazo y registra la acción en el historial del grupo.

**Why this priority**: Managing group composition is essential for the social aspect of shared expense tracking.

**Independent Test**: Given an Administrator and a group, when they add a registered friend who is not already a member, then the friend becomes a Member of the group and the action is recorded in the activity history. Given an Administrator, when they attempt to add a user who is already a member, then the system rejects the action and informs the Administrator of the reason. Given an Administrator, when they create a simulated profile with name and email, then the simulated profile becomes a Member of the group.

### User Story 4 - Eliminar miembros del grupo
**Priority**: P2

**Describe this user journey in plain language**: El Administrador puede eliminar miembros del grupo con confirmación previa. No se permite la eliminación si el miembro tiene deudas pendientes con otros miembros del grupo. Los gastos previos del miembro eliminado se conservan en el historial del grupo. La acción se registra en el historial.

**Why this priority**: While not as critical as creation or permission configuration, member removal is a common operation that must handle edge cases (pending debts) correctly.

**Independent Test**: Given an Administrator and a group member with no pending debts, when the Administrator initiates removal with confirmation, then the member is removed and the action is recorded. Given an Administrator and a group member with pending debts, when the Administrator attempts removal, then the system informs the Administrator that removal is not allowed due to pending debts and the member remains. Given an Administrator, when a member is removed, then their previous expenses remain in the group's history and are accessible.

### User Story 5 - Abandonar el grupo
**Priority**: P2

**Describe this user journey in plain language**: Un usuario puede abandonar un grupo con confirmación. No se permite si el usuario tiene saldo pendiente con otros miembros, y se le informa el motivo. El grupo deja de figurar como activo para él, sus gastos previos se conservan y queda registrado en el historial.

**Why this priority**: Users need a way to leave groups, but this must respect financial obligations between group members.

**Independent Test**: Given a user with no pending balances with other members, when they confirm leaving the group, then they are removed from the group members and the action is recorded. Given a user with pending balances, when they attempt to leave, then the system informs them of the pending balance reason and prevents them from leaving. Given a user who left the group, their previous expenses remain accessible in the group history.

### User Story 6 - Ver listado de miembros activos
**Priority**: P3

**Describe this user journey in plain language**: Se muestra el listado de miembros activos del grupo, con nombre y avatar si el miembro tiene uno. El usuario actual se distingue visualmente del resto. El listado se actualiza automáticamente con altas y bajas y es accesible desde la información del grupo.

**Why this priority**: Users need to see who is in their group and identify themselves in the list.

**Independent Test**: Given a group, when the user views the member list, then all current members are displayed with their names and avatars (if applicable). The current user is visually distinguished (e.g., highlighted or labeled). When a member is added or removed, the list updates to reflect the change.

### User Story 7 - Ver descripción del grupo
**Priority**: P3

**Describe this user journey in plain language**: Se muestra la descripción del grupo si existe, junto con la información principal del grupo. Si el grupo no tiene descripción, se indica que no tiene descripción.

**Why this priority**: The group description provides context about the group's purpose.

**Independent Test**: Given a group with a description, when the user views the group information, then the description is displayed. Given a group without a description, when the user views the group information, then a message indicates the group has no description.

### User Story 8 - Cargar/reemplazar foto del grupo
**Priority**: P3

**Describe this user journey in plain language**: Se puede cargar o reemplazar la foto del grupo, admitiendo solo archivos JPG, JPEG o PNG. Sin foto, se muestra un avatar gris por defecto. Si falla la carga o el archivo es inválido, se informa y se conserva la foto anterior.

**Why this priority**: Visual identification of the group improves the user experience.

**Independent Test**: Given an Administrator, when they upload a valid JPG/PNG file as the group photo, then the photo is displayed and replaces any previous photo. Given an Administrator, when they upload an invalid file type (e.g., GIF, PDF), then the system informs the user of the invalid file type and conserves the previous photo. Given an Administrator, when they upload a valid file but the upload fails, then the system informs the user and conserves the previous photo.

### User Story 9 - Ver historial de actividad del grupo
**Priority**: P1

**Describe this user journey in plain language**: Se muestra el historial de actividad del grupo (todos los miembros), incluyendo altas y bajas de miembros, gastos y pagos. Cada entrada indica quién la hizo y cuándo. El orden es cronológico descendente (más reciente primero). El historial se conserva aunque el elemento relacionado luego se modifique o elimine.

**Why this priority**: The activity history provides transparency and accountability for all group actions.

**Independent Test**: Given a group with activity history, when the user views the history, then entries are shown in descending chronological order, each indicating the actor and timestamp. When a member is added or removed, an entry appears in the history. When a group member leaves or is removed, an entry appears. When a payment or expense is recorded, an entry appears. Even if a related expense or member is later modified or deleted, the history entries remain preserved.

### User Story 10 - Gestión de roles de admin
**Priority**: P2

**Describe this user journey in plain language**: Los Administradores pueden asignar el rol de Administrador a un miembro del grupo (esto no modifica a los administradores existentes). Los Administradores pueden quitar el rol de Administrador a otro admin (este pasa a Miembro y conserva su pertenencia al grupo). Solo los Administradores pueden realizar esta operación, afecta únicamente a ese grupo, y si la operación falla se conserva la asignación anterior. A todo usuario que se incorpora se le asigna automáticamente el rol Miembro.

**Why this priority**: Role management ensures the group can always have an Administrator and that role assignments are flexible yet controlled.

**Independent Test**: Given an Administrator and a group member, when the Administrator assigns the Admin role to that member, then the member becomes an Administrator and no existing Administrators are affected. Given an Administrator and another Administrator in the group, when the Administrator removes the Admin role from that user, then that user becomes a Member and retains their group membership. Given an Administrator, when a role assignment operation fails, the previous role assignment is preserved. Given a new user joining the group, they are automatically assigned the Member role.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema debe permitir crear un grupo con nombre obligatorio, descripción y foto opcionales, y al menos un miembro además del creador.
- **FR-002**: El creador de un grupo queda automáticamente como Administrador sin acción adicional.
- **FR-003**: Si la creación de un grupo falla, el sistema no debe dejar ningún grupo ni rol a medias (estado consistente).
- **FR-004**: El Administrador puede configurar permisos de miembros mediante casillas que aplican restricciones específicas.
- **FR-005**: Los permisiones de miembros incluyen: no pueden hacer gastos, no pueden invitar gente, no pueden editar ajustes del grupo, pueden administrar perfiles simulados, pueden generar gastos en nombre de otras personas, y los pagos requieren validación.
- **FR-006**: Para agregar miembros, quien tenga permiso puede elegir entre amigos registrados del usuario o crear un perfil simulado con nombre y correo.
- **FR-007**: No se puede agregar a quien ya es miembro del grupo.
- **FR-008**: Se informa el éxito o el motivo del rechazo al agregar un miembro, y la acción se registra en el historial del grupo.
- **FR-009**: Para eliminar miembros, quien tenga permiso debe confirmar la acción previa.
- **FR-010**: No se permite eliminar un miembro si tiene deudas pendientes con otros miembros del grupo.
- **FR-011**: Los gastos previos del miembro eliminado se conservan en el historial del grupo.
- **FR-012**: La eliminación de miembro se registra en el historial del grupo.
- **FR-013**: Un usuario puede abandonar el grupo con confirmación, salvo que tenga saldo pendiente con otros miembros.
- **FR-014**: Si el usuario tiene saldo pendiente, se le informa el motivo y no se permite abandonar.
- **FR-015**: Al abandonar, el grupo deja de figurar como activo para ese usuario, sus gastos previos se conservan y la acción queda registrada en el historial.
- **FR-016**: El listado de miembros activos muestra nombre y avatar (si lo tiene), y el usuario actual se distingue visualmente del resto.
- **FR-017**: El listado de miembros se actualiza con altas y bajas.
- **FR-018**: La información del grupo es accesible desde la vista de información del grupo.
- **FR-019**: Se muestra la descripción del grupo si existe; si no, se indica que no tiene descripción.
- **FR-020**: La foto del grupo admite solo JPG, JPEG y PNG. Sin foto, se muestra un avatar gris por defecto.
- **FR-021**: Si falla la carga o el archivo es inválido, se informa y se conserva la foto anterior.
- **FR-022**: El historial de actividad muestra todas las entradas (altas, bajas, gastos, pagos) en orden cronológico descendente.
- **FR-023**: Cada entrada del historial indica quién la hizo y cuándo.
- **FR-024**: El historial se conserva aunque el elemento relacionado luego se modifique o elimine.
- **FR-025**: Solo los Administradores pueden asignar el rol Administrador a un miembro.
- **FR-026**: Los Administradores pueden quitar el rol Administrador a otro admin (este pasa a Miembro y conserva su pertenencia).
- **FR-027**: La asignación de rol Administrador no modifica a los administradores existentes.
- **FR-028**: Si la operación de asignación/quitar rol falla, se conserva la asignación anterior.
- **FR-029**: A todo usuario que se incorpora a un grupo se le asigna automáticamente el rol Miembro.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los usuarios pueden crear un grupo exitosamente proporcionando un nombre y al menos un miembro adicional en menos de 2 minutos.
- **SC-002**: Los configuraciones de permisos del grupo se aplican correctamente y los miembros quedan restringidos según la configuración seleccionada.
- **SC-003**: Los Administradores pueden agregar y quitar miembros con información clara de éxito o motivo de rechazo.
- **SC-004**: La eliminación de miembros con deudas pendientes está bloqueada y el usuario es informado del motivo.
- **SC-005**: Los usuarios pueden abandonar el grupo cuando no tienen saldos pendientes; cuando los tienen, se les informa del motivo.
- **SC-006**: El listado de miembros refleja siempre el estado actual y distingue al usuario actual.
- **SC-007**: La descripción del grupo se muestra cuando existe; cuando no, se indica claramente.
- **SC-008**: Las fotos de grupo se cargan correctamente en formatos JPG/JPEG/PNG; los archivos inválidos son rechazados conservando la foto anterior.
- **SC-009**: El historial de actividad se muestra en orden cronológico descendente y conserva entradas incluso cuando elementos relacionados se modifican o eliminan.
- **SC-010**: Los roles de Administrador pueden asignarse y quitarse por otros Administradores, con comportamiento consistente (quitarle el rol a un admin lo convierte en Miembro).

---

## Assumptions

- El usuario que crea un grupo tiene una cuenta registrada en el sistema.
- Los perfiles simulados (invitados) tienen nombre y correo electrónico, pero no una cuenta de usuario completa.
- La aplicación web y móvil comparten la misma lógica de permisos y configuración de grupos.
- Las deudas pendientes se refieren a saldos netos entre el usuario y otros miembros del grupo.
- La foto del grupo se almacena como archivo de imagen y se recupera para su visualización.
- El historial de actividad es un registro separado que no se modifica cuando se alteran los elementos originales (solo se añade nuevo contenido).
- Cada usuario puede pertenecer a múltiples grupos, y el rol es específico por grupo (un usuario puede ser admin en un grupo y miembro en otro).
- La configuración de permisos se aplica de manera global a todos los miembros del grupo (no por individuo a menos que se especifique).
- **Group dissolution**: Los grupos no tienen disolución explícita en esta versión. El grupo persiste mientras tenga al menos un miembro activo y conserva su historial. La salida se resuelve mediante Abandonar grupo y Eliminar miembros. La disolución queda como mejora futura para una versión posterior.
- **Last admin scenario**: Si el último administrador abandona el grupo o se le quita el rol, la operación está bloqueada y el usuario es informado que se necesita al menos un admin activo. Un nuevo admin solo puede asignarse si otro miembro del grupo es promocionado explícitamente al rol de Administrador.
- **Simulated profile permissions**: Los perfiles simulados no pueden abandonar por sí mismos al no tener cuenta de usuario. Solo un administrador puede eliminarlos, o un miembro si la casilla "Miembros pueden administrar perfiles simulados" está activa. Se aplican las mismas reglas de deudas pendientes que a usuarios registrados (confirmación requerida, registro en historial). Los administradores no pueden quitar el rol de admin a otro admin directamente; primero deben quitarle el rol, pasando a estado Miembro.

---

## Key Entities

- **Grupo**: Entidad que agrupa a personas para el seguimiento de gastos compartidos. Tiene nombre, descripción opcional, foto opcional, y configuración de permisos.
- **Usuario registrado**: Persona con cuenta en la aplicación, capaz de crear grupos y ser miembro/Administrador de los mismos.
- **Miembro**: Usuario registrado que pertenece a un grupo con el rol de Miembro (puede ser promovido a Administrador).
- **Administrador**: Usuario con permisos completos sobre el grupo, incluida la configuración de permisos, gestión de miembros y roles.
- **Perfil simulado**: Representación de una persona dentro de un grupo sin cuenta registrada (tiene nombre y correo, pero no acceso completo al sistema).
- **Rol por grupo**: La administración de roles es específica por grupo; un usuario puede ser Administrador en un grupo y Miembro en otro.
- **Historial de actividad**: Registro cronológico de todas las acciones y eventos dentro del grupo (altas, bajas, gastos, pagos).

---


## Clarifications

**Session 2026-10-23**

- Q: What happens if the last administrator abandons the group or se le quita el rol? → A: Se bloquea que el último admin abandone o pierda el rol; debe asignar antes el rol de Administrador a otro miembro.
- Q: ¿Se puede eliminar/disolver un grupo? ¿Quién y en qué condiciones? → A: En esta versión no hay disolución de grupos; el grupo persiste mientras tenga al menos un miembro activo y conserva su historial. La salida se resuelve con Abandonar grupo y Eliminar miembros. La disolución queda como mejora futura.
- Q: Un perfil simulado puede abandonar o ser eliminado con deudas? ¿Puede el admin eliminar a otro admin directamente? → A: Un perfil simulado no puede abandonar por sí mismo (no tiene cuenta). Lo elimina un admin, o un miembro si la casilla "Miembros pueden administrar perfiles simulados" está activa. Aplica la misma regla de deudas pendientes que a usuarios registrados, con confirmación y registro en el historial. Un admin no puede eliminar directamente a otro admin: primero debe quitarle el rol.
- Q: ¿Qué valores por defecto tienen las casillas de permisos al crear un grupo? → A: `spec.md` (FR-005) no las define; decisión acordada (2026-10-27): **todas las casillas en false** (CANNOT/REQUIRE desactivadas), es decir, sin restricciones activas al crear el grupo; el Administrador las activa explícitamente desde la configuración de permisos.

---

## Alcance y limitaciones

### Integración de gastos y pagos (módulo posterior)

La integración de gastos y pagos con grupos queda para un **módulo posterior**. En este módulo de gestión de grupos:

- Las casillas de permisos `Miembros no pueden hacer gastos`, `Miembros pueden generar gastos en nombre de otras personas` y `Los pagos requieren validación` **se crean, se guardan y se aplican como configuración**, pero su comportamiento gatillado (grabar gastos en un grupo, generar gastos en nombre de otros, validar pagos) se implementa cuando exista la integración de gastos por grupo.
- El historial de actividad de este módulo registra **solo altas y bajas de miembros** (más cambios de permisos y de foto). Los tipos de evento `EXPENSE_CREATED` y `PAYMENT_MADE` quedan definidos como **puntos de enganche** sin implementación; los escenarios de historial que los mencionan (US9) se completarán con el módulo de gastos.
- Mientras tanto, el historial muestra únicamente las entradas de altas y bajas de miembros (y de configuración si las hubo).

### Conservación de los gastos

Los **gastos pertenecen a la persona que los registró** (usuario), no al grupo. "Los gastos previos se conservan" (FR-011, FR-015) significa que al eliminar un miembro o al abandonar un grupo **no se elimina ningún gasto**: la eliminación/salida se realiza con baja lógica de la membresía y los gastos del usuario quedan intactos. La asociación gasto–grupo (qué gastos pertenece a qué grupo, y su visibilidad en el historial del grupo) la definirá el módulo de gastos por grupo; en este módulo no se modela esa relación.

### Reglas de deudas pendientes (con stub)

Las reglas de deudas pendientes de FR-010 y FR-014 se implementan contra un **puerto de consulta de deudas** (`DebtPort`, en `com.amano.backend.groups.port`) con un **stub hasta que exista el módulo de deudas**. Mientras exista el stub:

- Las verificaciones de deudas responden siempre "sin deudas pendientes", por lo que FR-010 y FR-014 quedan implementadas en su estructura pero sin efecto observable end-to-end.
- Los casos de deudas pendientes solo son verificables con tests unitarios que reemplazan el stub (mock del puerto).
- Al implementarse el módulo de deudas, se conecta a este puerto sin cambiar las reglas o historias de este módulo.

### Motivos de rechazo documentados (FR-008 y afines)

Cuando una operación se rechaza, el sistema informa el motivo con un mensaje claro en la UI. Los motivos de rechazo que este módulo debe reportar son, exactamente:

1. **Ya es miembro**: la persona a agregar ya pertenece al grupo (FR-007).
2. **Deudas pendientes**: la persona tiene deudas pendientes con otros miembros del grupo (FR-010 y reglas de perfiles simulados).
3. **No es amigo**: la persona a agregar no está en la relación de amistad del usuario (FR-006; solo se puede agregar entre amigos). Este motivo se informa con un **404 neutral** que no confirma si el usuario existe: la respuesta no revela la existencia de la persona en cuestión.
4. **Último admin**: la operación dejaría al grupo sin administrador activo (abandono del último admin o quita de su rol).
5. **Admin: primero quitar el rol**: un administrador no puede eliminar directamente a otro administrador (US10/Clarifications); se exige revocarle el rol antes de la baja.
