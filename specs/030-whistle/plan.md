# Implementation Plan: Whistle — aviso manual a un deudor

**Branch**: `feat/whistle` (a confirmar, ver spec Q11) | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/030-whistle/spec.md`

## Summary

El acreedor de una deuda abierta de un grupo puede mandarle al deudor un correo inmediato ("aviso"), con confirmación previa. El límite es 1 aviso por par acreedor–deudor por grupo cada 24 h, y cada aviso enviado queda en el historial del grupo.

Técnicamente:

- endpoint nuevo `POST /api/groups/{groupId}/debts/{debtId}/whistle`;
- servicio `DebtWhistleService` en un subpaquete nuevo `groupexpense/whistle/`;
- tabla `debt_whistles` y ampliación del `CHECK` de `group_activity` en **V37**;
- reutilización de `ReminderMailService` y de `ReminderMessageBuilder` de 025, con un método nuevo;
- evento `DEBT_WHISTLED` en `ActivityEventService`;
- en el frontend, un `WhistleButton` con `ConfirmModal`, montado en `MyDebtsPanel` y `ExpenseDebtList`.

## Technical Context

**Language/Version**: Java 25 (`<java.version>25</java.version>` en `backend/pom.xml`) + Spring Boot 4.1.1; React 19 + Vite 8 + Vitest 5 (`frontend/package.json`)

**Primary Dependencies**: Spring Web/MVC, Spring Data JPA, Flyway, Spring Mail (`JavaMailSender`), PostgreSQL; pruebas: JUnit 5, Mockito, Testcontainers (PostgreSQL), GreenMail 2.1.14, MockMvc; frontend: react-router, React Testing Library

**Storage**: PostgreSQL con migraciones Flyway como fuente de verdad (`ddl-auto=validate`)

**Testing**: `mvn -B verify` en `backend/` (unitarias `*Test` con `Clock.fixed`, integración `*IT` con Testcontainers + GreenMail); `npm ci && npm run lint && npm run test:coverage && npm run build` en `frontend/`

**Target Platform**: Web (API REST + SPA)

**Project Type**: Monorepo web (backend REST + frontend React)

**Performance Goals**: respuesta en menos de 5 s con el SMTP disponible (NFR-001); una consulta indexada para el límite

**Constraints**:

- Cobertura de líneas ≥ 85 % (JaCoCo y V8).
- Migración solo con `CREATE`, `ALTER` e índices, sin `UPDATE` ni `DELETE`, y sin editar V1 a V36.
- Errores RFC 7807 en español con `properties.code`.
- Sin dependencias nuevas.

**Scale/Scope**:

- 3 historias: US1 avisar (P1), US2 límite (P1), US3 historial (P2).
- 1 migración, 1 endpoint nuevo, 1 tipo de evento nuevo, 1 componente de UI nuevo y 2 componentes extendidos.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Comprobación | Estado |
|-----------|--------------|--------|
| I. Arquitectura y separación | Se reutilizan `ReminderMailService`, `ReminderMessageBuilder`, `ActivityEventService`, `ProblemDetailExceptionHandler`, el patrón de reclamo de 025 (D3), `ConfirmModal` y `useSnackbar`. El código nuevo vive en `groupexpense/whistle/`, junto a `reminder/`. | PASS |
| II. SDD y trazabilidad | Plan, research, data-model, contratos, quickstart y tareas trazan cada FR, NFR y SC. | PASS |
| III. Entrega incremental | Tareas por historia (US1 → US2 → US3). US1 es entregable sola. | PASS |
| IV. Alcance controlado | Solo lo pedido por el cronograma y la propuesta. Las decisiones Q1 a Q11 están marcadas **a confirmar** y no se tratan como confirmadas. | **PASS condicionado**: no se implementa hasta aprobar Q1–Q11 |
| V. Calidad y cobertura | Pruebas unitarias, de integración con GreenMail y de Vitest por cada criterio. Umbral del 85 %. | PASS |
| VI. Reglas de negocio | No se inventan transiciones de deuda: avisar solo lee (FR-006). Los permisos y estados admitidos son decisiones explícitas (Q1, Q2). | PASS |
| VII. Contratos REST | Solo cambios aditivos: endpoint y evento nuevos (FR-017), documentados en `contracts/`. | PASS |
| VIII. Validación y errores | Revalidación en el servidor; códigos 401/403/404/409/422/429/503 con mensajes en español, sin exponer correos (FR-016). | PASS |
| IX. Seguridad | Permiso verificado en el servicio, no solo en la UI. Sesión requerida. Límite aplicado en el servidor con bloqueo (D3). | PASS |
| X. Código mantenible | Un solo componente de botón reutilizado en dos vistas. Sin dependencias nuevas. | PASS |
| XI. Reproducibilidad/CI | Mismas verificaciones que CI. | PASS |
| XII/XIII. PRs y commits | PR de `feat/...` hacia `sprint-5`, con Conventional Commits. | PASS |

**Post-design re-check**: el diseño no agrega violaciones. La única novedad estructural, la tabla `debt_whistles`, está justificada en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/030-whistle/
├── spec.md
├── plan.md              # Este archivo
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── debt-whistle.md            # POST …/whistle
│   └── debt-whistle-activity.md   # evento DEBT_WHISTLED y correo
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/amano/backend/
│   ├── groupexpense/
│   │   ├── reminder/
│   │   │   └── ReminderMessageBuilder.java        # + debtWhistle(...)
│   │   └── whistle/                               # NUEVO subpaquete
│   │       ├── DebtWhistle.java                   # entidad de debt_whistles
│   │       ├── DebtWhistleStatus.java             # SENDING, SENT, FAILED
│   │       ├── DebtWhistleRepository.java         # conteo por ventana + mark SENT/FAILED
│   │       ├── DebtWhistleService.java            # reglas, reclamo, envío, evento
│   │       ├── DebtWhistleController.java         # POST …/whistle
│   │       ├── DebtWhistleResponse.java
│   │       ├── DebtNotWhistleableException.java   # 409
│   │       ├── SimulatedDebtorWhistleException.java  # 422 (sujeto a Q3)
│   │       ├── WhistleRateLimitedException.java   # 429 + retryAfterSeconds
│   │       └── WhistleDeliveryException.java      # 503
│   ├── groups/
│   │   ├── GroupActivity.java                     # + DEBT_WHISTLED
│   │   ├── ActivityEventService.java              # + recordDebtWhistled(...)
│   │   └── GroupMemberRepository.java             # + findForUpdate (bloqueo, D3)
│   └── common/ProblemDetailExceptionHandler.java  # + handlers 409/422/429/503
├── src/main/resources/db/migration/
│   └── V37__create_debt_whistles.sql
└── src/test/java/com/amano/backend/groupexpense/whistle/
    ├── DebtWhistleServiceTest.java                # unitario, Clock.fixed
    └── DebtWhistleIT.java                         # Testcontainers + GreenMail + MockMvc
    (+ ReminderMessageBuilderTest extendido)

frontend/src/
├── api/groupExpensesClient.js                     # + whistleDebt(groupId, debtId)
├── components/groups/
│   ├── WhistleButton.jsx                          # NUEVO: botón + ConfirmModal + feedback
│   ├── WhistleButton.test.jsx
│   ├── MyDebtsPanel.jsx                           # monta WhistleButton si el caller es acreedor
│   └── ExpenseDebtList.jsx                        # ídem en el detalle del gasto
└── pages/groups/activityDescriptions.js           # + DEBT_WHISTLED
```

**Structure Decision**: se mantiene el monorepo existente. El backend nuevo vive en `groupexpense/whistle/` porque comparte dominio (deudas) y canal (correo de `reminder/`) con 025. No se crean módulos nuevos.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Tabla nueva `debt_whistles` (V37) | FR-007 a FR-010 exigen aplicar un límite por par en una ventana de 24 h, ignorar intentos fallidos y resistir la concurrencia. Hace falta guardar cada intento con su estado y su fecha. | (a) Contar eventos `DEBT_WHISTLED` en `group_activity`: el detalle es JSON sin índice por par, no registra los intentos en curso (permite doble envío) y acopla el límite al historial. (b) Una columna `last_whistled_at` en `debts`: el límite es por par y no por deuda, y obligaría a hacer `ALTER` de una tabla central. (c) Límite en memoria: se pierde al reiniciar y no funciona con varias instancias. |
| Ampliación del `CHECK` de `group_activity.event_type` (V37) | FR-011 exige un evento nuevo, y el catálogo es cerrado (patrón de V20, V22, V28 y V36). | No aplica: es la única vía permitida. |

## Desvíos registrados durante la implementación (T035)

Fecha: 2026-11-15. Ninguno cambia el alcance de la spec.

| Desvío | Motivo |
|---|---|
| El repositorio expone `findBlocking(...)`, que devuelve los intentos que bloquean ordenados del más nuevo al más viejo, en lugar de `findLatestBlocking` | El servicio toma el `createdAt` máximo; con una lista se testea más fácil el caso de varios intentos |
| Se creó `WhistleNotCreditorException` en lugar de reutilizar `GroupPermissionDeniedException` | El handler existente no agrega `properties.code`, y el contrato pide `WHISTLE_NOT_CREDITOR` (research D5 dejaba la decisión abierta) |
| Archivo extra `frontend/src/components/groups/whistleText.js` con `formatWait` y `whistleSuccessMessage` | La regla de lint `react-refresh/only-export-components` no permite exportar funciones desde el archivo del componente |
| La frase del historial usa `formatMoney`, por ejemplo "Ana López le avisó a Juan Pérez por $ 8.000,00 (Cena)", en lugar de "{monto} {moneda}" | Mismo formato de moneda que el resto de los eventos de deuda del historial |
| `DEBT_WHISTLED` se agregó al filtro "Recordatorios" del historial y a los eventos que enlazan al gasto | Sin eso, el evento no se podía filtrar ni abrir, a diferencia de `DEBT_REMINDER_SENT` |
| En "Mis deudas pendientes", el modal no muestra el concepto | `PendingDebtResponse` no trae la descripción del gasto. Agregarla sería un cambio de contrato fuera de alcance. El detalle del gasto sí la muestra |
| El log de falla de envío registra solo el tipo de excepción | El mensaje de una `MailSendException` puede incluir la dirección del destinatario (NFR-002) |
| El backend de US2 y US3 se escribió en la misma pasada que US1 (`DebtWhistleService`) | Las tres historias comparten el método de reclamo. Los tests de cada historia se escribieron antes que su implementación, pero **no se ejecutó una fase "roja" separada** del backend: cada corrida en el contenedor tarda varios minutos. La fase roja del frontend tampoco se corrió por separado |
| El controller queda con una línea sin cubrir (75 %): la respuesta 401 propia | El filtro de seguridad responde 401 antes de llegar al controller. Es el mismo patrón y la misma rama inalcanzable que en `PendingDebtController` |
| Las IT validan el `details_json` de `group_activity` (no `details`) | Nombre real de la columna |

## Resultados de verificación (T030, T031)

- **Backend** (`./mvnw -B verify` en `eclipse-temurin:25-jdk`): 569 tests unitarios y 485 de integración, 0 fallos, `All coverage checks have been met`, `BUILD SUCCESS`.
  - JaCoCo total: líneas 93,79 %, ramas 79,38 %, instrucciones 93,75 %.
  - `DebtWhistleService`: líneas 96,6 %, ramas 90 %.
  - Después de ese `verify` se ajustó una línea de log en `DebtWhistleService`, y `DebtWhistleServiceTest`, `ReminderMessageBuilderTest` y `DebtWhistleIT` se volvieron a correr en verde (45 + 14 tests).
- **Frontend**: `npm run lint` con 0 errores y 2 advertencias anteriores (`RegisterForm.jsx`, `ChangePasswordPage.jsx`). `npm run test:coverage`: 93 archivos, 1186 tests, 0 fallos; líneas 95 %, ramas 89,42 %, sentencias 94,08 %. `npm run build` OK. `GroupDebtsView.test.jsx` pasó (024 T023), en una sola corrida.
