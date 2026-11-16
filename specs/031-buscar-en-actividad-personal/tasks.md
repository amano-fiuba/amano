---
description: "Task list for Buscar en actividad personal"
---

# Tasks: Buscar en actividad personal

**Input**: Design documents from `/specs/031-buscar-en-actividad-personal/`

**Prerequisites**: plan.md, spec.md, contracts/personal-expenses-search-api.md

**Tests**: Por pedido del usuario, la implementación se valida primero manualmente; las pruebas automatizadas (Fase 6) se escriben después de esa validación y son obligatorias para cerrar la historia (constitución V, cobertura ≥ 85 %).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia a la que pertenece (US1, US2, US3)

---

## Phase 1: Foundational (backend compartido)

- [x] T001 Crear `ExpenseNotFoundException` ("No encontramos ese gasto") en backend/src/main/java/com/amano/backend/expense/ExpenseNotFoundException.java y su handler 404 ("Gasto no encontrado") en backend/src/main/java/com/amano/backend/common/ProblemDetailExceptionHandler.java (FR-016)
- [x] T002 Hacer que `ExpenseRepository` extienda `JpaSpecificationExecutor<Expense>` y agregar la consulta de años distintos por fecha del gasto, en orden descendente, en backend/src/main/java/com/amano/backend/expense/ExpenseRepository.java (FR-005, FR-006)
- [x] T003 Exponer en `ExpenseService` un método que devuelva un `ExpenseResponse` con conversiones para una lista de gastos (reutilizado por la búsqueda) y `getExpense(userId, id)` en backend/src/main/java/com/amano/backend/expense/ExpenseService.java (FR-008, FR-014, FR-016)

## Phase 2: US1 – Buscar un gasto (P1) 🎯 MVP

- [x] T004 [US1] Crear `ExpenseSearchLabels` con las etiquetas en español de categorías y medios de pago (espejo de frontend/src/validation/expenseCatalog.js) y la normalización (recorte, minúsculas, sin tildes) en backend/src/main/java/com/amano/backend/expense/ExpenseSearchLabels.java (FR-003, FR-004)
- [x] T005 [US1] Crear `ExpenseSearchResponse` (`expenses`, `truncated`) en backend/src/main/java/com/amano/backend/expense/ExpenseSearchResponse.java (FR-008)
- [x] T006 [US1] Crear `ExpenseSearchService`: validación de `q` (2–100) y `year` (1–9999), `Specification` con `translate`/`lower`, `LIKE` con escape, categorías/medios coincidentes, filtro de año, orden `createdAt DESC, id DESC`, límite 201 → 200 + `truncated` en backend/src/main/java/com/amano/backend/expense/ExpenseSearchService.java (FR-003..FR-008, FR-018)
- [x] T007 [US1] Agregar `GET /api/expenses/search` y `GET /api/expenses/years` en backend/src/main/java/com/amano/backend/expense/ExpenseController.java (FR-005, FR-006, FR-017)
- [x] T008 [P] [US1] Agregar `searchExpenses(q, year)`, `listExpenseYears()` y el mensaje de error de búsqueda en frontend/src/api/expenseClient.js (FR-019)
- [x] T009 [US1] Agregar a frontend/src/pages/ActivityPage.jsx el formulario (campo de hasta 100 caracteres, "Buscar", "Limpiar"), validación de mínimo 2 caracteres, estado de la búsqueda en la URL (`q`, `year`), descarte de respuestas viejas, "No se encontraron resultados", aviso de 200 resultados y mensaje de reintento (FR-001, FR-002, FR-008..FR-011, FR-019)
- [x] T010 [P] [US1] Estilos del buscador y de los avisos en frontend/src/pages/MainPage.scss

## Phase 3: US3 – Ver el detalle de un gasto (P1)

- [x] T011 [US3] Agregar `GET /api/expenses/{id}` (id no numérico → 404) en backend/src/main/java/com/amano/backend/expense/ExpenseController.java (FR-014, FR-016)
- [x] T012 [P] [US3] Agregar `getExpense(id)` en frontend/src/api/expenseClient.js
- [x] T013 [US3] Crear frontend/src/pages/ExpenseDetailPage.jsx con todos los datos del gasto, datos de recurrencia, "Sin descripción", "No encontramos ese gasto", reintento y "Volver" que usa `state.from` o `/activity` (FR-014, FR-015, FR-016, FR-019)
- [x] T014 [US3] Registrar la ruta protegida `/expenses/:id` en frontend/src/App.jsx y convertir cada fila de frontend/src/pages/ActivityPage.jsx en un enlace al detalle que pase la URL actual en `state.from` (FR-013, FR-015)

## Phase 4: US2 – Ampliar a otros años (P2)

- [x] T015 [US2] Cargar los años en frontend/src/pages/ActivityPage.jsx, sumar el año actual, agregar "Todos los años", elegir el actual por defecto y no buscar al cambiar el año hasta "Buscar"/Enter; si la carga de años falla, ofrecer solo el actual y "Todos los años" (FR-006, FR-007, FR-012)

## Phase 4b: Ajustes tras la primera revisión manual (sesión 2)

- [x] T023 [US1] Buscar al dejar de escribir (400 ms) o con Enter, sin botón "Buscar", con "Buscando…" mientras se escribe y la URL actualizada con `replace` en frontend/src/pages/ActivityPage.jsx (FR-001, FR-002)
- [x] T024 [US2] Volver a consultar al cambiar el año con una búsqueda vigente en frontend/src/pages/ActivityPage.jsx (FR-001, US2-5)
- [x] T025 [US1] "Limpiar" visible solo con búsqueda vigente y "Reintentar" en el error de búsqueda en frontend/src/pages/ActivityPage.jsx (FR-010, FR-019)
- [x] T026 [P] [US1] Campo con lupa y texto "Buscar por categoría, descripción, medio de pago"; cada gasto como tarjeta con flecha (`.group-row`) en frontend/src/pages/ActivityPage.jsx y frontend/src/pages/MainPage.scss (FR-001, FR-013)

## Phase 4c: Ajustes tras la segunda revisión manual (sesión 3)

- [x] T027 [US2] `q` opcional en `GET /api/expenses/search` (sin texto = todos los gastos del año) en backend/src/main/java/com/amano/backend/expense/ExpenseSearchService.java y frontend/src/api/expenseClient.js (FR-012)
- [x] T028 [US2] La actividad se muestra siempre según el selector (por defecto, el año actual) y cambiar el año consulta aunque no haya texto, en frontend/src/pages/ActivityPage.jsx (FR-012, US2-6)
- [x] T029 [US1] Quitar "Limpiar": la x del campo vacía el texto y muestra el año elegido de inmediato, en frontend/src/pages/ActivityPage.jsx (FR-010)
- [x] T030 [P] Ancho de la pantalla igual a "Mis grupos" (760 px) en frontend/src/pages/MainPage.scss y "← Volver" en frontend/src/pages/ExpenseDetailPage.jsx (FR-001, FR-015)

## Phase 4d: Mes, paginación y limpieza (sesión 4)

- [x] T031 [US2] Agregar `month` (1–12, requiere `year`) a la validación y al filtro de `ExpenseSearchService` en backend/src/main/java/com/amano/backend/expense/ExpenseSearchService.java (FR-007, FR-020)
- [x] T032 [US4] Paginar `GET /api/expenses/search` (`page`, `size`, página fuera de rango → última) y cambiar `ExpenseSearchResponse` a `{expenses, page, size, totalElements, totalPages}` en backend/src/main/java/com/amano/backend/expense/ (FR-021..FR-023)
- [x] T033 Eliminar `GET /api/expenses`, `ExpenseService.listHistory` y `findTop200ByUserIdOrderByCreatedAtDescIdDesc`; actualizar el javadoc de `ExpenseResponse` (FR-024)
- [x] T034 [P] Agregar `month`, `page` y `size` a `searchExpenses` y eliminar `listExpenseHistory` en frontend/src/api/expenseClient.js (FR-020, FR-021, FR-024)
- [x] T035 [US2] Selector de mes entre el campo y el año, deshabilitado con "Todos los años", `?month=` en la dirección y mensaje vacío por mes en frontend/src/pages/ActivityPage.jsx (FR-001, FR-020)
- [x] T036 [US4] Tamaño de página según el alto disponible (5–20, recálculo en `resize`), paginación numerada con "…", `?page=` en la dirección y vuelta a la 1 al cambiar criterio en frontend/src/pages/ActivityPage.jsx (FR-021..FR-023)
- [x] T037 [P] Ensanchar la pantalla para los tres controles, disposición en celular y estilos de la paginación en frontend/src/pages/MainPage.scss (FR-001, FR-021)

## Phase 5: Validación manual

- [x] T016 Validar con el usuario los escenarios de la spec en la app levantada con Docker Compose (US1, US2, US3 y casos límite)

## Phase 6: Pruebas automatizadas (después de la validación manual)

- [x] T017 [P] Unit test de `ExpenseSearchLabels` (normalización, coincidencia por etiqueta) y de que el espejo coincide con `expenseCatalog.js` en backend/src/test/java/com/amano/backend/expense/ExpenseSearchLabelsTest.java
- [x] T018 [P] Integration test (Testcontainers + MockMvc) de search/years/detalle: campos, tildes, `%`/`_`, año, todos los años, truncado, 400, 401, 404 ajeno en backend/src/test/java/com/amano/backend/expense/ExpenseSearchIT.java
- [x] T019 [P] Tests de los clientes nuevos en frontend/src/api/expenseClient.test.js
- [x] T020 [P] Tests de búsqueda al dejar de escribir y con Enter, borrar el texto, cambio de año con y sin texto, mínimo de caracteres, sin resultados, error, años, URL y enlaces en frontend/src/pages/ActivityPage.test.jsx
- [x] T021 [P] Tests del detalle (único, recurrente, 404, error, Volver) en frontend/src/pages/ExpenseDetailPage.test.jsx
- [ ] T038 [P] Tests backend: mes (filtro, sin año, inválido), paginación (tamaño, totales, fuera de rango, `page`/`size` inválidos) y `GET /api/expenses` → 405 en ExpenseSearchIT.java y ExpenseSearchLabelsTest.java; tests de conversiones sobre `withConversions` y quitar los de historial de RecurringExpenseIT.java
- [ ] T039 [P] Tests frontend: selector de mes, paginación, tamaño por alto y `resize`, vuelta a la página 1, contador total y clientes en ActivityPage.test.jsx y expenseClient.test.js
- [ ] T040 Repetir T022 tras la sesión 4
- [x] T022 Ejecutar `mvn -B verify` en backend/ y `npm run lint && npm run test:coverage && npm run build` en frontend/; informar cobertura (≥ 85 %)

## Dependencies

- Fase 4d antes de volver a validar a mano (T016 se repite sobre lo nuevo) y de T038–T040.
- Fase 1 → Fases 2 y 3. US2 (Fase 4) depende de T007 y T009. Fase 5 después de 2–4. Fase 6 después de 5.
