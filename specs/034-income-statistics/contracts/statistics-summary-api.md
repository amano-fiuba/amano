# Contrato: resumen de estadísticas (034)

Requiere la cookie de sesión de una cuenta verificada (sin sesión → `401`, como el
resto de las pantallas protegidas). La persona sale siempre de la sesión y no hay
parámetros de usuario. Los errores usan ProblemDetail (RFC 7807) en español.

Una sola respuesta alimenta la pantalla completa: los dos slides del carrusel de
gastos, el bloque de ingresos, el bloque comparativo y el selector de año. Si el
pedido falla, el frontend no muestra bloques parciales (FR-027).

## GET /api/stats/summary

| Parámetro | Obligatorio | Regla |
|-----------|-------------|-------|
| `year` | sí | Entero de 1 a 9999. Año de la fecha del movimiento. |
| `month` | sí | Entero de 1 a 12. Mes de la fecha del movimiento dentro de `year`. |

**200**

```json
{
  "year": 2026,
  "month": 11,
  "currency": "ARS",
  "expenses": {
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
    }
  },
  "income": {
    "total": "1000000.00",
    "excludedCount": 0,
    "categories": [
      { "category": "SALARY", "amount": "800000.00", "percentage": "80.0" },
      { "category": "FREELANCE", "amount": "150000.00", "percentage": "15.0" },
      { "category": "SALE", "amount": "50000.00", "percentage": "5.0" }
    ]
  },
  "comparison": {
    "income": "1000000.00",
    "expenses": "160000.00",
    "balance": "840000.00"
  },
  "years": [2026, 2025, 2024]
}
```

### Campos comunes

- `year`, `month`: período devuelto; coinciden con los parámetros válidos recibidos.
- `currency`: moneda del cálculo, una sola para gastos, ingresos y comparación: la
  primera moneda preferida según el orden del catálogo (`Currency`), o `ARS` si la
  persona no tiene ninguna (FR-009).
- `years`: todo año (por fecha del movimiento) con al menos un gasto personal, una
  parte de gasto grupal o un ingreso **efectivizado** de la persona, en orden
  descendente y sin repetir. Nunca incluye años con solo ingresos proyectados.
  Puede ser `[]`. El frontend agrega el año actual al selector, como en 032/031
  (FR-004, plan D8).
- Todos los importes son cadenas con dos decimales y todos los porcentajes,
  cadenas con un decimal, redondeados `HALF_UP` al final, sobre los valores sin
  redondear.

### `expenses`

- Reutiliza exactamente la agregación de `GET /api/expenses/statistics` (032): el
  total personal por su monto y la parte de la persona en gastos grupales y con
  amigos, con la cotización de la fecha de cada movimiento.
- `total`: `expenses.personal.total + expenses.group.total`, sumando los totales ya
  redondeados (plan D7).
- `excludedCount`: gastos personales más partes grupales que no se sumaron por
  falta de cotización.
- `personal` / `group`: `total` del tipo y `categories` con importe mayor que cero,
  ordenadas por `amount` descendente y, a igual importe, por el orden del enum
  `ExpenseCategory`. `percentage` = `amount / total` del tipo × 100. Sin gastos:
  `{ "total": "0.00", "categories": [] }`.
- Un gasto grupal sin categoría (anterior a 027) cuenta como `OTHER`.

### `income`

- Solo ingresos personales **efectivizados**: `income_date` dentro del período y no
  posterior a hoy en UTC. Los proyectados no se agregan ni se informan (FR-008).
- Cada ingreso en otra moneda se convierte con la cotización de su fecha de
  ingreso; uno ya en la moneda del cálculo se suma sin conversión (FR-010).
- `total`: suma de los importes convertidos de las categorías incluidas.
- `excludedCount`: ingresos que no se sumaron por falta de cotización. Un ingreso
  excluido no aparece en `categories` ni en `total` (FR-011).
- `categories`: solo categorías con importe mayor que cero, ordenadas por `amount`
  descendente y, a igual importe, por el orden del enum `IncomeCategory`
  (`SALARY`, `FREELANCE`, `SALE`, `REFUND`, `OTHER`). `percentage` = `amount /
  income.total` × 100 (FR-012, FR-013). Sin ingresos: `[]` y `total` `"0.00"`.

### `comparison`

- `income`: igual a `income.total`.
- `expenses`: igual a `expenses.total` (personal más la parte de gastos grupales y
  con amigos; FR-017).
- `balance`: `income - expenses`, calculado en `BigDecimal` a partir de esos dos
  totales **ya redondeados**, formateado a dos decimales con signo cuando es
  negativo (FR-016, SC-004).
- Se devuelve siempre, también cuando ambos totales son `"0.00"`; en ese caso la
  interfaz no dibuja la dona y muestra el estado de período vacío (FR-023).

### Ejemplos de casos límite

| Caso | Respuesta relevante |
|------|---------------------|
| Mes sin ingresos, con gastos por 160.000,00 | `income.total = "0.00"`, `income.categories = []`, `comparison.balance = "-160000.00"` |
| Mes sin gastos, con ingresos por 1.000.000,00 | `expenses.total = "0.00"`, ambos `categories` vacíos, `comparison.balance = "1000000.00"` |
| Ingreso proyectado en el período | No aparece en `income` ni en `comparison`; el mes puede quedar sin ingresos |
| Ingreso con cotización faltante | No suma en `income.total` ni en `comparison.income`; `income.excludedCount` aumenta |
| Año con solo ingresos proyectados | No aparece en `years`; el frontend solo lo muestra si es el año actual |

**400** (`properties.fields`)

| Campo | Mensaje |
|-------|---------|
| `year` | `El año no es válido` (ausente, no numérico o fuera de 1..9999) |
| `month` | `El mes no es válido` (ausente, no numérico o fuera de 1..12) |

**401**: sin sesión o cuenta no verificada, con el mismo cuerpo que el resto de las
rutas protegidas.

## Compatibilidad

`GET /api/expenses/statistics` (032) y `GET /api/balance` (033) no cambian: mismo
contrato, mismos importes y mismos años (FR-028, SC-006). Este endpoint es de solo
lectura y no crea, edita ni elimina movimientos (FR-029).
