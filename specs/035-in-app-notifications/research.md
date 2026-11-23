# Research: Bandeja completa y configuración de notificaciones in-app

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Contrato**: [contracts/notifications-api.md](./contracts/notifications-api.md)

**Estado de clarificaciones**: no quedan `NEEDS CLARIFICATION` en el Technical Context del plan. Las decisiones de alcance que podrían haberlo sido (tamaño del popover, defaults, destinos inexistentes, duplicidad de correos) están resueltas como supuestos documentados en `spec.md` (`Clarificaciones`, `Supuestos`). Las decisiones técnicas siguientes se basan en la inspección del código existente y en las convenciones de la constitución.

---

## R1. Persistencia de la bandeja y de las preferencias

**Decision**: Dos tablas nuevas en una sola migración `V39__create_notifications.sql`: `notifications` (historial por usuario) y `user_notification_preferences` (una fila por usuario con dos canales y cuatro tipos de evento, todos activos por defecto). Los identificadores de destino (grupo, deuda, gasto, solicitud) se guardan como columnas planas, sin foreign keys.

**Rationale**: no existe ninguna entidad, tabla ni repositorio de notificaciones in-app en el backend; la campana actual solo consulta solicitudes de amistad en el momento. `reminders`, `debt_whistles` y `group_activity` son historiales de grupo o de envío, no bandejas por persona. Las FK a destinos impedirían conservar un aviso cuyo grupo o deuda fue eliminado, caso exigido por FR-019/SC-005. El proyecto usa ids planos en otras entidades (`GroupExpense.groupId`, `FriendshipRequest.senderId`) y `ddl-auto=validate` con Flyway.

**Alternatives considered**: (a) reutilizar `group_activity` como bandeja — descartado: pertenece al grupo, no al usuario, y no tiene estado de lectura; (b) derivar la bandeja de `reminders` + `debt_whistles` — descartado: no cubre gasto, pago ni actividad social y no permite marcar como leída; (c) guardar preferencias en el `User` existente — descartado: mezcla configuración de notificación con el perfil y no respeta el nombre de tabla exigido por el backlog.

## R2. Prefijo versionado `/api/v1/notifications`

**Decision**: Implementar literalmente los cinco endpoints exigidos bajo `/api/v1/notifications`, en un controlador nuevo, sin renombrar endpoints existentes.

**Rationale**: el backlog fija las rutas; el resto del API usa `/api/...` sin versión, pero agregar un prefijo nuevo no rompe ningún contrato existente. Requiere añadir `/api/v1/notifications/**` al matcher de `verifiedAccount(...)` de `SecurityConfig`, porque si no caería en `anyRequest().authenticated()` y permitiría cuentas no verificadas (FR-033).

**Alternatives considered**: (a) `/api/notifications` por uniformidad — descartado: incumple el requisito explícito; (b) exponer ambos prefijos como alias — descartado: duplica contratos y superficie de seguridad sin beneficio; (c) modificaciones a endpoints existentes — descartado: constitución VII.

## R3. Generación desacoplada y confirmada

**Decision**: Los servicios de negocio publican `NotificationEvent` mediante `NotificationEventPublisher.publishSafely(...)`; un `NotificationEventListener` con `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` entrega el aviso.

**Rationale**: `BackendApplication` ya tiene `@EnableAsync` y `@EnableScheduling`. Publicar desde dentro de la transacción y entregar después del commit garantiza que un fallo de notificación no revierta la operación de negocio y que no se generen avisos para operaciones fallidas. `fallbackExecution = true` cubre los jobs planificados que publican fuera de transacción (recordatorios, vencimientos).

**Alternatives considered**: (a) llamada directa al servicio de notificaciones dentro de la transacción de negocio — descartado: un fallo de la notificación revertiría la operación principal (viola FR-032); (b) `@Async` directo sin eventos — descartado: puede ejecutarse antes del commit y no distingue operaciones fallidas; (c) polling de tablas de negocio — descartado: cambia reglas y agrega un job nuevo fuera de alcance.

## R4. Correo resiliente y sin duplicados

**Decision**: `NotificationMailService.send` es `@Async` y captura `MessagingException | RuntimeException` con `log.warn`, sin propagar. El evento incluye `emailPolicy`: `NONE` para `REMINDER` y `DEBT_WHISTLE` (sus flujos ya envían correo de negocio en 025/030), `SUBSCRIBABLE` para el resto. Cada canal se evalúa en su propio `try/catch`.

**Rationale**: `VerificationMailService` y `PasswordResetMailService` ya siguen el patrón "atrapan y no propagan"; `ReminderMailService` y `DebtWhistleService` tienen sus propias reglas de fallo (`FAILED`/503) que no deben cambiar. Evitar una segunda copia respeta la constitución IV y la suposición documentada en la spec.

**Alternatives considered**: (a) redirigir los correos existentes por el nuevo servicio de preferencias — descartado: modificaría 025/030 y su límite de frecuencia; (b) enviar siempre correo de notificación — descartado: duplicaría avisos para deudas; (c) propagar el error y reintentar — descartado: viola FR-032 y el fuera de alcance de la spec.

## R5. Preferencias: defaults y validación

**Decision**: `NotificationPreferenceService.getOrCreate(userId)` crea la fila con los seis booleanos en `true` si no existe; `update` valida la presencia de los seis campos en backend, ignora `userId` del cuerpo y devuelve 400 con `properties.fields` si falta alguno.

**Rationale**: evita estados nulos y migraciones de datos, cumple FR-025 y FR-028 ("la persona de la sesión manda"). El proyecto no usa Bean Validation en controladores: la validación vive en servicios y se traduce con `ProblemDetailExceptionHandler`.

**Alternatives considered**: (a) defaults en el registro de usuario — descartado: exigiría tocar `RegistrationService` y una migración de datos; (b) nullable boolean con `COALESCE` en cada lectura — descartado: dispersa la regla y facilita estados inválidos; (c) validación solo en frontend — descartado: constitución VIII.

## R6. Listado: orden, cursor y contador

**Decision**: dos órdenes explícitos — `history` (`createdAt DESC, id DESC`) y `unread-first` (`readAt IS NULL DESC, createdAt DESC, id DESC`) — con `limit` 1–100 (default 20, popover 5), cursor opaco `Base64(epochMillis|id)` solo para `history`, y `unreadCount` en todas las respuestas.

**Rationale**: el popover necesita no leídas primero sin desordenar la bandeja; `unreadCount` evita un pedido adicional para el badge. El patrón de paginación por cursor y proyecciones con `@Query` ya existe en `UserRepository`/`FriendshipRequestRepository`.

**Alternatives considered**: (a) un único orden con no leídas primero — descartado: contradice "histórico ordenado por fecha" (FR-007); (b) `offset` — descartado: duplicados al insertar mientras se pagina; (c) dos pedidos (lista + contador) — descartado: más latencia y riesgo de desincronización del badge.

## R7. Enlaces profundos y destinos eliminados

**Decision**: resolvedor puro `notificationTarget.js` que devuelve `{path, missingTarget}` desde `target.kind` y sus ids; rutas `/groups/{id}`, `/groups/{id}/debts?debt=`, `/activity?debt=` y `/friends`. Se llama a `markRead(id)` antes de navegar; si falla no se navega; si el destino falta se navega a la sección general con aviso no bloqueante.

**Rationale**: el texto del mensaje no es una fuente fiable de destino; los ids se guardan estructurados en `NotificationResponse.target`. Las rutas destino ya existen en `App.jsx` y el parámetro real del grupo es `:id`. El patrón de `useSearchParams` + `navigate` ya se usa en `VerifyEmailPage`/`ResetPasswordPage`.

**Alternatives considered**: (a) parsear el título para decidir la ruta — descartado: frágil y no testeable; (b) guardar una URL completa en la notificación — descartado: quedaría rota si cambian las rutas o se elimina el destino; (c) navegar primero y marcar después — descartado: FR-016/FR-022 exigen marcar antes y no navegar si falla.

## R8. Autenticación y aislamiento por usuario

**Decision**: El controlador obtiene el usuario del principal `Long` del `SecurityContext` (helper `currentUserId()`), ignora parámetros `userId`, filtra todas las consultas por `user_id` y devuelve 404 neutral para ids inexistentes o ajenos.

**Rationale**: todos los controladores existentes usan ese patrón; `JwtCookieAuthenticationFilter` pone el `Long` del usuario en el contexto. No se debe confiar en que el frontend oculte acciones (constitución IX).

**Alternatives considered**: (a) 403 cuando el id existe pero es ajeno — descartado: revela existencia (SC-008); (b) confiar en un `userId` del cuerpo — descartado: vulnerabilidad de acceso; (c) filtrar solo en frontend — descartado: constitución IX.

## R9. Integración en el frontend existente

**Decision**: Refactorizar `NotificationsMenu.jsx` (conservando `BellIcon`, `useDismiss`, `.app-menu__panel--end`, `.app-header__badge` y las constantes exportadas) y agregar `NotificationsPage`, `NotificationRow`, `NotificationPreferencesModal`, `notificationsClient` y `notificationTarget`. La ruta `/notifications` se protege con `ProtectedRoute`.

**Rationale**: `NotificationsMenu.jsx` ya concentra campana, contador con tope "9+", cierre con Escape/click fuera y eventos de recarga; duplicarlo crearía dos campanas. El patrón de clientes `fetch` con `{ok:false,...}` y tests junto al archivo es uniforme en `frontend/src/api`.

**Alternatives considered**: (a) componente de campana nuevo — descartado: duplica UI y rompe los tests de 015; (b) poner las preferencias en `/settings` — descartado: la spec define un modal desde bandeja/popover; (c) React Query/SWR — descartado: no existe en el proyecto (dependencia nueva).

## R10. Pruebas y cobertura

**Decision**: `*Test` unitarios y `*IT` de integración con Testcontainers + GreenMail en backend; `*.test.js`/`*.test.jsx` con Vitest + Testing Library en frontend. Se actualiza `TRUNCATED_TABLES` en `AbstractIntegrationIT` con las dos tablas nuevas y se comprueba con `TruncateCoverageIT`. Cobertura ≥ 85 % con JaCoCo (`mvn -B verify`) y vitest v8 (`npm run test:coverage`).

**Rationale**: la constitución V y el CI (`/.github/workflows/ci.yml`) exigen esos comandos y umbrales. `AbstractIntegrationIT.TRUNCATED_TABLES` es explícito: una tabla nueva no listada hace fallar `TruncateCoverageIT`. Los tests de integración usan cookies JWT reales (`session(user)`), no `@WithMockUser`.

**Alternatives considered**: (a) reducir umbral o excluir código nuevo — descartado: constitución V; (b) tests solo de frontend — descartado: no cubre resiliencia SMTP/BD ni aislamiento; (c) mocking de la BD en los IT — descartado: se pierde la validación de Flyway y del esquema.

---

## Fuentes consultadas

- `backend/src/main/java/com/amano/backend/config/SecurityConfig.java`, `config/JwtCookieAuthenticationFilter.java`, `common/ProblemDetailExceptionHandler.java`
- `backend/src/main/java/com/amano/backend/groupexpense/reminder/{ReminderDispatcher,ReminderMailService,ReminderMessageBuilder}.java`, `groupexpense/whistle/DebtWhistleService.java`
- `backend/src/main/java/com/amano/backend/verification/VerificationMailService.java`, `passwordreset/PasswordResetMailService.java`
- `backend/src/main/resources/db/migration/V38__create_incomes.sql`, `V37__create_debt_whistles.sql`, `V27__create_reminders_table.sql`
- `backend/src/test/java/com/amano/backend/support/{AbstractIntegrationIT,TruncateCoverageIT}.java`
- `frontend/src/App.jsx`, `components/{AppHeader,NotificationsMenu}.jsx`, `api/{statisticsClient,friendsClient}.js`, `hooks/useDismiss.js`
- `.github/workflows/ci.yml`, `backend/pom.xml`, `frontend/vite.config.js`, `.specify/memory/constitution.md`
