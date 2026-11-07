# Research: Recordatorios automáticos de deudas pendientes

**Feature**: `025-debt-reminders` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

**Created**: 2026-11-07 | **Status**: Fase 0 completa

Este documento resuelve las incógnitas técnicas del plan a partir del código existente. No reemplaza la spec: cuando hay una diferencia de comportamiento, manda la spec.

## 1. Fecha límite de la deuda y estados elegibles

**Decisión**: La fecha límite es `GroupExpense.expenseDate + Group.debtDueDays` (la misma regla de 022). Son elegibles para recordatorio `PENDING`, `PARTIALLY_PAID` (legado) y `PAYMENT_REPORTED`. Se excluyen `PAID`, `VERIFIED` y `OVERDUE`. Sin `debtDueDays` no hay recordatorio.

**Hallazgos**:
- `Group` ya tiene `debtDueDays` (`groups.debt_due_days`, V17) y `GroupRepository.findByDebtDueDaysIsNotNull()`.
- `OverdueDebtJob` calcula el vencimiento con `expenseDate < today.minusDays(dueDays)`; es decir, la deuda vence el día siguiente a la fecha límite. La 025 usa el mismo límite: puede enviar el mismo día del vencimiento y deja de enviar cuando el job de 022 ya la pasó a `OVERDUE`.
- `DebtStatus.isSettled()` devuelve verdadero solo para `PAID` y `VERIFIED`; `PAYMENT_REPORTED` no está saldada.
- `Debt.getOutstanding()` ya calcula `amount - paidAmount` con `BigDecimal` escala 2.

**Por qué**: reutilizar el cálculo y los estados evita divergencias con 017/022 y respeta la constitución (principios I y VI).

**Alternativas descartadas**:
- Calcular el vencimiento en la 025 con otra regla: duplicaría la fuente de verdad y podría discrepar con el vencimiento.
- Incluir `OVERDUE`: la spec dice explícitamente que una deuda ya vencida no recibe recordatorio.
- Excluir `PAYMENT_REPORTED`: la clarificación del 2026-11-07 pide incluirla con la leyenda "pagado a verificar".

## 2. Dónde guardar la configuración por grupo

**Decisión**: dos columnas nuevas en `groups`: `reminder_send_days` (entero, por defecto 5, `CHECK` en 7/5/3/1) y `reminder_message` (`VARCHAR(500)` nulo). Los valores predeterminados se materializan en la base y no hace falta distinguir "nunca configurado" de "configurado con el valor predeterminado".

**Hallazgos**:
- La configuración existente del grupo ya vive en `groups` (`primary_currency`, `debt_due_days`) y en `group_permissions`.
- `Group` es la raíz del agregado y `GroupDetailResponse` ya expone datos de configuración.
- `GroupPermissionChecker.canManagePermissions` ya modela "solo ADMIN" para configuración.

**Por qué**: una tabla nueva de configuración agregaría una unión y un ciclo de vida extra para dos valores simples; las columnas siguen el patrón existente.

**Alternativas descartadas**:
- Tabla `group_reminder_settings`: más flexible para futuros campos, pero innecesaria para el alcance actual.
- Guardar la configuración en `group_permissions`: mezcla permisos con parámetros de negocio y confunde la semántica.

## 3. Persistencia de recordatorios y avisos

**Decisión**: una única tabla `reminders` con `kind` (`DEBT` o `PAYER_NOTICE`), objetivo (`debt_id` o `expense_id`), `send_date`, `status`, `claimed_at`, `sent_at`, `subject` y `body`. Índices únicos parciales: uno por `debt_id` cuando `kind = 'DEBT'` y otro por `expense_id` cuando `kind = 'PAYER_NOTICE'`.

**Hallazgos**:
- La spec define dos entidades ("Recordatorio de deuda" y "Aviso de vencimiento al pagador") pero aclara que el aviso "comparte las reglas y estados del recordatorio".
- No existe una tabla de recordatorios ni un mecanismo de envío programado de correos.
- `group_expenses` y `debts` ya tienen los datos necesarios (fecha, monto, moneda, pagador, deudor) y no hace falta duplicarlos.

**Por qué**: compartir tabla, estados y despachador evita duplicar la lógica de fechas, cancelación, reclamo y fallo; los índices parciales preservan la unicidad por objetivo.

**Alternativas descartadas**:
- Dos tablas separadas (`debt_reminders` y `payer_notices`): duplicaría el 80 % de la lógica y los estados.
- Guardar solo la fecha calculada sin fila: impediría saber qué se envió, cancelar y auditar, y no cumpliría el estado "programado/enviado/cancelado/fallido" de la spec.

## 4. Programación diaria y reutilización del scheduler

**Decisión**: una tarea `@Scheduled` diaria (`ReminderJob`) con cron `reminders.cron` por defecto `0 45 0 * * *`, zona `America/Argentina/Buenos_Aires`, ejecutada después del job de vencimiento (`0 30 0 * * *`). Usa el `Clock` inyectable de `TimeConfig`.

**Hallazgos**:
- `BackendApplication` tiene `@EnableScheduling`.
- `OverdueDebtJob` ya usa `@Scheduled(cron = "${overdue.cron:0 30 0 * * *}", zone = ZONE)` y `Clock`.
- `TimeConfig` construye el `Clock` con la zona de `recurrence.zone` (`America/Argentina/Buenos_Aires`).

**Por qué**: reutiliza el mecanismo existente y el orden respecto del vencimiento evita enviar recordatorios de deudas que acaban de pasar a `OVERDUE`.

**Alternativas descartadas**:
- Quartz o un planificador externo: dependencia nueva innecesaria.
- Ejecutar antes del vencimiento: una deuda que vence ese día podría recibir recordatorio y luego pasar a `OVERDUE` en la misma madrugada, contradiciendo la regla de no enviar a vencidas.

## 5. Correo: envío, fallos y reutilización

**Decisión**: `ReminderMailService` usa `JavaMailSender`/`MimeMessageHelper` y **propaga** las excepciones de envío. No usa `@Async`. El despachador captura el fallo, marca `FAILED`, avisa a los administradores y continúa con los demás envíos. No hay reintentos.

**Hallazgos**:
- `VerificationMailService` y `PasswordResetMailService` usan `@Async` y atrapan todas las excepciones para no bloquear el registro o el reset.
- Los tests de correo usan GreenMail en proceso (`VerificationMailServiceTest`, `EmailResendIT`, etc.) y nunca un SMTP real.
- `spring.mail.*` y `app.base-url` ya están configurados por variables de entorno.

**Por qué**: para marcar `FAILED` y avisar al administrador hace falta saber si el envío falló; el patrón de "tragar" el error de 003/006 no sirve para este caso. GreenMail permite probar el camino feliz y el fallo sin SMTP real.

**Alternativas descartadas**:
- Reutilizar `VerificationMailService` con `@Async`: no se puede conocer el resultado del envío.
- Reintentar con backoff: la spec prohíbe reintentos.
- Un proveedor externo de correo: dependencia y credenciales nuevas, fuera de alcance.

## 6. Idempotencia y concurrencia

**Decisión**: la fila se crea con un índice único parcial por objetivo. El envío se reclama con una actualización condicional (`status = 'SCHEDULED' AND claimed_at IS NULL`). Después del intento se marca `SENT` o `FAILED`. Una fila reclamada no se reintenta; si el proceso se cae, la siguiente ejecución la marca `FAILED`.

**Hallazgos**:
- El proyecto ya usa claves de idempotencia en gastos y pagos, y actualizaciones condicionales en `OverdueDebtJob` (`markDebtOverdueIfPending`).
- La tarea de vencimiento es idempotente y segura con varias instancias; la 025 sigue el mismo patrón.
- No hay un mecanismo de bloqueo distribuido ni cola de mensajes, y no se necesita.

**Por qué**: el índice único evita duplicados al materializar y el reclamo condicional evita que dos instancias envíen la misma fila. La semántica de un único intento coincide con la spec.

**Alternativas descartadas**:
- Enviar dentro de una transacción larga: un rollback después del envío produciría duplicados en la ejecución siguiente.
- Marcar `SENT` antes de enviar: una caída entre el marcado y el envío perdería el correo sin avisar; el reclamo explícito permite marcar `FAILED` y notificar.
- Bloqueo pesimista de fila durante el envío: mantendría una transacción abierta mientras se habla con SMTP.

## 7. Datos del aviso al pagador

**Decisión**: el aviso se envía al `GroupExpense.payerUserId`. El "monto total pendiente" del pagador se calcula sumando `Debt.getOutstanding()` de las deudas abiertas del grupo cuyo `debtorUserId` es el pagador, agrupadas por moneda. Se incluyen `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED` y `OVERDUE` (todas las no saldadas); la parte del pagador en su propio gasto no es una deuda y no se suma. Si el pagador no tiene deudas, el correo lo dice explícitamente.

**Hallazgos**:
- `GroupExpense.payerUserId` siempre es un usuario registrado (017: los perfiles simulados no pueden pagar).
- `DebtRepository.findOpenForUserInGroup` ya devuelve las deudas abiertas en las que el usuario es deudor o acreedor, pero incluye ambos roles; para el aviso hace falta filtrar por `debtorUserId`.
- `Debt.currency` puede ser distinta de la moneda principal en deudas legadas; la 022 convierte al registrar el gasto, pero no se puede asumir que todas las filas históricas estén en la misma moneda.

**Por qué**: usar el pagador del gasto y las deudas reales respeta la spec y evita inventar una noción de deuda que no existe.

**Alternativas descartadas**:
- Sumar deudas donde el pagador es acreedor: sería el saldo neto, no "lo que debe", y la spec pide deudas pendientes del pagador.
- Incluir la parte del pagador en el gasto: la spec aclara que quedó cubierta al pagar.

## 8. Historial de actividad

**Decisión**: dos eventos nuevos, `DEBT_REMINDER_SENT` y `PAYER_DUE_NOTICE_SENT`, registrados solo en envíos exitosos, dentro de la misma transacción que marca `SENT`. Se extiende el `CHECK` de `group_activity` con una migración y se actualizan el enum, el servicio de escritura y las frases del frontend.

**Hallazgos**:
- `ActivityEventService.record(...)` escribe con `Propagation.MANDATORY`; los eventos se registran dentro de la transacción de negocio.
- `GroupActivity.GroupActivityType` y el `CHECK` de V22 deben mantenerse sincronizados.
- `GroupActivityService` valida los tipos con `valueOf`, así que un tipo nuevo debe estar en el enum para poder filtrarse.
- El frontend tiene `activityDescriptions.js` con `DESCRIBERS` y `ACTIVITY_FILTERS`, y `GroupHistoryView`/`GroupFeed` enlazan al gasto cuando el detalle trae `expenseId`.

**Por qué**: seguir el patrón existente mantiene el historial consistente y permite filtrar y describir los eventos nuevos sin lógica especial.

**Alternativas descartadas**:
- Un único evento genérico `REMINDER_SENT`: obligaría a distinguir el tipo en los detalles y complicaría el filtro del historial.
- Registrar también los fallos: la spec dice que el historial queda solo para los envíos exitosos.

## 9. Migraciones y versionado

**Decisión**: V24 (columnas de configuración en `groups`), V25 (tabla `reminders`) y V26 (tipos de evento del historial). Son las siguientes versiones libres después de V23.

**Hallazgos**:
- La última migración del repositorio es `V23__default_members_can_generate_expenses_for_others_true.sql`.
- Las migraciones ya integradas no se editan (constitución, "Bases de datos y migraciones").
- El backend arranca con `spring.jpa.hibernate.ddl-auto=validate`, así que el DDL y las entidades deben coincidir exactamente.

**Por qué**: mantener una migración por unidad de cambio facilita la revisión y el rollback lógico.

**Alternativas descartadas**:
- Una sola migración con todo: dificulta aislar un fallo de DDL.
- Editar V17/V20/V22: prohibido por la constitución.

## 10. Frontend: panel de configuración y permisos

**Decisión**: un panel nuevo `GroupReminderSettingsPanel` dentro de la configuración del grupo, visible solo para `ADMIN`. Consulta y guarda con `groupsClient.js`. No se reutiliza `GroupPermissionsPanel` porque este edita casillas booleanas y guarda todo junto; mezclar campos obligaría a cambiar el contrato de permisos y el estado del formulario.

**Hallazgos**:
- `GroupInfoView` ya muestra `GroupPermissionsPanel` dentro de `showPermissions` y conoce `isAdmin`.
- `groupsClient.js` centraliza las llamadas y el formato `{ ok, ... }`.
- `GroupPermissionsPanel` usa `useSnackbar` y muestra errores en el formulario.
- El proyecto usa Vitest + Testing Library para componentes.

**Por qué**: un panel separado mantiene la configuración de recordatorios aislada, con su propio estado, validación y permiso, sin tocar el flujo de permisos existente.

**Alternativas descartadas**:
- Agregar los campos a `GroupPermissionsPanel`: mezcla permisos con configuración y hace que un error de recordatorios bloquee el guardado de permisos.
- Una pantalla nueva: la spec pide que esté en la configuración del grupo.

## 11. Pruebas y cobertura

**Decisión**: unitarias puras para fechas y mensajes; integración con Testcontainers y GreenMail para el flujo completo, los fallos y la idempotencia; Vitest para el panel y el historial. Se mantiene el umbral del 85 %.

**Hallazgos**:
- El backend ya usa Testcontainers y GreenMail; no hay `src/test/resources`.
- `mvn -B verify` ejecuta JaCoCo; `npm run test:coverage` ejecuta la cobertura de Vitest.
- Las pruebas de correo existentes leen el cuerpo con `GreenMailUtil.getBody(...)` y decodifican el quoted-printable.

**Por qué**: cubre los criterios de aceptación y los casos límite sin depender de un servicio externo.

**Alternativas descartadas**:
- Probar solo con mocks: no validaría la persistencia, los índices únicos ni la integración con el historial.
- Usar un SMTP real: prohibido por la regla del equipo y por la reproducibilidad.

## 12. Decisiones abiertas y supuestos

No quedan `NEEDS CLARIFICATION`. Se documentan estas interpretaciones técnicas, todas compatibles con la spec:

- **Varios administradores**: el aviso de fallo se envía a todos los `ADMIN` activos; la spec habla de "el Administrador" en singular, pero notificar a todos evita que el fallo quede sin dueño.
- **Monedas múltiples en el aviso al pagador**: se informa el total por moneda, sin convertir ni sumar monedas distintas.
- **Concepto nulo**: si el gasto no tiene descripción, se usa "Gasto del grupo" para que el correo siempre identifique el gasto.
- **Mensaje personalizado**: se agrega al recordatorio de deuda, no al aviso al pagador (la spec lo excluye explícitamente).
- **Reprogramación**: se hace de inmediato al guardar la configuración y también en cada ejecución diaria, para cubrir cambios hechos por fuera de la API.
- **Sin `debtDueDays`**: no se crean filas ni se reporta error; si el grupo deja de tener días de vencimiento, los recordatorios programados se cancelan.
