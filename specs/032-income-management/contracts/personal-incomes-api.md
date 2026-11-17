# Contrato: ingresos personales y balance neto (032)

Base: `/api` (sin `/v1`). Todos los endpoints requieren la cookie HttpOnly `amano_auth` de una cuenta verificada. El dueño sale siempre de la sesión. Errores en ProblemDetail (RFC 9457), en español, con `properties.fields` para errores por campo.

Errores comunes: `401` sin sesión · `403` cuenta no verificada · `404` neutral (id inexistente, ajeno o no numérico; mismo cuerpo) · `429` exceso de solicitudes · `503` cotizaciones no disponibles.

## Catálogo de categorías

`SALARY`, `FREELANCE`, `SALE`, `REFUND`, `OTHER` (etiquetas en español en el frontend: Sueldo, Freelance, Venta, Reembolso, Otros).

## IncomeResponse

```json
{
  "id": 12, "amount": "850000.00", "currency": "ARS", "date": "2026-11-17",
  "category": "SALARY", "paymentMethod": "TRANSFER", "description": "Sueldo noviembre",
  "projected": false, "createdAt": "2026-11-17T12:00:00Z",
  "conversions": [{ "currency": "ARS", "amount": "850000.00" }]
}
```

`projected` = `date` posterior a hoy. `conversions` = equivalente en la moneda principal (y preferidas), como en gastos.

## POST /api/incomes

Cabecera obligatoria `Idempotency-Key: <uuid>`. Cuerpo:

| Campo | Obligatorio | Regla |
|-------|-------------|-------|
| `amount` | sí | Decimal > 0, hasta 2 decimales, dentro del tope |
| `currency` | sí | Código del catálogo `/api/currencies` |
| `date` | sí | `YYYY-MM-DD`; hasta 1 año en el futuro |
| `category` | sí | Una del catálogo |
| `paymentMethod` | sí | `CASH`, `TRANSFER`, `VIRTUAL_WALLET`, `OTHER` (medio de cobro) |
| `deviceTimeZone` | no | Zona IANA del dispositivo para validar "hoy"; UTC si falta o es inválida (como en gastos) |
| `description` | no | Se recorta; vacía = ausente; mismo largo máximo que en gastos |

- **201** `IncomeResponse`. Reenvío con misma clave y mismo cuerpo: mismo ingreso, sin duplicar (`200`).
- **400**: datos inválidos o faltantes (`amount`, `currency`, `date`, `category`, `paymentMethod`, `description`) o `Idempotency-Key` ausente/no UUID (`idempotencyKey`: "La cabecera Idempotency-Key debe ser un UUID válido").
- **422**: misma clave con cuerpo distinto (igual que gastos; `properties.code` como en `/api/expenses`).

Una moneda fuera del catálogo es `400` con `fields.currency`.

## GET /api/incomes/{id}

- **200** `IncomeResponse`. **404** `{ "title": "Ingreso no encontrado", "detail": "No encontramos ese ingreso" }`.

## Inmutabilidad

Los ingresos no se editan ni se eliminan: `PATCH`, `PUT` y `DELETE` sobre `/api/incomes/{id}` responden **405** (ProblemDetail, cabecera `Allow: GET`). Igual que los gastos personales.

## GET /api/incomes/search

Busca entre todos los ingresos propios y devuelve una página. Sin filtros lista todos.

| Parámetro | Obligatorio | Regla |
|-----------|-------------|-------|
| `q` | no | Se recorta; 2 a 100 caracteres si viene. Parcial, sin mayúsculas ni tildes, sobre descripción y nombre de categoría. `%` y `_` literales |
| `year` | no | 1 a 9999, por fecha del ingreso |
| `month` | no | 1 a 12; requiere `year` |
| `from`, `to` | no | `YYYY-MM-DD`, ambos incluidos; `from` ≤ `to`; se intersecan con año/mes |
| `page` | no | ≥ 1 (def. 1); si excede, devuelve la última |
| `size` | no | 1 a 50 (def. 20) |

**200**

```json
{
  "incomes": [ { "id": 12, "amount": "850000.00", "currency": "ARS", "date": "2026-11-17",
                 "category": "SALARY", "paymentMethod": "TRANSFER", "description": "Sueldo noviembre", "projected": false,
                 "createdAt": "2026-11-17T12:00:00Z", "conversions": [] } ],
  "page": 1, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Orden: `date DESC, id DESC`.

**400** (`properties.fields`): `q` ("Ingresá al menos 2 caracteres para buscar" / "La búsqueda admite hasta 100 caracteres"), `year` ("El año no es válido"), `month` ("El mes no es válido" / "Elegí un año para filtrar por mes"), `from`/`to` ("La fecha no es válida" / "La fecha desde no puede ser posterior a la fecha hasta"), `page`, `size`.

## GET /api/incomes/years

**200** `{ "years": [2026, 2024] }`: años con al menos un ingreso, del más reciente al más antiguo; puede ser `[]`. El año actual lo agrega el frontend.

## GET /api/balance

Balance neto personal del período. Parámetros opcionales: `year`, `month` (requiere `year`), `from`, `to` (mismas reglas que la búsqueda).

**200**

```json
{
  "currency": "ARS",
  "income": "850000.00",
  "projectedIncome": "120000.00",
  "expenses": "400000.00",
  "net": "450000.00"
}
```

- `currency`: moneda principal de la cuenta (ARS si no hay).
- Los ingresos proyectados se convierten con la cotización de su fecha o, si no existe, con la de hoy; si tampoco hay cotización de hoy, `projectedIncome` se omite de la respuesta (el `200` no se ve afectado).
- `income`: ingresos con fecha ≤ hoy (UTC); `projectedIncome`: con fecha > hoy (no entra en `net`); `expenses`: gastos personales ya registrados en el período; `net = income − expenses` (puede ser negativo).
- **400** parámetros inválidos. **503** cotización no disponible para un gasto o un ingreso efectivizado (sin balance parcial); un ingreso proyectado nunca provoca `503`.

## Sin cambios

`/api/expenses/**`, `/api/groups/**`, `/api/currencies` y `/api/currencies/quote` no cambian.
