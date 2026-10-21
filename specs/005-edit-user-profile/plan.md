# Implementation Plan: Modificar perfil de usuario

**Branch**: `feat/edit-user-profile` | **Date**: 2026-10-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-edit-user-profile/spec.md`

## Summary

Edición del perfil para el usuario con sesión iniciada y correo verificado: pantalla "Modificar perfil" precargada con los datos frescos del servidor, edición de nombre, apellido, CBU/CVU y monedas con las reglas de `001`, correo electrónico visible pero invariante, cancelación sin guardar, cambio de contraseña con acreditación de la contraseña actual, y gestión de avatar (reemplazo/eliminación). A diferencia de `002`, esta feature **requiere cambios de backend**: endpoints de mutación nuevos (`PUT /api/users/me`, cambio de contraseña, avatar upsert/delete) sin migraciones nuevas (el esquema de `001` ya soporta todo).

## Technical Context

**Language/Version**: Backend Java 25 / Spring Boot 4.1.1 (extensión de `UserController`); frontend JavaScript ES2022 con React 19.2.8 (SPA)

**Primary Dependencies**: Spring Boot (bean validation / ProblemDetail ya configurado en `001`), BCrypt ya existente para la verificación de credenciales; react-router 8.4.0, AuthContext del repo, `authClient` (`frontend/src/api/authClient.js`); Vitest 5 + Testing Library (frontend), JUnit + MockMvc (backend)

**Storage**: PostgreSQL 18 vía backend existente; **sin migraciones nuevas** (`ddl-auto=validate` de la V1 de `001` debe seguir validando sin diferencias). Mutación de `users` (nombre, apellido, cbu, password_hash), `user_currencies` (delete+insert) y `user_avatars` (upsert/delete)

**Testing**: Vitest + Testing Library co-ubicado (gate de cobertura v8: ≥85% líneas/statements); `mvn verify` con JaCoCo (gate existente); `npm run lint` (ESLint 10 flat)

**Target Platform**: SPA servida por Vite (dev :8081, proxy `/api` → :8080) y nginx en producción (origen único); backend Spring Boot :8080

**Project Type**: Monorepo web (backend Spring Boot + frontend React) — primera feature de la línea del perfil con backend nuevo

**Performance Goals**: Precarga de la edición < 2 s (SC-001); feedback de validación < 2 s (SC-004); un solo `GET me` por ingreso a la edición y un `PUT` por guardado

**Constraints**: Correo electrónico invariante (FR-007): la API de mutación ignora/rechaza el campo `email`; contraseña actual como requisito del cambio (FR-013/FR-014); validación en fronteras (FR-022 de `001`): el servidor revalida todo; sin cambios parciales ante errores

**Scale/Scope**: Backend: 3 endpoints nuevos + 1 endpoint de cambio de contraseña en `UserController`, servicios y tests. Frontend: 2 páginas nuevas (`ProfileEditPage`, `ChangePasswordPage`), 2 rutas protegidas nuevas, entrada de navegación desde `ProfilePage` de `002`; 0 migraciones

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Test-first** (Constitution V del repo): los tests co-ubicados (frontend) y de `@WebMvcTest`/integración (backend) se escriben primero y se verifican FALLANDO antes de la implementación; se refleja en el orden de `tasks.md`.
- **Validación en fronteras** (FR-022 de `001`): aplica y se conserva — toda mutación nueva revalida en el servidor (Java validation + reglas de negocio), sin confiar en la UI.
- **Privacidad del CBU/CVU y de la contraseña** (FR-007/FR-009 de `001`): se conserva — los endpoints de edición son siempre del principal; la contraseña (actual y nueva) nunca se registra ni se devuelve; `email` es inalterable.
- **Cobertura ≥ 85% frontend**: se mantiene el gate; el nuevo código de backend usa el gate JaCoCo vigente de `001`.
- **Conventional Commits + PR a `sprint-1`**: flujo de cierre documentado en tasks.

Sin violaciones: no requiere entrada en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/005-edit-user-profile/
├── spec.md              # Historia, clarificaciones, FR/SC (hecho)
├── plan.md              # Este archivo
├── data-model.md        # Mutaciones sobre el esquema de 001; SIN migraciones
├── contracts/
│   └── users-api.md     # Endpoints nuevos de edición (PUT me, password, avatar)
├── quickstart.md        # Validación manual E2E por docker compose
└── tasks.md             # Plan de implementación por fases/historias
```

### Source Code (conducido por el plan)

**Backend** (paquete `user` existente):

```text
backend/src/main/java/com/amano/backend/user/
├── UserController.java          # MODIFICAR: PUT /api/users/me, PUT/DELETE /api/users/me/avatar
├── UserAccountService.java      # MODIFICAR: updateProfile, changePassword, updateAvatar, deleteAvatar
├── dto/UserAccountResponse.java # EXISTE: respuesta de las mutaciones (nunca password)
└── dto/UpdateProfileRequest.java           # NUEVO: firstName, lastName, currencies, cbuCvu (sin email)
└── dto/UpdatePasswordRequest.java          # NUEVO: currentPassword, newPassword, confirmPassword
```

**Frontend**:

```text
frontend/src/
├── api/
│   └── authClient.js            # MODIFICAR: updateProfile(), updatePassword(), updateAvatar(), deleteAvatar()
├── auth/
│   └── ProtectedRoute.jsx       # EXISTE: gate de 401 → /register con destino preservado
├── pages/
│   ├── ProfilePage.jsx          # EXISTE (002): MODIFICAR — link "Modificar perfil" → /profile/edit
│   ├── ProfileEditPage.jsx      # NUEVO: formulario de datos + avatar, precargado
│   ├── ProfileEditPage.test.jsx # NUEVO (test-first): escenarios US1/US3/US4
│   ├── ChangePasswordPage.jsx   # NUEVO: cambio de contraseña (US2)
│   └── ChangePasswordPage.test.jsx # NUEVO (test-first): escenarios US2
└── App.jsx                      # MODIFICAR: rutas /profile/edit y /profile/password con ProtectedRoute
```

**Structure Decision**: la edición reutiliza los validadores de `001` existentes (nombre/apellido 2–50 tras trim, CBU 22 dígitos con normalización, política de contraseña, magic bytes de avatar, conjunto de monedas {ARS, USD, EUR}). Las pantallas replican el patrón `DashboardPage`/`ProfilePage` (fetch de `me()` al montar, estados loading/datos/error, fallback de avatar). El cambio de contraseña vive en su propia página para no mezclar la acreditación con la edición de datos (decisiones: clarificación 2026-10-20 — contraseña actual exigida). El correo se renderiza deshabilitado en ambas pantallas.

## Implementation Strategy

- Cada User Story avanza con sus tests primero (FAIL) y su implementación después; los endpoints de backend de US1-US3 se agrupan en un único lote de contrato para no romper el build intermedio.
- US1 entrega el MVP (editar y persistir datos); US2 y US3 agregan contraseña y avatar; US4 cierra aislamiento/estados de fallo reutilizando `ProtectedRoute` y el patrón de error existente.
- Cierre: verificación (coverage frontend, `mvn verify`, lint) y quickstart manual con docker compose.

## Complexity Tracking

No hay entradas: el diseño reutiliza el esquema y los validadores de `001`; los endpoints son CRUD simple sobre recursos del principal.
