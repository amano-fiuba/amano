# Research: Aceptar y rechazar solicitudes de amistad (012)

**Fecha**: 2026-10-30 | **Rama**: `feat/respond-friend-request` | **Spec**: [spec.md](./spec.md)

**Propósito**: registrar las decisiones de diseño. No quedaron `NEEDS CLARIFICATION`: las decisiones de producto están en las Clarifications de la spec y aquí se justifican las técnicas.

## D1 — Numeración `012`

- **Decisión**: la carpeta es `specs/012-respond-friend-request`.
- **Rationale**: `010` está en curso con otro integrante y el equipo acordó el número `012` el 2026-10-30. El identificador evita ambigüedad en referencias cruzadas con 011.
- **Alternativas consideradas**: reutilizar `010` o solaparla con 011 (rompería la separación de historias y de ramas).

## D2 — Una sola spec para aceptar y rechazar

- **Decisión**: las historias 27 y 29 se cubren en una única feature; la respuesta del destinatario es una operación con dos destinos posibles (`ACCEPTED`/`REJECTED`).
- **Rationale**: ambas historias comparten la misma pantalla, el mismo endpoint, el mismo flujo y las mismas reglas (404, 409, concurrencia); separarlas duplicaría pruebas e infraestructura sin valor. Los criterios de una y otra quedan trazados por FR separados (FR-001..FR-003 para aceptar, FR-004..FR-006 para rechazar).
- **Alternativas consideradas**: dos specs (mismos archivos y contratos duplicados).

## D3 — Sin migración nueva

- **Decisión**: no se agrega ni modifica ningún archivo de `db/migration`; la tabla `friendship_requests` de `V6` ya soporta esta historia.
- **Rationale**: 011 ya creó `status` con el catálogo `PENDING/ACCEPTED/REJECTED`, `responded_at` con su restricción de coherencia (`PENDING` ⇒ nulo; estados finales ⇒ no nulo) y la restricción `sender_id <> recipient_id`. El índice único de amistades (`uq_friendship_requests_accepted_pair`) ya garantiza a lo sumo una amistad por pareja una vez que existan aceptaciones. La aceptación activa piezas preparadas a propósito.
- **Alternativas consideradas**: tabla `friendships` separada (duplica la relación y contradiere la decisión D3 de 011); migración para "arreglar" estados (innecesaria: el CHECK ya es válido).

## D4 — `PUT /api/users/me/friendship-requests/{id}` con la acción en el cuerpo

- **Decisión**: un único endpoint `PUT` sobre el recurso de la solicitud, con `{ "action": "ACCEPT" | "REJECT" }`; la ruta sigue el prefijo `me/` de 011.
- **Rationale**: la operación actúa sobre un recurso existente identificado por la ruta y cambia su estado, lo que encaja con `PUT`; colgar del prefijo `/api/users/**` hereda la regla de cuenta verificada de 003 sin tocar `SecurityConfig`. Un único endpoint evita duplicar validaciones (sesión, 404, 409, concurrencia) en dos rutas.
- **Alternativas consideradas**: `POST /{id}/accept` y `POST /{id}/reject` (RPC disfrazado, duplica el código de estado); `PATCH` con `status` en el cuerpo (permite manipular cualquier estado desde el cliente); id de la solicitud en el cuerpo (la ruta es su lugar natural).
- **Nota**: repetir el `PUT` no es idempotente en el resultado observado (200 → 409): la segunda llamada informa que ya fue respondida, lo que es correcto según FR-008.

## D5 — Actualización condicional para la concurrencia

- **Decisión**: la transición se materializa con una consulta de actualización condicional del estilo
  `UPDATE friendship_requests SET status = :newStatus, responded_at = now() WHERE id = :id AND recipient_id = :responderId AND status = 'PENDING'`.
  Si no afecta filas, una relectura distingue: fila inexistente o con `recipient_id` distinto → 404; fila con estado final → 409.
- **Rationale**: una sola sentencia atómica en la base garantiza que la primera respuesta gane y las restantes no cambien nada (SC-005), sin dependencia del orden de las transacciones ni de bloqueos explícitos. La restricción `ck_friendship_requests_responded` sigue haciendo imposible una fila inconsistente.
- **Alternativas consideradas**: leer, validar y asignar con JPA (condición de carrera entre lectores concurrentes: dos peticiones leen `PENDING` y ambas escriben); bloqueo pesimista `SELECT ... FOR UPDATE` (más contención para un caso raro); reintentos optimistas con versión (agrega columna que no existe).
- **Nota**: la relectura posterior al `UPDATE` fallido es segura porque la fila ya no cambia (los estados finales son estables).

## D6 — Mensajes de 404 y 409

- **Decisión**: 404 único y neutro "No se encontró la solicitud de amistad" para (a) id inexistente, (b) id no dirigido a la sesión (por ejemplo, la solicitud que uno envió) y (c) solicitud borrada por cascada. 409 único "Esta solicitud de amistad ya fue respondida" para cualquier re-respuesta, sin revelar si el resultado anterior fue una aceptación o un rechazo.
- **Rationale**: con solo el `id` en la ruta un atacante no debe poder enumerar solicitudes ajenas ni conocer su estado (mismo criterio neutro que 011 con D6); el `id` de la solicitud no es público en la interfaz. El 409 único simplifica el mensaje y la bandeja solo muestra pendientes, así que este caso solo ocurre por doble clic, solicitud manipulada o coincidencia de estado local obsoleto.
- **Alternativas consideradas**: 409 con mensajes por caso ("Ya son amigos" / "Ya fue rechazada") (filtraría el estado a quien pregunta y duplica mensajes para una situación anómala).

## D7 — La amistad existe al aceptar, la lista de amigos es otra historia

- **Decisión**: aceptar produce la fila `ACCEPTED`, que es la amistad (decisión D3 de 011). No hay tabla ni vista de amigos en 012.
- **Rationale**: el criterio "ambos usuarios deben quedar registrados como amigos" queda satisfecho por la persistencia de la relación; la visualización ("deben poder visualizarse en su lista de amigos") se acordó con el equipo como spec posterior para mantener 012 acotada. El mismo razonamiento cubre el criterio del rechazo: sin fila `ACCEPTED` no hay amistad y no puede haber apariencia en listas.
- **Alternativas consideradas**: incluir la sección "Amigos" en la pantalla desde ya (adelanta una historia no aprobada y agranda el alcance con UI nueva y consultas posteriores).

## D8 — Acciones en la bandeja, sin recargar la pantalla

- **Decisión**: `FriendsPage` agrega "Aceptar" y "Rechazar" por fila; tras un resultado (éxito, 409 o 404) la fila se retira del estado local y se muestra una confirmación condensada; tras error de red la fila queda reactiva. No se repite el `GET` completo ni se agrega un estado "respondida" en la fila.
- **Rationale**: la única lista pendiente, así que retirar la fila localmente refleja el estado real sin una petición extra; la confirmación (FR-013/FR-014) cumple "el sistema debe informar al usuario cuando la acción se haya realizado correctamente" y sigue visible si la lista queda vacía (FR-014), que el estado vacío de 011 es solo para cargas iniciales.
- **Alternativas consideradas**: recargar la bandeja tras cada respuesta (la confirmación de éxito desaparecería si queda vacía: contradice FR-014); mantener la fila con etiqueta "Aceptada"/"Rechazada" (contradice el criterio de no seguir apareciendo como pendiente).

## D9 — Sin cambios en el buscador ni en el contrato de 008/011

- **Decisión**: `UsersSearchPage` no cambia; la solicitud rechazada no bloquea un envío nuevo (FR-006) y esa conducta ya está implementada en 011.
- **Rationale**: 008 sigue sin conocer el estado de la relación (decisión D9 de 011); el emisor no recibe aviso de la respuesta (Assumptions) y se entera cuando exista una vista futura. Ningún archivo del buscador, `App.jsx` ni `SiteNav` se tocan.
- **Alternativas consideradas**: badge o aviso al emisor en el buscador (introduce un canal no aprobado y una consulta de estado por fila).

## D10 — Exposición mínima de datos

- **Decisión**: la respuesta del `PUT` devuelve `id`, `status`, `createdAt`, `respondedAt` y el resumen del emisor (`id`, `firstName`, `lastName`, `email`); nunca CBU/CVU, monedas, contraseña, hash, avatar ni fecha de verificación.
- **Rationale**: el respondedor ya conoce esos datos de la bandeja (FR-011 de 011); la respuesta confirma el resultado con la misma información mínima. El resumen del emisor es el dato externo de la relación desde la perspectiva de la destinataria.
- **Alternativas consideradas**: devolver solo `id` y `status` (obliga a cruzar con la bandeja local y complica pruebas); devolver ambos resúmenes (expone un resumen que el respondedor no necesita en este flujo).

## D11 — Estrategia de pruebas

- **Decisión**:
  - Backend unitario con Mockito (ampliación de `FriendshipRequestServiceTest`): transiciones válidas, acciones inválidas, 404 por inexistencia o ajeneidad, 409 por ya respondida, que la relectura distinga 404 de 409 y que el `UPDATE` condicional sea la única escritura.
  - Backend de integración con Testcontainers (ampliación de `FriendshipRequestIT`): flujo HTTP completo, aislamiento entre cuentas, 400/401/404/409, doble respuesta concurrente con una única transición, corridas de aceptar y rechazar sobre la misma fila, estado y `responded_at` en la base, invarianza de los endpoints de 011.
  - Frontend Vitest (ampliación de `FriendsPage.test.jsx` y `friendsClient.test.js`): botones por fila, carga por acción, retiro de fila, confirmaciones exactas, permanencia de la confirmación con lista vacía, 409/404, error de red con fila reactiva y 401.
- **Rationale**: principio V (cobertura ≥ 85 %). Las reglas de negocio se prueban en backend porque el frontend no es frontera de seguridad; la concurrencia se prueba con Testcontainers porque es donde realmente se materializa el `UPDATE` condicional.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| Doble aceptación/rechazo simultáneo deja la fila inconsistente | `UPDATE` condicional por `id + recipient_id + status = 'PENDING'` (D5) + restricción `ck_friendship_requests_responded` de 011 |
| Enumeración de solicitudes ajenas por la ruta | 404 neutro único para inexistente/ajena (D6) y validación en la misma sentencia de `UPDATE` |
| La bandeja muestra la solicitud ya respondida por estado local obsoleto | Retiro de fila al confirmar 200/409/404 (D8); el próximo `GET` devolvería solo pendientes |
| Interpretar `action` deformes desde el cliente | Validación estricta del valor exacto `ACCEPT`/`REJECT` en backend (FR-010), 400 sin tocar la base |
| La aceptación crea una segunda amistad para la misma pareja | Imposible por diseño: solo transiciones desde `PENDING`, que no coexiste con `ACCEPTED` para la misma pareja (índices únicos de 011) |
| Filtración de datos privados en la respuesta | DTO acotado (D10) y pruebas que verifican el JSON exacto |
