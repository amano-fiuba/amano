# Implementation Plan: Visualización de perfil de usuario

**Branch**: `feat/user-profile` | **Date**: 2026-10-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-user-profile/spec.md`

## Summary

Pantalla "Mi perfil" de solo lectura para el usuario con sesión iniciada: nombre, correo electrónico, contraseña enmascarada (nunca en texto claro, FR-007 de `001`), apellido, avatar (propio o predeterminado), CBU/CVU y preferencias de monedas (ARS por omisión). El backend de `001` ya expone los datos (`GET /api/users/me`, `GET /api/users/{id}/avatar`), por lo que esta feature es **100% frontend** (SPA) más la documentación del spec: sin endpoints nuevos, sin migraciones y sin cambios de esquema.

## Technical Context

**Language/Version**: JavaScript ES2022 con React 19.2.8 (SPA); backend Java 25 / Spring Boot 4.1.1 reutilizado sin modificaciones

**Primary Dependencies**: react-router 8.4.0 (routing), AuthContext del repo (sesión), `authClient.me()` (`frontend/src/api/authClient.js`, ya existente); Vitest 5 + Testing Library (tests)

**Storage**: PostgreSQL 18 vía backend existente (solo lectura de tablas `users`, `user_avatars`, `user_currencies`; sin cambios de esquema)

**Testing**: Vitest + Testing Library co-ubicado (gate de cobertura v8: ≥85% líneas/statements); `npm run lint` (ESLint 10 flat)

**Target Platform**: SPA servida por Vite (dev :8081, proxy `/api` → :8080) y nginx en producción (origen único)

**Project Type**: Monorepo web (backend Spring Boot + frontend React)

**Performance Goals**: Perfil visible con datos completos < 2 s en red normal (SC-001); un solo GET al perfil por visita de pantalla

**Constraints**: Sin datos ficticios ante fallo (FR-009); sí cookie HttpOnly → sin tokens manipulables desde el cliente; contraseña jamás en la red por fuera del hash ya resuelto en `001`

**Scale/Scope**: 1 página nueva (`ProfilePage`), 1 ruta protegida nueva (`/profile`), 1 entrada de navegación; 0 endpoints nuevos; 0 migraciones

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Test-first** (Constitution V del repo): los tests co-ubicados de cada historia se escriben primero y se verifican FALLANDO antes de la implementación; se refleja en el orden de `tasks.md`.
- **Validación en fronteras** (FR-022 de `001`): no aplica — no hay escritura de datos; la pantalla consume endpoints ya validados en el backend.
- **Privacidad del CBU/CVU y de la contraseña** (FR-007/FR-009 de `001`): se conserva — el endpoint `me` solo expone datos del principal de sesión; la presentación enmascarada es UI pura.
- **Cobertura ≥ 85% frontend**: se mantiene el gate de cobertura habilitado en `test:coverage`.
- **Conventional Commits + PR a `sprint-1`**: flujo de cierre documentado en tasks.

Sin violaciones: no requiere entrada en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/002-user-profile/
├── spec.md              # Historia, clarificaciones, FR/SC (hecho)
├── plan.md              # Este archivo
├── data-model.md        # No-op: lee tablas de 001, sin cambios de esquema
├── contracts/
│   └── users-api.md     # Reuso de GET /api/users/me + avatar, sin endpoints nuevos
├── quickstart.md        # Validación manual E2E por docker compose
└── tasks.md             # Plan de implementación por fases/historias
```

### Source Code (conducido por el plan)

**Backend**: SIN cambios. Los endpoints necesarios ya existen y cumplen el contrato:

- `GET /api/users/me` — `UserController` (`backend/src/main/java/com/amano/backend/user/UserController.java`), respuesta `UserAccountResponse` (nunca password).
- `GET /api/users/{id}/avatar` — binario o 404 (fallback default en cliente).

```text
frontend/src/
├── api/
│   └── authClient.js            # EXISTE: me() reutilizada sin cambios
├── auth/
│   ├── AuthContext.jsx          # EXISTE: useAuth() expone el perfil de sesión
│   └── ProtectedRoute.jsx       # EXISTE: gate de 401 → /register con destino preservado
├── pages/
│   ├── ProfilePage.jsx          # NUEVO: pantalla de perfil de solo lectura
│   └── ProfilePage.test.jsx     # NUEVO (test-first): escenarios US1/US2/US3
└── App.jsx                      # MODIFICAR: ruta /profile envuelta en ProtectedRoute
```

**Structure Decision**: el feature vive íntegramente en `frontend/src/pages/` y el wiring de `App.jsx`, replicando el patrón de `DashboardPage` (fetch de `me()`, fallback de avatar, default ARS). Se reutiliza `authClient.me()` en vez de `useAuth()` a secas para que la pantalla refresque datos frescos al entrar (igual que hace el dashboard) y para manejar explícitamente el estado de fallo de red.

## Implementation Strategy

- Cada User Story avanza con sus tests primero (FAIL) y su implementación después; los checkpoints de `tasks.md` cierran con lint + tests verdes por historia.
- US1 entrega el MVP (pantalla con datos correctos); US2 y US3 refinan aislamiento y resiliencia; US3 se integra con los mecanismos existentes (`ProtectedRoute`, fallback de avatar del dashboard) en vez de duplicarlos.
- Cierre: verificación (coverage, lint, `mvn verify` de regresión) y quickstart manual con docker compose.

## Complexity Tracking

No hay entradas: el diseño se mantiene simple (una página, sin estado nuevo, reuso de infraestructura existente).
