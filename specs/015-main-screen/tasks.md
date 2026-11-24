# Tasks: Pantalla principal con sesión y nueva navegación

## Etapa 1: Backend

- [x] T001 [US1] `lastActivityAt` en `GroupSummaryResponse`, `GroupActivityRepository` y `GroupService` (FR-007)
- [x] T002 [US1] `GET /api/expenses/recent` en `ExpenseRepository`, `ExpenseService` y `ExpenseController` (FR-008)
- [x] T003 [US1] Pruebas de ambos cambios, incluida la aislación entre cuentas

## Etapa 2: Barra y pantallas mínimas

- [x] T004 [US2] `AppHeader` con accesos y rama con sesión de `SiteNav` (FR-003)
- [x] T005 [US2] `NotificationsMenu` y evento de cambios en `friendsClient` (FR-004)
- [x] T006 [US2] `SettingsPage` con ruta protegida (FR-009)
- [x] T007 [US4] `QuickActionsButton` (FR-010)

## Etapa 3: Buscador

- [x] T008 [US3] `GlobalSearch` (FR-005)
- [x] T009 [US3] `?q=` en `UsersSearchPage` (FR-006)

## Etapa 4: Pantalla principal

- [x] T010 [US1] `RootRoute`, destino del login y `/dashboard` (FR-001, FR-002)
- [x] T011 [US1] `MainPage` con resumen, grupos y actividad personal
- [x] T012 [US1] `PendingRequestsPanel`
- [x] T013 Estilos en modo claro y oscuro, y layout angosto (FR-011)
- [x] T014 Verificación: lint, tests, cobertura y `mvn verify`
