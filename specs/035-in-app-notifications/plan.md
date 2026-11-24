# Implementation Plan: Bandeja completa y configuración de notificaciones in-app

**Branch**: `feat/in-app-notifications` | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/035-in-app-notifications/spec.md`

## Summary

Sustituir la campana actual de solicitudes de amistad por un popover de notificaciones completo y agregar una bandeja dedicada en `/notifications` con filtros, marcado individual/global y enlaces profundos. En el backend se crean dos tablas nuevas (`notifications` y `user_notification_preferences`) con una migración Flyway V39, un paquete `notification` con servicio de bandeja, servicio de preferencias y un despachador resiliente que reacciona **después de confirmar** los eventos de negocio existentes (recordatorios, silbatos, vencimientos, gastos grupales, pagos y amistad). El correo de notificación se envía de forma asíncrona y aislada: un fallo de SMTP o de base de datos nunca altera la operación de negocio que generó el aviso. El contrato REST exigido por el backlog usa el prefijo `/api/v1/notifications`. Sin dependencias nuevas.

## Arquitectura

### Endpoints REST

Todos usan la cookie `amano_auth`, exigen cuenta verificada, toman el usuario del `SecurityContext` e ignoran cualquier `userId` del cliente. Contrato completo: [contracts/notifications-api.md](./contracts/notifications-api.md).

| Método y ruta | Propósito | Respuesta principal | FR |
|---------------|-----------|---------------------|----|
| `GET /api/v1/notifications?limit=&before=&order=` | Listado descendente; `order=unread-first&limit=5` para el popover, `history` para la bandeja | `NotificationListResponse` con `notifications`, `unreadCount` y `nextCursor` | FR-002, FR-007, FR-010 |
| `PATCH /api/v1/notifications/{id}/read` | Marcar una notificación como leída (idempotente) | `NotificationResponse` actualizado; `404` si el id es ajeno o inexistente | FR-011, FR-014, FR-031 |
| `PATCH /api/v1/notifications/read-all` | Marcar todas las no leídas del usuario autenticado | `{ "updated": N }` (0 si ya no había no leídas) | FR-012, FR-014 |
| `GET /api/v1/notifications/preferences` | Obtener la configuración actual; crea los valores por defecto si no existe | `NotificationPreferencesResponse` — DTO de lectura de preferencias (6 booleanos: 2 canales + 4 tipos de evento) | FR-024, FR-025, FR-028 |
| `PUT /api/v1/notifications/preferences` | Actualizar canales y tipos de evento | `NotificationPreferencesResponse`; `400` con `properties.fields` si falta un campo | FR-026, FR-027, FR-028 |

### DTOs (`record`, bajo `notification/dto/`)

- `NotificationTargetResponse`: `kind`, `groupId?`, `debtId?`, `expenseId?`, `friendRequestId?`. Es la única fuente para resolver el destino; el texto no se interpreta.
- `NotificationResponse`: `id`, `type`, `title`, `message`, `createdAt`, `readAt`, `target`.
- `NotificationListResponse`: `notifications[]`, `unreadCount`, `nextCursor`.
- `NotificationPreferencesRequest` / `NotificationPreferencesResponse`: `channels{inApp,email}` y `eventTypes{remindersAndWhistles,debtDueAndPendingCollections,groupActivity,friendActivity}` (records anidados `Channels`/`EventTypes`).
- `NotificationEvent` (interno, no se expone): `userId`, `type`, `title`, `message`, `target`, `emailPolicy`.

Detalle de campos, formatos y correspondencia tipo → preferencia en [data-model.md](./data-model.md) y en el contrato.

### Entidades y persistencia

- **`notifications`**: `id`, `user_id` (FK a `users`, `ON DELETE CASCADE`), `type`, `title`, `message`, `target_kind`, `group_id`, `debt_id`, `expense_id`, `friend_request_id` (ids planos **sin FK**), `created_at`, `read_at`. Índices `(user_id, created_at DESC, id DESC)` y parcial de no leídas. Estado: `read_at = NULL` (no leída) → timestamp (leída), sin retorno.
- **`user_notification_preferences`**: `user_id` como PK y FK a `users`, seis booleanos (`in_app`, `email`, `reminders_whistles`, `debt_due_collections`, `group_activity`, `friend_activity`; todos `TRUE` por defecto) y `updated_at`. Una fila por persona, creada en el primer `GET`/`PUT`.
- Migración única `V39__create_notifications.sql`; sin `DELETE` y sin editar V1–V38. Ambas tablas se añaden a `TRUNCATED_TABLES` en `AbstractIntegrationIT`.

### Componentes de UI

| Componente / archivo | Responsabilidad | FR |
|----------------------|-----------------|----|
| `frontend/src/components/NotificationsMenu.jsx` | Popover del encabezado: campana, badge con tope "9+", hasta 5 avisos (`unread-first`), cierre con Escape/click fuera, accesos "Ver todas" y "Configuración" | FR-001..FR-006, FR-021, FR-023 |
| `frontend/src/components/NotificationRow.jsx` | Fila reutilizable: título, texto, fecha, etiqueta "No leída", acción "Marcar como leída" y clic para navegar | FR-002, FR-009, FR-011, FR-016 |
| `frontend/src/pages/NotificationsPage.jsx` + `.scss` | Pantalla dedicada `/notifications`: historial paginado, filtros "Todas"/"No leídas", "Marcar todas como leídas", estados carga/vacío/error y botón de configuración | FR-007..FR-015, FR-023 |
| `frontend/src/components/NotificationPreferencesModal.jsx` + `.scss` | Modal compartido por bandeja y popover: dos canales y cuatro tipos de evento, "Guardar"/"Cancelar", foco y accesibilidad | FR-023..FR-028, FR-034 |
| `frontend/src/api/notificationsClient.js` | Funciones `getNotifications`, `markRead`, `markAllRead`, `getPreferences`, `updatePreferences` con el patrón `{ok,...}` | FR-029 |
| `frontend/src/utils/notificationTarget.js` | Resolución de destino y manejo de destinos inexistentes/incompletos | FR-016..FR-022 |
| `frontend/src/App.jsx` | Ruta `/notifications` protegida con `ProtectedRoute` | FR-007, FR-033 |

**Flujo de datos**: operación de negocio exitosa → `NotificationEventPublisher.publishSafely` → `NotificationEventListener` (`AFTER_COMMIT`, `fallbackExecution=true`) → `NotificationDeliveryService` (preferencias + insert In-App + correo asíncrono condicionado por `emailPolicy`) → endpoints REST → cliente `notificationsClient` → popover/bandeja/modal. Un fallo en el despachador se registra y no propaga (FR-032).

## Technical Context

**Language/Version**: Java 25 + Spring Boot 4.1.1 (backend); React 19 + Vite 8 + React Router 8 (frontend)

**Primary Dependencies**: Spring Data JPA (Hibernate), Spring Security con cookie `amano_auth` (principal `Long` = `user_id`), `JavaMailSender` (dentro de `spring-boot-starter-mail`), `@EnableAsync`/`@EnableScheduling` ya activos, React Router `useNavigate`/`useSearchParams`, `fetch` + `SnackbarProvider` existente. Sin librerías nuevas.

**Storage**: PostgreSQL 18 con Flyway. Migración nueva `V39__create_notifications.sql` (la última integrada es `V38__create_incomes.sql`). `spring.jpa.hibernate.ddl-auto=validate`. No se editan migraciones ya integradas.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + GreenMail (`*Test` rápidos, `*IT` de integración con `mvn -B verify`); Vitest 5 + Testing Library + user-event (`*.test.jsx`/`*.test.js` con `npm run test:coverage`). Umbral de cobertura 85 % en ambos módulos (JaCoCo y vitest v8).

**Target Platform**: Web (SPA servida por Docker Compose / nginx), misma sesión que el resto de la aplicación.

**Project Type**: Monorepo web (backend + frontend).

**Performance Goals**: el popover consulta 5 filas con un solo pedido; la bandeja recursa por páginas de hasta 20; el contador de no leídas se calcula con `COUNT` indexado. Cambios visibles sin recargar (SC-001, SC-004).

**Constraints**: sin dependencias nuevas; sin cambios en reglas de negocio ni en respuestas de endpoints existentes; `/api/v1/notifications/**` debe agregarse explícitamente a la regla `verifiedAccount`; las tablas nuevas deben incorporarse a `AbstractIntegrationIT.TRUNCATED_TABLES` o falla `TruncateCoverageIT`.

**Scale/Scope**: una persona, decenas a cientos de notificaciones en el historial; eventos generados por los flujos ya existentes, sin jobs nuevos.

## Constitution Check

| Principio | Cumplimiento |
|-----------|--------------|
| I. Arquitectura | Nuevo paquete `com.amano.backend.notification` como módulo transversal; reutiliza `SecurityConfig`, `ProblemDetailExceptionHandler`, `JavaMailSender`, `ApplicationEventPublisher` y los textos de 025/030. Frontend reutiliza `BellIcon`, `useDismiss`, `AppHeader.scss`, `ProtectedRoute` y el proveedor de avisos. Sin refactorizaciones ajenas. |
| II. SDD | Especificación → plan → contrato → checklist → tareas antes de implementar; cada FR y SC se mapea a una tarea (ver `tasks.md`). |
| III. Incremental | US1 y US2 (P1) pueden entregarse primero; US3 comparte la bandeja; US4 y US5 se integran después. Cada historia es verificable por separado. |
| IV. Alcance | Solo bandeja, popover, preferencias y deep links. No se cambian reglas de deuda, gasto, pago ni amistad; no se agregan canales ni borrado de notificaciones. El prefijo `/api/v1` y la no duplicación de correos son decisiones documentadas. |
| V. Pruebas | `*Test`/`*IT` y `*.test.jsx` por historia, incluidos casos límite (SMTP caído, BD caída, IDs ajenos, destinos eliminados). Cobertura ≥ 85 % verificada con `mvn -B verify` y `npm run test:coverage`. |
| VI. Reglas de negocio | El despachador solo lee el resultado de operaciones ya completadas; no modifica importes, estados, saldos ni transiciones. Las preferencias gobiernan solo la generación de avisos. |
| VII. Contratos REST | Contrato nuevo documentado en [contracts/notifications-api.md](./contracts/notifications-api.md); ningún endpoint existente cambia. El prefijo versionado se justifica en Complexity Tracking. |
| VIII. Validación | `limit`, `order` y `before` se validan/normalizan en el servicio; `PUT` de preferencias valida los seis booleanos en backend y devuelve `properties.fields`; errores 404 neutrales para IDs ajenos. |
| IX. Seguridad | Usuario siempre desde el `SecurityContext`; `/api/v1/notifications/**` se agrega a `verifiedAccount(...)`; ningún parámetro `userId` del cliente es confiable; las consultas filtran por `user_id`. |
| X. Mantenibilidad | Convenciones existentes: DTOs `record`, rutas literales en cada `@GetMapping`/`@PatchMapping`, ids planos en entidades, clientes `fetch` con `{ok:false,...}`, tests junto al archivo. Sin dependencias nuevas. |
| XI. Entorno | Mismos comandos de verificación; la migración V39 se aplica al arrancar; sin cambios en CI ni en Docker Compose. |
| XII. Revisión | PR a `sprint-n`; revisión de contrato, seguridad (aislamiento por usuario), resiliencia y cobertura. |
| XIII. Commits | Conventional Commits por historia (`feat: ...`, `test: ...`, `docs: ...`) con referencia a la feature. |

### Re-evaluación post-diseño

Tras definir el esquema, los DTOs, los cinco endpoints y los componentes de UI, los trece gates siguen en verde: no aparecieron violaciones nuevas ni `NEEDS CLARIFICATION`. Las dos decisiones que se salen de lo habitual —prefijo `/api/v1/notifications` y dos tablas nuevas— están justificadas en `Complexity Tracking` y aceptadas por el backlog; el resto reutiliza los mecanismos existentes (seguridad, errores, correo, clientes `fetch`). No se reducen umbrales de cobertura ni se omiten pruebas (constitución V).

## Decisiones (research)

- **D1 – Dos tablas nuevas, una migración**: `V39__create_notifications.sql` crea `notifications` (id, `user_id` FK a `users` con `ON DELETE CASCADE`, `type`, `title`, `message`, `target_kind`, `group_id`, `debt_id`, `expense_id`, `friend_request_id`, `created_at`, `read_at`) y `user_notification_preferences` (`user_id` como PK y FK, seis booleanos con default `true`, `updated_at`). Los ids de grupo/deuda/gasto/amistad son **columnas planas sin FK**, igual que el resto del proyecto: un aviso debe sobrevivir a la eliminación de su destino (SC-005). Índices: `(user_id, created_at DESC, id DESC)` y parcial `(user_id) WHERE read_at IS NULL`. Sin `DELETE` en la migración, según la regla del equipo.
- **D2 – Prefijo `/api/v1/notifications`**: el backlog lo exige literalmente, aunque el resto del proyecto use `/api/...` sin versión. Se crea un controlador **nuevo**; no se renombra ni se toca ningún endpoint existente. Se registra como desviación consciente en Complexity Tracking. `SecurityConfig` debe añadir `/api/v1/notifications/**` al grupo `verifiedAccount(userRepository)`; de lo contrario caería en `anyRequest().authenticated()` y permitiría cuentas no verificadas.
- **D3 – Estructura del módulo backend**: en `com.amano.backend.notification`: entidades `Notification`, `UserNotificationPreferences`; enums `NotificationType`, `NotificationTargetKind`; repositorios `NotificationRepository` (derivados + `@Query`/`@Modifying`, inspirado en `ReminderRepository`) y `NotificationPreferenceRepository`; DTOs `record` (`NotificationResponse`, `NotificationListResponse`, `NotificationTargetResponse`, `NotificationPreferencesRequest/Response` con records anidados `Channels`/`EventTypes`); servicios `NotificationService` (listado, marcado, contador), `NotificationPreferenceService` (obtener/crear/actualizar), `NotificationDeliveryService` (generación); `NotificationMailService` (`@Async`); `NotificationEventPublisher`; `NotificationEventListener`; excepciones `NotificationNotFoundException` e `InvalidNotificationPreferencesException`; controlador `NotificationController` con rutas literales y el helper `currentUserId()` del `Long` principal.
- **D4 – Generación desacoplada y resistente a transacciones**: los servicios de negocio publican `NotificationEvent(userId, type, title, message, target, emailPolicy)` mediante `NotificationEventPublisher.publishSafely(...)` (que captura `RuntimeException`). `NotificationEventListener` usa `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` para cubrir tanto llamadas dentro de transacción (caso normal) como jobs planificados sin transacción. El evento solo se entrega si la operación de negocio **ya se confirmó**: un fallo posterior no puede revertirla. El listener captura y registra cualquier excepción (sin PII en logs).
- **D5 – Canales independientes y política de correo canónica**: `NotificationDeliveryService.deliver(event)` consulta preferencias y evalúa cada canal por separado en su propio `try/catch`: si el tipo está desactivado, no hace nada; si In-App está activo, inserta la notificación en su propia transacción (`REQUIRES_NEW`). La política canónica de correo (FR-026) es: `ALWAYS` para `DEBT_OVERDUE` (crítico, se envía aunque el toggle de correo esté apagado); `SUBSCRIBABLE` para `GROUP_EXPENSE`, `PAYMENT_RECORDED`, `FRIEND_REQUEST` y `FRIEND_ACTIVITY` (respetan el toggle); y `NONE` para `DEBT_WHISTLE`, `REMINDER` y `PAYER_DUE_NOTICE`, cuyos correos de negocio ya envían 025/030 con sus propias reglas y no se duplican. Los correos de verificación y recuperación no se tocan.
- **D6 – Consultas y marcado**: `NotificationRepository` expone `findByUserIdAnd...` con `Pageable`/`limit`, orden `createdAt DESC, id DESC` para `history` y `readAt IS NULL DESC, createdAt DESC, id DESC` para `unread-first`; `countByUserIdAndReadAtIsNull`; update condicional `@Modifying` para marcar una (`WHERE id = :id AND user_id = :userId AND read_at IS NULL`) y todas (`WHERE user_id = :userId AND read_at IS NULL`), devolviendo filas afectadas. Un `id` ajeno o inexistente produce el mismo 404 mediante `NotificationNotFoundException`. Cursor opaco `Base64(epochMillis|id)`, aplicable solo a `history`; en `unread-first` se ignora. `unreadCount` viaja en todas las respuestas de listado para pintar el badge.
- **D7 – Preferencias**: `NotificationPreferenceService.getOrCreate(userId)` crea la fila con todos los valores en `true` si no existe (evita migraciones de datos y estados nulos). `update(userId, request)` valida que los seis campos estén presentes, ignora `userId` del cuerpo y guarda con `@Modifying`/`save`. El cambio surte efecto para notificaciones generadas a partir de ese momento; no se aplica retroactivamente.
- **D8 – Puntos de enganche (solo publicación, sin cambiar reglas)**: `FriendshipRequestService` (creación → `FRIEND_REQUEST` al destinatario; respuesta → `FRIEND_ACTIVITY` al solicitante), `DebtWhistleService` (tras `SENT` → `DEBT_WHISTLE`, destino grupo + deuda), `ReminderDispatcher` (tras `SENT` → `REMINDER` al deudor y `PAYER_DUE_NOTICE` al pagador), `OverdueDebtJob`/servicio de vencimiento (`DEBT_OVERDUE`), `GroupExpenseService` (alta → `GROUP_EXPENSE` a los involucrados) y `PaymentService` (pago registrado/confirmado → `PAYMENT_RECORDED`). En todos los casos se añade **una línea** de publicación al final de la operación exitosa; se reutilizan los textos de `ReminderMessageBuilder` cuando existan.
- **D9 – Cliente API frontend**: `frontend/src/api/notificationsClient.js` sigue el patrón de `statisticsClient.js`: funciones `getNotifications({limit, before, order})`, `markRead(id)`, `markAllRead()`, `getPreferences()`, `updatePreferences(payload)`; siempre devuelve `{ok:true,...}`, `{ok:false,status,detail}` o `{ok:false,network:true}`; `fetch` con `credentials:'include'`; constantes `NOTIFICATIONS_ERROR_MESSAGE`, `NOTIFICATIONS_ACTION_ERROR_MESSAGE` y `PREFERENCES_SAVE_ERROR_MESSAGE` en español. Usa `problemDetail.js` para `fields`.
- **D10 – Refactor de la campana existente**: `NotificationsMenu.jsx` ya tiene `BellIcon`, `useDismiss`, badge y estilos `.app-menu__panel--end`/`.app-header__badge`, pero solo lista solicitudes de amistad vía `friendsClient.listReceivedFriendshipRequests()`. Se sustituye esa fuente por `getNotifications({limit:5, order:'unread-first'})`, se conservan las constantes exportadas (`NO_NOTIFICATIONS_MESSAGE`, `NOTIFICATIONS_ERROR_MESSAGE`), el cierre con clic fuera/Escape, el contador con tope "9+" y el evento `FRIEND_REQUESTS_CHANGED_EVENT` para recargar la bandeja cuando se responde una solicitud. Se agregan los accesos "Ver todas las notificaciones" y "Configuración" en el pie. Sus tests se actualizan para mockear el nuevo cliente (sin tocar `friendsClient`).
- **D11 – Página de bandeja**: `frontend/src/pages/NotificationsPage.jsx` (con `.scss`) protegida por `ProtectedRoute`, ruta `/notifications` en `App.jsx`: encabezado con "Marcar todas como leídas" y "Configuración de notificaciones", pestañas accesibles "Todas"/"No leídas" (`role="tablist"`, `aria-selected`), lista con `NotificationRow`, acción por fila, "Cargar más" con cursor, y estados carga/vacío/error con reintento. Las filas no leídas llevan etiqueta textual y estilo diferenciado; cada fila es un botón/enlace que navega al destino.
- **D12 – Modal de preferencias**: `frontend/src/components/NotificationPreferencesModal.jsx` reutilizado por la bandeja y el popover; dos bloques ("Canales" y "Tipos de evento") con `input type="checkbox"` etiquetados y nombres accesibles, "Guardar"/"Cancelar", foco inicial en el diálogo, cierre con Escape/click fuera y devolución del foco al control que lo abrió. Los cambios locales se descartan al cancelar y se conservan al fallar el guardado.
- **D13 – Resolución de enlaces profundos**: `frontend/src/utils/notificationTarget.js` expone `resolveNotificationTarget(notification)` → `{ path, missingTarget }`: `GROUP`/`GROUP_EXPENSE`/recordatorio/silbato → `/groups/{groupId}`; `DEBT` con grupo → `/groups/{groupId}/debts?debt={debtId}`; `DEBT` sin grupo → `/activity?debt={debtId}`; `FRIEND_REQUEST`/`FRIEND_ACTIVITY` → `/friends`; `NONE` o campos incompletos → sección general correspondiente con `missingTarget: true`. `GroupDebtsView` lee `?debt=` para señalar la fila; las demás pantallas toleran el parámetro sin cambios de forma. El flujo de clic llama a `markRead(id)` **antes** de navegar: si falla, muestra el error y no navega (FR-022); si el destino falta, navega igual y reporta un aviso no bloqueante con el proveedor de avisos existente.
- **D14 – Seguridad y pruebas de aislamiento**: el controlador nunca acepta `userId`; los tests de integración usan cookies JWT reales (`session(user)` de `AbstractIntegrationIT`) y comprueban que un usuario no ve ni marca notificaciones de otro (404). Se añaden `notifications` y `user_notification_preferences` a `TRUNCATED_TABLES` para que `TruncateCoverageIT` siga pasando. Los crons siguen desactivados en tests; se publican eventos explícitamente en las pruebas del despachador.
- **D15 – Resiliencia verificable**: en tests se usa `@MockitoSpyBean NotificationMailService` para lanzar `MessagingException` y `@MockitoSpyBean NotificationRepository` (o un fallo controlado de BD) para verificar que la operación de negocio responde 200/201 y que el evento queda registrado. `NotificationMailService` captura `MessagingException | RuntimeException`, hace `log.warn` y no propaga, igual que `VerificationMailService`.
- **D16 – Cobertura y regresión**: se agregan pruebas para los tres tipos de destino, el destino eliminado, el ID ajeno, el marcado idempotente, la creación de preferencias por defecto y la desactivación de canales/tipos. La regresión incluye los tests existentes de 015 (campana), 025 (recordatorios), 030 (silbato), 011/012 (amistad), 032/033/034 (estadísticas y balance) y `GET /api/groups/{id}/activity`.

## Project Structure

### Documentation (this feature)

```text
specs/035-in-app-notifications/
├── spec.md                         # comportamiento, US, FR, SC y casos límite
├── plan.md                         # este archivo (arquitectura y decisiones)
├── research.md                     # Phase 0: decisiones, rationale y alternativas
├── data-model.md                   # Phase 1: entidades, campos, reglas y estados
├── quickstart.md                   # Phase 1: guía de validación end-to-end
├── contracts/notifications-api.md  # Phase 1: contrato REST y eventos internos
├── checklists/requirements.md      # validación de calidad de la spec
└── tasks.md                        # desglose de implementación y pruebas
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/
├── config/SecurityConfig.java                      # + /api/v1/notifications/** con cuenta verificada
├── common/ProblemDetailExceptionHandler.java       # + 404 notificación y 400 preferencias
├── notification/                                   # NUEVO: módulo transversal
│   ├── Notification.java
│   ├── NotificationType.java
│   ├── NotificationTargetKind.java
│   ├── NotificationEvent.java
│   ├── NotificationRepository.java
│   ├── UserNotificationPreferences.java
│   ├── NotificationPreferenceRepository.java
│   ├── NotificationService.java
│   ├── NotificationPreferenceService.java
│   ├── NotificationDeliveryService.java
│   ├── NotificationEventListener.java
│   ├── NotificationEventPublisher.java
│   ├── NotificationMailService.java
│   ├── NotificationController.java
│   ├── dto/NotificationResponse.java
│   ├── dto/NotificationListResponse.java
│   ├── dto/NotificationTargetResponse.java
│   ├── dto/NotificationPreferencesRequest.java
│   └── dto/NotificationPreferencesResponse.java
├── friendship/FriendshipRequestService.java        # + publicación de evento
├── groupexpense/GroupExpenseService.java           # + publicación de evento
├── groupexpense/whistle/DebtWhistleService.java    # + publicación de evento
├── groupexpense/reminder/ReminderDispatcher.java   # + publicación de evento
├── groupexpense/overdue/OverdueDebtJob.java        # + publicación de evento
└── payment/PaymentService.java                     # + publicación de evento

backend/src/main/resources/db/migration/
└── V39__create_notifications.sql                   # NUEVA: notifications + user_notification_preferences

backend/src/test/java/com/amano/backend/
├── support/AbstractIntegrationIT.java              # + 2 tablas en TRUNCATED_TABLES
├── notification/NotificationServiceTest.java      # NUEVO
├── notification/NotificationPreferenceServiceTest.java  # NUEVO
├── notification/NotificationEventPublisherTest.java    # NUEVO
├── notification/NotificationDeliveryServiceTest.java    # NUEVO
├── notification/NotificationControllerIT.java      # NUEVO (5 endpoints, 401/404/400)
├── notification/NotificationPreferenceIT.java      # NUEVO (persistencia de preferencias)
├── notification/NotificationDeliveryIT.java        # NUEVO (resiliencia SMTP/BD)
├── notification/NotificationRegressionIT.java      # NUEVO (aislamiento y regresión)
├── friendship/FriendshipRequestServiceTest.java    # + publicación de evento
├── groupexpense/GroupExpenseServiceTest.java       # + publicación de evento
├── groupexpense/whistle/DebtWhistleServiceTest.java # + publicación de evento
└── payment/PaymentServiceTest.java                 # + publicación de evento

frontend/src/
├── App.jsx                                         # + ruta /notifications protegida
├── api/notificationsClient.js                     # NUEVO
├── api/notificationsClient.test.js                # NUEVO
├── components/NotificationsMenu.jsx               # refactor: popover del encabezado
├── components/NotificationsMenu.test.jsx          # actualizado
├── components/NotificationRow.jsx                 # NUEVO: fila reutilizable leída/no leída
├── components/NotificationRow.scss                # NUEVO
├── components/NotificationRow.test.jsx            # NUEVO
├── components/NotificationPreferencesModal.jsx    # NUEVO
├── components/NotificationPreferencesModal.scss   # NUEVO
├── components/NotificationPreferencesModal.test.jsx # NUEVO
├── pages/NotificationsPage.jsx                    # NUEVO
├── pages/NotificationsPage.scss                   # NUEVO
├── pages/NotificationsPage.test.jsx               # NUEVO
├── pages/groups/GroupDebtsView.jsx                # + ?debt= para señalar la deuda
├── pages/groups/GroupDebtsView.test.jsx           # actualizado
├── utils/notificationTarget.js                    # NUEVO: resolvedor de destinos
└── utils/notificationTarget.test.js               # NUEVO
```

**Structure Decision**: monorepo web existente. El paquete `notification` es un módulo nuevo de soporte a los flujos de negocio: publica eventos desde los servicios existentes sin alterar sus reglas. En frontend se conservan los componentes y estilos de la campana actual y se agregan la bandeja, la fila reutilizable y el modal, sin librerías nuevas.

## Riesgos

- **Correos duplicados en recordatorios**: 025 ya envía el correo de recordatorio/aviso al pagador. Mitigación: `emailPolicy = NONE` para `REMINDER` y `PAYER_DUE_NOTICE` (D5). Los silbatos también usan `NONE` por el mismo motivo; solo los vencimientos usan `ALWAYS`. Se cubre con test.
- **Eventos fuera de transacción**: los jobs planificados pueden publicar sin transacción activa. Mitigación: `@TransactionalEventListener(..., fallbackExecution = true)` y prueba explícita del camino sin transacción (D4).
- **Tablas nuevas y `TruncateCoverageIT`**: si no se actualiza `TRUNCATED_TABLES`, falla la suite de integración. Mitigación: tarea explícita en la Fase 1 (D14).
- **Cuentas no verificadas**: sin agregar `/api/v1/notifications/**` a `verifiedAccount`, la regla `anyRequest().authenticated()` permitiría el acceso. Mitigación: tarea de seguridad y test 401/403 (D2).
- **Diferencia de prefijo con el resto de la API**: `/api/v1` es exigido por el backlog pero rompe la uniformidad. Mitigación: documentado en Complexity Tracking; el cliente usa una sola constante `NOTIFICATIONS_API_BASE` para evitar duplicación (D9).
- **Destinos eliminados**: los ids de destino no tienen FK a propósito. Mitigación: resolvedor tolerante a `null`/inexistente con aviso no bloqueante (D13), probado con grupo y deuda eliminados.
- **Cambios de preferencia a mitad de operación**: el despachador lee preferencias al entregar el evento, no al publicarlo. Mitigación: la lectura y el insert ocurren juntos en `REQUIRES_NEW` y la decisión queda registrada en tests (D5).

## DoR (Definition of Ready) y trazabilidad

La constitución define ocho criterios de DoR. Su cumplimiento verificable en esta feature es:

| # | Criterio DoR (constitución) | Evidencia en los artefactos |
|---|-----------------------------|-----------------------------|
| 1 | Objetivo y alcance claros | `spec.md`: Input, Relación con otras especificaciones, Fuera de alcance, Supuestos |
| 2 | Criterios de aceptación verificables | `spec.md`: escenarios `Given/When/Then` de US1–US5; FR-001..FR-036; SC-001..SC-010 |
| 3 | Reglas de negocio, validaciones y casos límite identificados | `spec.md`: Matriz de casos límite y Edge Cases; `data-model.md`: restricciones y transiciones; `contracts/notifications-api.md`: 400/404 |
| 4 | Dependencias y bloqueos conocidos | `spec.md`: relaciones con 011/012, 015, 025, 030, 032/033/034; `research.md` R1–R10; migración V39 bloqueante |
| 5 | Plan técnico respeta arquitectura y stack | Secciones `Arquitectura`, `Technical Context`, `Decisiones D1–D16` y `Constitution Check` |
| 6 | Tareas descompuestas y trazables | `tasks.md`: fases por US, IDs T001–T044, rutas reales, FR/SC en cada tarea y pruebas asociadas |
| 7 | Ambigüedades resueltas o documentadas | `spec.md`: Clarificaciones y Supuestos; `research.md`: decisiones con alternativas; `Complexity Tracking` |
| 8 | Especificación y verificaciones de calidad revisadas | `checklists/requirements.md` con todos los ítems en `[x]`; re-evaluación post-diseño de este plan |

### Trazabilidad US → FR → UI/backend → fases

| Historia | FR | Componentes / módulo | Fases y tareas |
|----------|----|----------------------|----------------|
| US1 Popover | FR-001..FR-006, FR-021 | `NotificationsMenu`, `BellIcon`, `AppHeader.scss`, `notificationsClient` | Fase 2 (T017, T020), Fase 3 (T021–T023), Fase 5 (T030) |
| US2 Bandeja | FR-007..FR-015 | `NotificationsPage`, `NotificationRow`, `App.jsx`, `NotificationService`, `NotificationController` | Fase 2 (T006, T009, T014, T018, T019), Fases 4–5 (T024–T030) |
| US3 Deep links | FR-016..FR-022 | `notificationTarget`, `GroupDebtsView`, `NotificationsPage`, `NotificationsMenu` | Fase 5 (T028–T031) |
| US4 Preferencias | FR-023..FR-028 | `NotificationPreferencesModal`, `UserNotificationPreferences`, `NotificationPreferenceService`, puntos de enganche | Fase 2 (T007, T010, T019), Fase 6 (T032–T038), Fase 7 (T041) |
| US5 Resiliencia y reglas | FR-029..FR-036 | `NotificationDeliveryService`, `NotificationEventListener`, `NotificationMailService`, `SecurityConfig`, `ProblemDetailExceptionHandler` | Fase 2 (T011–T016, T018–T019), Fase 6 (T036–T038), Fase 7 (T039–T044) |

## Complexity Tracking

| Violación | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Prefijo `/api/v1/notifications` frente al `/api/...` sin versión del resto del proyecto | El backlog exige literalmente esas rutas | Reutilizar `/api/notifications` sería más uniforme pero incumpliría el contrato pedido; no se propone porque el requisito es explícito. No se renombra ningún endpoint existente. |
| Dos tablas nuevas (`notifications`, `user_notification_preferences`) | No existe historial ni preferencias por usuario; la campana actual solo consulta solicitudes de amistad en memoria de la petición | Reutilizar `reminders`, `debt_whistles` o `group_activity` no daría bandeja por usuario ni preferencias, y mezclaría historial de grupo con bandeja personal. |
| Refactor de `NotificationsMenu.jsx` en lugar de componente nuevo | Ya concentra campana, badge, cierre y estilos del encabezado | Crear una campana paralela duplicaría controles y estilos en la misma barra y rompería los tests de 015; se conservan sus contratos públicos. |
