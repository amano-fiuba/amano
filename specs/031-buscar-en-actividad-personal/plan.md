# Implementation Plan: Buscar en actividad personal

**Branch**: `feat/buscar-en-act-personal` | **Date**: 2026-11-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/031-buscar-en-actividad-personal/spec.md`

## Summary

Agregar a `/activity` un buscador (texto + selectores de mes y de año, con paginación) que consulta al backend
sobre **todos** los gastos personales de la persona, y una pantalla de detalle
`/expenses/:id`. El backend suma tres lecturas a `ExpenseController`
(`GET /api/expenses/search`, `GET /api/expenses/years`, `GET /api/expenses/{id}`)
sin migraciones, y elimina `GET /api/expenses`, que queda sin uso (sesión 4). El frontend guarda la
búsqueda vigente en la dirección (`?q=&year=&month=&page=`) para conservarla al volver del
detalle, con Atrás o al recargar.

## Technical Context

**Language/Version**: Java 25 + Spring Boot 4.1 (backend); React 19 + Vite + React Router 7 (frontend)

**Primary Dependencies**: Spring Data JPA (Hibernate), Spring Security (cookie `amano_auth`), React Router `useSearchParams`

**Storage**: PostgreSQL, tabla `expenses` existente. **Sin migraciones nuevas.**

**Testing**: JUnit + Spring Boot Test + Testcontainers (`*Test`, `*IT`); Vitest + React Testing Library (`*.test.jsx`). Cobertura ≥ 85 %.

**Target Platform**: Web (navegador) servida por Docker Compose

**Project Type**: Monorepo web (backend + frontend)

**Performance Goals**: Por búsqueda, una consulta de la página y una de conteo (`Page`); como máximo 50 filas por página.

**Constraints**: Sin dependencias nuevas; sin extensiones de PostgreSQL (`unaccent` descartada, ver D2).

**Scale/Scope**: Gastos personales de una sola persona (cientos a pocos miles de filas).

## Constitution Check

| Principio | Cumplimiento |
|-----------|--------------|
| I. Arquitectura | Se extienden `ExpenseController`/`ExpenseRepository` y la pantalla `ActivityPage` existentes; la lógica de búsqueda va en un servicio propio del paquete `expense`. |
| II. SDD | spec → plan → tasks antes de implementar. |
| III. Incremental | Una sola historia del backlog; US1/US3 (P1) antes que US2 (P2). |
| IV. Alcance | Solo gastos personales y los tres campos acordados; filtros de año y mes y paginación pedidos en la sesión 4. |
| V. Pruebas | Fase de tests al final, después de la validación manual pedida por el usuario; cobertura ≥ 85 %. |
| VI. Reglas de negocio | Solo lectura; no altera gastos ni series. |
| VII. Contratos REST | Endpoints nuevos documentados en [contracts/personal-expenses-search-api.md](./contracts/personal-expenses-search-api.md); `GET /api/expenses` se elimina por no tener uso (FR-024). |
| VIII. Validación | `q` y `year` se validan en el backend (400 con `properties.fields`); 404 neutro para gastos ajenos. |
| IX. Seguridad | El dueño sale siempre de la sesión; consultas filtradas por `userId`. |
| X. Mantenibilidad | Reutiliza `ExpenseResponse`, conversiones de `ExpenseService` y catálogos de `expenseCatalog.js`. |

## Decisiones (research)

- **D1 – Endpoint de búsqueda separado** (`/api/expenses/search`) en lugar de `?q=` sobre `GET /api/expenses`: la respuesta es un objeto con datos de paginación y el contrato existente devolvía un arreglo plano. Desde la sesión 4 `GET /api/expenses` se elimina (D15).
- **D2 – Tildes sin `unaccent`**: la descripción se compara con `lower(translate(description, 'ÁÉÍÓÚÜÑáéíóúüñ…', 'AEIOUUNaeiouun…'))` y el texto buscado se normaliza igual en Java (NFD sin marcas + minúsculas). Evita una migración `CREATE EXTENSION` que requiere privilegios en producción.
- **D3 – Categoría y medio de pago por etiqueta**: las etiquetas en español viven solo en el frontend; el backend incorpora un espejo (`ExpenseSearchLabels`) con las mismas etiquetas de `expenseCatalog.js`. El texto normalizado se busca en cada etiqueta normalizada y se obtiene el conjunto de códigos que coinciden; la consulta usa `category IN (…) OR payment_method IN (…)` solo si el conjunto no está vacío. Un test fija que el espejo coincide con el catálogo del frontend.
- **D4 – Consulta dinámica con `Specification`**: `ExpenseRepository` extiende `JpaSpecificationExecutor<Expense>`; los predicados de año y de categorías/medios se agregan solo cuando aplican (evita `IN ()` vacío). Orden `createdAt DESC, id DESC`. ~~Límite 201~~ → desde la sesión 4, `findAll(spec, PageRequest)` (D11).
- **D5 – Literalidad de `%` y `_`**: se escapan con `\` y el `LIKE` usa `escape '\'`.
- **D6 – Año**: el backend recibe `year` opcional (ausente = todos los años) y filtra `expense_date BETWEEN 1/1 y 31/12`. El año actual lo decide el navegador (zona de la persona); `GET /api/expenses/years` devuelve solo los años con gastos y el frontend agrega el actual.
- **D7 – Estado en la URL**: `/activity?q=farma&year=2026` (o `year=all`). El texto del campo es estado local; tras una pausa de 400 ms sin teclear (o con Enter) pasa a la URL con `replace` (no agrega entradas al historial). Mientras el texto difiere del vigente se muestra "Buscando…". Cambiar el año actualiza la URL en el acto, haya texto o no. Sin `year` en la URL rige el año actual: la pantalla siempre muestra lo que dice el selector, y sin texto pide `GET /api/expenses/search` solo con `year` (sesión 3). Vaciar el campo (la x nativa del `input type="search"`) aplica en el acto, sin esperar la pausa. Un `year` inválido en la URL se trata como el año actual; un `q` de menos de 2 caracteres se ignora.
- **D8 – Detalle**: `GET /api/expenses/{id}` reutiliza `findByIdAndUserId` y las conversiones de la actividad. 404 único (`ExpenseNotFoundException`, "No encontramos ese gasto") para inexistente o ajeno. El enlace de cada fila pasa `state.from` con la URL actual; "Volver" usa ese valor o `/activity`.
- **D9 – Respuestas fuera de orden y carga**: el componente de resultados se monta por cada búsqueda vigente y descarta la respuesta si se desmontó (cambió la búsqueda). La pausa de 400 ms limita las consultas a una por pausa; cada consulta filtra por `user_id` sobre los gastos de una sola persona, así que la carga sobre la base es baja.
- **D11 – Paginación en el servidor** (sesión 4): `GET /api/expenses/search` recibe `page` (1..n) y `size` (1..50) y responde `{expenses, page, size, totalElements, totalPages}` con `findAll(spec, PageRequest.of(page-1, size, sort))`. Si la página pedida supera la última, se devuelve la última (un segundo `findAll`); así un enlace viejo nunca muestra una lista vacía. Se descarta paginar en el navegador porque obligaría a traer todos los gastos y a reintroducir un tope.
- **D12 – Mes**: `month` (1..12) requiere `year` y filtra `expense_date` entre el primer y el último día de ese mes (`YearMonth`). En el frontend, `?month=` en la dirección; con `year=all` se ignora y el selector se deshabilita.
- **D13 – Tamaño de página según la pantalla**: `size = clamp(floor((innerHeight − bottom del formulario − reserva de contador y paginación) / alto de tarjeta), 5, 20)`, con el alto de tarjeta tomado de la primera tarjeta renderizada (o un valor estimado antes de la primera). Se recalcula en `resize` (con una pausa); al cambiar, la página pasa a la que contiene el primer gasto visible: `floor((page − 1) · sizeViejo / sizeNuevo) + 1`. El `size` no va en la dirección (depende del dispositivo).
- **D14 – Estado de la página**: `?page=` en la dirección (se omite la 1), actualizada con `replace` como el resto; cambiar texto, año o mes la quita. La página devuelta por el backend reemplaza a la de la dirección si difiere (página fuera de rango).
- **D15 – Eliminación de `GET /api/expenses`**: se borran el endpoint, `ExpenseService.listHistory`, `findTop200ByUserIdOrderByCreatedAtDescIdDesc` y `listExpenseHistory` del frontend; los tests de conversiones pasan a `withConversions` y los de `RecurringExpenseIT` sobre ese endpoint se eliminan (la búsqueda los cubre en `ExpenseSearchIT`). Como el manejador global convertía en 500 el método no soportado, se agrega un handler de `HttpRequestMethodNotSupportedException` → 405 ("Método no permitido").
- **D10 – Aspecto**: el campo reutiliza el estilo del buscador de la barra (lupa `SearchIcon` de `NavIcons`) y cada gasto reutiliza `.group-rows`/`.group-row`/`.group-row__chevron` de `groups.scss` (precedente: `FriendChooserDialog` importa esa hoja).

## Project Structure

### Documentation (this feature)

```text
specs/031-buscar-en-actividad-personal/
├── spec.md
├── plan.md
├── contracts/personal-expenses-search-api.md
├── tasks.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/
├── expense/
│   ├── ExpenseController.java          # + search, years, detail
│   ├── ExpenseRepository.java          # + JpaSpecificationExecutor, findExpenseYears
│   ├── ExpenseService.java             # + getExpense (detalle con conversiones), conversiones reutilizables
│   ├── ExpenseSearchService.java       # NUEVO: validación, normalización, Specification
│   ├── ExpenseSearchLabels.java        # NUEVO: etiquetas en español (espejo de expenseCatalog.js)
│   ├── ExpenseSearchResponse.java      # NUEVO: { expenses, page, size, totalElements, totalPages }
│   └── ExpenseNotFoundException.java   # NUEVO: 404 neutro
└── common/ProblemDetailExceptionHandler.java  # + handler 404

frontend/src/
├── api/expenseClient.js                # + searchExpenses, listExpenseYears, getExpense
├── pages/ActivityPage.jsx              # buscador, selector de año, filas con enlace
├── pages/ExpenseDetailPage.jsx         # NUEVO
├── pages/MainPage.scss                 # estilos del buscador, la paginación y el detalle
└── App.jsx                             # ruta /expenses/:id
```

**Structure Decision**: monorepo web existente; no se crean módulos nuevos.

## Complexity Tracking

Sin violaciones a justificar.
