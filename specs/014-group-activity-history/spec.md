# Feature Specification: Ver historial de actividad del grupo

**Feature Branch**: `feat/historial-actividad-grupal`

**Created**: 2026-11-03

**Status**: Implemented (pendiente validación manual)

**Input**: User description: "Ver historial de actividad del grupo (Sprint 4, 2 SP, responsable Nahuel). Consultar el registro de altas, bajas, gastos y pagos realizados en el grupo."

## Relación con otras especificaciones

- **Completa la US9 de 010 (Gestión de grupos)**: 010 dejó el endpoint `GET /api/groups/{id}/activity`, la tabla `group_activity` (`V10`) y la pantalla `/groups/:id/history`. La pantalla solo decía "Agregó un miembro" o "Quitó un miembro", sin indicar a quién, y los detalles de los eventos se armaban concatenando texto, lo que generaba JSON inválido con nombres que tuvieran comillas. 014 hace que el registro de **altas y bajas** sea legible y confiable.
- **Deja listo el registro de gastos y pagos**: los gastos y pagos de grupo todavía no existen (`ESTADO_ACTUAL.md` §4.1). 014 **no los crea**: define el formato de los eventos `EXPENSE_CREATED` y `PAYMENT_MADE` (ya admitidos por el CHECK de `V10`), expone un método por cada uno para que el futuro módulo de gastos los registre con una sola llamada, y la pantalla ya sabe mostrarlos y filtrarlos.
- **No modifica** permisos, roles, fotos, amistades (011–013) ni gastos personales (007/009). Los eventos `PERMISSION_CHANGED` y `PHOTO_CHANGED` de 010 se siguen mostrando como hasta ahora.

Los identificadores FR-### y SC-### de esta especificación son propios de 014.

## Clarifications

### Session 2026-11-03

- Q: ¿Qué alcance tiene la tarea? → A: Exactamente lo del cronograma: "Consultar el registro de altas, bajas, gastos y pagos realizados en el grupo". No se agregan eventos de otro tipo (creación del grupo, edición de nombre, cambios de rol).
- Q: ¿Se esperan los gastos de grupo? → A: No. Se deja el enganche listo (formato y método de registro) y se muestran en cuanto existan. Crear gastos o pagos de grupo queda fuera.
- Q: ¿Se necesita una migración? → A: No. Los 7 tipos ya están en el CHECK de `V10`; los detalles son texto JSON.
- Q: ¿La alta del miembro inicial al crear el grupo cuenta como alta? → A: Sí. Hoy no se registra; 014 la registra como `MEMBER_ADDED` marcada como "al crear el grupo".
- Q: ¿Qué pasa con los eventos ya guardados con pocos datos (por ejemplo, una baja con solo el id)? → A: No se modifican (sin `UPDATE`); la pantalla muestra una frase genérica ("quitó a un miembro del grupo").
- Q: ¿Quién ve el historial? → A: Igual que en 010: todo miembro activo del grupo.
- Q: Numeración → A: `014-group-activity-history`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver altas y bajas con quién y a quién (Priority: P1) 🎯 MVP

Un miembro del grupo abre "Ver historial de actividad" y ve, de lo más reciente a lo más antiguo, cada alta y baja escrita como una frase completa: quién la hizo, a quién afectó y cuándo. Las entradas se agrupan por día.

**Why this priority**: Es el núcleo de la tarea; sin saber a quién se agregó o quitó, el historial no sirve como registro.

**Independent Test**: Crear un grupo con un amigo, agregar un perfil simulado, quitarlo y hacer que el amigo abandone; abrir el historial y comprobar las cuatro frases en orden descendente.

**Acceptance Scenarios**:

1. **Given** un grupo recién creado con un amigo, **When** se abre el historial, **Then** aparece "Ana López agregó a Juan Pérez al crear el grupo".
2. **Given** un alta de un amigo registrado, **When** se abre el historial, **Then** aparece "Ana López agregó a Juan Pérez".
3. **Given** un alta de un perfil simulado, **When** se abre el historial, **Then** aparece "Ana López agregó el perfil simulado Abuela".
4. **Given** una baja de un miembro o de un perfil simulado, **When** se abre el historial, **Then** aparece "Ana López quitó a Juan Pérez del grupo" o "Ana López quitó el perfil simulado Abuela".
5. **Given** que un miembro abandonó, **When** se abre el historial, **Then** aparece "Juan Pérez abandonó el grupo".
6. **Given** un perfil simulado con comillas o caracteres especiales en el nombre, **When** se lo agrega y se abre el historial, **Then** el nombre se muestra tal cual.
7. **Given** que el miembro quitado cambia luego su nombre o el perfil simulado ya no existe, **When** se abre el historial, **Then** la entrada sigue mostrando el nombre que tenía al momento del evento.
8. **Given** entradas de días distintos, **When** se abre el historial, **Then** se agrupan bajo un encabezado con la fecha del día y cada una muestra la hora.

---

### User Story 2 - Gastos y pagos en el historial (Priority: P1)

Cuando el módulo de gastos de grupo registre un gasto o un pago, el historial lo muestra como "Ana López registró un gasto de $ 1.500,00 «Súper»" o "Ana López registró un pago de $ 500,00 de Juan Pérez a Ana López".

**Why this priority**: Es la otra mitad de la tarea del cronograma. Como los gastos de grupo no existen, el valor que se entrega es el enganche listo y probado.

**Independent Test**: Registrar un `EXPENSE_CREATED` y un `PAYMENT_MADE` con el método de enganche (prueba de integración) y verificar la respuesta del endpoint; en la interfaz, verificar las frases con datos simulados.

**Acceptance Scenarios**:

1. **Given** un gasto registrado con el método de enganche, **When** se consulta el historial, **Then** la entrada `EXPENSE_CREATED` trae id del gasto, monto, moneda y descripción.
2. **Given** un pago registrado con el método de enganche, **When** se consulta el historial, **Then** la entrada `PAYMENT_MADE` trae id del pago, monto, moneda, quién paga y quién recibe.
3. **Given** una entrada de gasto, **When** se muestra, **Then** la frase incluye el monto con formato de moneda y la descripción si existe.
4. **Given** una entrada de pago, **When** se muestra, **Then** la frase incluye el monto, quién paga y quién recibe.
5. **Given** un intento de registrar un gasto o pago sin monto positivo o sin moneda, **When** se invoca el método, **Then** se rechaza y no se guarda nada.

---

### User Story 3 - Filtrar, paginar y estados de la pantalla (Priority: P2)

El miembro puede filtrar por "Altas", "Bajas", "Gastos", "Pagos" o "Cambios del grupo", recorrer páginas y volver al grupo. La pantalla informa carga, vacío y errores.

**Why this priority**: Facilita consultar un historial largo; el registro en sí ya lo entregan US1 y US2.

**Independent Test**: Con un grupo con actividad variada, cambiar el filtro y la página; simular errores de red, 401 y 404.

**Acceptance Scenarios**:

1. **Given** el filtro "Bajas", **When** se aplica, **Then** solo se ven bajas y salidas, y la paginación vuelve a la página 1.
2. **Given** un filtro sin resultados, **When** se aplica, **Then** se muestra "No hay actividad de este tipo en el grupo".
3. **Given** un grupo sin actividad, **When** se abre el historial sin filtro, **Then** se muestra "Todavía no hay actividad en este grupo".
4. **Given** más de 20 entradas, **When** se abre el historial, **Then** se ven 20, el texto "Página 1 de N" y los botones "Anterior"/"Siguiente" según corresponda.
5. **Given** un tipo de evento inexistente en la consulta, **When** se llama al endpoint, **Then** responde 400 con el campo `eventType`.
6. **Given** un tamaño de página mayor a 100, **When** se llama al endpoint, **Then** devuelve como máximo 100 entradas.
7. **Given** un error de red o del servidor, **When** carga el historial, **Then** se muestra "No se pudo cargar el historial. Intentá nuevamente" con "Reintentar" y sin entradas viejas.
8. **Given** una sesión expirada (401), **When** carga el historial, **Then** se cierra la sesión local.
9. **Given** una persona que no es miembro, **When** abre el historial, **Then** ve "No existe el grupo solicitado o no sos miembro".
10. **Given** la pantalla del historial, **When** se pulsa "Volver al grupo", **Then** se vuelve a `/groups/:id`.

---

### Edge Cases

- **Eventos viejos con pocos datos** (antes de 014): una baja con solo `userId` se muestra "quitó a un miembro del grupo"; un alta con solo `email` se muestra "agregó a {email}".
- **Detalles inválidos guardados antes de 014** (JSON mal formado): la entrada se devuelve con `details: null` y la frase genérica; nunca rompe la respuesta.
- **Actor inexistente**: se muestra "Alguien" en lugar del nombre.
- **Miembro sin apellido**: se muestra solo el nombre, sin espacios sobrantes.
- **Moneda desconocida para el navegador**: se muestra "XYZ 1500" sin romper la frase.
- **Tipo de evento desconocido** (agregado en el futuro): se muestra "realizó una acción en el grupo".
- **Cambio rápido de filtro o página**: la respuesta de una consulta anterior nunca pisa a la más reciente.
- **Volver a agregar a alguien que había sido quitado**: se registra un alta nueva; las entradas anteriores se conservan.

## Requirements *(mandatory)*

### Functional Requirements

**Registro de altas y bajas (US1)**

- **FR-001**: Toda alta de miembro MUST registrar `MEMBER_ADDED` con el tipo de miembro (`REGISTERED` o `SIMULATED`), su id, su nombre visible y su correo al momento del evento.
- **FR-002**: La creación de un grupo MUST registrar `MEMBER_ADDED` para el miembro inicial, con la marca `atCreation: true`, en la misma transacción.
- **FR-003**: Toda baja (de miembro registrado o perfil simulado) MUST registrar `MEMBER_REMOVED` con el tipo, el id, el nombre visible y el correo al momento del evento.
- **FR-004**: Los detalles de cada evento MUST guardarse como JSON válido generado por un serializador, nunca por concatenación de texto.
- **FR-005**: Los eventos MUST registrarse en la misma transacción que el cambio que los produce (comportamiento de 010).

**Gastos y pagos (US2)**

- **FR-006**: El sistema MUST ofrecer un método para registrar `EXPENSE_CREATED` con id del gasto, monto, moneda y descripción opcional.
- **FR-007**: El sistema MUST ofrecer un método para registrar `PAYMENT_MADE` con id del pago, monto, moneda, nombre de quien paga y de quien recibe.
- **FR-008**: Ambos métodos MUST rechazar montos nulos o no positivos y monedas vacías, sin guardar nada.

**Consulta (US1–US3)**

- **FR-009**: `GET /api/groups/{id}/activity` MUST devolver `details` como objeto JSON (o `null` si no hay detalles válidos).
- **FR-010**: El parámetro `eventType` MUST aceptar uno o más tipos separados por coma; un tipo inexistente MUST responder 400 con el campo `eventType`.
- **FR-011**: El tamaño de página MUST limitarse a 100; el valor por defecto sigue siendo 20.
- **FR-012**: El orden MUST seguir siendo `created_at DESC, id DESC` y el acceso, solo para miembros activos (404 neutral en otro caso), como en 010.
- **FR-013**: Los actores de una página MUST resolverse en una sola consulta.

**Pantalla (US1–US3)**

- **FR-014**: Cada entrada MUST mostrarse como una frase con el actor, la acción y el afectado o el monto, según el tipo (ver contrato).
- **FR-015**: Las entradas MUST agruparse por día (zona horaria de Argentina) y mostrar la hora.
- **FR-016**: La pantalla MUST ofrecer el filtro "Mostrar": Toda la actividad, Altas, Bajas (incluye salidas), Gastos, Pagos y Cambios del grupo.
- **FR-017**: La pantalla MUST distinguir carga, lista, vacío (con mensaje según haya filtro o no) y error con "Reintentar"; ante 401 MUST cerrar la sesión local.
- **FR-018**: La pantalla MUST ofrecer "Volver al grupo" y la paginación con "Página X de Y".
- **FR-019**: La pantalla MUST NOT mostrar el aviso fijo "Los gastos y pagos se registrarán cuando exista la integración…".

**Alcance**

- **FR-020**: La feature MUST NOT crear migraciones, modificar filas existentes de `group_activity`, crear gastos o pagos de grupo ni agregar tipos de evento.

### Key Entities *(include if feature involves data)*

- **Evento de actividad** (existente, `group_activity`): tipo, grupo, actor, detalles JSON y fecha. Inmutable.
- **Detalles de alta/baja** (formato nuevo): tipo de miembro, id, nombre y correo del afectado; `atCreation` en la alta inicial.
- **Detalles de gasto** (formato nuevo): id del gasto, monto, moneda, descripción.
- **Detalles de pago** (formato nuevo): id del pago, monto, moneda, quién paga y quién recibe.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las altas y bajas nuevas muestra en la pantalla a quién afectaron.
- **SC-002**: El 100 % de los eventos nuevos guarda JSON válido, incluso con comillas, barras o acentos en los nombres.
- **SC-003**: Un miembro encuentra todas las bajas de un grupo en un solo paso (filtro "Bajas").
- **SC-004**: El futuro módulo de gastos registra un gasto o un pago en el historial con una sola llamada, sin migraciones ni cambios en la pantalla.
- **SC-005**: El historial carga en menos de 2 segundos con 100 entradas por página.

## Assumptions

- La sesión, la cookie `amano_auth` y la regla de miembro activo son las de 004 y 010.
- Los nombres se guardan como "copia" al momento del evento (patrón de preservación de 010).
- El monto se guarda como texto decimal (`"1500.00"`) para no perder precisión.
- La fecha y la hora se muestran en la zona horaria de Argentina.
- `lastActivity` del detalle del grupo sigue en `null` (fuera de alcance).
- Los textos se escriben en español con voseo.
- No se agregan dependencias nuevas.
