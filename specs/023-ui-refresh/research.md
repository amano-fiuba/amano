# Research: Renovación de la interfaz (023)

**Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

## D1 — Mantener la estética actual

Se armaron varias propuestas de estética y se consultó a distintas personas. La decisión fue conservar la actual y sumarle detalles. Un cambio total no aportaba lo suficiente, y en una app de pagos y deudas importa que la interfaz transmita confianza.

## D2 — Teclas y patrón en `detalles.scss`

`detalles.scss` se importa después de `App` en `main.jsx`, así que sobrescribe los estilos de cada pantalla sin editarlos. Los selectores se agrupan en quietos, secundarios y primarios. Las acciones destructivas quedan afuera para que sigan en rojo.

El patrón es una máscara sobre `body::before`, y `.home::before` lo repite en la pantalla principal. Con máscara, el color sale de `--color-patron` en cada tema: cambiar la intensidad es cambiar un valor.

## D3 — Naranja para interacción, azul de base

El azul sigue siendo el color de la marca en la app, y el naranja del logo aparece al interactuar y en detalles. Sobre naranja el texto va oscuro, porque el blanco queda por debajo de 3:1. Se descartó una línea naranja bajo la barra: no aportaba y se veía como un borde suelto.

## D4 — La foto se abre en un solo sentido

Los ids de usuario son enumerables, así que una sesión verificada no alcanza para ver un avatar (`AvatarVisibility`). Quien recibe una solicitud necesita ver quién la envía. Lo inverso no vale: si enviar una solicitud diera acceso a la foto ajena, cualquiera podría verla mandando solicitudes.

Por eso se usa `existsBySenderIdAndRecipientIdAndStatus`, que distingue el sentido, y no `existsBetween`. Si la solicitud deja de estar pendiente y no hay otra relación, se vuelve a responder 404. Sí queda expuesta la foto de quien envía a la persona elegida por esa misma cuenta, lo cual es esperable.

`findIdsWithAvatar` resuelve las fotos de toda la bandeja en una sola consulta.

## D5 — Pantalla propia para la actividad personal

`GET /api/expenses/recent` solo trae los últimos gastos, así que hizo falta `GET /api/expenses`. Tiene un tope de 200 para no agregar paginación todavía. Incluir gastos de grupo y pagos queda fuera de alcance.

## D6 — Tres columnas con los datos existentes

No existe un endpoint de gastos por grupo. `GET /api/groups/{id}/activity` ya registra los gastos con su enlace, así que `GroupExpensesCard` y `GroupFeed` se arman con él. `GroupExpensesCard` no lo consulta si `expensesCount` es 0. Cada tarjeta maneja su error, de modo que una falla no tira la pantalla.

Las proporciones vienen de la pantalla principal (015) y la disposición se apila a partir de 960px.

## D7 — Saldos: componente listo, sin cálculo

Otra persona del equipo implementa saldos cruzados y conversión de cotizaciones, y los perfiles simulados participan. `MemberBalance` no calcula nada: muestra lo que reciba. `balances` es una lista para admitir un saldo por moneda ahora y uno solo cuando haya conversión, sin cambiar el frontend.

## D8 — Compatibilidad con 020 y 021

Revisado tras mezclar `sprint-4`:

- **020**: el aviso de último administrador solo se ve en el modal (FR-016). `AdminRolePanel` conserva el texto, pero ninguna pantalla lo usa.
- **021**: "Editar" y su formulario quedan dentro del encabezado nuevo, y el buscador sigue usando `friendshipStatus`.
- Las tablas de ambas specs no cambian: esta spec no agrega migraciones.

## D9 — Elegir personas de una lista al crear el grupo

Pedir el correo exacto obligaba a conocerlo de memoria, y el backend solo aceptaba un miembro. Se evaluó buscar por nombre y listar los amigos. La lista con casillas gana: no hay que recordar nada y se ve de un vistazo a quién se puede sumar. El filtro por nombre cubre a quien tiene muchos amigos, y se resuelve en el navegador porque `GET /api/users/me/friends` ya trae la lista completa.

No hace falta un endpoint nuevo. Para un amigo, `identifier` sigue siendo el correo, que ahora sale de la lista en lugar de escribirse a mano.

## D10 — `members` junto a `member`

Reemplazar `member` rompería el contrato de 010 y decenas de pruebas que lo usan como preparación. Un campo aditivo mantiene todo funcionando. Con los dos presentes vale `members`, para que la regla sea simple.

Todo se valida antes del primer `INSERT`, como ya hacía el código, de modo que un rechazo no depende del rollback. El correo de perfil simulado repetido se rechaza acá para dar un mensaje claro: de otro modo saltaría el índice único de `simulated_profiles` y sería un error 500.

## D11 — Fotos en el buscador con la regla vigente

Los resultados incluyen cuentas sin relación, a las que `GET /api/users/{id}/avatar` responde 404. Enviar `avatarUrl` a todos dejaría imágenes rotas. Abrir las fotos a cualquier sesión verificada cambiaría la decisión de 002 y D4: los ids y las búsquedas permiten recorrer cuentas. Por eso `avatarUrl` solo llega si quien busca ya puede ver esa foto.

`AvatarVisibility.visibleAmong` aplica la regla a una lista, y queda junto a `canView` para que las dos no se separen. Reemplaza una consulta por resultado por dos consultas para toda la página.

Los grupos no necesitan cambios en el backend: `GET /api/groups` ya trae `photo` y solo lista grupos propios.

## D12 — Documentar en una spec nueva

Sigue la costumbre de 018 y 021: las specs anteriores no se editan. La tabla de `spec.md` indica qué punto de 002, 008, 010 y 011 queda reemplazado.
