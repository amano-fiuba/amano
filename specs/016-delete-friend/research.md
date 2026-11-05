# Research: Eliminar amigo (016)

**Fecha**: 2026-11-05 | **Rama**: `feat/delete-friend` | **Spec**: [spec.md](./spec.md)

**Propósito**: registrar las decisiones técnicas. No quedaron `NEEDS CLARIFICATION`: las decisiones de producto están en las Clarifications de la spec.

## D1 — Borrado físico de la fila `ACCEPTED`

- **Decisión**: eliminar la amistad es `DELETE` físico de la única fila `friendship_requests` con `status = 'ACCEPTED'` entre las dos cuentas. No se agrega estado `DELETED`, columna `deleted_at`, tabla `friendships` ni migración.
- **Rationale**: 011, 012 y 013 acordaron que la amistad es la solicitud aceptada. `uq_friendship_requests_accepted_pair` garantiza una sola fila por pareja, así que borrarla elimina la amistad para ambos de forma atómica. La historia no pide conservar historial de la amistad. Además, evita una migración y el tratamiento de índices únicos parciales con filas borradas lógicamente.
- **Consecuencia**: después de eliminar, se puede volver a ser amigo con una nueva solicitud aceptada; las filas `REJECTED` previas no se tocan.
- **Alternativas consideradas**: borrado lógico con estado `DELETED` (requiere migración y ajustar los índices únicos); tabla de historial de amistades (mayor alcance, fuera de la historia).

## D2 — `DirectDebtPort` con stub, sin módulo de deudas

- **Decisión**: definir `DirectDebtPort` con el método `hasPendingDirectDebt(Long userId, Long friendId)` en el paquete `friendship/`, e implementarlo con `NoOpDirectDebtPort` (`@Component`), que siempre responde `false`. El futuro módulo de gastos compartidos/deudas reemplazará el stub.
- **Rationale**: hoy el repo no tiene gastos compartidos ni deudas; el único antecedente es `DebtPort` de 010, que también usa un `NoOpDebtPort`. Este patrón permite que la regla quede escrita, probada con mocks y conectable sin cambiar la UI ni el contrato cuando exista el módulo real.
- **No reutilizar `DebtPort`**: su firma es `hasPendingDebts(Long groupId, Long userId)` y responde por deudas dentro de un grupo. La historia pide deuda directa sin grupo, que es otra consulta.
- **Consecuencia**: el bloqueo por deuda directa no se puede observar de punta a punta con datos reales hasta que exista el módulo; la prueba unitaria del servicio usa un mock de `DirectDebtPort` y la integración usa el stub sin deuda.
- **Alternativas consideradas**: depender de una spec 015 inexistente (bloquearía esta historia); implementar el dominio de deudas dentro de 016 (fuera de alcance).

## D3 — Pre-chequeo de elegibilidad + validación final

- **Decisión**: agregar `GET /api/users/me/friends/{friendId}/deletion-eligibility`, que responde `{ canDelete: true }` o `{ canDelete: false, reason }`. El `DELETE` vuelve a validar la deuda y responde `403` si cambió entre la consulta y la confirmación.
- **Rationale**: la persona usuaria eligió que el aviso de deuda aparezca al pulsar "Eliminar amigo", antes de abrir el diálogo. Un endpoint dedicado cumple eso sin cambiar el DTO de la lista de 013 ni hacer N consultas de deuda al cargar la lista. La segunda validación en el `DELETE` protege la regla de negocio aunque la deuda se contraiga o se salde entre ambos pasos.
- **Alternativas consideradas**: agregar `hasPendingDirectDebt` a `GET /api/users/me/friends` (cambia el contrato y las pruebas de 013, y consulta deuda por cada amigo); abrir siempre el diálogo y bloquear recién al confirmar (contradice la decisión de producto).

## D4 — Códigos HTTP y mensajes

- **Decisión**:
  - `403 Forbidden` con `ProblemDetail` (RFC 7807), título "Operación bloqueada por deuda pendiente", `detail` con el mensaje y `properties.code = PENDING_DIRECT_DEBT` cuando hay deuda directa. Mismo criterio que `PendingDebtsException` de 010.
  - `404 Not Found` con `detail = "No sos amigo de ese usuario"` para amistad inexistente, ajena o ya eliminada.
  - `400` con error de campo `friendId` para id no numérico o no positivo.
  - `401` sin sesión o cuenta no verificada, heredado de `/api/users/**`.
  - `204 No Content` en la eliminación exitosa.
- **Rationale**: `403` es el código que ya usa el proyecto para deudas pendientes; `404` neutro evita revelar si existe una relación concreta, igual que 011/012. `204` refleja que no hay cuerpo de respuesta.
- **Alternativas consideradas**: `409` o `422` para deuda (menos coherentes con 010); `200` con mensaje (agrega un contrato innecesario).

## D5 — Diálogo modal en la lista de amigos

- **Decisión**: crear `DeleteFriendDialog.jsx`, controlado por `FriendsList.jsx`, con el nombre del amigo y las acciones "Cancelar"/"Eliminar". Se reutiliza el patrón de `RemoveMemberDialog` de grupos (rol `dialog`, foco, cierre con Escape cuando no hay operación en curso).
- **Rationale**: la persona usuaria eligió confirmación modal; separar el diálogo facilita las pruebas y no hace crecer más `FriendsList`. La lógica de API queda en `FriendsList`, igual que en 013/012.
- **Alternativas consideradas**: confirmación inline (más ruido visual en la tarjeta); `window.confirm` (no testeable ni accesible con el estilo del proyecto).

## D6 — Concurrencia y borrado condicional

- **Decisión**: `FriendshipRequestRepository.deleteAcceptedBetween(a, b)` como `@Modifying` con `delete` filtrado por estado `ACCEPTED` y por la pareja en cualquier dirección; el servicio usa la cantidad de filas afectadas para distinguir éxito (`1`) de `404` (`0`).
- **Rationale**: la operación es atómica y no requiere leer y luego borrar. Ante dos eliminaciones simultáneas, una afecta la fila y la otra afecta 0, por lo que recibe `404` sin efectos colaterales. Es el mismo principio de actualización condicional de 012.
- **Alternativas consideradas**: `findById` + `delete` (ventana de carrera); bloquear la fila (innecesario para esta regla).

## D7 — Estrategia de pruebas

- **Backend unitario** (`FriendServiceTest`, Mockito):
  - elegibilidad positiva y negativa por deuda, con validación de id;
  - eliminación exitosa, `403` por deuda y `404` cuando no se borra ninguna fila;
  - el usuario sale siempre de la sesión y la deuda se consulta con la pareja correcta.
- **Backend integración** (`DeleteFriendIT`, Testcontainers):
  - eliminación real de la fila `ACCEPTED` y desaparición para ambas direcciones;
  - solicitudes `PENDING`/`REJECTED` y de terceros intactas;
  - `400`, `401`, `404` y `204`;
  - re-agregar después de eliminar;
  - grupos y gastos previos intactos (verificación de no regresión).
- **Frontend**:
  - `friendsClient.test.js`: URLs, métodos, `credentials`, 204, 4xx, 5xx y red;
  - `DeleteFriendDialog.test.jsx`: contenido, foco, cancelar, confirmar y estado "Eliminando…";
  - `FriendsList.test.jsx`: elegibilidad positiva abre el diálogo; negativa muestra el aviso sin abrirlo; éxito retira la tarjeta y confirma; `403`, `404`, red y `401`.
- **Cobertura** ≥ 85 % en ambos módulos (constitución V).

## D8 — Sin migración ni cambios de esquema

- **Decisión**: no se agrega ni modifica ningún archivo de `db/migration`.
- **Rationale**: se elimina una fila existente de `friendship_requests` (`V12`); no hacen falta columnas, tablas ni índices nuevos. Las tablas de grupos y gastos no se tocan.
- **Alternativas consideradas**: agregar `deleted_at` (D1 lo descarta); índices para el borrado (la pareja ya está indexada por los índices únicos parciales de `V12`).

## D9 — Fuera de alcance confirmado

- Notificaciones a la persona eliminada.
- Historial o auditoría de amistades eliminadas.
- Implementación del módulo real de gastos compartidos/deudas.
- Eliminación de la cuenta o de los datos del amigo.
- Salida de grupos existentes o modificación de sus miembros.
- Búsqueda, paginación o cambios al listado de 013.
