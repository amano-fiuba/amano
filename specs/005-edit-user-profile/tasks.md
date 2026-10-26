# Tasks: Modificar perfil de usuario

**Input**: Design documents from `/specs/005-edit-user-profile/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), data-model.md, contracts/users-api.md, quickstart.md

**Tests**: explícitamente solicitados y **OBLIGATORIOS** (Constitution V del repo): backend (JUnit + MockMvc) y frontend (Vitest + Testing Library, co-ubicados) se escriben primero y deben verse FALLAR antes de la implementación.

**Organization**: agrupado por User Story para implementación y testeo independientes; el lote de endpoints de backend se completa antes de las pantallas de frontend que los consumen.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede correr en paralelo (archivos distintos, sin dependencias)
- **[Story]**: historia a la que pertenece la tarea (US1, US2, US3, US4)
- Incluye paths exactos en las descripciones

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/user/` (tests espejados en `backend/src/test/java/...`)
- Frontend: `frontend/src/` (tests co-ubicados junto al código)

---

## Phase 1: Setup

**Purpose**: verificar el punto de partida antes de tocar código

- [ ] T001 [Setup] Verificar línea base verde: `mvn -B verify` en `backend/` y `npm run test` + `npm run lint` en `frontend/` (sin regresiones sobre el estado final de 002)

**Checkpoint**: línea base estable; el lote de backend puede arrancar

---

## Phase 2: Lote de backend (endpoints del contrato, compartidos por US1–US3)

**Goal**: exponer los endpoints de mutación del contrato (`specs/005-edit-user-profile/contracts/users-api.md`) con validación en fronteras y aislamiento por principal

**Independent Test**: cada endpoint rechaza peticiones inválidas con ProblemDetail/400 por campo, 401 sin sesión y persiste solo del principal con petición válida

### Tests for backend ⚠️ (escribir primero y verlos FALLAR)

- [ ] T002 [P] [US1] Test MockMvc `PUT /api/users/me` en `backend/src/test/java/.../user/`: datos válidos → 200 con representación actualizada y persistencia verificada (cubre FR-009 de la spec); campos inválidos (nombre <2, apellido >50, CBU 21 dígitos, moneda desconocida/duplicada) → 400 con `properties.fields` por campo (cubre FR-006/FR-010); sin cookie → 401 (cubre FR-018); la petición con `email` ajeno NO modifica el correo (cubre FR-007). Nota: `email` no forma parte de `UpdateProfileRequest`
- [ ] T003 [P] [US2] Test MockMvc `PUT /api/users/me/password` en el mismo paquete: current correcta + nueva válida → 200 y hash nuevo que autentica la nueva (cubre FR-013); current incorrecta → 400 "La contraseña actual no es correcta" y hash intacto (cubre FR-014); nueva que viola la política de 001 o confirmación distinta → 400 por campo; sin cookie → 401
- [ ] T004 [P] [US3] Test MockMvc `PUT/DELETE /api/users/me/avatar`: imagen JPG/PNG válida → 200 con `hasCustomAvatar=true` y binario nuevo servido por `GET /api/users/{id}/avatar` (cubre FR-015); magic bytes inválidos → 400; >2 MB → 413; `DELETE` con fila existente → 204 y `GET` pasa a 404 (cubre FR-016); `DELETE` sin fila → 204 idempotente; sin cookie → 401

### Implementation for backend

- [ ] T005 [US1] Crear `backend/src/main/java/com/amano/backend/user/dto/UpdateProfileRequest.java` (sin campo email) y `dto/UpdatePasswordRequest.java`; extender `UserAccountService` con `updateProfile` (transacción única: users + user_currencies delete+insert, normalizaciones de 001: trim, CBU sin espacios/guiones), `changePassword` (verificar BCrypt de currentPassword, re-hash de newPassword, sin rotación de sesión) y `updateAvatar`/`deleteAvatar` (upsert/delete en `user_avatars`)
- [ ] T006 [US1] Extender `UserController` con `PUT /api/users/me`, `PUT /api/users/me/password`, `PUT /api/users/me/avatar` y `DELETE /api/users/me/avatar`, resolviendo siempre el principal de sesión (cookie `amano_auth`); ProblemDetail RFC 7807 con `properties.fields` (mismo formato de 001); sin migraciones (ver data-model.md)

**Checkpoint**: los 4 endpoints verdes en MockMvc con la matriz del contrato; `mvn -B verify` verde

---

## Phase 3: User Story 1 - Editar los datos del perfil (Priority: P1) 🎯 MVP

**Goal**: pantalla `/profile/edit` precargada y editable (nombre, apellido, CBU/CVU, monedas), correo deshabilitado, cancelación sin guardar, guardado con confirmación y persistencia visible

**Independent Test**: con sesión activa, `/profile/edit` precarga los datos de `me()`; modificar y guardar aplica los cambios (visibles al reconsultar); cancelar no persiste nada; datos inválidos muestran mensajes por campo sin limpiar el formulario

### Tests for User Story 1 ⚠️

- [ ] T007 [P] [US1] Test co-ubicado `frontend/src/pages/ProfileEditPage.test.jsx`: mock de `me()` (devuelto por `authClient`) con perfil completo → verifica precarga de nombre, apellido, CBU/CVU, monedas marcadas y correo deshabilitado (cubre FR-002 y FR-007). Nota: mock del módulo authClient, no del fetch global (patrón de 002, T002 de esa spec)
- [ ] T008 [P] [US1] Test en el mismo archivo: editar campos (nombre, apellido vacío→completo, CBU con 21 dígitos→21) y enviar → verifica mensajes por campo, datos conservados y botón rehabilitado con `updateProfile` rechazada o validación de cliente (cubre FR-006 y FR-010); con campos todos válidos → verifica llamada de `updateProfile` con payload y normalización (CBU sin espacios/guiones, nombre/apellido trimmed), mensaje de éxito y datos frescos (cubre FR-009)
- [ ] T009 [P] [US1] Test en el mismo archivo: cancelar con cambios sin guardar → verifica navegación hacia `/profile` y 0 llamadas de `updateProfile` (cubre FR-008)
- [ ] T010 [US1] Crear `frontend/src/pages/ProfileEditPage.jsx`: formulario precargado desde `me()` al montar (estados loading/datos/error, patrón de 002), validación en vivo (patrón FR-013 de 001), guardado vía `updateProfile()`, cancelación y correo en modo solo lectura (cubre FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009)
- [ ] T011 [US1] Extender `frontend/src/api/authClient.js` con `updateProfile(payload)` (PUT /api/users/me) y agregar a `frontend/src/App.jsx` la ruta protegida `/profile/edit` (envuelta en `frontend/src/auth/ProtectedRoute.jsx`) + test de routing en `frontend/src/App.test.jsx` (cubre FR-001 y FR-018)
- [ ] T012 [US1] Agregar entrada "Modificar perfil" en `frontend/src/pages/ProfilePage.jsx` (patrón de navegación de 002) y extender su test co-ubicado (cubre FR-001); extender `frontend/src/pages/DashboardPage.jsx` o la fuente de navegación según corresponda para reflejar los datos actualizados tras el guardado

**Checkpoint**: con sesión, `/profile/edit` cumple el MVP (precargar/editar/guardar/cancelar) y la persistencia es visible en `/profile`; `npm run test` verde

---

## Phase 4: User Story 2 - Modificar la contraseña (Priority: P2)

**Goal**: cambio de contraseña con acreditación de la actual, política de 001 y confirmación, sesión persistente

**Independent Test**: con sesión activa, `/profile/password` rechaza la actual incorrecta (sin cambios), valida la política en vivo y concreta el cambio con la actual correcta (la sesión sigue vigente)

### Tests for User Story 2 ⚠️

- [ ] T013 [P] [US2] Test co-ubicado `frontend/src/pages/ChangePasswordPage.test.jsx`: envío con current incorrecta → verifica mensaje específico en el campo y datos del formulario conservados (cubre FR-014); nueva inválida (política de 001) o confirmación distinta → mensajes por campo (cubre FR-013); envío válido → llamada de `updatePassword` con payload y mensaje de éxito (cubre FR-013)
- [ ] T014 [US2] Extender `frontend/src/api/authClient.js` con `updatePassword(payload)` (PUT /api/users/me/password) y crear `frontend/src/pages/ChangePasswordPage.jsx`: campos de contraseña ocultos por defecto (FR-008 de 001), validación en vivo, guardado y éxito; el correo no aplica en esta pantalla
- [ ] T015 [US2] Registrar ruta protegida `/profile/password` en `frontend/src/App.jsx` + test de routing y entrada de navegación desde `/profile/edit` (cubre FR-013 y FR-018)

**Checkpoint**: el cambio de contraseña funciona de forma independiente sobre el backend del lote; `npm run test` verde

---

## Phase 5: User Story 3 - Gestionar el avatar (Priority: P3)

**Goal**: reemplazo y eliminación del avatar desde la edición, con validación en vivo y fallback al default

**Independent Test**: desde `/profile/edit`, subir imagen válida actualiza el avatar servido; archivos inválidos son rechazados sin alterar el vigente; eliminar vuelve al default

### Tests for User Story 3 ⚠️

- [ ] T016 [P] [US3] Test en `frontend/src/pages/ProfileEditPage.test.jsx`: matriz de archivos (JPG válido, magic bytes inválidos, >2 MB) → mensajes en vivo (cubre FR-015); subida válida → llamada de `updateAvatar` con multipart y avatar actualizado (constructor de FormData en el mock); eliminación → llamada de `deleteAvatar` y preview vuelve a `/avatar-default.png` (cubre FR-016)
- [ ] T017 [US3] Extender `frontend/src/api/authClient.js` con `updateAvatar(file)` (PUT /api/users/me/avatar) y `deleteAvatar()` (DELETE /api/users/me/avatar); extender `ProfileEditPage.jsx` con la sección de gestión del avatar (preview actual, sustitución, eliminación y fallback al default, patrón de 002)

**Checkpoint**: el avatar se crea/reemplaza/elimina desde la edición con el estado visual consistente en `/profile`; `npm run test` verde

---

## Phase 6: User Story 4 - Aislamiento, acceso protegido y estados de fallo (Priority: P4)

**Goal**: mutaciones solo del principal, 401 → redirección, fallo de red/servidor con mensaje y datos conservados, correo inmodificable por diseño

**Independent Test**: simulando red caída al guardar se muestra el error sin aplicar cambios ni limpiar datos; sin sesión hay redirección; ninguna variante de petición altera datos de otro usuario ni el correo

### Tests for User Story 4 ⚠️

- [ ] T018 [P] [US4] Test en `frontend/src/pages/ProfileEditPage.test.jsx`: mock de `updateProfile` con rechazo de red → mensaje "No se pudo completar la actualización. Intenta nuevamente", datos del formulario conservados y botón rehabilitado (cubre FR-011/FR-019); 401 → sin datos aplicados y redirección por gate (cubre FR-018)
- [ ] T019 [P] [US4] Test backend (junto a los del lote): petición de guardado válida del usuario A nunca muta filas del usuario B (asegurar en la aserción de DB de los tests de T002–T004; el servicio solo resuelve el principal) (cubre FR-017/SC-007)
- [ ] T020 [US4] Cerrar implementación de estados en `ProfileEditPage.jsx`/`ChangePasswordPage.jsx`: manejo de 401 (delegación en `ProtectedRoute`), estados de error de red sin cambios parciales (transacción del backend) y correo estrictamente no editable (deshabilitado + no se envía) (cubre FR-007, FR-017, FR-018, FR-019)

**Checkpoint**: todas las historias funcionan de forma independiente

---

## Phase 7: Verificación Final

**Purpose**: gates de calidad y validación E2E

- [ ] T021 [P] Ejecutar `npm run test:coverage` en `frontend/` (gate ≥85% líneas/statements) y corregir huecos de cobertura introducidos por la feature
- [ ] T022 [P] Ejecutar `npm run lint` en `frontend/` limpio (0 errores) y `mvn -B verify` en `backend/` (BUILD SUCCESS, gates JaCoCo) como regresión completa
- [ ] T023 Ejecutar la validación manual de `specs/005-edit-user-profile/quickstart.md` (E2E 1–6) con `docker compose up --build` y registrar resultados
- [ ] T024 Auditoría de trazabilidad FR/SC ↔ tests en esta misma checklist (FR-001..FR-020, SC-001..SC-008) y actualizar notas de tareas antes del PR a `sprint-1` (Conventional Commits)

---

## Phase 8: Post-plan fixes (FR-008/FR-009 enmendados)

- [x] T025 [FE] Ajuste UX del guardado/cancelación (pedido del usuario): en `frontend/src/pages/ProfileEditPage.jsx` el envío exitoso de "Guardar cambios" ahora aplica los datos frescos al contexto de sesión, marca la clave one-shot `amano.profile.saved` (exportada desde `ProfilePage.jsx`) y navega a `/profile`; se elimina el mensaje de éxito de la pantalla de edición. Nuevo botón "Cancelar cambios" (navega a `/profile` sin `PUT`, cubre FR-008); el botón "Volver atrás" se elimina (su rol queda cubierto). En `frontend/src/pages/ProfilePage.jsx` al montar se consume y remueve la clave y se muestra "Los cambios se guardaron correctamente" (`role="status"`, `data-testid="profile-saved"`, `className="form-success"`) una única vez tras un guardado novedoso; ni refresco ni ingresos posteriores lo repiten (FR-009 enmendado). Espec actualizada: FR-008/FR-009 enmendadas en `spec.md` y flujo Guardar/Cancelar en `contracts/users-api.md`. Tests co-ubicados extendidos en ambos archivos.
- [x] T026 [FE] Verificación tras T025: `npm run lint` (0 errores) y `npx vitest run src/pages/ProfileEditPage.test.jsx src/pages/ProfilePage.test.jsx` en verde (~1 min; suite completa diferida a cierre).
- [x] T027 [FE] Estados pendientes y botón dinámico (FR-011 enmendada, pedido del usuario): en `frontend/src/pages/ProfileEditPage.jsx` "Guardar cambios" se deshabilita (gris + `cursor:not-allowed` vía `.form-actions button:disabled` en `index.scss`) mientras no haya modificaciones pendientes; se habilita al modificar nombre/apellido/CBU (`isDirty` de RHF contra los defaultValues de la precarga), monedas (comparación por conjunto contra las iniciales) o tener avatar pendiente (subida/eliminación). "Cancelar cambios" queda siempre habilitado.
- [x] T028 [FE] Avatar aplazado (FR-015/FR-016 enmendadas): subida y eliminación como **pendientes** confirmadas con "Guardar cambios". La elección de archivo valida en vivo (tamaño→magic bytes) y retiene el File con preview por `URL.createObjectURL` (revoke al descartar/desmontar/confirmar). "Eliminar avatar" (presente solo con avatar propio) marca la eliminación: preview del default y botón deshabilitado mientras la marca está pendiente; "Cancelar cambios" es la única vía de anular (0 llamadas). El guardado ejecuta `DELETE` (si corresponde) → `PUT` de avatar → navegación con aviso one-shot; ante fallo no hay navegación (pendientes retenidos; reintento repite el `PUT /api/users/me`, idempotente). Tests co-ubicados reescritos (US1 guardar/cancelar/estado del botón; US3 pendientes y guardado).
- [x] T029 [FE] Bugfix del salto a `/login` tras marcar eliminación y guardar: `DELETE /api/users/me/avatar` responde 204 sin cuerpo — `deleteAvatar()` devuelve `{ ok: true }` sin `profile`; el flujo anterior hacía `setUser(eliminacion.profile)` → `undefined` → sesión vacía → redirección del gate. `perfilFinal` se compone con el `result.profile` del `PUT /api/users/me` ajustado post-DELETE (`hasCustomAvatar:false`, `avatarUrl:null`), o con `subida.profile` si hubo subida pendiente. Documentado en `contracts/users-api.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias; T001 primero.
- **Lote backend (Phase 2)**: depende de Setup; tests T002/T003/T004 FALLAN → T005, T006.
- **US1 (Phase 3)**: depende del lote backend (contrato disponible); tests T007–T009 → T010, T011, T012.
- **US2 (Phase 4)**: depende del lote backend; tests T013 → T014, T015.
- **US3 (Phase 5)**: depende de US1 (la pantalla de edición existe); tests T016 → T017.
- **US4 (Phase 6)**: refinamiento transversal; tests T018/T019 → T020.
- **Verificación (Phase 7)**: depende de todas las historias.

### User Story Dependencies

- **US1**: MVP; sin dependencias más allá del backend.
- **US2**: independiente de US3; requiere solo el endpoint del lote (puede convivir con la página propia).
- **US3**: extiende la pantalla de edición (natural: después de US1).
- **US4**: transversal; cierra aislamiento/estados de fallo reutilizando `ProtectedRoute`.

### Parallel Opportunities

- T002, T003, T004 en paralelo (tests del lote; mismo paquete de archivos, escritura recomendada secuencialmente por conflicto; marcar [P] por contenido).
- T007, T008, T009 en paralelo (mismo archivo; escritura secuencial recomendada por conflicto de archivo; marcar [P] por contenido).
- T013, T016, T018 en paralelo al llegar a sus fases (archivos distintos).
- T021/T022 en paralelo tras el cierre de historias.

## Implementation Strategy

- **MVP first**: Setup → lote backend → US1 → validar checkpoint → luego US2, US3 y US4.
- Cada checkpoint cierra con lint + tests verdes (backend y frontend).
- Commit Conventional por tarea o grupo lógico; PR `feat/edit-user-profile` → `sprint-1` después de T024.

## Notes

- Los tests se escriben antes de la implementación y deben verse FALLAR primero.
- Cualquier necesidad de nuevo endpoint fuera del contrato (o cualquier tentación de mutar `email`) es señal de desvío del contrato → revisar specs antes de codear.
- Evitar duplicación: reusar `me()`, `ProtectedRoute`, los validadores de 001 (backend y frontend) y el patrón de estados de `ProfilePage`.
- Contraseña (actual y nueva) jamás se registra, loguea o devuelve; solo hash persistido (FR-007 de 001).
