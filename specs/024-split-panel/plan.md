# Implementation Plan: Panel de reparto del gasto de grupo

**Branch**: `feat/group-debt-management` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/024-split-panel/spec.md`

## Summary

Spec retroactiva de un ajuste de frontend sobre la UI de reparto de 022 (T072, T077). El formulario "Nuevo gasto" pasa a dos columnas, con un panel de reparto a la derecha: interruptor "Gasto compartido", botones de método, una fila por participante con su control (slider + campo o importe) y su monto en una o dos monedas, un indicador de lo que falta y un botón para repartirlo en partes iguales. Después se compactó el panel: una línea por persona, nombre de pila, ícono de pago y columna de monto fija. Quedan abiertos el recorrido manual y una limpieza ([tasks.md](./tasks.md)).

## Technical Context

**Language/Version**: JavaScript con React 19.2 (frontend). Backend sin cambios.

**Primary Dependencies**: React, react-hook-form, react-router, Vite, SCSS. **Sin dependencias nuevas.**

**Storage**: sin cambios.

**Testing**: Vitest + Testing Library. Umbral de cobertura ≥ 85 %.

**Target Platform**: navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo; solo cambia el frontend.

**Constraints**: mismas reglas de cálculo que el backend (enteros, `BigInt`, piso al centavo); sin scroll horizontal a 360px; voseo; accesible con lector de pantalla.

## Constitution Check

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura | PASS | Se reutilizan `splitEqually`, `splitCustom`, `splitPercentage` y la cotización de 022; no hay lógica nueva de negocio en el backend |
| II | Desarrollo guiado por especificaciones | **Desvío** | El ajuste se hizo iterando con capturas antes de la spec, que lo regulariza |
| III | Entrega incremental | PASS con nota | Seis historias sobre la misma pantalla, entregadas juntas en la rama de 022 |
| IV | Alcance controlado | PASS | Sección "Fuera de alcance"; reglas de reparto sin cambios |
| V | Calidad y cobertura | PASS con nota | 970 tests en verde; falta el recorrido manual en navegador |
| VI | Integridad de reglas | PASS | Los montos por fila usan los mismos helpers que validan contra el backend |
| VII | Contratos REST | PASS | Sin cambios de contrato |
| VIII | Validación y errores | PASS | Mensajes de 022 sin cambios |
| IX | Seguridad | PASS | Sin cambios de acceso |
| X | Código mantenible | PASS | Estilos en `groups.scss`; el ícono en `NavIcons.jsx` |
| XI | Entorno reproducible | PASS con nota | Sin pasos nuevos; ver nota de permisos en research.md D7 |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS con desvío en II, justificado abajo.

## Project Structure

```text
frontend/src/
├── components/NavIcons.jsx                   # PaidIcon (billete)
└── pages/groups/
    ├── GroupExpenseFormView.jsx              # dos columnas, panel, filas, montos, "Repartir lo que falta"
    ├── GroupExpenseFormView.test.jsx         # tests adaptados y nuevos
    └── groups.scss                           # layout, interruptor, botones, slider, subgrid
```

Estado, valores derivados y unidades en [data-model.md](./data-model.md). No hay `contracts/`: el pedido y la respuesta de la API son los de 022.

Sin cambios en `splitEqually.js`, `splitCustom.js`, `splitPercentage.js`, `convertPreview.js` ni en el backend.

## Complexity Tracking

| Desvío | Por qué | Alternativa descartada |
|---|---|---|
| Spec posterior al código | El diseño se ajustó mirando capturas reales en cada iteración | Especificar antes: los problemas de espacio solo aparecieron con datos reales |
| Ajuste dentro de la rama de 022 | Toca la misma pantalla y los mismos tests que T072 y T077 | Rama aparte: conflictos en `GroupExpenseFormView.jsx` |
| `subgrid` de CSS | Alinea los sliders entre filas y deja que la columna del monto se adapte en conjunto | Grid por fila: cada slider medía distinto según su monto |
