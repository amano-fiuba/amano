# Feature Specification: Bandeja completa y configuración de notificaciones in-app

**Feature Branch**: `feat/in-app-notifications`

**Created**: 2026-11-23

**Status**: Draft

**Input**: User description: "Crear la especificación funcional y técnica para la feature de 'Bandeja Completa y Configuración de Notificaciones In-App'. La spec debe definir el popover rápido de la barra superior, la vista dedicada de notificaciones y la gestión de preferencias del usuario: (1) popover de la campana con notificaciones recientes, contador de badge y enlace a `/notifications`; (2) página dedicada con historial, diferenciación leídas/no leídas, marcado individual y global, y redirección contextual (grupo, deuda/transacción o amigos) marcando la notificación como leída; (3) configuración de canales (In-App y correo electrónico) y de preferencias por tipo de evento (recordatorios/silbatos, vencimiento de deudas, actividad de grupo y actividad social), persistidas en `user_notification_preferences`; (4) endpoints REST de listado, marcado y preferencias, con resiliencia ante fallos de guardado en BD o de envío de correo; (5) historias de usuario, criterios de aceptación con IDs y matriz de casos límite."

## Relación con otras especificaciones

- **Extiende la barra superior de navegación** de 015/023 con un ícono de campana que abre un popover rápido y muestra un contador de notificaciones no leídas. No cambia la navegación existente ni los demás íconos del encabezado.
- **Reutiliza los eventos de negocio ya especificados**: recordatorios y silbato de cobro (025 y 030), alta de gastos y pagos en grupos (010, 020, 022), vencimientos y estados de deudas (017), solicitudes de amistad y su respuesta (011, 012) y actividad social (013, 016). La feature **no modifica las reglas de negocio** de esas historias: solo genera, persiste y presenta avisos derivados de ellas.
- **Reutiliza el sistema de correo existente** para el canal de correo electrónico (verificación de cuenta 003 y recuperación de contraseña 006, y los avisos por correo ya presentes en deudas y silbatos). No introduce un proveedor de correo nuevo.
- **Reutiliza la autenticación y autorización existentes** (001, 004): solo una persona con sesión iniciada y cuenta verificada tiene bandeja, popover y preferencias; las notificaciones de otras personas nunca se muestran ni se modifican.
- **Agrega rutas nuevas** `/notifications` y una configuración de preferencias accesible desde esa página y desde el popover. Las rutas destino de los enlaces profundos (`/groups/{groupId}`, la vista de deudas/transacciones y `/friends`) ya existen y no cambian de forma.
- **No modifica** el balance (033), las estadísticas (032, 034), los saldos, presupuestos ni propuestas de liquidación de grupos.

## Fuera de alcance

- Notificaciones push del navegador o móviles, SMS, WhatsApp ni otros proveedores externos de mensajería.
- Borrado, archivado, ocultamiento individual o "deshacer" el marcado como leída; las notificaciones son de solo lectura salvo el marcado de lectura.
- Edición, agrupación o resumen de notificaciones por remitente, ni hilos de conversación.
- Actualización en tiempo real mediante conexiones persistentes: la bandeja y el popover se actualizan al abrirlos y después de cada acción.
- Reintentos automáticos o cola de reenvío de correos fallidos: un fallo de SMTP se registra y no interrumpe la operación de negocio.
- Cambios en las reglas de vencimiento, cobro, silbato, gastos grupales, pagos o amistad; la feature solo informa sobre ellas.
- Configuración de horarios de silencio, frecuencia de resumen diario ni preferencias por grupo o por persona.
- Una página de preferencias independiente: la configuración se abre como modal/diálogo desde la bandeja y desde el popover.
- Internacionalización: los textos visibles siguen el idioma del proyecto (español).

## Clarificaciones

### Sesión 2026-11-23

Las decisiones siguientes se resolvieron con valores por defecto razonables a partir del backlog y de las convenciones del repositorio; se registran como **supuestos documentados**, no como requisitos confirmados por el usuario:

- Q: ¿Cuántas notificaciones muestra el popover? → A: hasta **5**, ordenadas con las no leídas primero y, dentro de cada grupo, de la más reciente a la más antigua. El contador de la campana muestra el total de no leídas, con "9+" cuando supere nueve.
- Q: ¿Qué significa desactivar el canal In-App? → A: no se agregan **nuevas** entradas a la bandeja ni sube el contador; las notificaciones ya generadas siguen consultables en el historial. No se borra nada.
- Q: ¿Qué pasa si se desactiva un tipo de evento? → A: no se generan notificaciones de ese tipo en **ningún** canal. El canal de correo puede estar activo, pero sin eventos habilitados no hay correos de notificación.
- Q: ¿Se duplican los correos de recordatorios y silbatos? → A: No. Esos flujos ya envían su correo de negocio con sus reglas actuales (025, 030) y el subsistema de notificaciones no envía una segunda copia. El interruptor de correo de preferencias controla los **correos de notificación nuevos**, no los correos de negocio existentes.
- Q: ¿Los valores iniciales de las preferencias? → A: ambos canales y los cuatro tipos de evento activos para cada persona, creados la primera vez que se consultan o se guardan.
- Q: ¿Qué ocurre si la notificación apunta a un grupo, deuda o persona que ya no existe? → A: la notificación se marca como leída, se navega a la sección general correspondiente y se muestra un aviso no bloqueante indicando que el destino ya no está disponible.
- Q: ¿Se elimina una notificación al abrirla? → A: no; solo cambia a leída y permanece en el historial.
- Q: ¿Cómo se pagina el historial? → A: orden descendente por fecha, con un límite por pedido (20 por defecto, hasta 100) y un cursor para cargar más; el popover pide solo las 5 más recientes.

## Supuestos

- El destinatario de cada notificación es una persona autenticada y verificada; no existen notificaciones "globales" ni dirigidas a todos los usuarios.
- El contador de la campana es el número de notificaciones **no leídas** del usuario, nunca el total de la bandeja.
- "No leída" y "leída" son los únicos estados; no hay estado "enviada", "pendiente" ni "fallida" visible para la persona.
- Cada notificación guarda, además del texto, la información necesaria para resolver su destino (identificador de grupo, deuda/transacción o solicitud de amistad) y su tipo de evento, de modo que el enlace profundo no dependa de interpretar el texto.
- El orden por defecto del historial y del popover es el de recepción más reciente primero; en el popover, las no leídas tienen prioridad sobre las leídas.
- Las diferencias visuales entre leídas y no leídas incluyen siempre una señal textual o estructural (por ejemplo, etiqueta "No leída" y estado accesible), además de cualquier diferencia de color, peso u opacidad.
- El marcado individual y el marcado global afectan únicamente a las notificaciones del usuario autenticado; un identificador ajeno o inexistente se trata como "no encontrada" sin revelar si existe.
- Las preferencias se aplican **al generar** cada notificación: un evento habilitado genera una entrada en la bandeja solo si el canal In-App está activo y un correo solo si el canal de correo está activo.
- Un fallo al guardar una notificación o al enviar un correo se considera un efecto secundario: la operación de negocio que lo originó (carga de gasto, registro de pago, silbato, vencimiento, solicitud de amistad, etc.) se completa y responde con éxito, y el fallo queda registrado para el equipo técnico sin mensajes de error internos para la persona.
- La persistencia de preferencias es por persona, con una única configuración vigente; no hay configuración por dispositivo ni por sesión.
- La bandeja conserva todo el historial mientras la cuenta exista; no se define período de retención en esta feature.
- Los textos, estados vacíos, mensajes de error y etiquetas accesibles están en español, consistentes con el resto de la aplicación.
- La bandeja y el popover se actualizan al abrirse y tras cada acción; no se requieren actualizaciones en tiempo real.
- El límite del popover es cinco notificaciones con tope de "9+" en el contador; el historial se muestra en páginas de hasta 20 entradas (máximo 100 por pedido).
- Los valores por defecto de las preferencias son canales y tipos de evento activos, creados la primera vez que se consultan o se guardan.
- Un fallo de SMTP o de guardado de notificaciones se registra y se tolera; no se implementan reintentos ni colas en esta feature.
- Los correos de negocio ya existentes (verificación, recuperación de contraseña, recordatorios y silbatos) conservan su comportamiento y no se duplican: el canal de correo de estas preferencias gobierna solo los correos de notificación nuevos.
- Las rutas destino (`/groups/{groupId}`, `/groups/{groupId}/debts`, `/activity` y `/friends`) ya existen y se reutilizan; la deuda se señala mediante un parámetro de consulta, sin cambiar la forma de esas rutas. En el código la ruta del grupo usa el parámetro `:id`, que corresponde al `groupId` del enunciado.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver avisos rápidos desde la campana del encabezado (Priority: P1)

Lucía está en cualquier pantalla de la aplicación y ve una campana en la barra superior con un número que indica cuántos avisos tiene sin leer. La pulsa: se abre un cuadro flotante con los cinco avisos más recientes, primero los no leídos. Cada aviso muestra un título breve, un texto descriptivo y cuándo llegó. Al pie del cuadro hay un enlace destacado "Ver todas las notificaciones" que la lleva a la bandeja completa.

**Why this priority**: es el punto de entrada de toda la feature y el acceso más rápido al estado de las notificaciones; sin él, la persona no descubre que tiene avisos nuevos.

**Independent Test**: con una cuenta que tiene siete notificaciones (cuatro sin leer), abrir cualquier pantalla, comprobar el contador "4" en la campana, pulsar la campana y verificar que el popover muestra cinco avisos ordenados (no leídos primero), que el popover no altera el estado de la pantalla de origen y que el enlace del pie abre `/notifications`.

**Acceptance Scenarios**:

1. **Given** una persona con sesión iniciada y cuatro notificaciones no leídas, **When** mira la barra superior, **Then** la campana muestra el contador "4" junto al ícono.
2. **Given** la campana con el contador, **When** la persona la pulsa con mouse, toque o teclado, **Then** se abre un popover con hasta cinco notificaciones: primero las no leídas y dentro de cada grupo la más reciente primero, con título, texto y fecha relativa legible.
3. **Given** más de nueve notificaciones no leídas, **When** se muestra la campana, **Then** el contador se muestra como "9+" y el popover sigue mostrando solo cinco avisos.
4. **Given** ninguna notificación, **When** la persona pulsa la campana, **Then** el contador no aparece y el popover muestra un mensaje vacío como "No tenés notificaciones" sin listas vacías ni errores.
5. **Given** el popover abierto, **When** la persona pulsa "Ver todas las notificaciones", **Then** se navega a la bandeja dedicada `/notifications` y el popover se cierra.
6. **Given** el popover abierto, **When** la persona pulsa fuera del popover o Escape, **Then** el popover se cierra sin navegar ni marcar nada como leído.
7. **Given** un lector de pantalla, **When** recorre la campana y el popover, **Then** oye el número de no leídas, la lista de avisos y el enlace a la bandeja con nombres accesibles.
8. **Given** una persona sin sesión, **When** intenta abrir la bandeja desde el enlace, **Then** se aplica el mismo comportamiento de acceso protegido que al resto de las pantallas (redirección al acceso).

---

### User Story 2 - Gestionar el historial en la página de notificaciones (Priority: P1)

Lucía abre la bandeja para revisar todos sus avisos. Ve una pantalla completa con el historial ordenado de más reciente a más antiguo. Puede filtrar entre "Todas" y "No leídas": los avisos no leídos se distinguen claramente de los leídos. Puede marcar un aviso como leído con una acción explícita o usar "Marcar todas como leídas" cuando ya revisó todo.

**Why this priority**: cubre la consulta del historial y el control del estado de lectura, que es la base para que el contador de la campana tenga sentido y para que la persona pueda "ponerse al día".

**Independent Test**: con diez notificaciones (seis no leídas), abrir `/notifications`, contar los diez elementos, filtrar "No leídas" (seis), marcar una como leída (cinco en el filtro y contador de campana actualizado), ejecutar "Marcar todas como leídas" (cero no leídas) y recargar para comprobar que el estado persiste.

**Acceptance Scenarios**:

1. **Given** una persona con sesión iniciada, **When** abre `/notifications`, **Then** ve el historial completo de sus notificaciones, ordenado por fecha descendente, sin notificaciones de otras personas.
2. **Given** notificaciones leídas y no leídas, **When** se muestra la lista, **Then** las no leídas se distinguen de las leídas por una etiqueta o indicador accesible y por un tratamiento visual, sin depender solo del color ni de la opacidad.
3. **Given** las pestañas o filtros "Todas" y "No leídas", **When** se elige "No leídas", **Then** la lista muestra únicamente las no leídas y el contador de resultados coincide con el de la campana; al elegir "Todas" vuelve el historial completo.
4. **Given** una notificación no leída, **When** la persona pulsa su acción "Marcar como leída", **Then** la notificación pasa a leída, desaparece del filtro "No leídas" y el contador de la campana baja en uno sin recargar la página.
5. **Given** una notificación ya leída, **When** se muestra la lista, **Then** no se ofrece la acción "Marcar como leída" para esa notificación.
6. **Given** notificaciones no leídas, **When** la persona pulsa "Marcar todas como leídas", **Then** todas pasan a leídas, el contador de la campana queda en cero y la lista del filtro "No leídas" queda vacía con su mensaje correspondiente.
7. **Given** más notificaciones que el tamaño de página, **When** la persona llega al final de la lista o pulsa "Cargar más", **Then** se muestran las siguientes en orden descendente, sin duplicados ni saltos.
8. **Given** una bandeja vacía, **When** se abre la página, **Then** se muestra un mensaje vacío y las acciones de marcado no están disponibles.
9. **Given** un error de red o del servidor al cargar la bandeja, **When** falla la consulta, **Then** se muestra un mensaje de error en español con una acción de reintento, sin datos parciales.
10. **Given** la página cargada, **When** se usa el teclado o un lector de pantalla, **Then** los filtros, las acciones de marcado y el estado leído/no leído tienen nombres y estados accesibles.

---

### User Story 3 - Ir al contexto de una notificación y marcarla como leída (Priority: P1)

Lucía recibe un aviso de un gasto de su grupo, otro sobre una deuda que vence y otro de una solicitud de amistad. Desde el popover o desde la bandeja pulsa cada uno: la notificación se marca como leída automáticamente y la aplicación la lleva al lugar correcto: la vista del grupo, el detalle de la deuda o la sección de amigos/solicitudes. Si el destino ya no existe, ve un aviso y no se queda en una pantalla rota.

**Why this priority**: es el valor central del enlace profundo: convierte un aviso en una acción concreta. Sin el marcado automático, la persona tendría que marcar a mano cada aviso que abre.

**Independent Test**: crear notificaciones de tres tipos distintos, abrirlas una por una desde el popover y desde la bandeja, verificar la ruta destino y que cada una queda leída; repetir con un grupo y una deuda eliminados y comprobar el aviso de destino inexistente.

**Acceptance Scenarios**:

1. **Given** una notificación de recordatorio, silbato o gasto de un grupo, **When** se pulsa, **Then** se marca como leída y se navega a la vista del grupo correspondiente (`/groups/{groupId}`).
2. **Given** una notificación de deuda, vencimiento o pago personal con grupo asociado, **When** se pulsa, **Then** se marca como leída y se navega a la pestaña de deudas del grupo (`/groups/{groupId}/debts`) con esa deuda señalada; si la deuda no pertenece a un grupo, se navega a la actividad personal (`/activity`) con la deuda señalada.
3. **Given** una notificación de solicitud de amistad o actividad social, **When** se pulsa, **Then** se marca como leída y se navega a la sección de amigos/solicitudes (`/friends`).
4. **Given** el popover abierto, **When** se pulsa una notificación, **Then** el popover se cierra, la notificación queda leída y se navega al destino, con el contador de la campana actualizado.
5. **Given** una notificación ya leída, **When** se pulsa para navegar, **Then** se navega igualmente al destino sin cambiar su estado.
6. **Given** una notificación cuyo grupo, deuda o persona de origen fue eliminada, **When** se pulsa, **Then** se marca como leída, se navega a la sección general correspondiente y se muestra un aviso no bloqueante indicando que el destino ya no está disponible.
7. **Given** una notificación con información de destino incompleta o inválida, **When** se pulsa, **Then** se marca como leída, permanece en la bandeja y se muestra un aviso comprensible sin errores técnicos ni pantallas vacías.
8. **Given** un fallo al marcar como leída, **When** se pulsa la notificación, **Then** se informa del problema con un mensaje en español y se permite reintentar la navegación; no se navega a un destino incorrecto.

---

### User Story 4 - Configurar canales y tipos de notificaciones (Priority: P2)

Lucía decide que quiere seguir recibiendo avisos en la aplicación pero no por correo, y que no le interesa la actividad de grupo. Abre la configuración desde la bandeja (o desde el pie del popover), cambia esas opciones y guarda. Al volver a entrar más tarde, encuentra la misma configuración, y los eventos que desactivó ya no generan avisos.

**Why this priority**: da control al usuario sobre el volumen de avisos, pero no bloquea el uso de la bandeja; por eso va después de los flujos de consulta y navegación.

**Independent Test**: abrir la configuración, desactivar el canal de correo y el tipo "actividad de grupo", guardar, recargar la página y verificar que los valores persisten; provocar un evento de grupo y uno de deuda y comprobar que solo se genera el aviso permitido.

**Acceptance Scenarios**:

1. **Given** la bandeja abierta, **When** se pulsa el botón "Configuración de notificaciones", **Then** se abre un modal/diálogo con los canales y los tipos de evento con sus valores actuales.
2. **Given** el popover abierto, **When** se pulsa el acceso a la configuración, **Then** se abre el mismo modal/diálogo y el popover se cierra.
3. **Given** el modal de configuración, **When** se muestran las opciones, **Then** aparecen los canales In-App (bandeja) y Correo electrónico, y los tipos Recordatorios y silbatos de cobro, Vencimiento de deudas y cobros pendientes, Actividad de grupo y Solicitudes de amistad y actividad social, cada uno con su estado activo/inactivo. La primera vez, todos están activos.
4. **Given** cambios sin guardar, **When** se cierra el modal, **Then** no se persisten los cambios y los valores originales siguen vigentes.
5. **Given** cambios válidos, **When** se pulsa "Guardar", **Then** se confirma visualmente el guardado, el modal se cierra y los valores persisten tras recargar la página y tras cerrar y volver a iniciar sesión.
6. **Given** el canal In-App desactivado, **When** ocurre un evento habilitado, **Then** no se agrega una nueva notificación a la bandeja ni sube el contador; las notificaciones anteriores siguen visibles.
7. **Given** el canal de correo desactivado, **When** ocurre un evento habilitado, **Then** no se envía correo de notificación, aunque la notificación In-App se genere si ese canal está activo.
8. **Given** un tipo de evento desactivado, **When** ocurre un evento de ese tipo, **Then** no se genera notificación en ningún canal, mientras los demás tipos siguen funcionando según su configuración.
9. **Given** un error al guardar las preferencias, **When** se pulsa "Guardar", **Then** se muestra un mensaje en español, no se afirma que se guardó y se permite reintentar sin perder los cambios locales.
10. **Given** un lector de pantalla o teclado, **When** se usa el modal, **Then** los interruptores tienen nombres y estados accesibles, el foco entra al abrir y vuelve al botón que lo abrió al cerrar.

---

### User Story 5 - Mantener el flujo de negocio ante fallos de notificación (Priority: P2)

El equipo operativo necesita que un problema de correo o de base de datos no impida registrar un gasto, un pago o una solicitud de amistad. Cuando el correo está caído o no se puede guardar el aviso, la operación principal sigue completándose y responde con éxito; el incidente queda registrado para el equipo técnico sin exponer datos internos a la persona.

**Why this priority**: protege las operaciones centrales de la aplicación; la notificación es un complemento y nunca debe convertirse en un bloqueo del flujo principal.

**Independent Test**: forzar un fallo de SMTP y, por separado, un fallo de inserción de notificaciones en la base de datos, ejecutar cada operación de negocio que genera avisos y comprobar que la operación responde con éxito, que no aparece un error atribuible a notificaciones y que el fallo quedó registrado.

**Acceptance Scenarios**:

1. **Given** el servicio de correo caído, **When** se completa una operación que genera un aviso por correo, **Then** la operación de negocio se completa con éxito, no se muestra un error al usuario por el correo y el fallo queda registrado.
2. **Given** un fallo al guardar la notificación en la base de datos, **When** se completa la operación de negocio que la origina, **Then** la operación se completa con éxito y el fallo se registra sin revertir ni fallar la operación principal.
3. **Given** un identificador de notificación inexistente o perteneciente a otra persona, **When** se intenta marcar como leída, **Then** se responde como "no encontrada" sin modificar nada y sin revelar si la notificación existe.
4. **Given** un intento de consultar o modificar preferencias de otra persona, **When** se envía un identificador ajeno, **Then** se ignoran los parámetros de usuario y se usan siempre los de la sesión autenticada.
5. **Given** una sesión expirada, **When** se consulta la bandeja o se guarda una preferencia, **Then** se aplica el mismo comportamiento de acceso protegido que al resto de las pantallas.

---

### Matriz de casos límite

| Caso límite | Comportamiento esperado | Trazabilidad |
|-------------|-------------------------|--------------|
| Servicio SMTP caído o con credenciales inválidas | Se omite el correo, la operación de negocio se completa y el fallo se registra; la persona no ve un error de notificación | US5, FR-032, SC-007 |
| Base de datos caída al guardar una notificación | La operación de negocio se completa; se registra el fallo; no se revierte la operación principal | US5, FR-032, SC-007 |
| ID de notificación inexistente | Respuesta "no encontrada", sin cambios y sin detalles internos | US5, FR-031, SC-008 |
| ID de notificación de otro usuario | Igual que inexistente: 404, sin lectura ni filtración del contenido | US5, FR-028, FR-031, SC-008 |
| Navegación a un grupo eliminado | Marcar como leída, ir a la sección de grupos, aviso no bloqueante de destino inexistente | US3, FR-019, SC-005 |
| Navegación a una deuda/transacción eliminada | Marcar como leída, ir a la sección de deudas, aviso no bloqueante | US3, FR-019, SC-005 |
| Navegación a una persona o solicitud de amistad eliminada | Marcar como leída, ir a `/friends`, aviso no bloqueante | US3, FR-019, SC-005 |
| Notificación con destino incompleto o inválido | Marcar como leída, mantener en el historial, mensaje comprensible sin error técnico | US3, FR-020, SC-005 |
| Cero notificaciones | Campana sin contador, popover y bandeja con mensaje vacío, sin acciones de marcado | US1, US2, FR-006, FR-013 |
| Más de nueve no leídas | Contador "9+" y popover limitado a cinco avisos | US1, FR-002, FR-003, SC-001 |
| Más notificaciones que el tamaño de página | Carga incremental por cursor, sin duplicados ni saltos | US2, FR-010, SC-003 |
| Doble clic rápido en "Marcar todas como leídas" | Operación idempotente; el estado final es "todas leídas" sin errores | US2, FR-014, SC-004 |
| Cierre del modal con cambios sin guardar | Los cambios se descartan, sin aviso de guardado | US4, FR-027, SC-006 |
| Popular simultáneamente In-App y correo desactivados | No se genera ni entrada ni correo; no hay errores | US4, FR-026, SC-006 |
| Cambio de preferencias durante una operación en curso | Se aplica la configuración vigente al momento de generar el aviso; no se producen estados mixtos | US4, FR-026, SC-006 |
| Sesión expirada o cuenta no verificada | Redirección al acceso / respuesta de acceso protegida, igual que el resto de las pantallas | US1, US2, US4, US5, FR-033 |
| Pantalla angosta o lector de pantalla | Sin desplazamiento horizontal; contador, estados y acciones anunciados por texto | US1, US2, US4, FR-034, SC-009 |

### Edge Cases

- **Sin notificaciones**: la campana no muestra contador, el popover y la bandeja muestran mensajes vacíos y no se ofrecen acciones de marcado.
- **Muchas no leídas**: el contador se limita a "9+" y el popover a cinco avisos; la bandeja sigue mostrando el total real de no leídas en el filtro.
- **Notificaciones antiguas**: siguen accesibles en el historial con su fecha; no se eliminan automáticamente en esta feature.
- **Marcar dos veces**: marcar como leída una notificación ya leída no cambia el estado ni produce errores.
- **Marcar todas con cero no leídas**: la acción no se ofrece o no produce cambios; el contador ya está en cero.
- **Cambios de preferencia sin guardar**: al cerrar el modal se descartan; al guardar con error se conservan localmente para reintentar.
- **Destino eliminado**: se marca como leída y se navega a la sección general con aviso; no se muestra una página en blanco ni un error del servidor.
- **Datos de destino incompletos**: misma navegación segura con aviso; la notificación permanece en el historial.
- **IDs ajenos o inexistentes**: se tratan como no encontrados; no se revela existencia ni contenido de notificaciones de otras personas.
- **Fallo de SMTP o de base de datos al notificar**: no afecta la operación de negocio que la originó; se registra para el equipo técnico.
- **Error de red en la bandeja o en el guardado de preferencias**: mensaje en español con reintento, sin datos parciales ni confirmaciones falsas.
- **Popover abierto y navegación**: el popover se cierra al navegar, al pulsar fuera o al pulsar Escape; no intercepta otros controles de la barra superior.
- **Accesibilidad**: campana, contador, filtros, estados de lectura, acciones y modal tienen nombres y estados accesibles; la información no depende solo del color.
- **Pantalla angosta**: el popover y la bandeja se adaptan al ancho disponible sin desplazamiento horizontal de la página.

## Requirements *(mandatory)*

### Functional Requirements

**Popover rápido (US1)**

- **FR-001**: La barra superior de navegación MUST incluir un ícono de campana accesible para toda persona con sesión iniciada y cuenta verificada, con un contador visible del número de notificaciones no leídas; sin notificaciones no leídas, el contador MUST NOT mostrarse.
- **FR-002**: Al pulsar la campana MUST abrirse un popover con **hasta cinco notificaciones**: primero las no leídas y, dentro de cada grupo, la más reciente primero. Cada entrada MUST mostrar título, texto breve y fecha o fecha relativa comprensible.
- **FR-003**: El contador de la campana MUST reflejar siempre el número real de notificaciones no leídas del usuario, MUST mostrar "9+" cuando supere nueve y MUST actualizarse sin recargar la página después de marcar una o todas como leídas.
- **FR-004**: El pie del popover MUST incluir un enlace o botón destacado "Ver todas las notificaciones" que lleve a `/notifications` y cierre el popover.
- **FR-005**: El popover MUST cerrarse al pulsar fuera de él, al pulsar Escape o después de navegar; MUST NOT cerrarse de forma imprevista al moverse el cursor dentro de sus límites.
- **FR-006**: Sin notificaciones, el popover MUST mostrar un mensaje vacío en español y MUST NOT mostrar una lista vacía ni errores.

**Bandeja dedicada (US2)**

- **FR-007**: La ruta `/notifications` MUST mostrar una pantalla completa con el historial de notificaciones del usuario autenticado, ordenado de más reciente a más antiguo, sin incluir notificaciones de otras personas.
- **FR-008**: La bandeja MUST ofrecer filtros o pestañas "Todas" y "No leídas"; el filtro "No leídas" MUST mostrar exclusivamente las no leídas y su cantidad MUST coincidir con el contador de la campana.
- **FR-009**: Cada notificación MUST distinguirse como leída o no leída mediante una etiqueta o indicador textual y un estado accesible, además de cualquier diferencia visual de color, peso u opacidad.
- **FR-010**: El historial MUST cargarse en páginas ordenadas por fecha descendente, con un límite por pedido y un mecanismo para cargar más entradas sin duplicados ni saltos.
- **FR-011**: Cada notificación no leída MUST ofrecer una acción "Marcar como leída" que cambie solo esa notificación, actualice el filtro y el contador sin recargar la página; una notificación ya leída MUST NOT ofrecer esa acción.
- **FR-012**: La bandeja MUST ofrecer una acción global "Marcar todas como leídas" que marque todas las notificaciones no leídas del usuario y deje el contador en cero; con cero no leídas, la acción MUST no producir cambios.
- **FR-013**: Sin notificaciones, la bandeja MUST mostrar un mensaje vacío y MUST NOT ofrecer acciones de marcado.
- **FR-014**: El marcado de una notificación y el marcado global MUST ser idempotentes: repetir la acción sobre una notificación ya leída MUST NOT cambiar el estado ni generar errores.
- **FR-015**: Ante un error de carga, la bandeja MUST mostrar un mensaje en español con una acción de reintento y MUST NOT mostrar datos parciales.

**Enlaces profundos y marcado automático (US3)**

- **FR-016**: Al pulsar una notificación, desde el popover o desde la bandeja, la notificación MUST marcarse automáticamente como leída y la aplicación MUST navegar al contexto correspondiente según su tipo.
- **FR-017**: Una notificación de recordatorio, silbato o gasto de grupo MUST dirigir a la vista del grupo (`/groups/{groupId}`); una de deuda, vencimiento o pago personal MUST dirigir a la pestaña de deudas del grupo (`/groups/{groupId}/debts`) con la deuda señalada mediante desplazamiento automático (scroll) y un resaltado efímero de 2 segundos, o a la actividad personal (`/activity`) con la deuda señalada de la misma manera cuando no pertenezca a un grupo; una de solicitud de amistad o actividad social MUST dirigir a `/friends` (sección de solicitudes).
- **FR-018**: Al pulsar una notificación ya leída MUST navegarse al destino sin alterar su estado.
- **FR-019**: Si el grupo, la deuda, la transacción o la persona destino ya no existe, la notificación MUST marcarse como leída, la navegación MUST llevar a la sección general correspondiente y MUST mostrarse un aviso no bloqueante en español indicando que el destino no está disponible; este aviso MUST dispararse mediante un Snackbar de error de 4 segundos a través del `SnackbarProvider` existente.
- **FR-020**: Si la información de destino está incompleta o es inválida, la notificación MUST marcarse como leída, MUST permanecer en el historial y MUST mostrarse un mensaje comprensible sin errores técnicos.
- **FR-021**: El popover MUST cerrarse al pulsar una notificación y el contador de la campana MUST actualizarse como consecuencia del marcado automático.
- **FR-022**: Si el marcado automático falla, la interfaz MUST informarlo en español y MUST permitir reintentar; MUST NOT navegar a un destino incorrecto.

**Preferencias de notificación (US4)**

- **FR-023**: La bandeja MUST ofrecer un botón "Configuración de notificaciones" y el popover MUST ofrecer un acceso al mismo modal/diálogo de preferencias.
- **FR-024**: El modal de preferencias MUST mostrar y permitir cambiar dos canales —In-App (bandeja) y Correo electrónico— y cuatro tipos de evento —Recordatorios y silbatos de cobro, Vencimiento de deudas y cobros pendientes, Actividad de grupo, y Solicitudes de amistad y actividad social—, cada uno con estado activo/inactivo.
- **FR-025**: Por defecto, para cada persona, ambos canales y los cuatro tipos de evento MUST estar activos la primera vez que se consultan o se guardan las preferencias.
- **FR-026**: Un tipo de evento desactivado MUST impedir la generación de notificaciones de ese tipo en todos los canales; el canal In-App desactivado MUST impedir nuevas entradas en la bandeja y el canal de correo desactivado MUST impedir los correos de actividad (actividad de grupo y actividad social), sin borrar el historial existente. Los eventos críticos —silbatos de cobro y vencimientos de deudas— MUST enviar correo siempre, con independencia del estado del toggle de correo (`emailPolicy=ALWAYS`). Los correos de negocio ya existentes (verificación y recuperación) MUST NOT modificarse ni duplicarse.
- **FR-027**: Los cambios MUST persistir solo al confirmar "Guardar"; al cerrar sin guardar MUST descartarse; ante un error de guardado MUST mostrarse un mensaje en español y conservarse los cambios locales para reintentar.
- **FR-028**: Las preferencias consultadas y modificadas MUST ser siempre las de la persona autenticada, ignorando cualquier identificador de usuario enviado por la interfaz.

**Contrato y persistencia requeridos por el backlog (US1–US5)**

- **FR-029**: El sistema MUST exponer las operaciones de listado de notificaciones (con `order=history` —fecha descendente— u `order=unread-first` para el popover, y límite de cantidad), marcado individual, marcado global, consulta de preferencias y actualización de preferencias, conforme al contrato descrito en [contracts/notifications-api.md](./contracts/notifications-api.md).
- **FR-030**: El sistema MUST persistir el historial de notificaciones y la configuración de preferencias del usuario, incluida la tabla de preferencias `user_notification_preferences` exigida por el backlog, de modo que sobrevivan al cierre de sesión y a la recarga de la página.
- **FR-031**: Las operaciones anteriores MUST responder únicamente con datos de la persona autenticada; un identificador de notificación inexistente o ajeno MUST responderse como "no encontrado" sin modificar datos ni revelar información.

**Resiliencia, seguridad, accesibilidad y regresión (US5)**

- **FR-032**: Un fallo al guardar una notificación o al enviar un correo MUST NOT interrumpir, revertir ni fallar la operación de negocio que lo originó; el fallo MUST registrarse sin exponer detalles internos en la respuesta a la persona.
- **FR-033**: El acceso a la bandeja, al popover, a las preferencias y a las operaciones de marcado MUST requerir sesión de cuenta verificada, igual que el resto de las pantallas protegidas.
- **FR-034**: La campana, el contador, la lista, los filtros, las acciones de marcado y el modal MUST tener nombres y estados accesibles, soportar teclado y lector de pantalla, y no depender solo del color para comunicar el estado de lectura ni el resultado de una acción.
- **FR-035**: La feature MUST NOT alterar las reglas de negocio, importes, estados ni resultados de gastos, grupos, pagos, deudas, amistades, balance (033) ni estadísticas (032, 034).
- **FR-036**: La generación de notificaciones MUST ocurrir como efecto de los eventos de negocio existentes, sin exigir cambios en la forma en que la persona registra gastos, pagos, silbatos o solicitudes de amistad.

### Contrato API requerido

El backlog exige explícitamente las siguientes operaciones REST. Se listan aquí como **requisito de contrato**, no como decisión de implementación; los detalles de parámetros, respuestas y códigos se documentan en [contracts/notifications-api.md](./contracts/notifications-api.md) y en `plan.md`:

- `GET /api/v1/notifications`: listado de notificaciones del usuario, ordenado por fecha descendente, con soporte para limitar la cantidad (popover) y cargar páginas posteriores.
- `PATCH /api/v1/notifications/{id}/read`: marcar una notificación como leída.
- `PATCH /api/v1/notifications/read-all`: marcar todas las notificaciones del usuario como leídas.
- `GET /api/v1/notifications/preferences`: obtener la configuración actual del usuario.
- `PUT /api/v1/notifications/preferences`: actualizar la configuración de notificación del usuario.

### Key Entities *(include if feature involves data)*

- **Notificación**: aviso generado para una persona a partir de un evento de negocio. Atributos relevantes: destinatario, tipo de evento, título, texto, momento de creación, estado leído/no leído (con momento de lectura) y datos de destino (identificador de grupo, deuda/transacción o solicitud de amistad). Se guarda en el historial; no se elimina en esta feature.
- **Preferencias de notificación**: configuración única por persona con dos canales (In-App y correo electrónico) y cuatro tipos de evento (recordatorios/silbatos, vencimiento de deudas y cobros, actividad de grupo, actividad social), todos activos por defecto. Se persiste en `user_notification_preferences`.
- **Usuario**: entidad existente; dueño de las notificaciones y de las preferencias. Se identifica desde la sesión, nunca desde parámetros de la petición.
- **Grupo**: entidad existente; destino de notificaciones de recordatorios, silbatos y gastos grupales. Puede haber sido eliminado cuando se abre la notificación.
- **Deuda / Transacción**: entidad existente; destino de notificaciones de vencimiento, cobros pendientes y pagos. Puede haber sido eliminada o cancelada.
- **Solicitud de amistad / amistad**: entidades existentes; destino de notificaciones de actividad social.
- **Evento de notificación** (derivado, no se guarda por separado): momento en que una operación de negocio genera uno o más avisos, sujeto a las preferencias vigentes del destinatario.

## UI/UX

- **Barra superior**: campana con contador de no leídas ("9+" como tope), popover flotante anclado a la campana con hasta cinco avisos y pie con dos accesos: "Ver todas las notificaciones" y "Configuración de notificaciones".
- **Notificación en lista**: título, texto breve, fecha relativa, indicador textual de "No leída" cuando corresponda y acción "Marcar como leída" solo para no leídas. Al pulsar el cuerpo de la entrada se navega al destino y se marca como leída.
- **Bandeja `/notifications`**: encabezado con título, botón global "Marcar todas como leídas" y botón de configuración; debajo, filtros "Todas" / "No leídas" y la lista paginada. Estados: carga, vacío, error con reintento y "cargar más" al final.
- **Modal de preferencias**: dos bloques ("Canales" y "Tipos de evento") con interruptores etiquetados, botones "Guardar" y "Cancelar", foco inicial en el diálogo y retorno al control que lo abrió al cerrar.
- **Estados y mensajes**: textos en español, sin detalles técnicos; avisos no bloqueantes cuando el destino de una notificación ya no existe.
- **Responsive y accesibilidad**: popover y bandeja se adaptan a pantallas angostas sin desplazamiento horizontal; roles y nombres accesibles para campana, contador, filtros, estados de lectura, acciones y modal; el estado no se comunica solo por color.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con cuatro notificaciones no leídas, una persona ve el contador correcto en la campana y el popover abierto en menos de 2 segundos desde que pulsa el ícono.
- **SC-002**: El 100 % de los casos de prueba muestran en el popover hasta cinco avisos, con las no leídas primero y el más reciente primero dentro de cada grupo, y el enlace del pie abre la bandeja con un solo clic.
- **SC-003**: En el 100 % de los casos de prueba, el filtro "No leídas" muestra exactamente las notificaciones no leídas y su cantidad coincide con el contador de la campana.
- **SC-004**: Después de "Marcar todas como leídas", el contador de la campana llega a cero sin recargar en menos de 2 segundos y el estado persiste al recargar en el 100 % de los casos.
- **SC-005**: El 100 % de las notificaciones de prueba abiertas desde el popover o la bandeja llegan al contexto correcto y quedan marcadas como leídas; cuando el destino no existe, el 100 % de los casos muestran el aviso y permanecen en una sección válida.
- **SC-006**: El 100 % de los cambios de preferencias persisten tras cerrar sesión y volver a entrar, y solo afectan a notificaciones generadas con posterioridad al guardado.
- **SC-007**: Con SMTP caído o con fallos de inserción de notificaciones, el 100 % de las operaciones de negocio que originan avisos se completan con éxito y sin mensajes de error atribuibles a las notificaciones.
- **SC-008**: El 0 % de las notificaciones de otras personas son visibles en la bandeja ni accesibles mediante un identificador directo; los intentos responden como "no encontrado".
- **SC-009**: La campana, el popover, la bandeja y el modal se completan con teclado y lector de pantalla en el 100 % de los casos de prueba de accesibilidad, y no presentan desplazamiento horizontal en pantallas angostas.
- **SC-010**: Las verificaciones automáticas de backend y frontend pasan con cobertura mínima de líneas del 85 %, sin reducir umbrales ni desactivar pruebas.
