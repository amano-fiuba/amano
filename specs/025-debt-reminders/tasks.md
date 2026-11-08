---
description: "Task list for Recordatorios automáticos de deudas pendientes"
---

# Tasks: Recordatorios automáticos de deudas pendientes

**Input**: Design documents from `/specs/025-debt-reminders/`

**Prerequisites**: plan.md (requerido), spec.md (requerido para las historias), research.md, data-model.md, contracts/, quickstart.md

**Tests**: El plan y la constitución exigen pruebas automatizadas (unitarias puras, integración con Testcontainers + GreenMail, Vitest con cobertura ≥ 85 %). Las tareas de prueba están incluidas y se escriben ANTES de la implementación de cada historia.

**Organization**: Tareas agrupadas por historia de usuario para implementar y probar cada una de forma independiente (US1 → US2 → US3 → US4, según el orden de entrega incremental del plan).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: A qué historia pertenece (US1, US2, US3, US4)
- Incluir rutas de archivo exactas en cada descripción

## Path Conventions

- Monorepo web: `backend/src/main/java/com/amano/backend/`, `backend/src/main/resources/db/migration/`, `backend/src/test/java/com/amano/backend/`, `frontend/src/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verificaciones previas y configuración de entorno

- [x] T001 Verificar los supuestos de la sección "Verificaciones previas" de specs/025-debt-reminders/plan.md: `Group.debtDueDays` y `DebtStatus` en backend/src/main/java/com/amano/backend/groupexpense/DebtStatus.java, `Clock` inyectable en backend/src/main/java/com/amano/backend/config/TimeConfig.java, GreenMail en las pruebas de correo existentes, última migración integrada en backend/src/main/resources/db/migration/ y lista vigente del CHECK de `event_type` en `group_activity`; corregir plan.md y data-model.md si algo difiere
- [x] T002 [P] Agregar `reminders.cron=${REMINDERS_CRON:0 45 0 * * *}` a backend/src/main/resources/application.properties
- [x] T003 [P] Documentar `REMINDERS_CRON` en .env.example junto a `OVERDUE_CRON` (con nota de que debe ejecutarse después del job de vencimiento) y pasarlo al backend en compose.yaml y compose.prod.yaml con el valor predeterminado `0 45 0 * * *`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Migraciones, campos de configuración y entidad `Reminder` compartida por todas las historias

**⚠️ CRITICAL**: Ninguna historia puede comenzar hasta completar esta fase

- [x] T004 [P] Crear backend/src/main/resources/db/migration/V24__add_group_reminder_settings.sql: `ALTER TABLE groups ADD COLUMN reminder_send_days INTEGER NOT NULL DEFAULT 5;` y `ADD COLUMN reminder_message VARCHAR(500) NULL;`, más `CHECK (reminder_send_days IN (7, 5, 3, 1))` y `CHECK (reminder_message IS NULL OR char_length(reminder_message) <= 500)` (restricciones del data-model.md, sin editar migraciones ya integradas)
- [x] T005 [P] Crear backend/src/main/resources/db/migration/V25__create_reminders_table.sql con la tabla e invariantes del data-model.md, incluyendo el `CHECK` de coherencia `(kind='DEBT' AND debt_id IS NOT NULL AND expense_id IS NULL) OR (kind='PAYER_NOTICE' AND expense_id IS NOT NULL AND debt_id IS NULL)`, el `CHECK` de `status IN ('SCHEDULED','SENT','CANCELLED','FAILED')`, el `CHECK` de `SENT` con `sent_at/subject/body` no nulos y los índices `uq_reminders_debt (debt_id) WHERE kind='DEBT'`, `uq_reminders_expense (expense_id) WHERE kind='PAYER_NOTICE'` e `ix_reminders_due (group_id, status, send_date)`
- [x] T006 [P] Crear backend/src/main/resources/db/migration/V26__extend_group_activity_event_types_for_reminders.sql para recrear el CHECK de `group_activity.event_type` agregando `DEBT_REMINDER_SENT` y `PAYER_DUE_NOTICE_SENT`; la lista de tipos vigentes debe tomarse del CHECK real de `group_activity` (confirmado en T001), no copiarse a ciegas del data-model.md
- [x] T007 Extender backend/src/main/java/com/amano/backend/groups/Group.java con `reminderSendDays` (`Integer NOT NULL`, columna `reminder_send_days`, inicializada en `5` en Java porque el INSERT de JPA no aplica el DEFAULT de la BD) y `reminderMessage` (`String` nullable, máximo 500); depender de T004
- [x] T008 [P] Crear backend/src/main/java/com/amano/backend/groupexpense/reminder/ReminderKind.java (`DEBT`, `PAYER_NOTICE`) y ReminderStatus.java (`SCHEDULED`, `SENT`, `CANCELLED`, `FAILED`), y agregar los valores `DEBT_REMINDER_SENT` y `PAYER_DUE_NOTICE_SENT` al enum backend/src/main/java/com/amano/backend/groups/GroupActivity.java
- [x] T009 Crear backend/src/main/java/com/amano/backend/groupexpense/reminder/Reminder.java mapeando la tabla `reminders` según data-model.md: `id`, `groupId`, `kind`, `debtId` (nullable), `expenseId` (nullable), `sendDate` (`LocalDate`, NOT NULL), `status`, `claimedAt`, `sentAt`, `subject` (`TEXT`), `body` (`TEXT`), auditoría `createdAt/updatedAt`; depender de T005 y T008
- [x] T010 Crear backend/src/main/java/com/amano/backend/groupexpense/reminder/ReminderRepository.java con consultas para el barrido (por `group_id`, `status` y `send_date`), filas `SCHEDULED` de un grupo, el reclamo condicional de envío (`WHERE status='SCHEDULED' AND claimed_at IS NULL`) y el marcado final; depender de T009

**Checkpoint**: Base de datos y entidades listas - las historias pueden comenzar

---

## Phase 3: User Story 1 - Enviar recordatorio automático de deudas (Priority: P1) 🎯 MVP

**Goal**: Con la configuración predeterminada (5 días, mensaje predeterminado), cada deudor con deuda elegible recibe exactamente un correo en la fecha calculada, el envío queda registrado en el historial y los fallos se notifican a los administradores sin reintentos.

**Independent Test**: Con un grupo con `debtDueDays` configurado y un gasto que genera deudas, sin tocar la configuración de recordatorios, cada deudor con deuda sin saldar recibe exactamente un correo 5 días antes del vencimiento con grupo, concepto, monto y fecha límite; los miembros con deuda saldada no reciben nada y el envío aparece en el historial del grupo.

### Tests for User Story 1 (escribir primero, verificar que FALLAN)

- [x] T011 [P] [US1] Crear backend/src/test/java/com/amano/backend/groupexpense/reminder/ReminderScheduleTest.java (unitarias puras): cálculo de `dueDate = expenseDate + debtDueDays` y `sendDate = dueDate - reminderSendDays`; elegibilidad `status IN (PENDING, PARTIALLY_PAID, PAYMENT_REPORTED)`; exclusión de `PAID`, `VERIFIED` y `OVERDUE`; grupo sin `debtDueDays` no genera nada; `sendDate` ya pasada con `today <= dueDate` se envía en la evaluación siguiente; `today > dueDate` no se envía
- [x] T012 [P] [US1] Crear backend/src/test/java/com/amano/backend/groupexpense/reminder/ReminderMessageBuilderTest.java (unitarias puras): asunto `Recordatorio de deuda - {grupo}`; cuerpo predeterminado con nombre del deudor, grupo, monto con dos decimales y moneda, concepto y fecha límite `dd/MM/yyyy`; concepto nulo o vacío → "Gasto del grupo"; deuda `PAYMENT_REPORTED` incluye "pagado a verificar"; sin copia al acreedor
- [x] T013 [P] [US1] Crear backend/src/test/java/com/amano/backend/groupexpense/reminder/ReminderDebtFlowIT.java y ReminderFailureIT.java (Testcontainers + GreenMail): exactamente un correo por deuda y sin reenvíos en ejecuciones repetidas; deuda `PAID`/`VERIFIED` se cancela y no envía; `PAYMENT_REPORTED` envía con la leyenda; grupo sin `debtDueDays` no genera ni reporta error; `sendDate` pasado envía en la evaluación siguiente; deudor registrado `User.email` y deudor simulado `SimulatedProfile.email`; evento `DEBT_REMINDER_SENT` registrado solo por envíos exitosos; fallo de entrega → `FAILED`, correo a todos los ADMIN activos, sin reintento y sin bloquear los demás envíos; si el correo de aviso a los administradores también falla, solo se registra en el log sin propagar; grupo sin ADMIN activos → solo log, sin correo de aviso; fila reclamada huérfana (`claimed_at` no nulo) pasa a `FAILED` en la ejecución siguiente

### Implementation for User Story 1

- [x] T014 [US1] Implementar ReminderSchedule.java (dominio puro, sin Spring) en backend/src/main/java/com/amano/backend/groupexpense/reminder/ con el cálculo de fechas y la elegibilidad que cubren las pruebas de T011; depender de T010 y T011
- [x] T015 [US1] Implementar ReminderMessageBuilder.java (dominio puro) en backend/src/main/java/com/amano/backend/groupexpense/reminder/ con el formato del contrato (correo de deuda, textos en español con voseo) que cubre T012; depender de T012
- [x] T016 [US1] Implementar ReminderRecipients.java en backend/src/main/java/com/amano/backend/groupexpense/reminder/ para resolver correos de `User` y `SimulatedProfile`, y agregar la consulta de administradores activos (`role='ADMIN'` con `left_at IS NULL`) a backend/src/main/java/com/amano/backend/groups/GroupMemberRepository.java si no existe; depender de T010
- [x] T017 [US1] Implementar ReminderMaterializer.java en backend/src/main/java/com/amano/backend/groupexpense/reminder/: crear las filas `DEBT` faltantes de deudas elegibles (una por deuda, respetando el índice único), recalcular `send_date` de las `SCHEDULED` con `claimed_at IS NULL` con la configuración vigente y cancelar las `SCHEDULED` saldadas, vencidas o sin fecha límite (las `SENT` y `FAILED` no se tocan); depender de T014 y T010
- [x] T018 [US1] Implementar ReminderMailService.java en backend/src/main/java/com/amano/backend/groupexpense/reminder/ con `JavaMailSender` + `MimeMessageHelper` y `setText(body, false)`: envío de recordatorio de deuda y correo de fallo a los administradores (`No se pudo enviar un recordatorio - {grupo}`, con destinatario, fecha límite y monto, texto del contrato); NO usar `@Async` y propagar la excepción para que el despachador marque `FAILED`; depender de T015 y T016
- [x] T019 [US1] Agregar `recordDebtReminderSent` a backend/src/main/java/com/amano/backend/groups/ActivityEventService.java con los detalles JSON del contrato (`reminderId`, `debtId`, `expenseId`, `amount`, `currency`, `fromName`, `toName`, `dueDate` ISO, `actor` nulo); depender de T008
- [x] T020 [US1] Implementar ReminderDispatcher.java en backend/src/main/java/com/amano/backend/groupexpense/reminder/: reclamar con la actualización condicional de T010, enviar fuera de la transacción, marcar `SENT` (con `sent_at`, `subject`, `body`) registrando el evento vía T019 en la misma transacción, o `FAILED` capturando la excepción de T018 y avisando a los administradores sin bloquear el resto (si no hay ADMIN activos, solo se registra el fallo en el log); una fila con `claimed_at` no se reintenta; depender de T017, T018 y T019
- [x] T021 [US1] Implementar ReminderJob.java en backend/src/main/java/com/amano/backend/groupexpense/reminder/ con `@Scheduled(cron = "${reminders.cron:0 45 0 * * *}", zone = "America/Argentina/Buenos_Aires")` y `LocalDate.now(clock)`: por cada grupo con `debtDueDays` o con recordatorios programados, materializar (T017) y despachar (T020); depender de T017 y T020
- [x] T022 [P] [US1] Agregar en frontend/src/pages/groups/activityDescriptions.js y su prueba activityDescriptions.test.js la frase de `DEBT_REMINDER_SENT` ("envió el recordatorio de la deuda de {monto} a {deudor}") y el filtro "Recordatorios"; depender de T008

**Checkpoint**: US1 completa y probada de forma independiente: recordatorios de deuda con la configuración predeterminada, historial y manejo de fallos

---

## Phase 4: User Story 2 - Personalizar tiempo de envío (Priority: P2)

**Goal**: El Administrador elige el tiempo de envío (7/5/3/1) y los recordatorios se reprograman; los ya enviados no cambian. Incluye los endpoints GET/PUT de configuración (contrato completo).

**Independent Test**: Con US1 funcionando, el Administrador cambia el tiempo de 5 a 7 días; una deuda nueva se programa para D − 7, un recordatorio programado se reprograma y uno ya enviado no cambia. Un miembro no administrador recibe 403 y un no miembro 404.

> Nota de alcance: el `PUT` valida y guarda también el `message` (recorte, vacío → `NULL`, máximo 500) para respetar el contrato completo; el uso del mensaje en los correos llega con US4.

### Tests for User Story 2 (escribir primero, verificar que FALLAN)

- [x] T023 [P] [US2] Crear backend/src/test/java/com/amano/backend/groups/ReminderSettingsIT.java (Testcontainers): `GET` devuelve `{"sendDays":5,"message":null,"defaultSendDays":5,"allowedSendDays":[7,5,3,1]}`; `PUT` con `sendDays=7` responde 200 con los valores guardados y reprograma las filas `SCHEDULED` con `claimed_at IS NULL` (`send_date = dueDate - sendDays`); cuerpo vacío `{}` no cambia nada; `message` de 501 caracteres → 400 con `properties.fields.message = "El mensaje no puede superar los 500 caracteres"`; `sendDays=4` → 400 con `properties.fields.sendDays = "El tiempo de envío debe ser 7, 5, 3 o 1 días antes"`; miembro no administrador → 403; no miembro o grupo inexistente → 404 neutral; filas `SENT` y `FAILED` no cambian
- [x] T024 [P] [US2] Crear frontend/src/components/groups/GroupReminderSettingsPanel.test.jsx (Vitest + Testing Library): el panel se muestra solo cuando `isAdmin` es verdadero; selector con "7 días antes", "5 días antes", "3 días antes" y "1 día antes"; consulta los valores al montar y muestra el error del backend en el formulario con snackbar de éxito; depender de T027

### Implementation for User Story 2

- [x] T025 [US2] Implementar GroupReminderSettingsService.java en backend/src/main/java/com/amano/backend/groups/: lectura de la configuración, permisos (solo `ADMIN` activo; 403 para miembro no administrador y 404 neutral para no miembro/grupo inexistente, reutilizando `GroupPermissionChecker` y `ProblemDetailExceptionHandler` en backend/src/main/java/com/amano/backend/common/), validación de `sendDays` contra [7, 5, 3, 1] y de la longitud del `message` (recortado; vacío → `NULL`; > 500 rechazado), actualización atómica (sin cambios ante error) y reprogramación inmediata de las filas `SCHEDULED` no intentadas del grupo vía ReminderRepository; depender de T007 y T010
- [x] T026 [US2] Implementar backend/src/main/java/com/amano/backend/groups/dto/ReminderSettingsRequest.java, ReminderSettingsResponse.java y GroupReminderSettingsController.java con `GET`/`PUT /api/groups/{id}/reminder-settings` según contracts/reminder-settings-api.md (actualización parcial; campos ausentes conservan el valor); depender de T025
- [x] T027 [US2] Agregar `getReminderSettings`/`updateReminderSettings` a frontend/src/api/groupsClient.js (con su prueba en groupsClient.test.js) y montar GroupReminderSettingsPanel dentro de la configuración del grupo en frontend/src/pages/groups/GroupInfoView.jsx (mismo ancho y estilo que GroupPermissionsPanel, sin bloquear el resto ante error de carga); depender de T024 y T026

**Checkpoint**: US1 + US2 completas e independientes: envío base y configuración de tiempo verificables

---

## Phase 5: User Story 3 - Avisar al pagador que se acerca el vencimiento (Priority: P2)

**Goal**: Un aviso por gasto con al menos una deuda elegible, enviado al pagador en la misma fecha de envío, con días restantes y su situación de deudas en el grupo; comparte la máquina de estados y los fallos de US1.

**Independent Test**: Con US1 funcionando, un gasto pagado por Ana con deudas pendientes y fecha límite D genera en D − 5 un aviso a Ana con el tiempo restante y si debe o no (monto total por moneda o "No tenés deudas pendientes en el grupo"); si todas las deudas del gasto se saldan antes de la fecha, no se envía el aviso.

### Tests for User Story 3 (escribir primero, verificar que FALLAN)

- [x] T028 [P] [US3] Ampliar backend/src/test/java/com/amano/backend/groupexpense/reminder/ReminderMessageBuilderTest.java y ReminderScheduleTest.java: asunto `Aviso de vencimiento - {grupo}`; cuerpo con grupo, concepto, fecha límite y `n = ChronoUnit.DAYS.between(today, dueDate)` (puede ser 0); el pagador con deudas pendientes ve "También tenés deudas pendientes en el grupo por {total} {moneda}" (una línea por moneda, usando el cálculo de monto pendiente existente de `Debt`, `amount - paidAmount`); sin deudas → "No tenés deudas pendientes en el grupo"; la parte del pagador en su propio gasto no se suma; no se usa el mensaje personalizado
- [x] T029 [P] [US3] Crear backend/src/test/java/com/amano/backend/groupexpense/reminder/PayerNoticeIT.java (Testcontainers + GreenMail): un aviso por gasto (índice único por `expense_id`); sin deudas elegibles → `SCHEDULED` se cancela y no se envía; dos gastos del mismo pagador con la misma fecha límite → un aviso por gasto; evento `PAYER_DUE_NOTICE_SENT` con `pendingTotals: []` cuando no debe y con los totales por moneda cuando debe; cambio posterior de configuración no reenvía; el mismo fallo de entrega de T013 aplica al aviso

### Implementation for User Story 3

- [x] T030 [US3] Extender ReminderMaterializer.java para crear filas `PAYER_NOTICE` por gasto con al menos una deuda elegible (una por gasto), reprogramarlas y cancelarlas con las mismas reglas que los `DEBT`; depender de T010 y T017
- [x] T031 [US3] Extender ReminderMessageBuilder.java con el correo del aviso al pagador que cubre T028 (incluye el cálculo del total pendiente del pagador agrupado por moneda); depender de T028
- [x] T032 [US3] Agregar `recordPayerDueNoticeSent` a backend/src/main/java/com/amano/backend/groups/ActivityEventService.java con los detalles del contrato (`reminderId`, `expenseId`, `payerName`, `concept`, `dueDate`, `daysRemaining`, `pendingTotals`); extender ReminderDispatcher.java para el `kind` `PAYER_NOTICE` reutilizando reclamo, envío y manejo de fallos; depender de T008, T020 y T031
- [x] T033 [P] [US3] Agregar en frontend/src/pages/groups/activityDescriptions.js y activityDescriptions.test.js la frase de `PAYER_DUE_NOTICE_SENT` ("envió el aviso de vencimiento del gasto «{concepto}» a {pagador}") e implementar los enlaces al detalle del gasto para ambos eventos con `expenseId` en frontend/src/pages/groups/GroupHistoryView.jsx y frontend/src/components/groups/GroupFeed.jsx; depender de T022 y T032

**Checkpoint**: US1 + US2 + US3 completas: recordatorios de deuda y aviso al pagador funcionando con la misma base

---

## Phase 6: User Story 4 - Personalizar mensaje del recordatorio (Priority: P3)

**Goal**: El Administrador guarda un mensaje de hasta 500 caracteres que acompaña a los datos de la deuda en los próximos recordatorios; en blanco vuelve al predeterminado; los ya enviados no cambian.

**Independent Test**: Con US1/US2 funcionando, guardar un mensaje personalizado → el próximo recordatorio lo incluye junto a grupo, concepto, monto y fecha límite; guardar 501 caracteres → rechazado; guardar en blanco → vuelve al predeterminado.

### Tests for User Story 4 (escribir primero, verificar que FALLAN)

- [x] T034 [P] [US4] Ampliar backend/src/test/java/com/amano/backend/groupexpense/reminder/ReminderMessageBuilderTest.java, ReminderDebtFlowIT.java y ReminderSettingsIT.java: correo con mensaje personalizado seguido del bloque de datos (el mensaje acompaña, no reemplaza); mensaje en blanco → `message = null` y correo predeterminado; `PAYMENT_REPORTED` conserva la leyenda también con mensaje personalizado; los correos ya `SENT` no cambian cuando se modifica el mensaje
- [x] T035 [P] [US4] Ampliar frontend/src/components/groups/GroupReminderSettingsPanel.test.jsx: campo de texto de hasta 500 caracteres con contador y ayuda "Si lo dejás vacío, se usa el mensaje predeterminado"; guardar un mensaje lo persiste; quedar en blanco al guardar y recargar muestra valor vacío; error de validación del backend mostrado en el formulario

### Implementation for User Story 4

- [x] T036 [US4] Extender ReminderMessageBuilder.java con la variante de mensaje personalizado que cubre T034 (los datos de identificación siempre presentes); depender de T034
- [x] T037 [US4] Completar frontend/src/components/groups/GroupReminderSettingsPanel.jsx con el textarea del mensaje, contador de caracteres y manejo de errores; depender de T027 y T035

**Checkpoint**: Las cuatro historias completas e independientemente verificables

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verificaciones obligatorias, validación de punta a punta y preparación del PR

- [x] T038 Ejecutar `mvn -B verify` en backend/ con Docker disponible: pruebas en verde, cobertura JaCoCo ≥ 85 % y correr también `mvn -B test -Dtest='*Reminder*Test'` y `mvn -B verify -Dit.test='*Reminder*IT'` para acotar la feature; depender de todas las historias
- [x] T039 [P] Ejecutar `npm ci && npm run lint && npm run test:coverage && npm run build` en frontend/: cobertura Vitest ≥ 85 % y build exitoso
- [ ] T040 Ejecutar la validación de specs/025-debt-reminders/quickstart.md: entorno con Docker Compose, datos de prueba (grupo con `debtDueDays`, gasto pagado por Ana con Juan y un perfil simulado a 5 días del vencimiento), `REMINDERS_CRON` cada minuto, casos límite manuales de la sección 2.6 y verificación de la sección 3 del frontend; depender de T038 y T039
- [ ] T041 Revisión final: commits convencionales en español por unidad coherente, `git diff` sin artefactos de depuración ni información sensible, contratos REST aditivos confirmados contra backend/src/main/java/com/amano/backend/groups/GroupController.java y el nuevo controlador de settings, y preparar el PR a `sprint-n` según la constitución (principios XII y XIII)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias; T001 conviene hacerla primero porque valida los supuestos que T004–T006 asumen
- **Foundational (Phase 2)**: Depende del Setup; BLOQUEA todas las historias
- **User Stories (Phase 3–6)**: Dependen de la fase Foundational; el orden recomendado es US1 → US2 → US3 → US4 (US2 reprograma lo que US1 crea; US3 reutiliza el despachador de US1; US4 usa la configuración de US2 y el builder de US1), pero US3 y US4 pueden ejecutarse en paralelo entre sí una vez integrados US1 y US2
- **Polish (Phase 7)**: Depende de todas las historias deseadas completas

### User Story Dependencies

- **User Story 1 (P1)**: Empezar después de Foundational; no depende de otras historias
- **User Story 2 (P2)**: Después de Foundational; usa el repositorio y el materializador de US1 para reprogramar
- **User Story 3 (P2)**: Después de US1; extiende materializador, builder y despachador existentes
- **User Story 4 (P3)**: Después de US2; completa el builder y el panel existentes

### Within Each User Story

- Las pruebas (T011–T013, T023–T024, T028–T029, T034–T035) se escriben primero y DEBEN fallar antes de implementar
- Dominio puro antes de servicios con Spring
- Materializador y correo antes del despachador
- El job programado es el último componente backend (T021)
- El frontend de cada historia va después de su backend

### Parallel Opportunities

- Setup: T002 y T003 en paralelo (tras T001)
- Foundational: T004, T005, T006 y T008 en paralelo; T007, T009, T010 secuencian por dependencia
- Dentro de cada historia: todas las pruebas [P] del bloque de tests en paralelo
- Historias en paralelo (con dos integrantes): US3 y US4 pueden avanzar en paralelo tras integrar US1 + US2
- T022 y T033 (frontend del historial) en paralelo con el backend de sus respectivas historias

---

## Parallel Example: User Story 1

```bash
# Lanzar las pruebas unitarias e integración de US1 juntas:
Task: "Crear ReminderScheduleTest.java en backend/src/test/java/com/amano/backend/groupexpense/reminder/"
Task: "Crear ReminderMessageBuilderTest.java en backend/src/test/java/com/amano/backend/groupexpense/reminder/"
Task: "Crear ReminderDebtFlowIT.java y ReminderFailureIT.java (Testcontainers + GreenMail)"

# Después, la implementación encadena: T014/T015/T016 → T017/T018/T019 → T020 → T021
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1: Setup (T001 primero)
2. Completar Phase 2: Foundational (CRÍTICO - bloquea todas las historias)
3. Completar Phase 3: User Story 1
4. **STOP and VALIDATE**: probar US1 de forma independiente (`mvn -B verify` + verificación manual del job)
5. Entregar/demostrar si está listo

### Incremental Delivery

1. Setup + Foundational → fundación lista
2. US1 → probar de forma independiente → demo (recordatorios con valores predeterminados)
3. US2 → probar → endpoints de configuración y reprogramación
4. US3 → probar → aviso al pagador
5. US4 → probar → mensaje personalizado y panel completo
6. Polish → verificaciones obligatorias, quickstart y PR

### Parallel Team Strategy

Con dos integrantes:

1. El equipo completa Setup + Foundational juntos
2. Cuando Foundational termine:
   - Integrante A: US1, luego US2
   - Integrante B: espera a US1+US2 integradas y toma US3;
   - Después ambos pueden dividirse US4 y las verificaciones de Polish

---

## Notes

- Las tareas [P] operan en archivos distintos sin dependencias pendientes
- El rótulo [Story] garantiza la trazabilidad con spec.md (FR/SC)
- Cada historia debe poder completarse y probarse de forma independiente
- Verificar que las pruebas fallen antes de implementar (TDD de cada historia)
- Commits chicos y frecuentes en español con Conventional Commits (constitución XIII); no hacer push sin pedido explícito
- Detenerse en cualquier checkpoint para validar la historia de forma independiente
- Evitar: tareas vagas, conflictos de archivo, dependencias entre historias que rompan su independencia
