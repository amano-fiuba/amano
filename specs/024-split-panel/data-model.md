# Data Model: Panel de reparto del gasto de grupo (024)

**Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Sin migraciones, tablas nuevas ni cambios de contrato.**

## Representaciones

### CreateGroupExpenseRequest (existente — 017, 022)

Sin cambios. El panel arma el mismo pedido a `POST /api/groups/{id}/expenses`:

| Campo | Origen en el panel |
|---|---|
| `splitMethod` | Botón elegido; `EQUAL` si "Gasto compartido" está apagado |
| `participants` | Filas tildadas, en el orden de la lista. Con el interruptor apagado, solo quien pagó |
| `participants[].amount` | Campo de la fila en Personalizado |
| `participants[].percentage` | Campo de la fila en Porcentajes (el slider escribe en el mismo valor) |

### GroupExpenseResponse (existente — 022)

Sin cambios. Las partes que guarda el backend coinciden con las que muestra el panel (SC-005).

## Estado del formulario

No se persiste: vive en `GroupExpenseFormView.jsx` mientras la pantalla está abierta.

| Estado | Valor inicial | Notas |
|---|---|---|
| `shared` | `true` | Reemplaza a `personal` de 022 T077; `personal = !shared` |
| `splitMethod` | `EQUAL` | Se ignora con `shared` en `false` |
| `selected` | Todos los miembros | Quien pagó se incluye siempre en Personalizado y Porcentajes |
| `splitValues` | `{ CUSTOM: {}, PERCENTAGE: {} }` | Texto escrito por método y participante; se conserva al cambiar de método |

## Valores derivados

Se recalculan en cada render con los helpers de 022; ninguno se guarda.

| Valor | Cálculo | Uso |
|---|---|---|
| `originalShares` | `splitEqually`, `customShares` o `percentageShares` sobre el monto original | Monto de cada fila en la moneda del gasto |
| `convertedShares` | Los mismos helpers sobre el total convertido | "≈" en la moneda del grupo |
| `rowAmount` | Parte exacta si el reparto cierra; si no, piso de `total × parte / entero` | Estimación en vivo por fila |
| `missing` | `differenceCents` o `differenceHundredths` | Indicador y "Repartir lo que falta" |
| `shortNames` | Primera palabra del nombre; el nombre completo si otro miembro tiene la misma | Nombre visible de cada fila |

## Unidades

| Método | Unidad entera | Ejemplo |
|---|---|---|
| Personalizado | Centavos | $ 33,34 → 3334 |
| Porcentajes | Centésimos de % | 33,33 % → 3333 |

"Repartir lo que falta" opera en estas unidades y escribe el resultado como texto: `33,34` (sin separador de miles) o `33,33`.

## Variables de diseño

No son datos persistidos, pero son el punto de ajuste del panel (`groups.scss`).

| Valor | Uso |
|---|---|
| `max-width: 1040px` | Ancho de la página con dos columnas (`.group-expense--wide`) |
| 820px | Desde ese ancho, dos columnas |
| 560px | Por debajo, el nombre va arriba del control |
| `fit-content(7.5rem)` | Columna del nombre |
| 9,5rem | Columna fija del monto en Porcentajes |
| 3,4rem | Campo del porcentaje |
| `--fill` | Parte coloreada del slider en navegadores WebKit |
