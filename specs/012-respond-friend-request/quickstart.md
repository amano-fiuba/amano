# Quickstart: validación de aceptación y rechazo de solicitudes de amistad (012)

**Fecha**: 2026-10-30 | **Spec**: [spec.md](./spec.md) | **Contrato**: [contracts/friendship-requests-api.md](./contracts/friendship-requests-api.md)

Guía para verificar de punta a punta la respuesta del destinatario sobre las solicitudes pendientes. Solo comandos y escenarios observables.

## Prerrequisitos

- Entorno de desarrollo de `first-steps.md` (base en Docker, backend con `mvn spring-boot:run`, frontend con `npm run dev`).
- **Tres cuentas verificadas** (A, B y C) para probar emisora, destinataria y cuenta ajena.
- **Solicitudes pendientes de base**: enviar desde A a B y desde C a B usando la pantalla de 011 ("Buscar usuarios" → "Agregar"), o creando filas `PENDING` por SQL.
- **Sin migración nueva**: no hace falta reiniciar la base; el código vale sobre el esquema de 011 (`V12`).

## 1. Verificaciones automatizadas

```bash
cd backend && ./mvnw -B verify
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

| Suite | Cubre |
|-------|-------|
| `friendship/FriendshipRequestServiceTest` (ampliado) | Acción o id inválido, 404 por inexistencia y por solicitud ajena, 409 por ya respondida (aceptada y rechazada), transiciones válidas con `respondedAt`, relectura que distingue 404 de 409 y escritura exclusivamente por `UPDATE` condicional |
| `friendship/FriendshipRequestIT` (ampliado) | Flujo HTTP del `PUT`: 200 con estado final, 400/401/404/409, aislamiento entre cuentas, doble respuesta concurrente con una única transición, estado y `responded_at` en la base, invarianza de `POST` y `GET` de 011 |
| `pages/FriendsPage.test.jsx` (ampliado) | Botones por fila, carga por acción, retiro de fila, confirmaciones exactas, confirmación en el snackbar con lista vacía, 409/404, error de red con fila reactiva, 401 |
| `api/friendsClient.test.js` (ampliado) | `PUT`, URL con id, cuerpo `{ action }`, `credentials`, parseo de 2xx/4xx/red y mensaje de red fijo |
| Suites previas de 011 | Regresión: envío, bandeja, buscador, acceso y navegación sin cambios |

## 2. Escenarios manuales

### A. Aceptar una solicitud (US1)

1. Cerrar sesión e iniciar como **B** (destinataria). Abrir "Amigos"; la bandeja muestra a A y a C como pendientes (orden de la más reciente a la más antigua).
2. Pulsar **Aceptar** en la fila de A.
3. **Esperado**: los botones de esa fila muestran "Aceptando…" y luego la fila desaparece con el aviso "Solicitud de amistad aceptada"; la fila de C sigue intacta.
4. Verificar en la base:

   ```sql
   SELECT id, sender_id, recipient_id, status, created_at, responded_at
   FROM friendship_requests
   ORDER BY id DESC;
   ```

   **Esperado**: la fila de A tiene `status = 'ACCEPTED'`, `responded_at` no nulo, `created_at` sin cambios; la de C sigue `PENDING`.
5. Volver a abrir "Amigos". **Esperado**: solo aparece C como pendiente; A ya no está en la lista (y la amistad quedará visible en su historia posterior, no acá).

### B. Rechazar una solicitud (US2)

1. Continuando como **B**, pulsar **Rechazar** en la fila de C.
2. **Esperado**: "Rechazando…", la fila desaparece y aparece "Solicitud de amistad rechazada".
3. **Esperado** en la base: fila de C con `status = 'REJECTED'`, `responded_at` no nulo y **sin** ninguna fila `ACCEPTED` entre B y C.
4. Como **C**, buscar a B y enviar una nueva solicitud. **Esperado**: el envío se acepta (un rechazo es histórico y no bloquea); en la bandeja de B vuelve a aparecer C como pendiente.

### C. Reglas de negocio (US3)

1. Sin pendientes en pantalla, como **B** repetir el `PUT` sobre la solicitud ya respondida (por ejemplo, con cURL):

   ```bash
   curl -s -X PUT http://localhost:8080/api/users/me/friendship-requests/<id-de-la-fila-de-A> \
     -H 'Content-Type: application/json' -b 'amano_auth=<cookie-de-B>' \
     -d '{"action": "ACCEPT"}'
   ```

   **Esperado**: 409 "Esta solicitud de amistad ya fue respondida"; sin cambios en la fila (misma `responded_at`).
2. Como **A** (la emisora), intentar responder la solicitud que ella misma envió a B (id de la fila, acción `ACCEPT`). **Esperado**: 404 "No se encontró la solicitud de amistad".
3. Con un id inexistente (`<id-inexistente>`), repetir como B. **Esperado**: 404 con el mismo mensaje neutro.
4. Con una acción inválida (`{"action": "MAYBE"}`), repetir. **Esperado**: 400 con error de campo sobre `action`, "La acción no es válida"; la fila no cambia.
5. Con id no numérico en la ruta, repetir. **Esperado**: 400 sin acceso a datos.
6. Verificar que la fila A sigue `ACCEPTED` y la de C `REJECTED` salvo por el paso B4 nuevo `PENDING`.

### D. Acceso protegido y concurrencia (US3)

1. Sin sesión, llamar al `PUT` con un id cualquiera. **Esperado**: 401.
2. Con una cuenta **no verificada**, repetir. **Esperado**: 401.
3. Con una solicitud pendiente nueva (por ejemplo, C → B de nuevo), disparar dos respuestas simultáneas del mismo id (dos pestañas, o dos `curl` en paralelo):

   ```bash
   curl -s -X PUT http://localhost:8080/api/users/me/friendship-requests/<id> \
     -H 'Content-Type: application/json' -b 'amano_auth=<cookie-de-B>' \
     -d '{"action": "ACCEPT"}' &
   curl -s -X PUT http://localhost:8080/api/users/me/friendship-requests/<id> \
     -H 'Content-Type: application/json' -b 'amano_auth=<cookie-de-B>' \
     -d '{"action": "REJECT"}' &
   wait
   ```

   **Esperado**: exactamente una respuesta 200 y una 409; la fila final tiene **un** estado final coherente con la que ganó y una única `responded_at`. Nunca quedan filas ambiguas.

### E. Errores y sesión

1. Detener el backend y pulsar "Aceptar" o "Rechazar". **Esperado**: "No se pudo responder la solicitud. Intentá nuevamente"; la fila permanece con sus botones reactivos y sin confirmación de éxito.
2. Levantar el backend y responder de nuevo la misma fila. **Esperado**: la operación concluye con su confirmación correcta.
3. Expirar la sesión y pulsar una acción. **Esperado**: redirección al acceso sin datos visibles ni confirmaciones falsas.
4. En la última pendiente de la bandeja, responderla con éxito. **Esperado**: la confirmación aparece en el snackbar, se cierra sola a los 5 segundos y debajo se ve "No tenés solicitudes de amistad pendientes".

## 3. Regresiones a observar

- El envío de 011 funciona igual: "Agregar", mensaje "Solicitud de amistad enviada" y las reglas 409/404/400 sin cambios.
- La bandeja conserva listado, orden, datos por fila, vacío y reintento de 011; solo se agregan las dos acciones.
- `GET /api/users/search` no cambia.
- Un rechazo no bloquea reenvíos; una aceptación hace que la pareja figure como amigos solo en la futura historia de lista de amigos (no hay vista acá).
- No se agregan ni modifican cuentas, gastos ni rutas de navegación.

## Resultado de la validación

- **Automatizada (2026-10-31, Java 25 + PostgreSQL 18 vía Testcontainers)**:
  - `cd backend && ./mvnw -B verify` → **BUILD SUCCESS**, 109 pruebas en verde y umbral de cobertura cumplido. Incluye `FriendshipRequestServiceTest` (reglas del `respond` con Mockito) y el nuevo `FriendshipRequestRespondIT` (flujo HTTP completo, 400/401/404/409, carrera concurrente con una única transición y regresión del `POST`/`GET` de 011).
  - `cd frontend`: `npm run lint` (0 errores), `npm run test:coverage` (36 suites, 425 pruebas en verde; cobertura global 93.45 %) y `npm run build`.
- **Manual**: pendiente; la primera prueba visual la realiza la persona usuaria ejecutando los escenarios A–E de §2 y luego se registran los resultados acá.
