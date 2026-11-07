# Implementation Plan: Recordatorios automáticos de deudas pendientes

**Branch**: `feat/envio-recordatorio-deudas` | **Date**: 2026-11-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/025-debt-reminders/spec.md`

## Summary

La 025 agrega el envío automático de recordatorios por correo a los deudores de un grupo y un aviso al pagador del gasto, ambos con fecha calculada a partir del vencimiento que ya define la 022. **No crea estados de deuda, no modifica pagos y no reimplementa el vencimiento**: reutiliza `DebtStatus`, `Group.debtDueDays`, el historial de actividad y la infraestructura de correo existente.

El plan es una extensión acotada:

1. Una configuración única por grupo (tiempo de envío de la lista fija 7/5/3/1 días y mensaje opcional de hasta 500 caracteres), editable solo por un administrador. Los valores predeterminados son 5 días y mensaje predeterminado.
2. Una tabla `reminders` compartida por los dos tipos de envío (`DEBT` y `PAYER_NOTICE`), con estados `SCHEDULED`, `SENT`, `CANCELLED` y `FAILED`, una fila por deuda o por gasto, y el contenido realmente enviado guardado como instantánea.
3. Una tarea diaria `@Scheduled` posterior a la de vencimiento que materializa recordatorios, los reprograma cuando cambia la configuración, cancela los que ya no corresponden y envía los que llegan a su fecha. Es idempotente y segura ante varias instancias.
4. Un servicio de correo propio que, a diferencia de los de verificación y recuperación, propaga el fallo para poder marcarlo como `FAILED` y avisar a los administradores, sin reintentar.
5. Dos eventos nuevos del historial (`DEBT_REMINDER_SENT` y `PAYER_DUE_NOTICE_SENT`) y el panel de configuración en la pantalla del grupo.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1; Spring Mail (`JavaMailSender`), Spring Scheduling, Spring Data JPA, React, react-router, Vite. **Sin dependencias nuevas.**

**Storage**: PostgreSQL con migraciones Flyway en `backend/src/main/resources/db/migration`. Se agregan tres migraciones nuevas (V24, V25 y V26); las ya integradas no se editan.

**Testing**: JUnit 5 + Mockito, Spring Boot Test + Testcontainers, GreenMail en proceso para el correo, Vitest + Testing Library. Umbrales JaCoCo y Vitest ≥ 85 %; no se bajan. Las pruebas nunca contactan un SMTP real.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: La evaluación diaria recorre solo grupos con `debtDueDays` configurado o con recordatorios programados, y sus deudas abiertas; el volumen esperado (grupos y deudas de la aplicación) no exige caché ni procesamiento por lotes especial. El envío de correos es secuencial y acotado por la cantidad de recordatorios que vencen ese día.

**Constraints**:
- Sin punto flotante: montos con `BigDecimal` escala 2.
- Fechas a nivel de día, con `Clock` inyectable y zona `America/Argentina/Buenos_Aires`.
- Solo correo electrónico; sin canales nuevos.
- Sin reintentos de entrega.
- Solo el Administrador consulta y modifica la configuración.
- Textos en español con voseo.
- Contratos REST aditivos.

**Scale/Scope** (estimado): backend con unas 12 clases nuevas (entidad y repositorio de recordatorios, cálculo de fechas, armado de mensajes, servicio de configuración, despachador, tarea programada, servicio de correo, controlador y DTOs) y unas 6 clases modificadas (`Group`, `GroupActivity`, `ActivityEventService`, `GroupMemberRepository`, `GroupController` o nuevo controlador, `ProblemDetailExceptionHandler` si hiciera falta); 3 migraciones. Frontend con 1 panel nuevo, 1 archivo de cliente API modificado, 2 archivos de historial/actividad modificados y pruebas.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura | PASS | Extiende `groups` y `groupexpense`; reutiliza `DebtStatus`, `Group.debtDueDays`, `ActivityEventService`, `JavaMailSender` y `@Scheduled`. No hay capas ni proyectos nuevos. |
| II | Desarrollo guiado por especificaciones | PASS | Plan derivado de la spec 025 y sus clarificaciones del 2026-11-07; cada decisión remite a FR/SC. |
| III | Entrega incremental | PASS | Orden por historia: US1 (envío base) → US2 (tiempo) → US3 (aviso al pagador) → US4 (mensaje). US2 y US4 reutilizan la base de US1; US3 comparte el despachador. |
| IV | Alcance controlado | PASS | No se agregan estados, canales ni reintentos; no se toca el flujo de pagos ni el vencimiento de 022. La configuración de `debtDueDays` sigue siendo de 022. |
| V | Calidad y cobertura | PASS | Unitarias puras, integración con Testcontainers y GreenMail, y Vitest. Se cubren casos límite y errores previstos. |
| VI | Integridad de reglas | PASS | Solo se excluyen `PAID` y `VERIFIED`; `PAYMENT_REPORTED` recibe recordatorio con la leyenda "pagado a verificar"; una deuda vencida no recibe recordatorio. No se inventan transiciones. |
| VII | Contratos REST | PASS | Dos endpoints aditivos para la configuración y dos eventos nuevos del historial; el contrato existente no cambia de forma. |
| VIII | Validación y errores | PASS | Validación de `sendDays` y longitud del mensaje en el backend; 400 con campos, 403 sin rol, 404 neutral para no miembros. Los fallos de correo no exponen detalles internos. |
| IX | Seguridad | PASS | Autorización en el servidor: solo `ADMIN` activo consulta y modifica; el envío lo decide el sistema. No se confía en el frontend. |
| X | Código mantenible | PASS | Una tabla y un despachador compartidos para los dos tipos de envío; nombres técnicos en inglés, textos en español. |
| XI | Entorno reproducible | PASS | Migraciones versionadas, cron y zona configurables por propiedades/variables de entorno; pruebas con GreenMail, sin SMTP real. |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto. |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Conventional Commits al implementar. |

**Gate resultado**: PASS. No hay violaciones que justificar.

## Verificaciones previas (primera tarea del feature)

Antes de implementar hay que confirmar estos puntos; si alguno cambia, se corrige el plan:

1. `Group` ya tiene `debtDueDays` (`groups.debt_due_days`, V17) y `DebtStatus.isSettled()` distingue `PAID`/`VERIFIED`. Confirmado en el código al planificar.
2. La fecha de vencimiento de una deuda es `GroupExpense.expenseDate + Group.debtDueDays`; el job de 022 usa el mismo cálculo con límite estricto (vence el día siguiente al vencimiento).
3. `@EnableScheduling` está activo y existe un `Clock` inyectable en `TimeConfig`. Confirmado.
4. `JavaMailSender` está configurado y los tests usan GreenMail. Confirmado.
5. `ActivityEventService` escribe dentro de la transacción de negocio y el `CHECK` de `group_activity.event_type` se extiende con una migración nueva.
6. La última migración integrada es `V23`; las nuevas son `V24`, `V25` y `V26`.
7. `GroupMemberRepository` permite obtener miembros activos y administradores; si falta una consulta de administradores activos, se agrega sin cambiar datos.
8. La pantalla de configuración del grupo ya muestra un panel solo para `ADMIN`; el panel de recordatorios se suma ahí.
9. El frontend ya traduce los eventos del historial en `activityDescriptions.js`; los dos eventos nuevos se agregan ahí.
10. No existe todavía una configuración de `debtDueDays` en el frontend de 022. La 025 **no** la implementa: los escenarios manuales la fijan por API/BD, y la feature funciona igual cuando 022 la exponga.

## Decisiones de diseño

**D1. Reutilizar estados y vencimiento.** No se crean estados ni transiciones. La fecha límite de una deuda es `expenseDate + group.debtDueDays` (definición de 022). Si el grupo no tiene `debtDueDays`, la deuda no genera recordatorio ni error. Son elegibles `PENDING`, `PARTIALLY_PAID` (legado) y `PAYMENT_REPORTED`; quedan excluidas `PAID`, `VERIFIED` y `OVERDUE`. La deuda vencida ya la registra el job de 022.

**D2. Configuración en `groups`.** Se agregan dos columnas a `groups`: `reminder_send_days INTEGER NOT NULL DEFAULT 5` con `CHECK (reminder_send_days IN (7, 5, 3, 1))` y `reminder_message VARCHAR(500) NULL` con `CHECK (reminder_message IS NULL OR char_length(reminder_message) <= 500)`. Los grupos existentes quedan en 5 días y sin mensaje. El mensaje en blanco se normaliza a `NULL` (vuelve al predeterminado). La configuración se expone con endpoints dedicados y **solo** para un `ADMIN` activo, sin depender de la casilla `members_can_edit_group_data`.

**D3. Una entidad `Reminder` compartida.** La tabla `reminders` tiene `kind` (`DEBT` o `PAYER_NOTICE`), `debt_id` o `expense_id` según el tipo, `send_date`, `status`, `claimed_at`, `sent_at`, `subject` y `body`. Una fila por deuda y una por gasto, garantizado con índices únicos parciales. Esto evita duplicar la lógica de programación, cancelación, intento y estados entre los dos tipos de envío, y refleja que la spec dice que el aviso al pagador "comparte las reglas y estados del recordatorio".

**D4. Fecha de envío y elegibilidad.** `sendDate = dueDate - reminderSendDays`. Se envía cuando `sendDate <= today <= dueDate`. Si `sendDate` ya pasó pero la fecha límite no, se envía en la evaluación siguiente. Si la deuda ya está vencida (`today > dueDate` o estado `OVERDUE`), no se envía y el recordatorio programado se cancela. El cálculo es una función pura y testeable (`ReminderSchedule`).

**D5. Materialización, reprogramación y cancelación.** La tarea diaria, para cada grupo con `debtDueDays` (y también para los grupos que tengan recordatorios programados aunque hayan perdido los días de vencimiento):
- crea las filas faltantes de deudas elegibles y de gastos con al menos una deuda elegible;
- recalcula `send_date` de las filas `SCHEDULED` no intentadas con la configuración vigente;
- cancela las `SCHEDULED` no intentadas cuyo objetivo se saldó, venció o quedó sin fecha límite;
- envía las que corresponden.

La configuración también reprograma de inmediato las filas `SCHEDULED` no intentadas del grupo, para que el cambio de tiempo se refleje sin esperar a la tarea. Las filas `SENT` y `FAILED` no se tocan.

**D6. Entrega con un único intento y sin duplicados.** Antes de enviar, la fila se "reclama" con una actualización condicional (`status = 'SCHEDULED' AND claimed_at IS NULL`). El envío ocurre fuera de la transacción; después se marca `SENT` (con asunto, cuerpo e instante) o `FAILED`. Una fila con `claimed_at` no se vuelve a intentar: si el proceso se cae después de reclamar y antes de terminar, la siguiente ejecución la marca `FAILED` y avisa al administrador. Esto da semántica de **un único intento** (sin reintentos) y evita correos duplicados, a costa de poder perder un envío ante una caída excepcional, tal como pide la spec.

**D7. Contenido de los correos.** `ReminderMessageBuilder` es una clase pura que arma asunto y cuerpo en texto plano:
- Recordatorio de deuda: si no hay mensaje personalizado, se usa el texto predeterminado que incluye nombre del grupo, concepto del gasto, monto y moneda, y fecha límite. Si hay mensaje personalizado, el cuerpo es ese mensaje seguido de un bloque con los datos de la deuda (los datos nunca faltan). Si el gasto no tiene descripción, el concepto es "Gasto del grupo". Si la deuda está en `PAYMENT_REPORTED`, se agrega "pagado a verificar: tu pago está informado y pendiente de verificación".
- Aviso al pagador: grupo, concepto, fecha límite y días restantes; luego indica si el pagador tiene deudas pendientes en el grupo (monto total por moneda) o que no debe nada. No usa el mensaje personalizado; tiene texto predeterminado propio.
Los nombres salen de `User` (nombre y apellido o correo) o de `SimulatedProfile.name`. Los correos se envían con `MimeMessageHelper` y `setText(..., false)` (sin HTML).

**D8. Destinatarios.** Deudor registrado: `User.email`. Deudor simulado: `SimulatedProfile.email` cargado por el administrador. Pagador del gasto: siempre un usuario registrado (017). Los administradores que reciben el aviso de fallo son todos los `ADMIN` activos del grupo; si no hay ninguno, se registra el fallo en el log.

**D9. Fallos de entrega.** `ReminderMailService` no usa `@Async` ni se traga las excepciones (a diferencia de los correos de 003/006). Si el envío falla, el recordatorio queda `FAILED`, no se reintenta, no se registra en el historial y se envía un correo a los administradores indicando el recordatorio y el destinatario. Un fallo no bloquea los demás envíos. Si el aviso a los administradores también falla, solo se registra en el log.

**D10. Historial de actividad.** Se agregan dos tipos al enum y al `CHECK` de `group_activity` (V26): `DEBT_REMINDER_SENT` y `PAYER_DUE_NOTICE_SENT`. Se registran **solo los envíos exitosos**, en la misma transacción que marca `SENT`. Los detalles incluyen `reminderId`, `debtId` o `expenseId`, monto, moneda, deudor/pagador y fecha límite; para el aviso, también el concepto del gasto, los días restantes y el total pendiente del pagador. El frontend agrega las frases y el filtro correspondiente.

**D11. Concurrencia e idempotencia.** Los índices únicos parciales impiden dos filas para la misma deuda o gasto. Las transiciones usan actualizaciones condicionales por estado y `claimed_at`, de modo que dos instancias no envían dos veces. La tarea se puede ejecutar varias veces con el mismo resultado.

**D12. Sin dependencias nuevas.** Se reutilizan `JavaMailSender`, `@Scheduled`, `Clock`, JPA/Flyway, Jackson y las utilidades existentes. No se agrega Quartz, Redis, Caffeine ni una librería de plantillas.

**D13. Zona y reloj.** La tarea usa `LocalDate.now(clock)` y la zona de `TimeConfig` (`America/Argentina/Buenos_Aires`), igual que el vencimiento de 022. El cron por defecto es `0 45 0 * * *` (después del de vencimiento, `0 30 0 * * *`) y se configura con `REMINDERS_CRON`.

**D14. Fecha límite y "tiempo restante".** En el aviso al pagador, los días restantes se calculan como `dueDate - today` al momento del envío (puede ser 0 si se envía el mismo día del vencimiento). No se usa la cantidad configurada si el envío se atrasó.

**D15. Alcance del aviso al pagador.** Se envía un aviso por gasto con al menos una deuda elegible al llegar la fecha. Si todas las deudas del gasto se saldan antes, no se envía y el programado se cancela. Las deudas `PAYMENT_REPORTED` cuentan como pendientes. La parte del pagador en su propio gasto no es deuda y no se suma.

## Project Structure

### Documentation (this feature)

```text
specs/025-debt-reminders/
├── plan.md                  # Este archivo
├── research.md              # Fase 0
├── data-model.md            # Fase 1
├── quickstart.md            # Fase 1
├── contracts/
│   ├── reminder-settings-api.md
│   └── reminder-delivery-and-activity.md
├── checklists/requirements.md
└── tasks.md                 # Fase 2 (/speckit.tasks), no lo crea este comando
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/
├── groups/
│   ├── Group.java                                  # modificado: reminderSendDays, reminderMessage
│   ├── GroupMemberRepository.java                  # consulta de administradores activos (si falta)
│   ├── GroupActivity.java                          # enum con DEBT_REMINDER_SENT y PAYER_DUE_NOTICE_SENT
│   ├── ActivityEventService.java                   # métodos recordDebtReminderSent y recordPayerDueNoticeSent
│   ├── GroupReminderSettingsController.java        # nuevo: GET/PUT /api/groups/{id}/reminder-settings
│   ├── GroupReminderSettingsService.java           # nuevo: validación, permisos y reprogramación
│   └── dto/
│       ├── ReminderSettingsRequest.java            # nuevo
│       └── ReminderSettingsResponse.java           # nuevo
└── groupexpense/reminder/                          # nuevo subpaquete
    ├── Reminder.java                               # entidad y transiciones
    ├── ReminderKind.java                           # DEBT, PAYER_NOTICE
    ├── ReminderStatus.java                         # SCHEDULED, SENT, CANCELLED, FAILED
    ├── ReminderRepository.java
    ├── ReminderSchedule.java                       # dominio puro: dueDate, sendDate, elegibilidad
    ├── ReminderMessageBuilder.java                 # dominio puro: asunto y cuerpo
    ├── ReminderMaterializer.java                   # crea, reprograma y cancela filas
    ├── ReminderDispatcher.java                     # reclama, envía y completa
    ├── ReminderMailService.java                    # correo de recordatorio/aviso/fallo
    ├── ReminderRecipients.java                     # resuelve correos y administradores
    └── ReminderJob.java                            # @Scheduled diario

backend/src/main/resources/
├── application.properties                          # reminders.cron y zona
└── db/migration/
    ├── V24__add_group_reminder_settings.sql
    ├── V25__create_reminders_table.sql
    └── V26__extend_group_activity_event_types_for_reminders.sql

backend/src/test/java/com/amano/backend/groupexpense/reminder/   # pruebas espejo
backend/src/test/java/com/amano/backend/groups/                  # pruebas de settings y actividad

frontend/src/
├── api/groupsClient.js                             # getReminderSettings / updateReminderSettings
├── components/groups/
│   ├── GroupReminderSettingsPanel.jsx              # nuevo panel solo ADMIN
│   └── GroupReminderSettingsPanel.test.jsx
├── pages/groups/GroupInfoView.jsx                  # monta el panel en la configuración
├── pages/groups/activityDescriptions.js            # frases y filtro de los eventos nuevos
├── pages/groups/GroupHistoryView.jsx               # enlaces a gasto para los eventos nuevos
└── components/groups/GroupFeed.jsx                 # idem
```

**Structure Decision**: Se mantiene el monorepo existente (backend REST + frontend SPA). La configuración del grupo vive en `groups`; la programación y el envío de recordatorios viven en un subpaquete de `groupexpense`, junto a las deudas que los originan. No se crean proyectos ni capas nuevas.

## API (aditiva)

| Endpoint | Quién | Notas |
|----------|-------|-------|
| `GET /api/groups/{id}/reminder-settings` | `ADMIN` activo | Devuelve `sendDays`, `message`, `defaultSendDays` y `allowedSendDays`. 403 para miembros no administradores; 404 neutral para no miembros o grupo inexistente. |
| `PUT /api/groups/{id}/reminder-settings` | `ADMIN` activo | Actualiza `sendDays` y/o `message`; valida la lista fija y los 500 caracteres; reprograma los recordatorios programados no intentados. 400 con campos; 403; 404. |

No se agregan endpoints públicos para listar o forzar recordatorios. El envío lo controla la tarea programada y el historial de actividad es la vía de consulta.

## Migraciones

Son tres migraciones nuevas, versionadas e inmutables una vez integradas:

1. **V24 — `groups`**: `reminder_send_days INTEGER NOT NULL DEFAULT 5` y `reminder_message VARCHAR(500) NULL`; `CHECK` de la lista fija y de la longitud del mensaje. Los grupos existentes quedan con los valores predeterminados.
2. **V25 — `reminders`**: tabla con `kind`, `debt_id`, `expense_id`, `send_date`, `status`, `claimed_at`, `sent_at`, `subject`, `body`, `created_at` y `updated_at`; `CHECK` de coherencia entre `kind` y el objetivo; índices únicos parciales por deuda y por gasto; índice de barrido por `group_id`, `status` y `send_date`.
3. **V26 — `group_activity`**: se recrea el `CHECK` de `event_type` para admitir `DEBT_REMINDER_SENT` y `PAYER_DUE_NOTICE_SENT` sin perder los eventos existentes.

No se edita ninguna migración ya integrada.

## Configuración de entorno

- `application.properties`: `reminders.cron=${REMINDERS_CRON:0 45 0 * * *}`. La zona es la misma que la del vencimiento de 022 (`America/Argentina/Buenos_Aires`).
- `.env.example`: se documenta `REMINDERS_CRON` junto a `OVERDUE_CRON`, con el valor predeterminado y una nota de que debe ejecutarse después del job de vencimiento.
- `compose.yaml` y `compose.prod.yaml`: se pasa `REMINDERS_CRON` al backend con el mismo valor predeterminado que `OVERDUE_CRON`.
- No se agregan credenciales nuevas: se reutiliza el SMTP existente (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`).

## Diseño del frontend

- `GroupReminderSettingsPanel` se muestra dentro de "Configuración del grupo", solo cuando `isAdmin` es verdadero.
- Selector de tiempo con las opciones "7 días antes", "5 días antes", "3 días antes" y "1 día antes"; por defecto 5.
- Campo de texto de hasta 500 caracteres con contador y ayuda: "Si lo dejás vacío, se usa el mensaje predeterminado".
- Botón "Guardar recordatorios"; muestra el error de validación del backend en el formulario y un snackbar de éxito.
- Al montar, consulta `GET /api/groups/{id}/reminder-settings`; si falla, muestra un mensaje de error sin bloquear el resto de la configuración.
- El historial agrega las frases de los dos eventos nuevos y un filtro "Recordatorios"; los eventos con `expenseId` enlazan al detalle del gasto.
- Diseño responsive: el panel usa el mismo ancho y estilo que `GroupPermissionsPanel`.

## Pruebas

- **Unitarias (puras)**: `ReminderSchedule` (fecha de envío, elegibilidad, fecha ya pasada, deuda vencida, grupo sin días, días restantes) y `ReminderMessageBuilder` (datos obligatorios, mensaje personalizado, `PAYMENT_REPORTED`, pagador con y sin deudas, múltiples monedas, concepto nulo).
- **Integración (Testcontainers + GreenMail)**: creación de recordatorios para deudores registrados y simulados; uno por deuda; sin envío para `PAID`/`VERIFIED`; envío para `PAYMENT_REPORTED` con "pagado a verificar"; sin envío sin `debtDueDays`; envío en la evaluación siguiente si la fecha ya pasó; sin envío si está vencida; mensaje personalizado y mensaje en blanco; cambio de tiempo que reprograma; recordatorio ya enviado que no cambia; aviso al pagador con y sin deudas; un aviso por gasto; cancelación si se saldan todas las deudas; eventos de historial solo en envíos exitosos; fallo de entrega que avisa al administrador y no reintenta; ejecución repetida idempotente; dos instancias no duplican.
- **Contrato/validación**: `GET`/`PUT` de configuración con 200, 400, 403 y 404; `sendDays` fuera de la lista; mensaje de 501 caracteres; mensaje en blanco que vuelve al predeterminado.
- **Frontend (Vitest)**: panel visible solo para `ADMIN`; opciones del selector; contador y validación de 500 caracteres; guardado exitoso y error; frases y filtro de los eventos nuevos; enlaces al gasto.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| Enviar dos veces por una caída entre el envío y el marcado | Reclamo previo con `claimed_at`; una fila reclamada no se reintenta. |
| Correos a perfiles simulados con correo inválido | Se captura el fallo, se marca `FAILED` y se avisa a los administradores; no se reintenta. |
| Cambio de configuración con envíos programados | Reprogramación inmediata de las filas `SCHEDULED` no intentadas y recálculo en cada ejecución. |
| Deudas creadas con vencimiento más cercano que el tiempo configurado | Se envía en la evaluación siguiente si la fecha límite no pasó; si ya venció, no se envía. |
| Volumen de correos en la ejecución diaria | Solo se envían los que vencen ese día; el resto queda programado. |
| Dependencia de `debtDueDays` de 022 | La feature no lo configura; sin días de vencimiento simplemente no genera recordatorios, como pide la spec. |

## Complexity Tracking

Sin desvíos. No hay violaciones de la constitución que justificar.
