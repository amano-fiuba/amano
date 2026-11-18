# Contrato: estadísticas de gastos (032)

Requiere la cookie de sesión de una cuenta verificada (sin sesión → `401`, como el
resto de `/api/expenses/**`). La persona sale siempre de la sesión. Los errores
usan ProblemDetail (RFC 7807) en español.

## GET /api/expenses/statistics

Estadísticas por categoría de un mes: gastos personales por su monto y gastos
grupales (de grupo y con amigos) por la parte de la persona, convertidos a la
moneda del cálculo y ya agregados.

| Parámetro | Obligatorio | Regla |
|-----------|-------------|-------|
| `year` | sí | Entero de 1 a 9999. Año de la fecha del gasto. |
| `month` | sí | Entero de 1 a 12. Mes de la fecha del gasto dentro de `year`. |

**200**

```json
{
  "year": 2026,
  "month": 11,
  "currency": "ARS",
  "total": "160000.00",
  "excludedCount": 0,
  "personal": {
    "total": "100000.00",
    "categories": [
      { "category": "HOUSING", "amount": "45000.00", "percentage": "45.0" },
      { "category": "FOOD", "amount": "35000.00", "percentage": "35.0" },
      { "category": "TRANSPORT", "amount": "20000.00", "percentage": "20.0" }
    ]
  },
  "group": {
    "total": "60000.00",
    "categories": [
      { "category": "FOOD", "amount": "30000.00", "percentage": "50.0" },
      { "category": "LEISURE", "amount": "30000.00", "percentage": "50.0" }
    ]
  },
  "years": [2026, 2024]
}
```

- `currency`: moneda del cálculo: la primera moneda preferida según el orden del catálogo (`Currency`), o `ARS` si no hay ninguna.
- `total`: `personal.total + group.total`.
- `excludedCount`: gastos personales más partes grupales que no se sumaron por falta de cotización.
- `personal` / `group`: `total` del tipo y `categories` con importe mayor que cero, ordenadas por `amount` descendente y, a igual importe, por el orden del enum `ExpenseCategory`. `percentage` = `amount / total * 100` del tipo, con un decimal. Sin gastos: `{ "total": "0.00", "categories": [] }`.
- Un gasto grupal sin categoría (anterior a 027) cuenta como `OTHER`.
- Los importes son cadenas con dos decimales y los porcentajes, cadenas con un decimal, ambos redondeados `HALF_UP` al final, sobre los valores sin redondear.
- `years`: años (por fecha del gasto) con al menos un gasto personal o una parte grupal de la persona, del más reciente al más antiguo. Puede ser `[]`. El año actual lo agrega el frontend.

**400** (`properties.fields`)

| Campo | Mensaje |
|-------|---------|
| `year` | `El año no es válido` (ausente, no numérico o fuera de 1..9999) |
| `month` | `El mes no es válido` (ausente, no numérico o fuera de 1..12) |
