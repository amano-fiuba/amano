# Research: Ver historial de actividad del grupo (014)

**Fecha**: 2026-11-03 | **Rama**: `feat/historial-actividad-grupal` | **Spec**: [spec.md](./spec.md)

**Propósito**: registrar las decisiones técnicas. No quedaron `NEEDS CLARIFICATION`: las decisiones de producto están en las Clarifications de la spec.

## D1 — Numeración `014` y alcance acotado al cronograma

- **Decisión**: carpeta `specs/014-group-activity-history`. El alcance es la tarea del cronograma ("altas, bajas, gastos y pagos"), sin tipos de evento nuevos.
- **Rationale**: 001–013 ya están en `sprint-3`. Se descartaron eventos de creación del grupo, edición de nombre o cambios de rol porque no están en la tarea y obligarían a una migración (`V13`) para ampliar el CHECK de `event_type`.
- **Alternativas consideradas**: (a) solo mejorar lo existente, sin enganche de gastos: deja sin cubrir la mitad de la tarea; (b) esperar a los gastos de grupo: no tienen spec ni responsable y bloquearían la tarea todo el sprint.

## D2 — Sin migración

- **Decisión**: no se agrega ningún archivo a `db/migration`.
- **Rationale**: el CHECK de `V10` ya admite los 7 tipos, incluidos `EXPENSE_CREATED` y `PAYMENT_MADE` (hook points de 010). `details_json` es `TEXT`, así que los formatos nuevos no cambian el esquema. Las filas viejas no se tocan (regla de solo `CREATE`/`ALTER`/`INDEX` y patrón de preservación).

## D3 — Detalles serializados con Jackson

- **Decisión**: `ActivityEventService.record` recibe un `Map<String, ?>` y lo serializa con el `ObjectMapper` de Spring (`tools.jackson.databind`, Jackson 3, ya presente). Las claves con valor `null` se omiten.
- **Rationale**: 010 concatenaba texto (`"{\"name\":\"" + name + "\"}"`): un nombre con comillas producía JSON inválido (FR-004, SC-002). El serializador escapa correctamente y centraliza el formato en un solo lugar.
- **Alternativas consideradas**: escapar a mano (frágil); clases DTO por tipo de evento para todos los eventos (más código sin beneficio para la lectura, que es genérica).

## D4 — Copia del nombre al momento del evento

- **Decisión**: altas y bajas guardan `name` y `email` del afectado. Para registrados, el nombre visible es `firstName + " " + lastName` sin espacios sobrantes.
- **Rationale**: es el patrón de preservación de 010 (FR-024 de 010): el historial no depende de que el miembro o el perfil simulado sigan existiendo. Los perfiles simulados se borran físicamente al quitarlos.
- **Clave `type`**: se mantiene el nombre de clave `type` (`REGISTERED`/`SIMULATED`) que ya usaban las altas de 010, para que filas viejas y nuevas se lean igual.

## D5 — Alta del miembro inicial

- **Decisión**: `GroupService.createGroup` registra `MEMBER_ADDED` para el miembro inicial con `atCreation: true`.
- **Rationale**: es una alta real que 010 dejó sin registrar ("los eventos comienzan con las historias que los usan"). La marca permite la frase "agregó a X al crear el grupo" sin un tipo nuevo.

## D6 — Enganche de gastos y pagos con métodos tipados

- **Decisión**: `ActivityEventService.recordExpenseCreated(groupId, actorUserId, ExpenseActivityDetails)` y `recordPaymentMade(groupId, actorUserId, PaymentActivityDetails)`, con records públicos en `groups/`. Validan monto positivo y moneda no vacía (`IllegalArgumentException`).
- **Rationale**: el futuro módulo de gastos solo hace una llamada y no necesita conocer el formato JSON (SC-004). `Propagation.MANDATORY` (heredado de `record`) obliga a llamarlo dentro de la transacción del gasto o pago. El monto se guarda como texto decimal (`toPlainString`) para no perder precisión en JavaScript.
- **Alternativas consideradas**: puerto `ActivityPort` en `groups/port/` (más indirección; el módulo de gastos dependerá de `groups` de todos modos para validar membresía).

## D7 — Filtro por varios tipos y validación

- **Decisión**: `eventType` acepta una lista separada por comas (`MEMBER_REMOVED,MEMBER_LEFT`). Cada valor se normaliza (trim, mayúsculas); uno inexistente lanza `FieldValidationException` → 400 con `properties.fields.eventType`. La consulta usa `findByGroupIdAndEventTypeInOrderByCreatedAtDescIdDesc`.
- **Rationale**: el filtro "Bajas" de la pantalla agrupa `MEMBER_REMOVED` y `MEMBER_LEFT`; con un solo tipo harían falta dos consultas. Antes, un tipo inexistente devolvía una lista vacía y ocultaba errores del cliente.
- **Compatibilidad**: un solo tipo sigue funcionando igual que en 010.

## D8 — Tope de página y actores en una consulta

- **Decisión**: `size` se limita a 100 (por encima se usa 100). Los actores de la página se buscan con `userRepository.findAllById`.
- **Rationale**: evita respuestas arbitrariamente grandes y elimina el N+1 de 010 (FR-011, FR-013).

## D9 — `details` como objeto en la respuesta

- **Decisión**: `Entry.details` pasa de `String` a `JsonNode`. Si el texto guardado no es JSON válido (filas viejas), se devuelve `null`.
- **Rationale**: el cliente no tiene que volver a parsear. Ningún consumidor usaba `details` (la pantalla lo ignoraba), así que el cambio no rompe nada.

## D10 — Pantalla

- **Decisión**: `GroupHistoryView` se reescribe con: frase por tipo (`describeActivity`, función pura exportada y probada aparte), agrupación por día con `Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' })`, `<select>` "Mostrar" que mapea a listas de tipos, estados de carga/vacío/error con reintento, 401 con `setUser(null)` (patrón de 013), descarte de respuestas viejas y "Volver al grupo".
- **Cliente**: `getActivity` acepta `eventTypes` (arreglo) y pasa a tratar 5xx como `network: true`, como el resto de `groupsClient`.
- **Mensaje de error propio**: "No se pudo cargar el historial. Intentá nuevamente" (voseo). `GROUPS_NETWORK_ERROR_MESSAGE` no se cambia porque lo usan otras pantallas y pruebas de 010.

## D11 — Estrategia de pruebas

- Backend unitario (Mockito): `ActivityEventServiceTest` (serialización, nulos omitidos, comillas, enganches y validaciones), `GroupActivityServiceTest` (filtro múltiple, 400, tope, actores en lote, `details` inválido → `null`, 404 y permisos). Se actualizan las pruebas de 010 que verificaban `record(..., anyString())`.
- Backend integración (Testcontainers): `GroupActivityControllerIT` ampliado (alta inicial, nombres con comillas, baja con copia del nombre, filtro múltiple, 400, tope 100, gasto y pago con el enganche).
- Frontend (Vitest): `activityDescriptions.test.js` (todas las frases y casos viejos), `GroupHistoryView.test.jsx` (agrupación, filtro, paginación, vacío, error con reintento, 401, 404, volver) y `groupsClient.test.js` (`eventTypes`, 5xx).
- Cobertura ≥ 85 % en ambos módulos (constitución V).
