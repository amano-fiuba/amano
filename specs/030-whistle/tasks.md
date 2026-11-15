---
description: "Task list for Whistle — aviso manual a un deudor"
---

# Tasks: Whistle — aviso manual a un deudor

**Input**: Design documents from `/specs/030-whistle/`

**Prerequisites**: plan.md (requerido), spec.md (requerido para las historias), research.md, data-model.md, contracts/, quickstart.md

**Tests**: la constitución (principio V) exige pruebas automatizadas: unitarias con `Clock.fixed`, integración con Testcontainers + GreenMail + MockMvc y Vitest, con cobertura de líneas ≥ 85 %. **Las tareas de prueba van ANTES de la implementación que cubren y deben fallar antes de implementarla (TDD).**

**Organization**: tareas agrupadas por historia de usuario.

- US1: avisar (P1, MVP).
- US2: límite de 24 h (P1).
- US3: historial (P2).

US1 se puede entregar sola. US2 y US3 dependen de US1.

**⚠️ Precondición**: no empezar la Fase 2 hasta que Nahuel apruebe las clarificaciones Q1 a Q11 de spec.md. Si Q3 (perfiles simulados) cambia, ajustar T007, T008, T010, T015, T018 y `data-model.md` antes de seguir.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede ejecutar en paralelo (archivos distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1, US2, US3).
- Cada descripción incluye la ruta exacta del archivo.

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/`, `backend/src/main/resources/db/migration/`, `backend/src/test/java/com/amano/backend/`
- Frontend: `frontend/src/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: verificaciones previas

- [x] T001 Verificar los supuestos de plan.md y research.md y corregir los documentos si algo difiere:
  - `specs/` termina en 029 y `db/migration/` en V36, sin una `030` ni una `V37` en otras ramas.
  - Lista vigente del `CHECK` de `group_activity` en `V36__group_activity_recurrence_events.sql`.
  - `DebtStatus.isPayable()` en `backend/src/main/java/com/amano/backend/groupexpense/DebtStatus.java`.
  - Firma de `ReminderMailService.send` y `ReminderRecipients.emailOf`.
  - Handler de `GroupPermissionDeniedException` en `common/ProblemDetailExceptionHandler.java`.
  - `isPayableDebtStatus` en `frontend/src/pages/groups/debtStatusLabels.js`.
  - `DebtView.creditor.userId` en `GroupExpenseResponse.java`.
  - Setup de GreenMail y `@MockitoBean Clock` en `backend/src/test/java/com/amano/backend/groupexpense/reminder/ReminderFailureIT.java` y `groupexpense/overdue/OverdueDebtJobIT.java`.
  - Q1 a Q11 de spec.md aprobadas por Nahuel.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: migración, entidad, evento y errores compartidos por las tres historias

**⚠️ CRITICAL**: ninguna historia puede empezar hasta completar esta fase.

- [x] T002 [P] Crear `backend/src/main/resources/db/migration/V37__create_debt_whistles.sql` según data-model.md:
  - `CREATE TABLE debt_whistles (...)` con FKs, `CHECK` de `status` y `CHECK` de `sent_at`.
  - `CREATE INDEX ix_debt_whistles_pair ...`.
  - `ALTER TABLE group_activity DROP CONSTRAINT group_activity_event_type_check;` y `ADD CONSTRAINT ...` con la lista de V36 más `'DEBT_WHISTLED'`.
  - **Sin `UPDATE` ni `DELETE`** (NFR-004).
- [x] T003 Crear en `backend/src/main/java/com/amano/backend/groupexpense/whistle/`:
  - `DebtWhistle.java`: entidad de `debt_whistles`, con `createdAt` asignado desde el `Clock` de la aplicación, no por el `DEFAULT` de la base, para que las pruebas con `@MockitoBean Clock` controlen la ventana.
  - `DebtWhistleStatus.java`: `SENDING`, `SENT`, `FAILED`.
  - `DebtWhistleRepository.java`: `findBlocking(groupId, creditorId, debtorId, since)` (intentos más recientes primero) sobre `status IN (SENDING, SENT)`, más `markSent(id, sentAt)` y `markFailed(id)` como updates condicionales desde `SENDING`.
  - Depende de T002.
- [x] T004 Agregar `DEBT_WHISTLED` a `GroupActivity.GroupActivityType` (`backend/src/main/java/com/amano/backend/groups/GroupActivity.java`) y el método `ActivityEventService.recordDebtWhistled(...)` (`groups/ActivityEventService.java`, `Propagation.MANDATORY`, detalles de data-model.md). Depende de T002.
- [x] T005 Crear las excepciones en `groupexpense/whistle/` y agregar sus handlers RFC 7807 con `properties.code` en `common/ProblemDetailExceptionHandler.java` (contracts/debt-whistle.md):
  - `DebtNotWhistleableException` → 409, con `DEBT_NOT_WHISTLEABLE` o `DEBTOR_NOT_MEMBER`.
  - `SimulatedDebtorWhistleException` → 422.
  - `WhistleRateLimitedException` → 429, con la cabecera `Retry-After` y `properties.retryAfterSeconds`.
  - `WhistleDeliveryException` → 503.
  - Para el 403 (`WHISTLE_NOT_CREDITOR`): reutilizar `GroupPermissionDeniedException` si su handler permite el código; si no, crear `WhistleNotCreditorException`.

**Checkpoint**: migración aplicada en un arranque limpio y tipos listos. Las historias pueden empezar.

---

## Phase 3: User Story 1 - Avisar a un deudor (Priority: P1) 🎯 MVP

**Goal**: el acreedor confirma y el deudor registrado recibe un correo en ese momento. Las reglas de permiso, estado, simulado y miembro se aplican en el servidor y la UI muestra el botón solo donde corresponde (FR-001 a FR-006, FR-009, FR-013 a FR-016).

**Independent Test**: con una deuda `PENDING` de un usuario registrado, avisar como acreedor y verificar un único correo en GreenMail y la deuda sin cambios.

### Tests for User Story 1 ⚠️ (escribir primero, deben fallar)

- [x] T006 [P] [US1] Extender `backend/src/test/java/com/amano/backend/groupexpense/reminder/ReminderMessageBuilderTest.java` con `debtWhistle`:
  - asunto "{acreedor} te recuerda una deuda - {grupo}";
  - cuerpo con saldo pendiente, moneda y concepto (incluido "Gasto del grupo" si el concepto está vacío);
  - fecha límite solo si el gasto tiene `dueDate`;
  - detalles del gasto de 025 y la línea "Este aviso lo envió {acreedor}";
  - que nunca use el mensaje personalizado del grupo (contracts/debt-whistle-activity.md §1).
- [x] T007 [P] [US1] Crear `backend/src/test/java/com/amano/backend/groupexpense/whistle/DebtWhistleServiceTest.java` (Mockito, `Clock.fixed` en `America/Argentina/Buenos_Aires`):
  - éxito con `PENDING`, `PARTIALLY_PAID` y `OVERDUE`: la fila pasa de `SENDING` a `SENT`, se envía un solo correo con `ReminderMailService` y la deuda no cambia;
  - orden de validación de research D4: 404 si el caller no es miembro, 404 si la deuda es de otro grupo, 403 si no es el acreedor, 409 `DEBT_NOT_WHISTLEABLE` para `PAID`, `VERIFIED` y `PAYMENT_REPORTED` (con el mensaje específico), 422 si el deudor es simulado (Q3), 409 `DEBTOR_NOT_MEMBER`;
  - 503 si `send` lanza una excepción (la fila queda `FAILED`) y 503 si `emailOf` está vacío.
  - Debe fallar antes de T015.
- [x] T008 [P] [US1] Crear `backend/src/test/java/com/amano/backend/groupexpense/whistle/DebtWhistleIT.java` (Testcontainers + GreenMail + MockMvc, mismo setup que `ReminderFailureIT`):
  - `POST /api/groups/{g}/debts/{d}/whistle` responde 201 con el cuerpo del contrato, sin el correo del deudor;
  - GreenMail recibe exactamente un mensaje con el asunto esperado;
  - la deuda conserva estado, `paid_amount` y recordatorios de 025;
  - 401 sin sesión; 404 neutral para no miembro y para deuda de otro grupo; 403 para el deudor y para un tercero; 409 para una deuda pagada; 422 para un simulado (Q3); 503 con el SMTP fallando (patrón de `ReminderFailureIT`).
  - Debe fallar antes de T016.
- [x] T009 [P] [US1] Extender `frontend/src/api/groupExpensesClient.test.js` con `whistleDebt`: 201 → `{ ok, data }`; 409, 422 y 503 → `{ ok: false, status, detail, code }`; error de red → `{ network: true }`.
- [x] T010 [P] [US1] Crear `frontend/src/components/groups/WhistleButton.test.jsx`:
  - el botón tiene `aria-label` "Avisar a {deudor}";
  - abre un `ConfirmModal` con título, monto, concepto y la aclaración de 24 h;
  - la × y Escape no llaman al API;
  - confirmar muestra el snackbar "Le avisamos a {deudor}";
  - mientras `busy`, no se puede confirmar dos veces;
  - simulado: botón deshabilitado con el texto visible "Los perfiles simulados no reciben avisos" (Q3);
  - 409 muestra el mensaje y llama a `onStale`; 503 muestra su mensaje; error de red permite reintentar; 401 llama a `setUser(null)`.
- [x] T011 [P] [US1] Extender `frontend/src/pages/groups/GroupDebtsView.test.jsx` (pestaña "Mis deudas pendientes", `MyDebtsPanel`): "Avisar" aparece en "X te debe" con estado avisable y no aparece en "Le debés a…", en `PAYMENT_REPORTED` ni en deudas ajenas.
- [x] T012 [P] [US1] Extender `frontend/src/components/groups/ExpenseDebtList.test.jsx`: "Avisar" aparece solo cuando `debt.creditor.userId` es el usuario de la sesión y el estado es avisable.

### Implementation for User Story 1

- [x] T013 [US1] Agregar `debtWhistle(...)` a `backend/src/main/java/com/amano/backend/groupexpense/reminder/ReminderMessageBuilder.java`, reutilizando `concept`, `money` y `appendExpenseDetails`. Pasa T006.
- [x] T014 [US1] Agregar `findForUpdate(groupId, userId)` con `@Lock(PESSIMISTIC_WRITE)` a `backend/src/main/java/com/amano/backend/groups/GroupMemberRepository.java` (research D3).
- [x] T015 [US1] Crear `backend/src/main/java/com/amano/backend/groupexpense/whistle/DebtWhistleService.java`:
  - validaciones de research D4;
  - transacción 1 con bloqueo de la membresía del acreedor e inserción de la fila `SENDING`;
  - envío fuera de la transacción con `ReminderRecipients.emailOf` y `ReminderMailService.send`;
  - transacción 2 que marca `SENT` o `FAILED` (en el segundo caso, lanza 503).
  - El límite se agrega en T024. Pasa T007. Depende de T003, T005, T013 y T014.
- [x] T016 [US1] Crear `DebtWhistleController.java` y `DebtWhistleResponse.java` en `groupexpense/whistle/` (`POST /api/groups/{groupId}/debts/{debtId}/whistle`, 201, mismo 401 que `PendingDebtController`). Pasa T008.
- [x] T017 [US1] Agregar `whistleDebt(groupId, debtId)` a `frontend/src/api/groupExpensesClient.js`, reutilizando `problemOf` y `failure`, y leyendo `properties.code` y `properties.retryAfterSeconds` o la cabecera `Retry-After`. Pasa T009.
- [x] T018 [US1] Crear `frontend/src/components/groups/WhistleButton.jsx` (`ConfirmModal`, `useSnackbar`, textos exportados como constantes para los tests, estado deshabilitado para simulados). Pasa T010.
- [x] T019 [US1] Montar `WhistleButton` en `frontend/src/components/groups/MyDebtsPanel.jsx` cuando `debt.creditorUserId === currentUserId` y `isPayableDebtStatus(debt.status)`. Simulado = `debt.debtorUserId == null`. `onStale` llama a `onChanged`. Pasa T011.
- [x] T020 [US1] Montar `WhistleButton` en `frontend/src/components/groups/ExpenseDebtList.jsx` (con el usuario de `useAuth`) cuando `debt.creditor.userId` es el usuario de la sesión y el estado es avisable. Simulado = `debt.debtor.type === 'SIMULATED'`. Pasa T012.

**Checkpoint**: US1 funciona y se puede probar sola: escenarios 1 a 4 y 9 de quickstart.md.

---

## Phase 4: User Story 2 - Límite de un aviso por día (Priority: P1)

**Goal**: como máximo 1 aviso enviado o en curso por par (grupo, acreedor, deudor) cada 24 h. Responde 429 con el tiempo restante, ignora los intentos fallidos y resiste la concurrencia (FR-007, FR-008, FR-010).

**Independent Test**: dos avisos seguidos al mismo deudor en el mismo grupo producen un correo y un 429. Con el reloj adelantado 24 h, el aviso vuelve a salir.

### Tests for User Story 2 ⚠️

- [x] T021 [P] [US2] Extender `DebtWhistleServiceTest.java`:
  - un intento `SENT` a las 23:59:59 bloquea y a las 24:00:00 ya no;
  - un intento `FAILED` no bloquea;
  - un `SENDING` reciente bloquea;
  - el límite es por par: otro deudor u otro grupo no quedan bloqueados, y la misma pareja desde otra deuda sí;
  - `retryAfterSeconds` se redondea hacia arriba.
- [x] T022 [P] [US2] Extender `DebtWhistleIT.java`:
  - el segundo aviso responde 429 con `Retry-After` y `properties.retryAfterSeconds`, y GreenMail sigue con un solo mensaje;
  - con `@MockitoBean Clock` adelantado 24 h, el aviso vuelve a salir (201);
  - un 503 seguido de un reintento con el SMTP sano responde 201;
  - **concurrencia**: dos solicitudes simultáneas en hilos distintos producen exactamente un 201, un 429 y un solo correo.
- [x] T023 [P] [US2] Extender `WhistleButton.test.jsx`: 429 con `retryAfterSeconds = 63600` muestra "Vas a poder volver a hacerlo en 17 h 40 min"; con menos de una hora, solo minutos; siempre redondeando hacia arriba.

### Implementation for User Story 2

- [x] T024 [US2] Agregar a `DebtWhistleService.java`, dentro de la transacción 1 y después del bloqueo de T014, la consulta `findBlocking` con la ventana de 24 h desde el `Clock` y lanzar `WhistleRateLimitedException` con `retryAfterSeconds`. Pasa T021 y T022.
- [x] T025 [US2] Formatear en `WhistleButton.jsx` el mensaje de 429 en horas y minutos, redondeando hacia arriba. Pasa T023.

**Checkpoint**: escenarios 5 a 7 de quickstart.md.

---

## Phase 5: User Story 3 - Avisos en el historial del grupo (Priority: P2)

**Goal**: cada aviso enviado queda como `DEBT_WHISTLED` a nombre del acreedor. Los intentos fallidos o rechazados no dejan nada (FR-011, FR-012).

**Independent Test**: avisar y ver el evento en `GET /api/groups/{id}/activity` y en la pantalla de historial. Un 503 o un 429 no agrega eventos.

### Tests for User Story 3 ⚠️

- [x] T026 [P] [US3] Extender `DebtWhistleIT.java`: tras un 201, la actividad del grupo tiene un `DEBT_WHISTLED` con actor = acreedor y los detalles de contracts/debt-whistle-activity.md §2. Tras un 503, un 429 o un 403, la cantidad de eventos no cambia.
- [x] T027 [P] [US3] Extender `frontend/src/pages/groups/activityDescriptions.test.js`: `DEBT_WHISTLED` con y sin `description`, y un tipo desconocido sigue usando la descripción por defecto (compatibilidad aditiva).

### Implementation for User Story 3

- [x] T028 [US3] Llamar a `ActivityEventService.recordDebtWhistled` (T004) en la transacción 2 de `DebtWhistleService.java`, solo cuando `markSent` actualizó la fila. Pasa T026.
- [x] T029 [US3] Agregar la descripción de `DEBT_WHISTLED` en `frontend/src/pages/groups/activityDescriptions.js`. Pasa T027.

**Checkpoint**: escenario 8 de quickstart.md.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T030 Correr `./mvnw -B verify` en `backend/`. Sin `javac` local, usar el contenedor `eclipse-temurin:25-jdk` con el socket de Docker montado. Registrar tests, `All coverage checks have been met`, la cobertura de líneas (≥ 85 %) y la de ramas (de `target/site/jacoco/jacoco.csv`).
- [x] T031 Correr `npm ci && npm run lint && npm run test:coverage && npm run build` en `frontend/`. Registrar la cobertura de líneas y de ramas (≥ 85 % de líneas) y confirmar que `GroupDebtsView.test.jsx` no falla (024 T023).
- [ ] T032 [P] Revisar la accesibilidad de `WhistleButton` con teclado y lector de pantalla (quickstart escenario 10, NFR-006).
- [x] T033 [P] Revisión final:
  - `V37` sin `UPDATE` ni `DELETE` (`grep -nE "UPDATE|DELETE" backend/src/main/resources/db/migration/V37__*.sql` solo debe devolver la cláusula FK, si existe);
  - sin código de depuración, `console.log` ni imports sin uso;
  - ninguna respuesta ni log nuevo expone el correo del deudor (NFR-002);
  - contratos aditivos confirmados.
- [ ] T034 Recorrer los escenarios 1 a 10 de `specs/030-whistle/quickstart.md` con la app levantada, en escritorio y a 375 px, y anotar el resultado en la sección "Resultado" de quickstart.md.
- [x] T035 Actualizar el `Status` de `specs/030-whistle/spec.md` (de `Draft` a "Implementada en la rama, pendiente de revisión"), registrar en plan.md los desvíos encontrados y marcar en `checklists/requirements.md` los ítems que hayan quedado resueltos.
- [x] T036 Proponer en texto (sin ejecutar) los commits con Conventional Commits (`test:`, `feat(backend):`, `feat(frontend):`, `docs:`) y el PR hacia `sprint-5`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias, pero bloqueada por la aprobación de Q1 a Q11.
- **Foundational (Fase 2)**: depende de la Fase 1 y bloquea todas las historias.
- **US1 (Fase 3)**: depende de la Fase 2.
- **US2 (Fase 4)**: depende de T015 (US1), porque extiende el mismo servicio.
- **US3 (Fase 5)**: depende de T004 y T015. Puede ir en paralelo con US2 porque toca archivos distintos, salvo `DebtWhistleService.java` y `DebtWhistleIT.java`, que se comparten. En ese caso, conviene secuenciar T024 antes de T028.
- **Polish (Fase 6)**: después de las historias que se decida entregar.

### Within Each User Story

- Los tests van antes de la implementación y deben fallar primero.
- Backend: builder → repositorio → servicio → controller.
- Frontend: cliente → componente → montaje en las vistas.

### Parallel Opportunities

- T002 se puede hacer en paralelo con la lectura de T001. T004 y T005 se pueden hacer en paralelo después de T002.
- Las pruebas de cada historia marcadas [P] (T006 a T012, T021 a T023, T026 y T027) se pueden escribir en paralelo.
- El frontend de US1 (T017 a T020) es independiente del backend hasta el quickstart.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fases 1 y 2.
2. US1 (T006 a T020).
3. **STOP and VALIDATE**: escenarios 1 a 4 y 9 del quickstart.
4. Si los 2 SP del cronograma no alcanzan, US1 ya cumple la actividad "aviso manual e instantáneo".

### Incremental Delivery

1. Migración, entidad, evento y errores.
2. US1: aviso funcional.
3. US2: límite anti-spam. **Recomendado antes de mergear**: sin él, el botón permite spam.
4. US3: historial.

---

## Notes

- [P] = archivos distintos, sin dependencias.
- No editar migraciones integradas. La única migración nueva es `V37`.
- No ejecutar `git add`, `git commit` ni `git push`. Los commits los hace Nahuel (T036).
- Marcar cada casillero solo después de verificar el comportamiento (DoD de la constitución).
