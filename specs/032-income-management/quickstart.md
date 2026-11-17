# Quickstart: validar ingresos personales (032)

Guía para comprobar la feature de punta a punta. Detalle de campos y errores en [contracts/personal-incomes-api.md](./contracts/personal-incomes-api.md); esquema en [data-model.md](./data-model.md).

## Prerrequisitos

- Base y servicios levantados con Docker Compose (`compose.yaml`); el backend aplica Flyway al arrancar (incluye `V38`).
- Dos cuentas verificadas (A y B) y la cookie de sesión `amano_auth` de cada una (login por `/api/auth/login`).

## Pruebas automatizadas

```bash
cd backend && mvn verify          # incluye JaCoCo (umbral 85 %)
cd frontend && npm run test:coverage
```

Esperado: todo en verde y cobertura ≥ 85 % en ambos módulos.

## Escenarios manuales (curl)

Con `$K` un UUID nuevo y `-b cookies-A.txt` la sesión de A:

1. **Alta (US1)**: `POST /api/incomes` con `Idempotency-Key: $K` y cuerpo `{"amount":"850000.00","currency":"ARS","date":"<hoy>","category":"SALARY","paymentMethod":"TRANSFER"}` → `201`.
2. **Idempotencia**: repetir la misma llamada → `200` con el mismo `id`; con otro monto y la misma clave → `422` (`IDEMPOTENCY_KEY_REUSED`); sin cabecera o con una clave no UUID → `400`.
3. **Validación (US5)**: monto `0`, categoría inexistente, medio de cobro ausente o moneda fuera del catálogo → `400` con `properties.fields`.
4. **Consulta**: `GET /api/incomes/{id}` → `200`; con la sesión de B → `404` idéntico al de un id inexistente.
5. **Inmutabilidad (US3)**: `PATCH`, `PUT` y `DELETE` sobre `/api/incomes/{id}` → `405`.
6. **Búsqueda (US2)**: `GET /api/incomes/search?q=sueldo&year=<año>&page=1&size=20` y `GET /api/incomes/years`.
7. **Balance**: con gastos cargados, `GET /api/balance?year=<año>` → `net = income − expenses`; crear un ingreso con fecha futura → aparece en `projectedIncome` y no cambia `net` (US4).
8. **Otra moneda (US4)**: ingreso en USD → `conversions` y balance en la moneda principal.
9. **Seguridad**: llamada sin cookie → `401`; con cuenta no verificada → `403`.
10. **Grupos (SC-006)**: `GET /api/groups/{id}/balances` antes y después de crear ingresos → respuestas idénticas.

## Escenarios en la interfaz

- `/activity`: alternar **Gastos | Ingresos**; la tarjeta de balance se ve en ambas y se actualiza al registrar.
- "Nuevo ingreso" → `/incomes/new`: categorías de ingreso, "Medio de cobro", sin Tipo ni Frecuencia; con fecha futura aparece "Se contará en el balance a partir de esa fecha"; Guardar → resumen → Confirmar → snackbar.
- Detalle de un ingreso: solo lectura, sin editar ni eliminar; "Volver" conserva la búsqueda.
