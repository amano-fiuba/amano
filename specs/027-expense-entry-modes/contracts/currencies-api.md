# Contract: Catálogo de monedas y monedas aceptadas (027)

**Requisitos**: FR-011 a FR-014 | **Research**: R1–R3, R11

## `GET /api/currencies` (nuevo)

Catálogo para el diálogo "Ver más".

- **Autenticación**: cookie `amano_auth`. Sin sesión → `401`.
- **Parámetros**: ninguno. La búsqueda se hace en el cliente (≤ 32 entradas).

### Respuesta `200 OK`

```json
{
  "currencies": [
    { "code": "ARS", "bcraName": "PESO" },
    { "code": "BRL", "bcraName": "REAL" },
    { "code": "MXN", "bcraName": "PESO MEXICANO" }
  ]
}
```

| Campo | Descripción |
|---|---|
| `code` | Código ISO 4217 que acepta el resto de la API (constante de `Currency`) |
| `bcraName` | `denominacion` del BCRA, tal cual. El frontend muestra su propia etiqueta en español y usa esta como respaldo |

Orden: alfabético por `code`. Solo entran monedas que estén a la vez en `Maestros/Divisas` y en `Currency`.

### Otros códigos

| Caso | Código | Cuerpo |
|---|---|---|
| BCRA no responde y no hay catálogo en caché | `503` | `ProblemDetail`, `properties.code = CURRENCY_CATALOG_UNAVAILABLE`, mensaje "No se pudo obtener el catálogo de monedas; reintentá en unos minutos" |

Caché: en memoria, 24 h. Un catálogo vencido se sigue sirviendo si el BCRA falla al refrescarlo.

## `GET /api/currencies/quote?currency=&date=` (nuevo, FR-035)

Vista previa de cotización a pesos para el gasto personal (no hay grupo). Misma lógica, validaciones y errores que `GET /api/groups/{id}/exchange-quote` de 022, con destino fijo ARS.

- **Autenticación**: cookie `amano_auth`. Sin sesión → `401`.
- **Respuesta `200`**: `{ "rate": 281.63495900, "rateDate": "2026-11-05", "rateSource": "BCRA", "groupCurrency": "ARS" }`. Con `currency = ARS`: `rate = 1` y `rateSource = SAME_CURRENCY`, sin llamar al BCRA. (`groupCurrency` conserva el nombre del contrato de 022: es la moneda destino.)
- **Errores**: `400` en `currency` (falta o fuera del catálogo) o `date` (formato o fecha futura); `503 EXCHANGE_RATE_UNAVAILABLE` si no hay cotización.

## Campos `currency` en endpoints existentes (cambio aditivo)

Desde 027, los siguientes campos aceptan cualquier `code` del catálogo (32 monedas), no solo ARS, USD y EUR:

| Endpoint | Campo |
|---|---|
| `POST /api/expenses` (007) | `currency` |
| `POST /api/groups/{id}/expenses` (017/022) | `currency` |
| `GET /api/groups/{id}/exchange-quote` (022) | `currency` |
| `POST /api/users/me/friends/{friendId}/expenses` (027, nuevo) | `currency` |
| `PUT /api/users/me` (002/005) | `currencies[]` |

Mensajes: un código fuera del catálogo devuelve el `400` existente en ese campo. El texto "Las monedas deben ser ARS, USD o EUR" de `PUT /api/users/me` pasa a "Moneda no soportada".

**Sin cambios**: `primaryCurrency` de `POST /api/groups` y `PATCH /api/groups/{id}` sigue aceptando solo ARS, USD o EUR.

## Conversión en grupos

Sin cambios de forma respecto de [022 currency-conversion](../../022-group-debt-management/contracts/currency-conversion.md): misma respuesta, mismo `503 EXCHANGE_RATE_UNAVAILABLE`. Solo cambia que el par puede ser cualquier moneda del catálogo, cruzada contra ARS con la cotización del BCRA de la fecha del gasto.
