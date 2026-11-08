# Research: Panel de reparto del gasto de grupo (024)

**Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

## D1 — Sliders independientes

Se evaluaron dos comportamientos para los porcentajes:

1. **Independientes con indicador**: cada slider se mueve solo y un indicador muestra lo que falta o sobra.
2. **Reacomodo automático**: subir uno baja los demás para mantener 100.

Se eligió el 1. Es predecible, coincide con los campos de texto de 022 y no cambia valores que la persona no tocó. El 2 funciona con 2 personas, pero con 4 o más los valores se mueven solos y confunde. El botón "Repartir lo que falta" (D4) cubre el caso que el 2 resolvía.

## D2 — Slider de a 1 % y campo con decimales

Un slider de a 0,01 % produce valores como 33,47 al arrastrar. El slider va de a 1 % y el campo de texto acepta dos decimales, como exige 022. Ambos editan el mismo valor; el navegador muestra el slider en el entero más cercano.

## D3 — Montos por fila con las reglas del backend

`ExpenseSharePreview` mostraba la división en una lista aparte. Ahora cada fila muestra su parte, calculada con los mismos helpers de 022:

- En la moneda del gasto: `splitEqually`, `customShares` o `percentageShares` sobre el monto original.
- En la moneda del grupo: los mismos helpers sobre el total convertido, como hace `GroupExpenseService.sharesOf`.

Mientras el reparto no cierra, las filas con valor muestran una estimación con piso al centavo, la misma regla que el backend aplica a quienes no pagaron. Cuando cierra, se usan las partes exactas, con el resto para quien pagó.

## D4 — "Repartir lo que falta"

El faltante se calcula en unidades enteras: centavos en Personalizado y centésimos de % en Porcentajes. Se reparte entre quienes no tienen un valor válido; si todos tienen, entre todos. Las unidades que sobran van a los primeros de la lista, para que el resultado sea predecible. Los importes se escriben sin separador de miles ("33,34"), que `parseArgentineAmount` acepta.

## D5 — Panel compacto

La primera versión usaba dos líneas por persona y textos de ayuda, y en un grupo de 6 el panel ocupaba casi toda la pantalla. Se cambió a:

- **Una línea por persona**: nombre, control y monto.
- **Textos de ayuda**: "Quien pagó siempre participa del reparto" pasa a tooltip; "Ingresá el monto…" se elimina; las leyendas quedan solo para lectores de pantalla.
- **Nombre de pila**: el resto del nombre y "(perfil simulado)" quedan en un `span` oculto visualmente, precedido por un espacio fuera del `span`. Sin ese espacio, el nombre accesible quedaba "JuanPérez".
- **Ícono de billete** en lugar de "(pagó)".

## D6 — Columnas compartidas con `subgrid`

Con un grid por fila, cada slider medía distinto según el monto de su fila, y los montos grandes los achicaban. La lista define las columnas y cada fila es un `subgrid`:

- Nombre: `fit-content(7.5rem)`, se ajusta al nombre más largo.
- Control: el resto del ancho.
- Monto: fijo de 9,5rem en Porcentajes; `auto` en Personalizado, donde con la moneda del grupo no hay montos y la columna no debe ocupar lugar.

## D7 — Nota de entorno

En la máquina de desarrollo, `frontend/node_modules/.vite-temp` y `frontend/dist` pertenecen a `root`, y `npm test` y `npm run build` fallan por permisos. Se verificó con `npx vitest run --configLoader runner` y un `--outDir` temporal. No afecta a CI.
