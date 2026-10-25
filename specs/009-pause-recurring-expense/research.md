# Research: Pausar o finalizar un gasto recurrente (009)

**Fecha**: 2026-10-24 | **Rama**: `feat/definir-tipo-gasto` | **Spec**: [spec.md](./spec.md)

**Propósito**: registrar las decisiones de diseño. No quedaron `NEEDS CLARIFICATION`: las decisiones de producto están en las Clarifications de la spec y aquí se justifican las técnicas.

## D1 — Numeración `009` en lugar de `007`

- **Decisión**: la carpeta es `specs/009-pause-recurring-expense`.
- **Rationale**: `007` pertenece a `007-register-expense` (PR #15) y `008` a `008-user-search`. `create-new-feature.sh` de Spec Kit corrige solo un prefijo ya usado, y las referencias cruzadas del tipo "FR-010 de 007" serían ambiguas con dos specs 007. Acordado con el equipo el 2026-10-24.
- **Alternativas consideradas**: `007-pause-recurring-expense` tal como venía en la tarea (rompe la convención y la trazabilidad).

## D2 — Extender `expenses` en lugar de crear tablas nuevas

- **Decisión**: la serie es el propio gasto recurrente registrado (columnas `recurrence_frequency`, `recurrence_status`, `recurrence_next_date`) y cada repetición es una fila más de `expenses` con `recurrence_source_id`.
- **Rationale**: una repetición **es** un gasto (aparecerá en futuros listados, balances y reportes sin tratamiento especial), y la serie ya tiene todos los datos a copiar. Reutiliza la entidad, el repositorio, la idempotencia y las restricciones de 007 (principio I).
- **Alternativas consideradas**: tabla `recurring_expense_schedules` separada con los datos de plantilla (duplica columnas y validaciones de 007); calcular repeticiones "al vuelo" sin persistirlas (no habría gastos reales que listar ni balancear, y la tarea habla de "generación").

## D3 — Las filas de 007 no se tocan

- **Decisión**: la migración no hace `UPDATE`. Los `RECURRING` existentes quedan con las columnas nuevas en `NULL` ("recurrente sin programación"): no se listan, no se pueden pausar y no generan.
- **Rationale**: asignarles una frecuencia (por ejemplo, mensual) sería inventar datos que el usuario nunca dio. La regla del equipo prohíbe `DELETE` en migraciones y conviene mantener `UPDATE` fuera también. Todas las restricciones admiten la combinación de cuatro `NULL`.
- **Alternativas consideradas**: backfill a `MONTHLY` + `PAUSED` (dato inventado); hacer obligatoria la frecuencia en la base (rompería las filas existentes).

## D4 — Frecuencia obligatoria solo en la API para los nuevos

- **Decisión**: `ExpenseService` exige `frequency` para `RECURRING` y la rechaza para `ONE_TIME`, acumulando el error con los de 007. La base lo refuerza con `ck_expenses_recurrence_type` (un `ONE_TIME` nunca tiene frecuencia).
- **Rationale**: es la única forma de que los nuevos recurrentes generen algo (FR-001) sin romper los datos viejos (D3).
- **Consecuencia sobre 007**: tres tests existentes se adaptaron (ver plan.md §Complexity Tracking): el payload `RECURRING` de `RegisterExpenseIT` suma `frequency`, la mutación de tipo en `ExpenseServiceTest` suma la frecuencia y `ExpenseSchemaIT` suma las cuatro columnas a la lista exacta.

## D5 — Fechas ancladas al inicio

- **Decisión**: la fecha n de la serie es `inicio.plus(n, unidad)` (`RecurrenceFrequency.nth`); "la siguiente" se busca como el menor n cuya fecha supera la referencia (`firstAfter`).
- **Rationale**: `LocalDate.plusMonths` recorta al último día del mes. Si se sumara un mes a la fecha anterior, una serie del 31/01 quedaría en 28/02, 28/03, 28/04… para siempre; anclando al inicio vuelve a 31/03, 30/04, 31/05 (FR-004). Lo mismo con el 29/02 anual.
- **Alternativas consideradas**: guardar un contador de repeticiones (columna extra sin necesidad: n se recalcula con `ChronoUnit.between` y un ajuste de ±1).

## D6 — Sin generación retroactiva al registrar ni al reanudar

- **Decisión**: al registrar, `next = primera fecha de la serie > hoy`; al reanudar, `next = primera fecha de la serie ≥ hoy`.
- **Rationale**: registrar hoy un alquiler "desde enero" no debe crear de golpe nueve gastos que la persona probablemente ya cargó o no quiere ver; y pausar significa que esas fechas no corresponden. Al reanudar se incluye hoy porque, si hoy es fecha de la serie, ese gasto sí corresponde.
- **Alternativas consideradas**: catch-up completo (sorpresivo, riesgo de duplicar cargas manuales); pedir al usuario si quiere recuperar fechas (más UI sin pedido explícito).
- **Diferencia con la caída del job**: si la generación no corre varios días con la serie **activa**, sí se recuperan todas las fechas vencidas (US2-3): ahí la persona no pidió frenar nada.

## D7 — Generación con `@Scheduled` diario y clave determinística

- **Decisión**: `RecurringExpenseScheduler` (cron `recurrence.cron`, por defecto 00:15, zona `recurrence.zone`) invoca `RecurringExpenseService.generateDueOccurrences(hoy)`, que en una transacción recorre las series `ACTIVE` vencidas (índice parcial `ix_expenses_recurrence_due`) y crea una repetición por fecha. La clave de idempotencia de cada repetición es `UUID v3("amano-recurrence:" + serie + ":" + fecha)`.
- **Rationale**: mismo patrón que `UnverifiedAccountCleanupScheduler` (003, D6). La clave determinística y `uq_expenses_recurrence_occurrence (source, fecha)` garantizan que dos corridas no dupliquen (FR-006) incluso si se superponen. La lógica vive en el servicio y recibe "hoy" por parámetro, así los tests la invocan sin esperar al cron. Límite de seguridad: 1000 repeticiones por serie y corrida.
- **Alternativas consideradas**: generar al consultar (no hay listado de gastos todavía y acopla la escritura a una lectura); cola/eventos (infraestructura que el proyecto no tiene).

## D8 — Zona horaria

- **Decisión**: el "hoy" del registro usa la zona del dispositivo enviada por 007 (`deviceTimeZone`, UTC si es inválida); el "hoy" de la generación y de reanudar usa `recurrence.zone` (`America/Argentina/Buenos_Aires`), inyectada como `Clock`.
- **Rationale**: el registro ya trae la zona del usuario (D4/D5 de 007). La generación corre sin usuario, así que necesita una zona del servidor; la del público objetivo evita que el gasto "del 1°" se genere el 30 a la noche. El `Clock` inyectado permite tests deterministas (`Clock.fixed`).

## D9 — Máquina de estados y respuestas

- **Decisión**: `PATCH /api/expenses/{id}/status` con `{ "status": ... }`. Mismo estado → 200 sin cambios; salir de `FINISHED` → 409 `RECURRENCE_FINISHED`; no ser una serie del usuario → 404 neutro; estado inválido → 400 por campo.
- **Rationale**: un único endpoint idempotente cubre pausar, reanudar y finalizar (la tarea sugería `/status` o `/pause`). Tratar el mismo estado como éxito absorbe dobles clics y pestañas duplicadas sin mostrar errores falsos. El 404 único sigue el criterio de no revelar recursos ajenos (008, 006).
- **Alternativas consideradas**: endpoints separados `/pause`, `/resume`, `/finish` (tres rutas para la misma operación); 409 también para el mismo estado (ruido en la UI).

## D10 — Listado `GET /api/expenses/recurring`

- **Decisión**: endpoint de solo lectura que devuelve las series del usuario (no las repeticiones), más recientes primero, con la misma forma de `ExpenseResponse`.
- **Rationale**: pausar necesita una pantalla desde donde hacerlo y hoy no existe ningún listado de gastos (007 solo registra). Se limita a las series para no adelantar el historial de gastos, que es otra historia del backlog.

## D11 — `ExpenseResponse` extendido con campos opcionales

- **Decisión**: `frequency`, `status` y `nextOccurrenceDate` se agregan al final del record y se omiten cuando son `null` (`NON_NULL`, como `description`).
- **Rationale**: un gasto `ONE_TIME` responde exactamente igual que en 007; el contrato de 007 no cambia para los clientes existentes.

## D12 — Frontend

- **Decisión**: `ExpenseForm` muestra "Frecuencia" con `useWatch` sobre el tipo (no `watch`, que dispara la advertencia `react-hooks/incompatible-library` que ya tienen `RegisterForm` y `ChangePasswordPage`) y la envía solo para `RECURRING`. `RecurringExpensesPage` carga con `useEffect` + bandera de cancelación (patrón de `NewExpensePage`), confirma "Finalizar" en línea, deshabilita las acciones de la serie en curso y reemplaza la serie por la respuesta del servidor; ante 404/409 recarga. Montos y fechas se formatean sobre las cadenas canónicas (`expenseFormat.js`), sin `Number`, como exige SC-004 de 007.
- **Alternativas consideradas**: `window.confirm` (el resto de la app no usa diálogos nativos y no es testeable igual); actualización optimista (mostraría un estado que el servidor podría rechazar).

## D13 — Estrategia de pruebas

- **Decisión**: unitarios sin base (`RecurrenceFrequencyTest`, `ExpenseRecurrenceRegistrationTest`, `RecurringExpenseServiceTest` con `Clock.fixed`), integración con Testcontainers (`RecurringExpenseIT`: flujo completo por HTTP, generación invocada con el "hoy" del servicio, propiedad, filas heredadas y cada restricción de V5) y Vitest (`RecurringExpensesPage.test.jsx`, `ExpenseForm.test.jsx`, `expenseClient.test.js`, `expenseCatalog.test.js`, `expenseFormat.test.js`, `SiteNav.test.jsx`).
- **Rationale**: principio V (cobertura ≥ 85 %). En los IT la generación se llama siempre con "hoy" para no generar fechas futuras de series de otros tests (las corridas son globales a la base del contenedor).

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| Generación duplicada si hubiera dos instancias del backend | Clave determinística + `uq_expenses_recurrence_occurrence`; la segunda transacción falla y se reintenta al día siguiente sin duplicar |
| Serie con muchas fechas vencidas tras una caída larga | Límite de 1000 repeticiones por serie y corrida; la siguiente corrida continúa |
| Repetición generada antes de que la persona "pague" | La generación es solo registro (la app no mueve dinero); finalizar o pausar la detiene hacia adelante |
| Usuario espera ver hoy un gasto que vence hoy | Se genera en la corrida de las 00:15; documentado en Assumptions |
| No hay forma de borrar una repetición no deseada | Fuera de alcance (edición/eliminación de gastos); candidata para el backlog |
