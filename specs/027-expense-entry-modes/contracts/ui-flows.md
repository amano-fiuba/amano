# Contract: Flujos y componentes de UI (027)

**Requisitos**: FR-001 a FR-004, FR-010 a FR-013, FR-018 a FR-024, FR-030, FR-031 | **Research**: R6–R11

## Rutas

| Ruta | Pantalla | Estado |
|---|---|---|
| `/expenses/new` | Gasto personal (`NewExpensePage` + `ExpenseForm`) | Existente, modificada |
| `/groups/:id/expenses/new` | Gasto de grupo (`GroupExpenseFormView` → `SharedExpenseForm`) | Existente, modificada |
| `/friends/:friendId/expenses/new` | Gasto con amigo (`FriendExpenseFormView` → `SharedExpenseForm`) | Nueva, protegida |

Al guardar, cada flujo vuelve a donde estaba: personal → `/`, grupo → `/groups/:id`, amigo → `/` con snackbar "Gasto registrado con {nombre}". "Cancelar" vuelve atrás (`navigate(-1)`, o `/` si no hay historial).

## `QuickActionsButton` (modificado)

```text
[+] ─► ┌───────────────────────┐
       │ Crear grupo           │
       │ Registrar gasto     ▸ │ ─► ┌──────────────────────┐
       │ Gastos recurrentes    │    │ 👤 Gasto personal     │
       └───────────────────────┘    │ 🤝 Gasto con amigo    │
                                    │ 👥 Gasto grupal       │
                                    └──────────────────────┘
```

- `role="menu"`; "Registrar gasto" es `menuitem` con `aria-haspopup="menu"` y `aria-expanded`.
- Abre: hover (cierre con retardo ~150 ms), clic/toque, `→` o `Enter`. Cierra: `←`, `Escape` (vuelve el foco al ítem), clic fuera, elegir opción.
- Si a la derecha no hay espacio (`getBoundingClientRect`), el submenú se ubica arriba/izquierda; en móvil se muestra en lugar de la lista principal con un ítem "‹ Volver".
- Íconos nuevos en `NavIcons.jsx` (SVG inline, como los existentes).

## `GroupChooserDialog` (nuevo)

Modal "¿En qué grupo?" con los grupos activos del usuario (`listGroups`) y buscador. Cada grupo se muestra como en "Mis grupos": foto (o inicial), nombre, cantidad de miembros y la flecha "›"; tocarlo lleva a `/groups/:id/expenses/new`. Un grupo con `canCreateExpenses = false` queda grisado, sin flecha ni enlace, con "No podés registrar gastos en este grupo". Sin grupos: "Todavía no formás parte de ningún grupo" + enlace "Crear grupo". Solo hay botón "Cancelar".

## `FriendChooserDialog` (nuevo)

Modal basado en la lógica de búsqueda de `FriendPicker` (se extrae la normalización y el renderizado de fila para reutilizarlos), en modo **selección única** (radio). Botón "Continuar" deshabilitado hasta elegir. Sin amigos: `NO_FRIENDS_MESSAGE` + enlace a `/users/search`. Al confirmar → `/friends/:friendId/expenses/new`.

## `DatePickerField` (nuevo)

```text
Fecha
┌──────────────────────────┬────┐
│ 08/11/2026               │ 📅 │
└──────────────────────────┴────┘
   ┌─────────────────────────┐
   │ ‹   noviembre 2026    › │
   │ lu ma mi ju vi sá do    │
   │                    1    │
   │ 2  3  4  5  6  7  8●    │
   │ ...                     │
   └─────────────────────────┘
```

- Props: `name`, `value` (ISO `YYYY-MM-DD`), `onChange`, `max`, `error`, `label`. Compatible con `register`/`Controller` de react-hook-form.
- El texto acepta `dd/mm/aaaa`; el valor del formulario sigue siendo ISO (como hoy).
- Popover `role="dialog"` con grilla `role="grid"`; semana empieza el lunes; días posteriores a `max` deshabilitados; hoy marcado; el seleccionado resaltado.
- Teclado: flechas mueven el día, `PageUp/PageDown` cambian de mes, `Enter` elige, `Escape` cierra y devuelve el foco al botón.

## `CurrencySelect` + `CurrencyCatalogDialog` (nuevos)

- Opciones: preferidas del usuario (orden de perfil; ARS si no hay) → moneda actual si no es preferida → "Ver más…" (sin separador).
- "Ver más…" no cambia el valor: abre el diálogo; al confirmar, la moneda elegida se agrega como opción temporal y queda seleccionada; al cancelar, se mantiene la anterior.
- Diálogo: buscador (código o nombre, sin acentos), lista con radio, estados *Cargando*, *Error + Reintentar* (`503`) y *Sin resultados*.
- En grupos, elegir una moneda ≠ moneda del grupo dispara la vista previa de cotización de 022 como hoy.

## `ExpenseForm` (gasto personal, modificado)

```text
┌ page (fondo blanco) ─────────────────────────────┐
│ Nuevo gasto                                       │
│ ┌ group-card (celeste) ─────────────────────────┐ │
│ │ Monto / Moneda / Fecha / Categoría / Tipo /   │ │
│ │ [Frecuencia] / Medio de pago / Descripción    │ │
│ └───────────────────────────────────────────────┘ │
│ [Cancelar]                              [Guardar] │
└───────────────────────────────────────────────────┘
```

Sin interruptor "Gasto compartido" ni panel de reparto. Con una moneda distinta de ARS y monto y fecha válidos, debajo de la fecha aparece "Monto estimado: $ 100,00 USD ≈ $ 154.550,00 ARS" con el aviso "Estimación con la cotización oficial del BCRA. El gasto se guarda en su moneda." (FR-035, `GET /api/currencies/quote`, debounce 400 ms); si falla, un aviso que no bloquea el guardado. "Cancelar" con el formulario `isDirty` → `confirm` ("¿Descartar el gasto? Se perderán los datos cargados").

## `SharedExpenseForm` (grupo y amigo)

```text
┌ main (celeste) ────────┐ ┌ split (celeste) ─────────────┐
│ Monto                  │ │ [●] Gasto compartido (solo   │
│ Moneda                 │ │      en grupo)               │
│ Fecha                  │ │ [Iguales][Personal.][%]      │
│ Categoría              │ │ ┌ filas (scroll) ──────────┐ │
│ Tipo de gasto          │ │ │ fila altura fija         │ │
│ Medio de pago          │ │ │ ...                      │ │
│ Descripción            │ │ └──────────────────────────┘ │
│ ¿Quién pagó?           │ │                              │
└────────────────────────┘ └──────────────────────────────┘
[Cancelar]                                        [Guardar]
```

- La altura de la grilla la fija la columna izquierda; el panel derecho tiene `min-height: 0` y la lista de filas `overflow-y: auto`.
- Fila de miembro de altura igual en los tres métodos (variable CSS `--split-row-height`).
- En modo amigo: sin interruptor "Gasto compartido", participantes fijos (usuario y amigo, ambos tildados y no destildables en "Partes iguales"), pagador elegible entre los dos, sin vista previa de cotización.
- Móvil (`< 768px`): columnas apiladas, sin altura máxima.

## `ExpenseSummaryDialog` (nuevo, FR-036)

"Guardar" en el alta de cualquiera de los tres formularios abre "Confirmá el gasto": filas etiqueta/valor (Grupo o Con, Monto, Estimado en la otra moneda si hay cotización, Moneda, Fecha, Categoría, Tipo de gasto, Frecuencia si es recurrente, Medio de pago, Descripción, ¿Quién pagó?, Reparto) y, si se reparte, "Parte de cada uno". "Confirmar" (enfocado al abrir) guarda; "Volver" o Escape cierran sin guardar y sin perder lo cargado. Mientras guarda, ambos botones quedan deshabilitados.

En "¿En qué grupo?", el aviso "No podés registrar gastos en este grupo" va en su propio renglón, debajo de la cantidad de miembros.
