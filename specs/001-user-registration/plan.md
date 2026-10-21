# Implementation Plan: Registro de usuario

**Branch**: `feat/user-registration` | **Date**: 2026-10-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-user-registration/spec.md`

## Summary

Implementar el registro de usuarios de la plataforma "A mano": pantalla de registro en el
frontend web responsive con validaciones en vivo y manejo de errores controlado, y un backend
Spring Boot que expone `POST /api/auth/register` (multipart atómico con datos y avatar opcional),
`GET /api/users/me` (perfil autenticado) y `GET /api/users/{id}/avatar`. La cuenta se crea con
contraseña hasheada mediante BCrypt, correo normalizado único (índice UNIQUE + manejo concurrente
con 409), avatar almacenado en tabla dedicada `user_avatars` (o default estático del frontend),
monedas preferidas multiselección (default ARS) y sesión automática posterior al registro mediante
JWT en cookie HttpOnly `SameSite=Strict` (24 h). Errores estandarizados con ProblemDetail
(RFC 7807).

## Technical Context

**Language/Version**: Java 25 (LTS) + Spring Boot 4.1.1 (Spring Framework 7.0.9, Spring Security 7.1.1, Hibernate Validator 9.1.3). Frontend: React 19.2.8 con Vite 8.3.0 (Rolldown) y Node.js 20.19+/22.12+.

**Primary Dependencies**: Backend: `spring-boot-starter-webmvc`, `spring-boot-starter-security` (nuevo), `spring-boot-starter-validation` (nuevo), `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway` + `flyway-database-postgresql`, `io.jsonwebtoken:jjwt-api/impl/jackson` (nuevo), PostgreSQL JDBC driver 42.7.13. Frontend: `react-router` (nuevo), `react-hook-form` (nuevo), ESLint 10, Vitest 5 + React Testing Library + `@vitest/coverage-v8` 5.0.5.

**Storage**: PostgreSQL 18 (imagen `postgres:18-alpine`, contenedor Docker con volumen persistente `db_data`). Migraciones versionadas con Flyway. Binarios de avatar en tabla dedicada `user_avatars` (`@OneToOne` con `FetchType.LAZY`).

**Testing**: Backend: JUnit Jupiter (`*Test` unitarias/rápidas) + Testcontainers PostgreSQL (`*IT` de integración) con Maven Failsafe y JaCoCo 0.8.15 (umbral ≥ 85 % líneas, ya configurado en `mvn verify`). Frontend: Vitest en jsdom con Testing Library y `user-event`, cobertura V8 (≥ 85 % líneas y sentencias, ya configurado).

**Target Platform**: Aplicación web responsive (navegadores móviles y desktop; sin cliente nativo). Despliegue orquestado con Docker Compose (servicios `db`, `backend`, `frontend`); proxy inverso del frontend (Nginx en producción / Dev Proxy de Vite en desarrollo) reescribe `/api/*` a `http://backend:8080` garantizando origen único.

**Project Type**: Monorepo web (backend `backend/` Java/Spring, frontend `frontend/` React/Vite, CI con GitHub Actions).

**Performance Goals**: Criterios de éxito de la spec convertidos a objetivos operativos: feedback de error visible y botón rehabilitado < 2 s ante fallo de validación o de red (SC-003); proceso completo de registro hasta el dashboard < 1 min en condiciones normales (SC-001).

**Constraints**: Cobertura ≥ 85 % en ambos módulos (umbral ya configurado, no debe bajarse); historia a la vez por ruta crítica (P1→P2→P3); estricto cumplimiento de FR-001..FR-022 y SC-001..SC-007; CSRF deshabilitado conscientemente (cookies `SameSite=Strict` + origen único por proxy); contraseñas nunca recuperables ni visibles (BCrypt + exclusión de DTOs y logs).

**Scale/Scope**: Proyecto universitario, un feature (Registro de usuario), 22 requisitos funcionales, 3 historias de usuario (P1/P2/P3), 3 tablas nuevas.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Veredicto | Evidencia |
|---|---|---|
| I. Arquitectura y separación de responsabilidades | ✅ PASS | Se respeta la estructura monorepo existente (`backend/` Spring Boot, `frontend/` React, proxy Nginx existente); no hay refactorizaciones fuera del alcance. |
| II. Desarrollo SDD y trazabilidad | ✅ PASS | spec + clarificaciones integradas (4 Q); este plan y tasks posteriores trazables a FR/SC. |
| III. Entrega incremental por historia | ✅ PASS | Tareas se organizarán por US1 (P1) → US2 (P2) → US3 (P3), cada una implementable/revisable por separado vía PR a `sprint-n`. |
| IV. Alcance controlado | ✅ PASS | Sin funcionalidades extra; fuera de alcance respetado sin OAuth, 2FA, verificación de correo ni recuperación de contraseña. Los 4 supuestos de la spec se respetan. Se agregan dependencias (security/validation/jjwt/react-router/react-hook-form) exclusivamente requeridas por la spec y el stack confirmado. |
| V. Calidad, pruebas y cobertura | ✅ PASS | Umbrales ≥ 85 % ya configurados (JaCoCo BUNDLE LINE y Vitest lines/statements); se agregan pruebas unitarias e IT por requisito y edge cases. No se excluye código nuevo. |
| VI. Integridad de reglas de negocio | ✅ PASS | Validaciones en fronteras (frontend en vivo + servidor con Bean Validation); datos inválidos o duplicados nunca generan estados inconsistentes (índice UNIQUE como última línea). |
| VII. Contratos REST y compatibilidad | ✅ PASS | Contratos documentados en `contracts/`; no hay endpoints previos que romper (solo existe el healthcheck). Headers JSON coherentes con ProblemDetail. |
| VIII. Validación y manejo de errores | ✅ PASS | ProblemDetail RFC 7807 para 400/409/413; mensajes de usuario en español según spec; sin exposición de credenciales ni detalles internos. |
| IX. Seguridad y control de acceso | ✅ PASS con nota | El repo aún no tenía autenticación; la estrategia (Spring Security + BCrypt + JWT en cookie HttpOnly `SameSite=Strict` 24 h) es la decisión explícita y confirmada del usuario del plan — se trata explícitamente aquí en lugar de inventarse. `GET /api/users/me` y `GET /api/users/{id}/avatar` requieren sesión válida; CBU/CVU solo visible para el dueño. |
| X. Código mantenible | ✅ PASS | Convenciones: paquetes `com.amano.backend.*`, inglés en código, español en artefactos; sin dependencias redundantes (evaluadas en research.md). |
| XI. Entorno reproducible e CI | ✅ PASS | Reutiliza `compose.yaml` existente (healthcheck de `db` ya presente, parametrizado con `${POSTGRES_USER}` y `${POSTGRES_DB}` vía `pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}`), Dockerfiles existentes, CI existente con las verificaciones de contrato. |
| XII. Revisión por pares | ✅ PASS | PRs de feature dirigidos a `sprint-n` conforme a constitución (fuera del alcance de este plan). |
| XIII. Commits y trazabilidad | ✅ PASS | Conventional Commits; referencias a tareas cuando existan. |

**Verdict**: ✅ GATE PASSED — sin violaciones que requieran tabla de complejidad.

## Project Structure

### Documentation (this feature)

```text
specs/001-user-registration/
├── plan.md              # Este archivo (/speckit.plan)
├── research.md          # Salida Phase 0: decisiones técnicas y verificación de versiones
├── data-model.md        # Salida Phase 1: entidades, tablas y validaciones
├── quickstart.md        # Salida Phase 1: guía de validación end-to-end
├── contracts/           # Salida Phase 1: contratos de API REST
│   ├── auth-api.md
│   └── users-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit.tasks) — NO se crea en /speckit.plan
```

### Source Code (repository root)

```text
backend/
├── Dockerfile                        # multi-stage (existente)
├── pom.xml                           # + spring-boot-starter-security, starter-validation, jjwt
└── src/
    ├── main/java/com/amano/backend/
    │   ├── BackendApplication.java
    │   ├── common/
    │   │   └── ProblemDetailExceptionHandler.java   # traduce 400/409/413 a RFC 7807
    │   ├── user/
    │   │   ├── User.java                            # entidad JPA
    │   │   ├── UserAvatar.java                      # entidad JPA (@OneToOne LAZY)
    │   │   ├── UserRepository.java
    │   │   ├── UserAvatarRepository.java
    │   │   ├── UserController.java                  # GET /api/users/me, GET /api/users/{id}/avatar
    │   │   ├── CurrencyConversion.java              # monedas del perfil ↔ JSON
    │   │   └── UserMapper.java                      # entidad → DTO de respuesta (sin credenciales)
    │   ├── auth/
    │   │   ├── AuthController.java                  # POST /api/auth/register (multipart)
    │   │   ├── RegistrationService.java             # transacción, validación, avatar, correo
    │   │   ├── RegisterRequest.java                 # DTO con Bean Validation
    │   │   ├── JwtService.java                      # firma/lectura de tokens (jjwt)
    │   │   └── AuthCookie.java                      # helper de cookie HttpOnly SameSite=Strict
    │   └── config/
    │       ├── SecurityConfig.java                  # filtro JWT, CSRF off, reglas por ruta
    │       └── JwtProperties.java                   # JWT_SECRET / JWT_EXPIRATION (env)
    ├── main/resources/
    │   ├── application.properties                   # + multipart 2MB/3MB, flyway
    │   └── db/migration/V1__create_user_registration.sql
    └── test/java/com/amano/backend/
        ├── user/…Test.java                          # unitarias rápidas
        └── user/…IT.java                           # Testcontainers (BD real, unicidad, avatar)

frontend/
├── Dockerfile                        # multi-stage (existente)
├── nginx.conf                        # proxy /api/* → backend:8080 (existente, reutilizado)
├── vite.config.js                    # + server.proxy en desarrollo apuntando a backend:8080
├── package.json                      # + react-router, react-hook-form
├── public/
│   └── avatar-default.png            # avatar predeterminado (recurso estático del frontend)
└── src/
    ├── main.jsx
    ├── App.jsx
    ├── api/
    │   └── authClient.js             # fetch con credenciales (cookie), multipart, mensajes de error
    ├── auth/
    │   ├── AuthContext.jsx           # sesión (usuario actual) tras registro/consulta de /me
    │   └── ProtectedRoute.jsx        # rutas protegidas
    ├── pages/
    │   ├── RegisterPage.jsx          # UI de registro + envío/estados
    │   └── DashboardPage.jsx         # placeholder protegido con datos de GET /api/users/me
    ├── components/
    │   ├── RegisterForm.jsx          # react-hook-form, validación en vivo
    │   ├── CurrencySelector.jsx      # multiselección ARS/USD/EUR
    │   ├── AvatarUploader.jsx        # validación local de formato/tamaño
    │   └── TextField.jsx             # campo con mensaje de error estandarizado
    └── validation/
        └── registerValidation.js     # reglas compartidas (correo, nombres, clave, CBU, imagencrypto)
```

**Structure Decision**: Se mantiene la Option 2 (web application) con la estructura real del
monorepo: backend Java bajo `backend/src/main/java/com/amano/backend/` con paquetes `common`,
`user`, `auth` y `config`; frontend React bajo `frontend/src/`. No se crean carpetas nuevas en
la raíz. El proxy `/api/*` ya existente en `frontend/nginx.conf` y el patrón de `compose.yaml`
se reutilizan sin cambios estructurales.

## Complexity Tracking

> Sin violaciones constitucionales que justificar. Nota consciente documentada en research.md:
> la protección CSRF de Spring Security se deshabilita (`csrf.disable()`) porque la sesión viaja
> en cookie `SameSite=Strict` y el sitio corre bajo un único origen controlado por el proxy del
> frontend, eliminando el vector CSRF entre sitios; cualquier cambio futuro que permita orígenes
> cruzados debe reevaluar esta decisión.
