# Quickstart: Visualización de perfil de usuario

**Feature**: `002-user-profile` | **Spec**: [spec.md](spec.md)

Escenarios manuales E2E (equivalente de los Acceptance Scenarios). Entorno local: `docker compose up --build` (frontend :8081, backend :8080, proxy `/api` unificado). Secrets en `.env` (`JWT_SECRET`, `JWT_EXPIRATION`).

## Preparación

1. `docker compose up --build` y esperar SALUD (`GET /api/health` → 200).
2. Registrar dos usuarios con datos distintos (secuencia A y B) desde `http://localhost:8081/register`:
   - **Usuario A — completo**: nombre "Ana", apellido "Pérez", email `ana@ejemplo.com`, contraseña `S3gura!2026` (8–64, con mayúscula/minúscula/número/caracter especial), monedas ARS + USD, CBU/CVU real de 22 dígitos (ej. `0000003100010000000001`), avatar JPG/PNG < 2 MB.
   - **Usuario B — mínimo**: nombre "Beto", email `beto@ejemplo.com`, contraseña, sin datos opcionales.

## E2E 1 — Perfil completo (US1, scenarios 1–2)

1. Con la sesión de A (tras el registro hay sesión automática), navegar a `http://localhost:8081/profile`.
2. **Esperado**:
   - Nombre: `Ana`; email: `ana@ejemplo.com`.
   - Contraseña: enmascarada `●●●●●●●●` con la leyenda "Por seguridad, la contraseña no se muestra" (nunca texto claro).
   - Apellido: `Pérez`; CBU/CVU: los 22 dígitos registrados; avatar: la imagen subida; monedas: `ARS` y la preferida(s) elegida(s).

## E2E 2 — Opcionales ausentes (US1, scenario 3)

1. Cerrar sesión de A (borrar cookie `amano_auth` o sesión nueva en incógnito).
2. Registrar el usuario B y navegar a `/profile`.
3. **Esperado**: apellido `No informado`; CBU/CVU `No informado`; avatar `/avatar-default.png`; monedas `ARS` (default, `currencies` llega vacío).

## E2E 3 — Aislamiento (US2)

1. Con sesión de A, verificar que no aparece ningún dato de B (email, nombre, apellido, CBU) en la pantalla (DOM inspeccionado / texto visible).
2. Intentar abrir la URL del avatar de B (si B tuviera avatar): en esta feature la pantalla solo resuelve su propia sesión; cualquier id ajeno responde 404 y la pantalla no renderea datos de B.
3. **Esperado**: el 100% de los campos corresponden a A (SC-002).

## E2E 4 — Acceso sin sesión (US3, scenario 1)

1. Incógnito sin cookie, abrir `http://localhost:8081/profile` directo.
2. **Esperado**: redirección a `/register` sin destello de datos; destino retenido por el gate (`ProtectedRoute`).

## E2E 5 — Fallo de red / servidor (US3, scenario 2 + Edge Case)

1. Con sesión de A, detener el servicio backend (`docker stop <backend>`) y recargar `/profile`.
2. **Esperado**: mensaje de error; sin datos incompletos ni placeholders que parezcan datos reales.
3. Levantar el backend (`docker start <backend>`), recargar: **esperado** perfil con datos frescos.
4. Sesión expirada: borrar la cookie tras cargar la pantalla y reintentar la consulta → redirección al flujo de acceso.

## Criterios de cierre (SC)

- [ ] SC-001: llegada al perfil en ≤2 interacciones y datos completos <2 s.
- [ ] SC-002: 100% aislamiento de datos entre usuarios (E2E 3).
- [ ] SC-003: contraseña jamás en texto claro en pantalla ni en red (inspeccionar respuestas de `me`).
- [ ] SC-004: estados de fallo visibles <2 s tras detectarse.
- [ ] SC-005: representaciones consistentes ("No informado", default avatar, ARS).
