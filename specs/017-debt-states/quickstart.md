# Quickstart: Crear estados de deuda (017)

**Spec**: [spec.md](./spec.md) | **Contrato**: [contracts/group-expenses-api.md](./contracts/group-expenses-api.md)

## 1. Preparación

- Rama: `feat-crear-estadoss-deudas` (desde `sprint-4`, `223a768`). La spec y el backend de US1–US2 nacieron en `feat/estados-deuda` (desde `89c4339`).
- **Migración nueva**: `V13__create_group_expenses_and_debts.sql`. Si tu base local tiene aplicada una `V13` de un borrador anterior (por ejemplo `V13__create_debts.sql`), Flyway va a fallar al arrancar con un error de checksum o de migración faltante: recreá la base (`docker compose down -v && docker compose up -d db`). Antes de correr las pruebas, hacé `./mvnw clean` para que no quede una `V13` vieja en `target/classes`.
- Levantar: base (`docker compose up -d db`), backend (`cd backend && (set -a; source ../.env; set +a; ./mvnw spring-boot:run)`, ver `README.md`, sección «Cómo levantarlo») y frontend (`cd frontend && npm run dev`).
- Datos: una cuenta **A** (Ana) y una cuenta **B** (Juan), amigas (011/012). Un grupo "Depto" creado por A con B como miembro y un perfil simulado "Abuela".

### Pruebas automatizadas de la feature

| Prueba | Qué cubre |
|--------|-----------|
| `groupexpense/EqualSplitTest` | División exacta, resto al pagador, resto en orden sin pagador, mínimo por participante, suma exacta |
| `groupexpense/DebtTest` | `DebtStatus.of`, `applyPayment`: `PENDING → PARTIALLY_PAID → PAID`, pago total directo, rechazos (≤ 0, mayor al saldo, ya `PAID`) |
| `groupexpense/GroupExpenseServiceTest` | Validaciones de 007, pagador y participantes, casillas, idempotencia, partes y deudas generadas, evento registrado |
| `groups/port/LedgerDebtPortIT` | Deudor, acreedor, perfil simulado, estados que cuentan y que no, otro grupo, ids coincidentes |
| `groups/GroupPermissionCheckerTest` | `canCreateExpenses` y `canExpenseForOthers` por rol y casilla |
| `groupexpense/GroupExpenseMigrationIT` | CHECK de estado vs. monto pagado, tipo de participante, `ck_debts_not_self`, `SET NULL` al borrar un perfil |
| `groupexpense/GroupExpenseControllerIT` | Contrato HTTP: 201/200/400/403/404/422, `Location`, historial |
| `groups/GroupDebtBlockingIT` | Abandonar y quitar con deudas → 403 `PENDING_DEBTS`; ids coincidentes; deudas `PAID` u otro grupo no bloquean |
| `pages/groups/splitEqually.test.js` | Mismos casos que `EqualSplitTest` |
| `api/groupExpensesClient.test.js` | Cabecera, cuerpo, 201/200, 400 por campo, 403, 404, 401, 5xx y red |
| `pages/groups/GroupExpenseFormView.test.jsx` | Preselección, vista previa, pagador fijo, errores por campo, reintento con la misma clave, navegación al detalle |
| `pages/groups/GroupExpenseDetailView.test.jsx` | Partes, deudas con las tres etiquetas de estado, 404, 401, error con reintento |
| `pages/groups/debtStatusLabels.test.js`, `expensePermissions.test.js` | Etiquetas de estado; permisos por rol y casillas |
| `components/groups/DebtStatusBadge`, `ExpenseSharePreview`, `ExpenseDebtList`, `GroupExpensesCard` (`.test.jsx`) | Badges, vista previa, lista de deudas, tarjeta "Gastos" del grupo |
| `GroupInfoView`, `GroupHistoryView`, `App` (`.test.jsx`, ajustes) | Tarjeta montada, "Ver gasto", `?filter=EXPENSES`, rutas protegidas |

```bash
cd backend && ./mvnw -B clean verify
cd frontend && npm run lint && npm run test:coverage   # gates: JaCoCo y V8 ≥ 85 %
```

## 2. Validación manual

Con la cuenta **A** en el grupo "Depto" (A ADMIN, B MEMBER, Abuela simulado):

- **A. Gasto con resto al pagador**: "Nuevo gasto" → $ 10.000,00, "Súper", pagó Ana, entre Ana, Juan y Abuela. La vista previa muestra 3.333,34 / 3.333,33 / 3.333,33. Guardar: el detalle muestra dos deudas "Pendiente" de 3.333,33 hacia Ana.
- **B. Gasto sin el pagador**: $ 100,00 pagados por Ana entre Juan, Abuela y un segundo perfil simulado "Tío". La vista previa y el detalle muestran 33,34 (el primero de la lista) / 33,33 / 33,33.
- **C. Historial**: abrir "Ver historial de actividad". Aparecen "Ana registró un gasto de $ 10.000,00 «Súper»" y el de $ 100,00. "Ver gasto" abre el detalle.
- **D. Bloqueos**: intentar quitar a Juan → "No se permite la baja: la persona tiene deudas pendientes…". Quitar a Abuela → mismo mensaje. Con la cuenta B, "Abandonar grupo" → "No podés abandonar el grupo: tenés saldos pendientes…".
- **E. Acreedor**: promover a Juan a ADMIN y, con la cuenta B, intentar quitarle el rol a Ana y quitarla del grupo → la baja se bloquea por deudas a cobrar (después de quitarle el rol de admin).
- **F. Permisos**: como ADMIN, activar la casilla "No podrán hacer gastos". Con la cuenta B, el botón "Nuevo gasto" no aparece. Desactivarla: B ve el formulario con el pagador fijo en sí mismo. Activar "Pueden generar gastos en nombre de otras personas": B puede elegir a Ana como pagadora.
- **G. Errores**: monto $ 0,02 entre tres → error en el monto. Fecha de mañana → error en la fecha. Con el backend apagado, "Guardar" → "No se pudo registrar el gasto. Intentá nuevamente". Levantar el backend y reintentar: se crea un solo gasto.
- **H. Sin deudas**: en un grupo nuevo sin gastos, quitar miembros y abandonar funcionan como en 010.
- **I. Tarjeta "Gastos"**: en la pantalla del grupo, la tarjeta muestra "2 gastos registrados" después de A y B. "Ver gastos en el historial" abre el historial con el filtro "Gastos" aplicado. Abrir a mano `/groups/{id}/history?filter=CUALQUIERA` muestra toda la actividad.

Los estados "Pago parcial" y "Pagada" no se pueden generar desde la interfaz hasta 018. Están cubiertos por `DebtTest`, `GroupExpenseMigrationIT` y `GroupExpenseDetailView.test.jsx`.

## 3. Regresión

- Gastos personales (007/009): `POST /api/expenses`, `/expenses/recurring` y el scheduler no cambian (`expenses` no se modifica).
- Grupos (010): crear, agregar, quitar, abandonar, permisos, roles y foto siguen igual en grupos sin deudas. Las pruebas de 010 que mockean `DebtPort` se ajustan a la firma nueva.
- Historial (014): las entradas viejas y los filtros no cambian. "Gastos" ahora muestra entradas reales.

## Resultado de la validación

- **Línea base (T003, 05/11, `feat-crear-estadoss-deudas` con el merge de 016)**: backend 276 unitarias + 186 de integración, JaCoCo 93,0 % de líneas; frontend 603 pruebas. Todo en verde.
- **Automatizada (T051/T052, 05/11)**:
  - Backend `./mvnw -B clean verify`: 335 unitarias + 230 de integración, 0 fallos, BUILD SUCCESS. JaCoCo 94,8 % de líneas en total, `groupexpense` 99,3 %, `groups.port` 100 %. La clase nueva o modificada con menor cobertura es `GroupMemberService`, con 91,9 %.
  - Frontend `npm run lint && npm run test:coverage && npm run build`: 0 errores de lint (2 warnings previos en `ChangePasswordPage`), 672 pruebas en verde, V8 93,9 % de líneas y 93,2 % de sentencias. Todos los archivos nuevos superan el 96 % de líneas y build OK.
- **Manual**: pendiente.
