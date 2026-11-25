# Quickstart: validación de gastos recurrentes (009)

**Fecha**: 2026-10-24 | **Spec**: [spec.md](./spec.md) | **Contrato**: [contracts/recurring-expenses-api.md](./contracts/recurring-expenses-api.md)

Guía para verificar de punta a punta la recurrencia, la pausa y la finalización. Solo comandos y escenarios observables.

## Prerrequisitos

- Entorno de desarrollo del `README.md` (base en Docker, backend con `mvn spring-boot:run`, frontend con `npm run dev`).
- Una cuenta **verificada**.
- **Reiniciar el backend** después de actualizar la rama: Flyway aplica `V5__expense_recurrence.sql` al arrancar.

## 1. Verificaciones automatizadas

```bash
cd backend && mvn -B verify                                                  # JaCoCo ≥ 85 %
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

| Suite | Cubre |
|-------|-------|
| `expense/RecurrenceFrequencyTest` | Semanal/mensual/anual, fin de mes sin desplazamiento, 29/02, no retroactividad, "hoy o posterior" al reanudar |
| `expense/ExpenseRecurrenceRegistrationTest` | Frecuencia obligatoria/inválida/prohibida, serie `ACTIVE` con próxima fecha, `ONE_TIME` sin columnas nuevas, conflicto de idempotencia por frecuencia |
| `expense/RecurringExpenseServiceTest` | Pausar, reanudar, finalizar, mismo estado, 409, 404 para todo lo que no es una serie propia, estado inválido, generación, recuperación de fechas vencidas, fin de mes, claves determinísticas |
| `expense/RecurringExpenseIT` | Flujo HTTP completo sobre PostgreSQL, generación sin duplicados, pausa sin generación, finalizar definitivo, propiedad, 401, filas de 007 intactas y cada restricción de V5 |
| `expense/RegisterExpenseIT`, `ExpenseServiceTest`, `ExpenseSchemaIT` | Regresión de 007 con la regla nueva |
| `pages/RecurringExpensesPage.test.jsx` | Lista, acciones por estado, carga, confirmación, éxito, 409, red, 401, vacío, reintento |
| `components/ExpenseForm.test.jsx` | Campo "Frecuencia": visibilidad, obligatoriedad, envío, error de servidor |
| `api/expenseClient.test.js`, `validation/expenseCatalog.test.js`, `validation/expenseFormat.test.js`, `components/SiteNav.test.jsx` | Cliente, catálogos, formato de monto/fecha, acceso en la barra |

## 2. Escenarios manuales

### A. Registrar un recurrente (US1)

1. Ir a "Registrar gasto" (`/expenses/new`). Elegir tipo **Recurrente**.
2. **Esperado**: aparece "Frecuencia" (Semanal / Mensual / Anual). Con tipo **Único** desaparece.
3. Guardar sin frecuencia. **Esperado**: "La frecuencia es obligatoria para un gasto recurrente".
4. Completar con frecuencia **Mensual** y fecha de hoy; guardar. **Esperado**: "El gasto se guardó correctamente".
5. Repetir con fecha 01/01/2026. **Esperado**: en "Gastos recurrentes" su próxima fecha es el próximo día 1, sin repeticiones de los meses anteriores.

### B. Ver y pausar (US3)

1. Ir a "Gastos recurrentes" (`/expenses/recurring`) desde la barra.
2. **Esperado**: cada serie muestra monto, moneda, frecuencia, categoría, estado "Activo" y "Próxima fecha".
3. Pulsar **Pausar**. **Esperado**: el botón dice "Pausando…"; luego "El gasto recurrente se pausó", estado "Pausado", botones "Reanudar" y "Finalizar".

### C. Generación y pausa (US2/US3)

Simular que una serie venció y correr la generación sin esperar a las 00:15:

```sql
-- id de la serie: el de la tarjeta (o SELECT id, recurrence_status FROM expenses WHERE recurrence_frequency IS NOT NULL;)
UPDATE expenses SET recurrence_next_date = CURRENT_DATE WHERE id = <id>;
```

Para disparar la generación en el entorno local, arrancar el backend con un cron cercano, por ejemplo cada minuto:

```bash
cd backend && (set -a; source ../.env; set +a; RECURRENCE_CRON="0 * * * * *" mvn spring-boot:run)
```

1. Con la serie **Pausada**, esperar un minuto. **Esperado**: `SELECT * FROM expenses WHERE recurrence_source_id = <id>;` no devuelve filas.
2. **Reanudar** desde la pantalla. **Esperado**: "El gasto recurrente se reanudó", estado "Activo" y próxima fecha hoy o posterior (las fechas de la pausa no se generan).
3. Si la próxima fecha quedó en hoy, esperar un minuto. **Esperado**: aparece una fila con `recurrence_source_id = <id>`, fecha de hoy y los datos de la serie; la próxima fecha avanzó un período; nunca hay dos filas para la misma fecha.

### D. Finalizar (US4)

1. Pulsar **Finalizar**. **Esperado**: confirmación "Finalizar es definitivo: no se generarán más gastos de esta serie." con "Sí, finalizar" y "Cancelar".
2. **Cancelar**. **Esperado**: no cambia nada.
3. **Finalizar → Sí, finalizar**. **Esperado**: "El gasto recurrente se finalizó", estado "Finalizado", sin botones ni próxima fecha.
4. Intentar reanudarla por API:

   ```bash
   curl -s -X PATCH http://localhost:8080/api/expenses/<id>/status \
     -H 'Content-Type: application/json' -b 'amano_auth=<cookie>' -d '{"status":"ACTIVE"}'
   ```

   **Esperado**: 409 "El gasto recurrente ya está finalizado y no puede modificarse".

### E. Errores

1. PATCH sobre el id de un gasto **Único** o de otro usuario. **Esperado**: 404 "No se encontró el gasto recurrente".
2. Detener el backend y pulsar "Pausar". **Esperado**: "No se pudo actualizar el gasto recurrente. Intentá nuevamente"; la serie sigue "Activo".
3. Abrir la pantalla con el backend detenido. **Esperado**: mensaje de carga fallida con "Reintentar".

## 3. Regresiones a observar

- Registrar un gasto **Único** funciona igual que en 007 (sin "Frecuencia" y misma respuesta).
- Los gastos "Recurrente" cargados antes de esta feature siguen en la base sin cambios y no aparecen en "Gastos recurrentes".
- Buscador de usuarios (008) y cuenta (001–006) sin cambios.

## Resultado de la validación (2026-10-24)

- **Automatizada**: `mvn -B verify` → BUILD SUCCESS, "All coverage checks have been met"; 195 unitarios y 101 de integración en verde (nuevos: `RecurrenceFrequencyTest` 8, `ExpenseRecurrenceRegistrationTest` 10, `RecurringExpenseServiceTest` 15, `RecurringExpenseIT` 11). `npm run test:coverage` → 33 archivos y 363 tests en verde, 92,66 % de líneas; `npm run lint` sin errores (2 advertencias previas, ajenas a esta feature); `npm run build` OK.
- **Manual**: pendiente (escenarios A–E).
