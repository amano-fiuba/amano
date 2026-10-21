# Quickstart: Modificar perfil de usuario

**Feature**: `005-edit-user-profile` | **Spec**: [spec.md](spec.md)

Escenarios manuales E2E (equivalente de los Acceptance Scenarios). Entorno local:
`docker compose up --build` (frontend :8081, backend :8080, proxy `/api` unificado).
Secrets en `.env` (`JWT_SECRET`, `JWT_EXPIRATION`).

## Preparación

1. `docker compose up --build` y esperar SALUD (`GET /api/health` → 200).
2. Registrar tres usuarios desde `http://localhost:8081/register` (con el flujo de
   verificación de `003`: verificar el correo de cada uno mediante el enlace recibido):
   - **Usuario A — completo**: nombre "Ana", apellido "Pérez", email `ana@ejemplo.com`,
     contraseña `S3gura!2026` (8–64, con mayúscula/minúscula/número/caracter especial),
     monedas ARS + USD, CBU/CVU `0000003100010000000001`, avatar JPG/PNG < 2 MB.
   - **Usuario B — mínimo**: nombre "Beto", email `beto@ejemplo.com`, contraseña, sin
     datos opcionales.
   - **Usuario C — reservado para el aislamiento** (US4): nombre "Caro", email
     `caro@ejemplo.com`, otros datos opcionales propios.

## E2E 1 — Precarga y edición de datos (US1, scenarios 1–3)

1. Con la sesión de A, ir a "Mi perfil" (`/profile` de `002`) y pulsar "Modificar perfil"
   (o navegar a `/profile/edit`).
2. **Esperado**: formulario precargado con lo actual del servidor en <2 s: nombre `Ana`,
   apellido `Pérez`, CBU/CVU `0000003100010000000001`, monedas `ARS`+`USD` marcadas, y el
   correo `ana@ejemplo.com` visible pero **deshabilitado** (no editable).
3. Cambiar el nombre por `Anabel`, completar el CBU con espacios (`0000 0031-0001
   00000000-0001`), marcar además `EUR` y guardar.
4. **Esperado**: mensaje "Los cambios se guardaron correctamente"; volver a `/profile`:
   nombre `Anabel`, CBU normalizado a 22 dígitos, monedas `ARS/USD/EUR` (SC-002).

## E2E 2 — Opcionales vacíos y desmarcado de monedas (US1, scenarios 3–4 + edge)

1. Con la sesión de B (perfil mínimo), entrar a `/profile/edit`.
2. **Esperado**: apellido y CBU/CVU precargados vacíos; monedas sin marcar (default ARS en
   la lectura).
3. Completar apellido `Gómez`, completar el CBU con 21 dígitos y guardar.
4. **Esperado**: mensaje por campo "El CBU/CVU debe tener 22 dígitos" (u equivalente), los
   demás datos conservados y el botón rehabilitado para corregir (FR-010/SC-004).
5. Corregir el CBU con 22 dígitos y guardar; luego reingresar, limpiar el apellido y el
   CBU, desmarcar las monedas y volver a guardar.
6. **Esperado**: al reconsultar `/profile`, apellido y CBU/CVU "No informado" y monedas
   efectivas `ARS` (FR-004/FR-006 de `002`).

## E2E 3 — Cambio de contraseña (US2)

1. Con la sesión de A, ir a `/profile/password` (o el acceso desde la edición).
2. Ingresar la contraseña actual **incorrecta** y una nueva válida `N3uva#Clave`.
3. **Esperado**: rechazo con "La contraseña actual no es correcta"; sin cambios.
4. Ingresar la actual correcta con una nueva inválida (sin especial) y confirmación.
5. **Esperado**: mensaje por campo de la política de `001`; sin cambios.
6. Ingresar la actual correcta y la nueva válida con su confirmación; confirmar.
7. **Esperado**: mensaje de éxito, sesión vigente; cerrar sesión e iniciar sesión con la
   nueva contraseña: entra al dashboard (la anterior ya no autentica).

## E2E 4 — Avatar: reemplazo y eliminación (US3)

1. Con la sesión de A, subir una imagen PNG ≠ 2 MB desde la edición y guardar.
2. **Esperado**: `/profile` muestra la imagen nueva (el binario anterior no se sirve más).
3. Intentar subir un archivo `.txt` renombrado (magic bytes inválidos) y un JPG > 2 MB.
4. **Esperado**: rechazo en vivo y/o `400` con mensaje de `001`; el avatar vigente no cambia.
5. Eliminar el avatar y reconsultar `/profile`.
6. **Esperado**: `/avatar-default.png` y `hasCustomAvatar=false` (E2E equivalente al
   fallback de `002`).

## E2E 5 — Cancelación sin guardar (US1 scenario 8 + SC-006)

1. Con la sesión de A, entrar a `/profile/edit`, cambiar el nombre (sin guardar), pulsar
   "Cancelar" y también probar abandonar el navegando atrás.
2. **Esperado**: al reconsultar `/profile`, el nombre sigue siendo el anterior: 0 cambios
   persistidos en el 100 % de los abandonos.

## E2E 6 — Aislamiento y acceso (US4)

1. Con la sesión de A, inspeccionar el DOM de `/profile/edit`: no aparece ningún dato de B
   ni de C (SC-007).
2. Manipular una petición de guardado para altear el id/correo de destino (o eliminar la
   cookie y guardar): **esperado** 401/redirección; los datos de B y C siguen intactos y el
   correo de A no cambia (SC-003: 0 cambios de correo en ningún intento).
3. Detener el backend y guardar: **esperado** "No se pudo completar la actualización.
   Intenta nuevamente", datos del formulario conservados, botón rehabilitado; al levantar y
   reintentar, guardado exitoso.

## Criterios de cierre (SC)

- [ ] SC-001: precarga con lo último guardado del servidor <2 s (E2E 1).
- [ ] SC-002: el 100 % de los guardados exitosos visualizan los cambios al reconsultar (E2E 1, 2).
- [ ] SC-003: 0 cambios de correo en el 100 % de los intentos (E2E 1, 6).
- [ ] SC-004: motivo por campo <2 s, sin limpiar el formulario (E2E 2, 3).
- [ ] SC-005: 0 cambios de contraseña con la actual incorrecta (E2E 3).
- [ ] SC-006: 0 cambios persistidos en abandono/cancelación (E2E 5).
- [ ] SC-007: 100 % de mutaciones sobre el principal; 0 datos de terceros alterados (E2E 6).
- [ ] SC-008: avatar reemplazado/eliminado consistente con la visualización (E2E 4).
