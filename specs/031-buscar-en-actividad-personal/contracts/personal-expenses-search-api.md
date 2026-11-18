# Contrato: búsqueda y detalle de gastos personales (031)

Todos los endpoints requieren la cookie de sesión de una cuenta verificada
(sin sesión → `401`, como el resto de `/api/expenses/**`). El dueño sale siempre
de la sesión. Los errores usan ProblemDetail (RFC 7807) en español.
`GET /api/expenses` **se elimina** (sesión 4, FR-024): la pantalla de actividad usa `/api/expenses/search`. `POST /api/expenses` no cambia; un `GET` a esa ruta pasa a responder `405`.

## GET /api/expenses/search

Busca entre todos los gastos personales de la persona y devuelve una página. Sin `q`, lista todos los gastos del año (y mes) pedido, o de todos los años: es lo que muestra la actividad sin texto.

| Parámetro | Obligatorio | Regla |
|-----------|-------------|-------|
| `q` | no | Se recorta; vacío o ausente = sin filtro de texto. Si viene, 2 a 100 caracteres. Coincidencia parcial, sin mayúsculas ni tildes, en descripción, etiqueta de categoría o etiqueta de medio de pago. `%` y `_` son literales. |
| `year` | no | Entero de 1 a 9999. Filtra por año de la fecha del gasto. Ausente = todos los años. |
| `month` | no | Entero de 1 a 12. Filtra por mes de la fecha del gasto dentro de `year`. Requiere `year`. |
| `page` | no | Entero ≥ 1 (por defecto 1). Si supera la cantidad de páginas, se devuelve la última. |
| `size` | no | Entero de 1 a 50 (por defecto 20). El frontend lo calcula según el alto de la pantalla (5 a 20). |

**200**

```json
{
  "expenses": [
    {
      "id": 41, "amount": "3500.00", "currency": "ARS", "date": "2026-10-03",
      "category": "HEALTH", "type": "ONE_TIME", "paymentMethod": "CASH",
      "description": "Farmacia centro", "createdAt": "2026-10-03T14:20:00Z",
      "conversions": [{ "currency": "USD", "amount": "2.50" }]
    }
  ],
  "page": 1,
  "size": 9,
  "totalElements": 1,
  "totalPages": 1
}
```

- `expenses`: los gastos de la página, orden `createdAt DESC, id DESC`, con `conversions` a las monedas preferidas.
- `page`: página devuelta (1 si no hay gastos; la última si la pedida no existe).
- `size`: tamaño de página aplicado.
- `totalElements`: total de gastos que cumplen el criterio. `totalPages`: páginas en total (0 si no hay gastos).

**400** (`properties.fields`)

| Campo | Mensaje |
|-------|---------|
| `q` | `Ingresá al menos 2 caracteres para buscar` (1 carácter tras recortar) |
| `q` | `La búsqueda admite hasta 100 caracteres` |
| `year` | `El año no es válido` |
| `month` | `El mes no es válido` |
| `month` | `Elegí un año para filtrar por mes` (mes sin `year`) |
| `page` | `La página no es válida` |
| `size` | `El tamaño de página no es válido` |

## GET /api/expenses/years

**200**: `{ "years": [2026, 2024] }`: años (por fecha del gasto) con al menos un
gasto de la persona, de más reciente a más antiguo. Puede ser `[]`. El año
actual lo agrega el frontend.

## GET /api/expenses/{id}

**200**: `ExpenseResponse` del gasto, con `conversions` a las monedas preferidas
(igual que en la actividad) y, si es recurrente, `frequency`, `status` y
`nextOccurrenceDate`.

**404**: `{ "title": "Gasto no encontrado", "detail": "No encontramos ese gasto" }`
para un id inexistente, ajeno o no numérico (no revela si existe).
