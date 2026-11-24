---
description: "Task list for Bandeja completa y configuración de notificaciones in-app"
---

# Tasks: Bandeja completa y configuración de notificaciones in-app

**Input**: Design documents from `/specs/035-in-app-notifications/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/notifications-api.md, checklists/requirements.md

**Tests**: obligatorias para cerrar cada historia (constitución V, DoR 2, SC-010). Escribir primero y verificar que fallan antes de la implementación. Cobertura mínima del 85 % con JaCoCo (`mvn -B verify`) y vitest v8 (`npm run test:coverage`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: historia a la que pertenece (US1..US5)

## Path Conventions

- Backend producción: `backend/src/main/java/com/amano/backend/`
- Backend pruebas: `backend/src/test/java/com/amano/backend/` (`*Test` rápidos, `*IT` con Testcontainers + GreenMail)
- Frontend: `frontend/src/`, pruebas junto al archivo (`*.test.js`, `*.test.jsx`)
- Migraciones: `backend/src/main/resources/db/migration/`

---

## Fase 1: Setup y esquema (compartida)

**Purpose**: contrato, esquema y modelos base. Todo lo demás depende de esta fase.

- [x] T001 [P] Crear `V39__create_notifications.sql` con `notifications` (id, `user_id` FK `ON DELETE CASCADE`, `type`, `title`, `message`, `target_kind`, `group_id`, `debt_id`, `expense_id`, `friend_request_id`, `created_at`, `read_at`) y `user_notification_preferences` (`user_id` PK/FK, seis booleanos con default `true`, `updated_at`); índice `(user_id, created_at DESC, id DESC)` e índice parcial de no leídas; sin `DELETE` y sin editar V1–V38, en `backend/src/main/resources/db/migration/V39__create_notifications.sql` (FR-030, plan D1, data-model.md)
- [x] T002 [P] Crear entidades `Notification` y `UserNotificationPreferences` y los enums `NotificationType` y `NotificationTargetKind`, con ids de destino como columnas planas y sin FK hacia entidades borrables, en `backend/src/main/java/com/amano/backend/notification/` (FR-017, FR-019, FR-030, plan D1, D3)
- [x] T003 [P] Crear DTOs `record` `NotificationResponse`, `NotificationTargetResponse`, `NotificationListResponse`, `NotificationPreferencesRequest` y `NotificationPreferencesResponse` (con records anidados `Channels`/`EventTypes`), con los campos exactos del contrato, en `backend/src/main/java/com/amano/backend/notification/dto/` (FR-029, plan D3)
- [x] T004 [P] Crear `NotificationEvent`, `NotificationNotFoundException` e `InvalidNotificationPreferencesException` en `backend/src/main/java/com/amano/backend/notification/` (FR-029, FR-031, plan D3, D4)
- [x] T005 Verificar contra `specs/035-in-app-notifications/contracts/notifications-api.md` y `data-model.md` que nombres, formatos de fecha, enums, códigos HTTP y mensajes coinciden antes de implementar servicios (FR-029, constitución VII, DoR 5)

---

## Fase 2: Fundacional (bloqueante)

**⚠️ CRITICAL**: ninguna historia de usuario puede empezar hasta completar esta fase y sus pruebas.

### Backend

- [x] T006 [P] Crear `NotificationRepository` con listado para `history` y `unread-first`, `countByUserIdAndReadAtIsNull` y updates condicionales para marcar una y todas filtrando por `user_id`, en `backend/src/main/java/com/amano/backend/notification/NotificationRepository.java` (FR-007, FR-010..FR-012, FR-031, plan D6)
- [x] T007 [P] Crear `NotificationPreferenceRepository` con búsqueda por `user_id` y persistencia de los seis booleanos, en `backend/src/main/java/com/amano/backend/notification/NotificationPreferenceRepository.java` (FR-025, plan D7)
- [x] T008 [P] Agregar `notifications` y `user_notification_preferences` a `TRUNCATED_TABLES` en `backend/src/test/java/com/amano/backend/support/AbstractIntegrationIT.java` para no romper `TruncateCoverageIT` (plan D14, R10)
- [x] T009 Implementar `NotificationService`: listado con `limit`/`before`/`order`, `unreadCount`, marcado individual y global idempotentes, cursor opaco y 404 neutral para id ajeno o inexistente, en `backend/src/main/java/com/amano/backend/notification/NotificationService.java` (FR-007..FR-014, FR-031, plan D6)
- [x] T010 Implementar `NotificationPreferenceService.getOrCreate` (default todo en `true`) y `update` validando los seis campos e ignorando `userId` del cuerpo, en `backend/src/main/java/com/amano/backend/notification/NotificationPreferenceService.java` (FR-024..FR-028, plan D7)
- [x] T011 [P] Implementar `NotificationEventPublisher.publishSafely` y `NotificationEventListener` con `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` y captura de excepciones con log sin PII, en `backend/src/main/java/com/amano/backend/notification/` (FR-032, FR-036, plan D4)
- [x] T012 [P] Implementar `NotificationDeliveryService.deliver`: lee preferencias, evalúa cada canal en su propio `try/catch` con transacción `REQUIRES_NEW`, inserta si In-App está activo y aplica la política canónica de correo (`ALWAYS` para críticos, `SUBSCRIBABLE` para opcionales, `NONE` para recordatorios), en `backend/src/main/java/com/amano/backend/notification/NotificationDeliveryService.java` (FR-025, FR-026, FR-032, plan D5)
- [x] T013 [P] Implementar `NotificationMailService.send` con `@Async`, `MimeMessage`/`MimeMessageHelper`, `try/catch` de `MessagingException | RuntimeException` y `log.warn` sin propagar, siguiendo `VerificationMailService`, en `backend/src/main/java/com/amano/backend/notification/NotificationMailService.java` (FR-032, plan D15)
- [x] T014 Implementar `NotificationController` con las cinco rutas literales `GET /api/v1/notifications`, `PATCH .../{id}/read`, `PATCH .../read-all`, `GET .../preferences` y `PUT .../preferences`, usuario desde el principal `Long` y sin parámetros de usuario, en `backend/src/main/java/com/amano/backend/notification/NotificationController.java` (FR-029, FR-031, plan D2, D3)
- [x] T015 [P] Agregar `/api/v1/notifications/**` al `requestMatchers(...).access(verifiedAccount(userRepository))` sin tocar las reglas existentes, en `backend/src/main/java/com/amano/backend/config/SecurityConfig.java` (FR-033, plan D2)
- [x] T016 [P] Agregar al `ProblemDetailExceptionHandler` el 404 neutral para notificación no encontrada y el 400 con `properties.fields` para preferencias inválidas, en `backend/src/main/java/com/amano/backend/common/ProblemDetailExceptionHandler.java` (FR-029, FR-031, constitución VIII)

### Cliente API frontend

- [x] T017 [P] Crear `notificationsClient.js` con `getNotifications({limit, before, order})`, `markRead(id)`, `markAllRead()`, `getPreferences()` y `updatePreferences(payload)`, constante única `NOTIFICATIONS_API_BASE`, patrón `{ok:true,...}` / `{ok:false,status,detail}` / `{ok:false,network:true}` y mensajes en español, en `frontend/src/api/notificationsClient.js` (FR-029, plan D9)

### Pruebas fundacionales

- [x] T018 [P] Escribir tests unitarios de `NotificationService` y `NotificationPreferenceService`: orden `history`/`unread-first`, cursor, contador, idempotencia, id ajeno, defaults y validación de preferencias, en `backend/src/test/java/com/amano/backend/notification/NotificationServiceTest.java` y `backend/src/test/java/com/amano/backend/notification/NotificationPreferenceServiceTest.java` (FR-007..FR-014, FR-024..FR-028)
- [x] T019 [P] Escribir `NotificationControllerIT` con cookies JWT reales (`session(user)`) para los cinco endpoints: 200, `unreadCount`, 404 para id ajeno e inexistente, 400 con `fields`, 401 sin sesión/cuenta no verificada y aislamiento entre usuarios, en `backend/src/test/java/com/amano/backend/notification/NotificationControllerIT.java` (FR-029, FR-031, FR-033, plan D14)
- [x] T020 [P] Escribir tests de `notificationsClient`: éxito, 400/404, 401 y error de red, verificando URL `/api/v1/notifications`, `method` y `credentials: 'include'`, en `frontend/src/api/notificationsClient.test.js` (FR-029, plan D9)

**Checkpoint**: la API y el cliente funcionan y están probados; las historias pueden empezar.

---

## Fase 3: User Story 1 - Popover de la campana (Priority: P1) 🎯 MVP

**Goal**: campana con contador de no leídas y popover con cinco avisos recientes y accesos a la bandeja y la configuración.

**Independent Test**: con siete notificaciones (cuatro no leídas), ver contador "4", pulsar la campana, comprobar cinco avisos ordenados y abrir `/notifications` desde el pie.

- [x] T021 [P] [US1] Escribir tests de `NotificationsMenu` mockeando `notificationsClient`: contador, "9+", cinco avisos, orden no leídas primero, estado vacío, error, Escape/click fuera y ambos accesos del pie, en `frontend/src/components/NotificationsMenu.test.jsx` (FR-001..FR-006, SC-001, SC-002, plan D10)
- [x] T022 [US1] Refactorizar `NotificationsMenu.jsx`: reemplazar `listReceivedFriendshipRequests()` por `getNotifications({limit:5, order:'unread-first'})`, usar `unreadCount` para el badge, conservar constantes exportadas, `useDismiss`, `FRIEND_REQUESTS_CHANGED_EVENT` y `BellIcon`, y agregar "Ver todas las notificaciones" y "Configuración", en `frontend/src/components/NotificationsMenu.jsx` (FR-001..FR-006, plan D10)
- [x] T023 [US1] Adaptar `.app-header__badge` y `.app-menu__panel--end` para los dos accesos del pie, el estado vacío y pantallas angostas, en `frontend/src/components/AppHeader.scss` (FR-003, FR-034, SC-009)

**Checkpoint**: US1 es funcional, probada y verificable de forma independiente.

---

## Fase 4: User Story 2 - Bandeja dedicada (Priority: P1)

**Goal**: página `/notifications` con historial, filtros, marcado individual/global, paginación y estados.

**Independent Test**: con diez notificaciones (seis no leídas), filtrar, marcar una y todas, recargar y comprobar que el contador llega a cero y el estado persiste.

- [x] T024 [P] [US2] Escribir tests unitarios de `NotificationsPage` y `NotificationRow`: historial ordenado, filtros "Todas"/"No leídas", etiqueta de lectura, marcar una y todas, idempotencia, "Cargar más", vacío, error con reintento y roles accesibles, en `frontend/src/pages/NotificationsPage.test.jsx` y `frontend/src/components/NotificationRow.test.jsx` (FR-007..FR-015, SC-003, SC-004, plan D11)
- [x] T025 [US2] Crear `NotificationRow.jsx` y `NotificationRow.scss`: título, texto, fecha relativa, etiqueta "No leída", acción "Marcar como leída" solo para no leídas y clic en el cuerpo para navegar, en `frontend/src/components/NotificationRow.jsx` y `frontend/src/components/NotificationRow.scss` (FR-009, FR-011, FR-016)
- [x] T026 [US2] Crear `NotificationsPage.jsx` y `NotificationsPage.scss` con encabezado, botones globales, pestañas accesibles, lista con `NotificationRow`, paginación por cursor, estados carga/vacío/error y botón de configuración, en `frontend/src/pages/NotificationsPage.jsx` y `frontend/src/pages/NotificationsPage.scss` (FR-007..FR-015, plan D11)
- [x] T027 [US2] Registrar la ruta `/notifications` en `App.jsx` dentro de `ProtectedRoute`, sin modificar las rutas existentes, en `frontend/src/App.jsx` (FR-007, FR-033)

**Checkpoint**: US1 y US2 funcionan y se prueban por separado y en conjunto.

---

## Fase 5: User Story 3 - Enlaces profundos (Priority: P1)

**Goal**: pulsar una notificación la marca como leída y navega al grupo, las deudas o amigos, con aviso cuando el destino no existe.

**Independent Test**: abrir notificaciones de los tres tipos desde popover y bandeja; verificar ruta, marcado y aviso en destino eliminado.

- [x] T028 [P] [US3] Escribir tests unitarios del resolvedor: grupo, deuda con grupo, deuda sin grupo, amistad, `NONE`, campos incompletos y destino eliminado, en `frontend/src/utils/notificationTarget.test.js` (FR-016..FR-022, SC-005, plan D13)
- [x] T029 [US3] Crear `notificationTarget.js` con `resolveNotificationTarget(notification)` → `{path, missingTarget}` para `/groups/{id}`, `/groups/{id}/debts?debt=`, `/activity?debt=` y `/friends`, marcando destinos incompletos, en `frontend/src/utils/notificationTarget.js` (FR-017, FR-019, FR-020, plan D13)
- [x] T030 [US3] Integrar el clic en `NotificationsPage`, `NotificationRow` y `NotificationsMenu`: llamar `markRead(id)` antes de navegar; ante un fallo, verificar que se muestra un mensaje de error en español con un botón de reintento y que no se navega; cerrar el popover, actualizar el contador y mostrar aviso no bloqueante si `missingTarget`; ampliar sus tests, en `frontend/src/pages/NotificationsPage.jsx`, `frontend/src/components/NotificationRow.jsx`, `frontend/src/components/NotificationsMenu.jsx` y sus `*.test.jsx` (FR-016, FR-018, FR-021, FR-022)
- [x] T031 [US3] Hacer que `GroupDebtsView` lea `?debt=` para señalar la deuda destino y tolerar su ausencia, actualizando `frontend/src/pages/groups/GroupDebtsView.jsx` y `frontend/src/pages/groups/GroupDebtsView.test.jsx` (FR-017, plan D13)

**Checkpoint**: las tres historias P1 son independientes y demostrables.

---

## Fase 6: User Story 4 - Preferencias de notificación (Priority: P2)

**Goal**: modal con canales y tipos de evento, persistido vía `GET`/`PUT` y aplicado a notificaciones futuras.

**Independent Test**: desactivar correo y actividad de grupo, guardar, recargar, verificar persistencia y que un evento de grupo no genera aviso mientras una deuda sí.

- [x] T032 [P] [US4] Escribir tests unitarios del modal: seis controles con valores iniciales, cancelar descarta, guardar persiste, error conserva cambios, Escape/foco y nombres accesibles, en `frontend/src/components/NotificationPreferencesModal.test.jsx` (FR-023..FR-028, FR-034, SC-006)
- [x] T033 [US4] Crear `NotificationPreferencesModal.jsx` y `NotificationPreferencesModal.scss` con bloques "Canales" y "Tipos de evento", interruptores etiquetados, "Guardar"/"Cancelar" y manejo de foco, en `frontend/src/components/` (FR-024, FR-034, plan D12)
- [x] T034 [US4] Conectar la apertura del modal desde `NotificationsPage` y desde el pie de `NotificationsMenu`, recargando preferencias al guardar; actualizar ambos tests, en `frontend/src/pages/NotificationsPage.jsx` y `frontend/src/components/NotificationsMenu.jsx` (FR-023, plan D12)
- [x] T035 [US4] Publicar `NotificationEvent` al final de las operaciones exitosas en `FriendshipRequestService`, `GroupExpenseService`, `DebtWhistleService`, `ReminderDispatcher`, `OverdueDebtJob` y `PaymentService`, reutilizando textos de `ReminderMessageBuilder` cuando existan, sin alterar reglas de negocio, en `backend/src/main/java/com/amano/backend/` (FR-036, plan D8)
- [x] T036 [P] [US4] Ampliar los tests unitarios de los 6 puntos de enganche para verificar explícitamente la publicación de eventos de cada tipo —incluidos `REMINDER` y `PAYER_DUE_NOTICE` en `ReminderDispatcher` y `DEBT_OVERDUE` en `OverdueDebtJob`— y que no se publica en operaciones fallidas: `FriendshipRequestServiceTest.java`, `GroupExpenseServiceTest.java`, `whistle/DebtWhistleServiceTest.java`, `payment/PaymentServiceTest.java`, `groupexpense/reminder/ReminderDebtFlowIT.java` y `groupexpense/overdue/OverdueDebtJobIT.java`; crear `backend/src/test/java/com/amano/backend/notification/NotificationEventPublisherTest.java` para el comportamiento `publishSafely` (FR-036, plan D4, D8)
- [x] T037 [P] [US4] Escribir tests unitarios de `NotificationDeliveryService`: mapeo tipo → preferencia, In-App off, correo off, tipo off, `emailPolicy ALWAYS` para vencimiento, `NONE` para silbato/recordatorio y persistencia aislada en `REQUIRES_NEW`, en `backend/src/test/java/com/amano/backend/notification/NotificationDeliveryServiceTest.java` (FR-025, FR-026, FR-032, plan D5)
- [x] T038 [P] [US4] Escribir `NotificationPreferenceIT` con cookies JWT reales: `GET` crea defaults, `PUT` persiste los seis booleanos tras recargar, 400 con `properties.fields` por campo faltante y aislamiento entre usuarios, en `backend/src/test/java/com/amano/backend/notification/NotificationPreferenceIT.java` (FR-024..FR-028, SC-006)

**Checkpoint**: las preferencias se configuran, persisten y afectan la generación futura, con pruebas unitarias e integración.

---

## Fase 7: User Story 5 - Resiliencia, regresión y cierre (Priority: P2)

**Goal**: fallos de SMTP/BD no interrumpen el negocio; aislamiento por usuario, accesibilidad, cobertura y trazabilidad DoR.

**Independent Test**: forzar fallo de SMTP y de inserción, ejecutar operaciones que generan avisos y comprobar éxito del negocio, registro del fallo y ausencia de errores para la persona.

- [x] T039 [P] [US5] Escribir `NotificationDeliveryIT` con `@MockitoSpyBean NotificationMailService` lanzando `MessagingException` y fallo controlado de inserción: la operación de negocio responde con éxito, no se revierte, el fallo se registra y se cubre el camino `AFTER_COMMIT` y el `fallbackExecution` sin transacción, en `backend/src/test/java/com/amano/backend/notification/NotificationDeliveryIT.java` (FR-032, SC-007, plan D4, D15)
- [x] T040 [P] [US5] Escribir `NotificationRegressionIT`: ids inexistentes/ajenos, destino eliminado, preferencias por usuario y regresión de campana 015, recordatorios 025, silbato 030, amistad 011/012, balance 033 y estadísticas 032/034, en `backend/src/test/java/com/amano/backend/notification/NotificationRegressionIT.java` (FR-019, FR-031, FR-035, SC-005, SC-008)
- [x] T041 [P] [US5] Escribir tests del flujo de guardado con error 400/red: mensaje en español, sin confirmación falsa y cambios locales conservados, en `frontend/src/components/NotificationPreferencesModal.test.jsx` y `frontend/src/api/notificationsClient.test.js` (FR-027)
- [x] T042 [P] Revisar accesibilidad y responsive de campana, bandeja, fila y modal con teclado y lector de pantalla: nombres de campana/contador/filtros/estados/acciones, foco del modal y sin desplazamiento horizontal, ampliando los tests de `NotificationsMenu.test.jsx`, `NotificationsPage.test.jsx`, `NotificationRow.test.jsx` y `NotificationPreferencesModal.test.jsx` (FR-034, SC-009)
- [x] T043 Ejecutar `mvn -B verify` en `backend/` y `npm ci && npm run lint && npm run test:coverage && npm run build` en `frontend/`; corregir fallos e informar la cobertura alcanzada (≥ 85 %), sin reducir umbrales ni desactivar pruebas (SC-010, constitución V, XI)
- [x] T044 Verificar la trazabilidad FR/SC → tarea y los ocho criterios DoR de `plan.md`, la consistencia entre `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/notifications-api.md`, `quickstart.md` y este archivo, y actualizar `checklists/requirements.md` con las decisiones finales (constitución II, DoR 6–8)

---

## Dependencies & Execution Order

- Fase 1 → Fase 2 → Fases 3–7. Ninguna historia puede empezar antes del checkpoint de la Fase 2.
- Fase 2: T006–T008, T015, T016 y T017 son independientes; T009 depende de T006, T010 de T007, T011–T013 solo por contrato de eventos, T014 de T009/T010, T018–T020 dependen del código que prueban.
- Fase 3: T021 antes de T022; T023 depende de T022.
- Fase 4: T024 antes de T025; T026 depende de T025; T027 de T026.
- Fase 5: T028 antes de T029; T030 depende de T022, T025 y T029; T031 depende de T029.
- Fase 6: T032 antes de T033; T034 depende de T022, T026 y T033; T035 es independiente del frontend; T036 depende de T011 y T035; T037 de T012; T038 de T007 y T010.
- Fase 7: T039–T042 después de las fases 3–6; T043 al final, después de T039–T042; T044 después de T043.

## Parallel Opportunities

- T001–T005 en paralelo entre sí.
- T006, T007, T008, T015, T016 y T017 en paralelo; T018–T020 en paralelo cuando esté listo el código que prueban.
- T021, T024, T028 y T032 (tests) pueden escribirse en paralelo apenas termina la Fase 2.
- T035 (backend) en paralelo con T032–T034 (frontend); T036 y T037 en paralelo; T038 en paralelo con ellos.
- T039, T040, T041 y T042 en paralelo; T043 y T044 secuenciales al final.

## Implementation Strategy

1. **MVP (US1 + US2)**: Fases 1–4 entregan campana con popover y bandeja completa sobre una API probada.
2. **Deep links (US3)**: Fase 5 agrega navegación contextual y marcado automático.
3. **Preferencias (US4)**: Fase 6 agrega el modal, la persistencia y los puntos de publicación, con tests unitarios e integración.
4. **Resiliencia y cierre (US5)**: Fase 7 cubre SMTP/BD caídos, regresión, accesibilidad, cobertura y DoR.
5. **Entrega incremental**: cada fase termina con su checkpoint y puede revisarse por separado, con PR a `sprint-n` (constitución III, XII).

## Trazabilidad de pruebas por funcionalidad

| Funcionalidad | Test unitario | Test de integración | Test frontend |
|---------------|---------------|---------------------|---------------|
| Listado y paginación (FR-007, FR-010) | T018 `NotificationServiceTest` | T019 `NotificationControllerIT` | T020, T024 |
| Marcado individual/global (FR-011, FR-012, FR-014) | T018 | T019 | T024 |
| Preferencias GET/PUT (FR-024..FR-028) | T010 en T018, T037 | T038 `NotificationPreferenceIT` | T032, T041 |
| Generación y canales (FR-026, FR-032) | T037 `NotificationDeliveryServiceTest` | T039 `NotificationDeliveryIT` | T032 |
| Eventos de negocio (FR-036) | T036 (servicios originadores + publisher) | T039, T040 | — |
| Popover (FR-001..FR-006) | — | T019 (datos) | T021, T042 |
| Deep links (FR-016..FR-022) | T028 `notificationTarget.test.js` | T040 `NotificationRegressionIT` | T030, T031, T042 |
| Seguridad y aislamiento (FR-031, FR-033) | T018 | T019, T038, T040 | T027 |

## Notas

- No hay dependencias nuevas: `JavaMailSender`, `ApplicationEventPublisher` y React Router ya están en el proyecto (constitución X, XI).
- La migración V39 es nueva: no se editan V1–V38.
- El prefijo `/api/v1/notifications` se conserva porque el backlog lo exige; justificación en `plan.md` (`Complexity Tracking`).
- Los correos de verificación, recuperación, recordatorios y silbatos no cambian: no se envían copias duplicadas desde el subsistema de notificaciones.
- Ninguna tarea crea, edita ni elimina gastos, pagos, deudas ni amistades: los eventos se publican después de completar esas operaciones.
- Un task no se marca como terminada solo por escribir código: debe verificarse el comportamiento y las pruebas correspondientes (DoD).
