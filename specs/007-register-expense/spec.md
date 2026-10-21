# Feature Specification: Registro de gasto personal

**Feature Branch**: `feat/register-expense`

**Created**: 2026-10-21

**Status**: Draft

**Input**: User description: "Como usuario autenticado, quiero registrar un gasto personal a través de un formulario para llevar un control de mis finanzas. El gasto es estrictamente personal: pertenece únicamente a su creador, no se divide, no genera deudas y no se asocia a amigos ni a grupos."

## Relación con otras especificaciones

- **Reutiliza la autenticación existente de 001 (Registro de usuario) y 004 (Inicio de sesión)**: la sesión, el usuario autenticado, la protección de rutas y el destino tras iniciar sesión son los ya existentes. Esta feature no introduce ningún mecanismo de autenticación nuevo.
- **No depende de funcionalidades inexistentes**: hoy no existen amigos, grupos, gastos compartidos, deudas ni balances. Esta es la primera funcionalidad de gastos y no crea ninguno de esos conceptos.
- Los identificadores FR-###, SC-### y D# de esta especificación son propios de 007 y no coinciden necesariamente en número con los de otras especificaciones.

## Clarifications

### Session 2026-10-21

- Q: ¿Qué significa exactamente "Recurrente" en esta iteración? (D4) → A: Solo una clasificación del gasto: se guarda "Recurrente" o "Único" como dato, sin generar gastos futuros ni pedir frecuencia.
- Q: ¿Qué monedas debe admitir el gasto, de dónde debe salir la moneda precargada y puede el usuario elegir una moneda fuera de su preferencia de perfil? (D1) → A: ARS, USD y EUR; se precarga la moneda del perfil (ARS si no hay preferencia; si hay varias, la primera) y el usuario puede elegir cualquiera de las tres aunque no esté en su preferencia.
- Q: ¿Las categorías del gasto deben ser una lista fija y, en ese caso, cuáles son sus valores? (D2) → A: Lista fija: Alimentos, Transporte, Vivienda, Servicios, Salud, Educación, Ocio, Compras, Mascotas y Otros.
- Q: ¿Los medios de pago del gasto deben ser una lista fija y, en ese caso, cuáles son sus valores? (D3) → A: Lista fija: Efectivo, Tarjeta de débito, Tarjeta de crédito, Transferencia, Billetera virtual y Otro.
- Q: ¿Qué fechas debe admitir el gasto, existe una fecha mínima y qué zona horaria debe determinar el "día actual" precargado? (D5 y D6) → A: Se admiten fechas pasadas y el día actual; no se admiten fechas futuras; sin fecha mínima; el "día actual" se determina con la zona horaria del dispositivo del usuario.
- Q: ¿Deben mantenerse los criterios entonces numerados SC-001 (2 minutos) y SC-004 (2 segundos) como criterios de éxito, o eliminarse por no figurar en el backlog original? → A: Eliminar ambos criterios por no estar en el backlog original.

### Session 2026-10-21

- Q: ¿Cuántos decimales y qué rango de monto debe admitir un gasto, y esa cantidad de decimales debe ser la misma para ARS, USD y EUR? (D7) → A: Dos decimales para ARS, USD y EUR; mínimo 0,01 y máximo 999.999.999,99.
- Q: ¿Cuál debe ser la longitud máxima de la descripción y deben recortarse los espacios en blanco al inicio y al final antes de medirla y guardarla? (D8) → A: Máximo 200 caracteres después de recortar espacios al inicio y al final; se mide y se guarda el texto recortado.
- Q: ¿Qué debe ocurrir inmediatamente después de un guardado exitoso: limpiar el formulario, redirigir a otra pantalla o actualizar una vista? (D9) → A: Mostrar la notificación de éxito, limpiar el formulario y permanecer en él para registrar otro gasto.
- Q: ¿Con qué referencia debe el backend decidir que una fecha es futura, para no rechazar por error una fecha válida del dispositivo del usuario? (D5 y D6) → A: El backend valida contra la zona horaria del dispositivo informada al guardar; si falta o es inválida, aplica una tolerancia de un día respecto de su propia fecha.
- Q: Cuando el perfil tiene varias monedas preferidas, ¿qué orden determina cuál se precarga en el formulario? (D1) → A: Orden fijo ARS → USD → EUR; se precarga la primera moneda de ese orden que esté en la preferencia.

### Session 2026-10-21

- Q: ¿Cómo debe redactarse la regla de fecha futura para que la tolerancia de un día no contradiga "no se admiten fechas futuras", y qué debe pasar con la fecha precargada si el formulario cruza la medianoche? (CHK006, CHK007, CHK039) → A: Regla general: no se admiten fechas futuras según la zona horaria informada. La tolerancia de un día es una excepción acotada solo a zona ausente o inválida y no un permiso para el usuario. Si el formulario cruza la medianoche, la fecha precargada se actualiza al nuevo día actual solo si el usuario no la modificó; si la modificó, se conserva.
- Q: ¿Cómo debe interpretar y normalizar el formulario un monto escrito con coma, punto o separadores de miles? (CHK002) → A: Solo formato argentino: coma como separador decimal y punto o espacio como separador de miles; se eliminan los separadores de miles y se normaliza el valor antes de validar. El punto decimal, las combinaciones malformadas y más de dos decimales se rechazan sin redondear. La visualización del monto usa coma decimal.
- Q: ¿Qué debe considerarse una descripción válida respecto de espacios en blanco, tabs, saltos de línea y espacios internos, y cómo se cuentan los 200 caracteres? (CHK014, CHK016) → A: Se recortan espacios en blanco Unicode al inicio y al final; si el resultado queda vacío, la descripción se considera ausente. Se permiten espacios internos, tabs y saltos de línea; los saltos CRLF/CR se normalizan a LF, cada tab o salto cuenta como un carácter y no se colapsan espacios múltiples. El máximo de 200 se mide después del recorte y la normalización.
- Q: Después de un guardado exitoso, ¿qué campos se limpian o se vuelven a precargar y cuánto tiempo permanece visible la notificación de éxito? (CHK030, CHK031) → A: El formulario vuelve a su estado inicial: monto y descripción vacíos; fecha precargada con el día actual; moneda precargada según la preferencia del perfil; categoría, tipo y medio de pago sin selección. La notificación de éxito se cierra automáticamente a los 5 segundos, salvo que el usuario la cierre antes.
- Q: Si el servidor persiste el gasto pero la respuesta se pierde por timeout, ¿se acepta la limitación o se exige protección contra duplicados? (CHK026) → A: Se exige protección contra duplicados cuando el backend ya persistió y el usuario reintenta el mismo envío; el mecanismo se decidirá en el plan y se mantiene SC-003.

### Session 2026-10-21

- Q: Si la preferencia de moneda del perfil falta, está vacía, contiene un valor no reconocido o no puede consultarse, ¿qué moneda debe precargarse en el formulario? (CHK012) → A: ARS en todos esos casos. Si el perfil no puede consultarse, se muestra un aviso no bloqueante y se continúa con ARS.
- Q: Cuando la sesión expira al confirmar, ¿qué debe pasar con los datos ya ingresados y qué ocurre después de volver a iniciar sesión? (CHK019) → A: Los datos se conservan solo mientras el formulario permanece visible. Si la reautenticación obliga a abandonar la pantalla, se descartan y, tras volver a iniciar sesión, no se restauran automáticamente; el usuario debe reingresar el gasto. No se guardan borradores.
- Q: Mientras se guarda el gasto, ¿debe exigirse una indicación visible de que la operación está en curso, además de bloquear los controles? (CHK028) → A: Sí. El botón de confirmar se deshabilita y muestra un texto claro como "Guardando…" o un indicador visible equivalente hasta que la operación termina; el formulario permanece visible.
- Q: ¿Qué aspectos no funcionales deben incluirse como requisito mínimo en esta iteración y cuáles deben declararse explícitamente fuera de alcance: accesibilidad, rendimiento, soporte móvil y localización? (CHK036) → A: Incluir accesibilidad básica y uso en navegador móvil con diseño usable; excluir umbrales medibles de rendimiento y localización multi-idioma/multi-región.
- Q: ¿Qué formato de fecha debe ver y escribir el usuario, y cómo debe comportarse el formulario ante un formato no reconocido o una fecha inexistente como 31/02? (CHK008) → A: Se muestra como dd/mm/aaaa; se aceptan dd/mm/aaaa y d/m/aaaa, normalizando día y mes a dos dígitos. Se rechazan los formatos no reconocidos y las fechas inexistentes como 31/02, sin corrección automática ni traslado al mes siguiente.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un gasto personal válido (Priority: P1)

Un usuario autenticado accede a un formulario de nuevo gasto, completa los campos obligatorios (monto, moneda, fecha, categoría, tipo de gasto y medio de pago), opcionalmente agrega una descripción y confirma. El sistema valida los datos, guarda el gasto vinculado exclusivamente al usuario que lo creó y le muestra una notificación de éxito.

**Why this priority**: es el valor central de la feature. Sin el registro y la persistencia del gasto no existe ninguna funcionalidad de gastos en la aplicación.

**Independent Test**: con un usuario autenticado, completar solo los campos obligatorios con datos válidos y confirmar; comprobar que aparece la notificación de éxito y que el gasto existe vinculado únicamente a ese usuario, sin participantes, divisiones ni deudas.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado en el formulario de nuevo gasto, **When** completa los campos obligatorios con datos válidos y confirma, **Then** el gasto se persiste, queda vinculado automáticamente a ese usuario como creador, se muestra una notificación de éxito, el formulario vuelve a su estado inicial (monto y descripción vacíos; fecha del día actual; moneda según la preferencia del perfil; categoría, tipo y medio de pago sin selección) y el usuario permanece en él para registrar otro gasto.
2. **Given** un usuario autenticado, **When** registra un gasto con descripción vacía u omitida, **Then** el gasto se persiste porque la descripción es opcional.
3. **Given** un usuario autenticado, **When** registra un gasto con una descripción válida, **Then** la descripción se conserva junto con el gasto.
4. **Given** un usuario autenticado que abre el formulario, **When** la pantalla carga, **Then** la fecha aparece precargada con el día actual en la zona horaria de su dispositivo y el usuario puede modificarla antes de confirmar.
5. **Given** un gasto registrado con éxito, **When** se observa el resultado, **Then** el gasto no tiene otros participantes, no se divide, no genera deudas y no se asocia a amigos ni a grupos.
6. **Given** un usuario autenticado, **When** elige el tipo de gasto, **Then** solo puede elegir entre "Recurrente" y "Único"; "Recurrente" es solo una clasificación y no genera gastos futuros ni requiere una frecuencia.
7. **Given** un usuario autenticado con preferencia de moneda en su perfil, **When** abre el formulario, **Then** la moneda se precarga según esa preferencia (ARS si no hay preferencia; si hay varias, la primera en el orden fijo ARS → USD → EUR) y puede elegir ARS, USD o EUR aunque no esté en su preferencia.
15. **Given** un usuario cuya preferencia de moneda falta, está vacía o contiene un valor no reconocido, o cuyo perfil no puede consultarse, **When** abre el formulario, **Then** la moneda se precarga como ARS y, si el perfil no pudo consultarse, se muestra un aviso no bloqueante.
16. **Given** un usuario que navega solo con teclado, **When** completa y envía el formulario, **Then** puede alcanzar y operar todos los campos y el botón de confirmar, el foco es visible y cada error de validación queda asociado a su campo.
17. **Given** un usuario que abre el formulario en un navegador móvil, **When** completa y envía el gasto, **Then** todos los campos y el botón de confirmar son visibles y utilizables sin desplazamiento horizontal.
8. **Given** un usuario autenticado, **When** elige la categoría del gasto, **Then** solo puede elegir una de la lista fija: Alimentos, Transporte, Vivienda, Servicios, Salud, Educación, Ocio, Compras, Mascotas u Otros.
9. **Given** un usuario autenticado, **When** elige el medio de pago, **Then** solo puede elegir uno de la lista fija: Efectivo, Tarjeta de débito, Tarjeta de crédito, Transferencia, Billetera virtual u Otro.
10. **Given** un usuario autenticado, **When** ingresa una descripción con espacios al inicio o al final, **Then** el sistema los recorta antes de medir la longitud y de guardar el texto.
11. **Given** un usuario cuyo dispositivo está en una zona horaria adelantada respecto del servidor, **When** registra un gasto con la fecha del día actual de su dispositivo, **Then** el backend acepta la fecha porque usa la zona horaria informada por el dispositivo.
12. **Given** un formulario abierto antes de la medianoche, **When** el día cambia mientras el formulario sigue abierto, **Then** la fecha precargada se actualiza al nuevo día actual si el usuario no la modificó, y se conserva la fecha elegida por el usuario si ya la había modificado.
13. **Given** un usuario autenticado, **When** ingresa una descripción compuesta solo por espacios, tabs o saltos de línea, **Then** la descripción se considera ausente y el gasto se registra normalmente.
14. **Given** un guardado exitoso, **When** transcurren 5 segundos sin que el usuario cierre la notificación, **Then** la notificación se cierra automáticamente; si el usuario la cierra antes, también se cierra.

---

### User Story 2 - Validación de datos y bloqueo del envío (Priority: P2)

Un usuario autenticado intenta registrar un gasto con datos inválidos o incompletos. El sistema muestra mensajes de error claros junto al campo correspondiente, conserva lo ya ingresado y bloquea el envío hasta que los datos sean válidos.

**Why this priority**: evita que se persistan gastos inválidos y garantiza que la información financiera sea confiable. Sin esta validación la persistencia de US1 no sería confiable.

**Independent Test**: intentar enviar el formulario con cada campo obligatorio vacío, con montos inválidos, con formatos de monto no admitidos y con una fecha inválida; comprobar que cada caso muestra un error asociado al campo, que no se persiste nada y que el envío queda bloqueado.

**Acceptance Scenarios**:

1. **Given** el formulario de nuevo gasto, **When** el monto está vacío, es cero, es negativo o no es numérico, **Then** se muestra un mensaje claro asociado al campo monto, el envío se bloquea y no se persiste ningún gasto.
2. **Given** el formulario de nuevo gasto, **When** uno o varios campos obligatorios están vacíos, **Then** cada campo afectado muestra su mensaje y el envío se bloquea.
3. **Given** el formulario de nuevo gasto, **When** la fecha está vacía, es inválida o es futura, **Then** se muestra un mensaje claro asociado a la fecha y el envío se bloquea.
4. **Given** el formulario de nuevo gasto, **When** el monto tiene más de dos decimales, es menor que 0,01 o es mayor que 999.999.999,99, **Then** se muestra un mensaje claro asociado al monto y el envío se bloquea.
5. **Given** el formulario de nuevo gasto, **When** la descripción supera los 200 caracteres después del recorte y la normalización, **Then** se muestra un mensaje claro asociado a la descripción y el envío se bloquea.
6. **Given** un formulario con errores, **When** el usuario corrige los campos inválidos, **Then** los mensajes correspondientes desaparecen y el envío se habilita cuando no quedan errores.
7. **Given** el formulario con datos válidos, **When** el usuario confirma, **Then** los datos se envían al backend para su validación independiente.
8. **Given** un envío rechazado por validación, **When** el usuario observa el formulario, **Then** los datos previamente ingresados se conservan.
9. **Given** el formulario de nuevo gasto, **When** el monto se escribe en formato argentino válido, **Then** se eliminan los separadores de miles, se normaliza la coma decimal y el valor se valida con el monto ingresado, sin redondear.
10. **Given** el formulario de nuevo gasto, **When** el monto usa punto como separador decimal o una combinación de separadores no admitida, **Then** se muestra un mensaje claro asociado al monto y el envío se bloquea.
11. **Given** el formulario de nuevo gasto, **When** la fecha usa un formato no reconocido o representa una fecha inexistente como 31/02, **Then** se muestra un mensaje claro asociado a la fecha, el envío se bloquea y la fecha no se corrige ni se traslada a otro mes.

---

### User Story 3 - Protección de la identidad, sesión y resiliencia (Priority: P3)

Un usuario autenticado se encuentra con situaciones adversas al registrar un gasto: sesión expirada, intento de atribuir el gasto a otra persona, envío repetido, timeout tras persistencia o falla del servidor o de la red. El sistema protege la propiedad del gasto, evita duplicados accidentales y conserva lo ingresado sin reportar éxitos falsos.

**Why this priority**: protege la integridad y la confianza en el registro. Aunque el camino feliz funciona sin esto, estas situaciones son parte explícita del comportamiento esperado y de la seguridad de los datos.

**Independent Test**: simular sesión expirada, solicitud con otro creador, doble clic en confirmar, timeout con persistencia previa y reintento, y falla de red; comprobar que no se crean gastos indebidos ni duplicados, que los mensajes son claros y que no se pierde lo ingresado.

**Acceptance Scenarios**:

1. **Given** un usuario no autenticado, **When** intenta acceder al formulario, **Then** el sistema le impide el acceso y lo dirige al inicio de sesión existente, sin crear un mecanismo nuevo.
2. **Given** un usuario cuya sesión expiró, **When** confirma un gasto, **Then** el gasto no se persiste, se informa que debe iniciar sesión nuevamente y los datos se conservan solo mientras el formulario permanece visible. Si la reautenticación obliga a abandonar la pantalla, los datos se descartan y no se restauran después de volver a iniciar sesión.
3. **Given** una solicitud que intenta indicar a otro usuario como creador del gasto, **When** el backend la procesa, **Then** la operación se rechaza, no se persiste ningún gasto y no se crea ningún registro a nombre de otro usuario.
4. **Given** un usuario que presiona confirmar repetidamente o reenvía el mismo formulario mientras la operación está en curso, **When** el sistema procesa los envíos, **Then** se crea como máximo un gasto, los controles permanecen deshabilitados y el botón de confirmar muestra un texto claro como "Guardando…" o un indicador visible equivalente hasta que la operación termina.
5. **Given** una falla del servidor o de la red durante el guardado, **When** la operación no se completa, **Then** el usuario recibe un mensaje claro, no se muestra una notificación de éxito, no queda ningún registro parcial y el formulario conserva los datos ingresados para reintentar.
6. **Given** un error de validación informado por el backend, **When** el usuario lo observa, **Then** el mensaje es claro, se asocia al campo correspondiente cuando sea posible y no revela detalles internos ni información sensible.
7. **Given** un gasto que el backend ya persistió pero cuya respuesta se perdió por timeout, **When** el usuario reintenta el mismo envío, **Then** no se crea un gasto adicional y el resultado sigue siendo un único gasto.

---

### Edge Cases

- **Monto igual a cero**: el envío se bloquea con un mensaje asociado al monto y no se persiste nada.
- **Monto negativo**: el envío se bloquea con un mensaje asociado al monto y no se persiste nada.
- **Monto vacío o no numérico**: el envío se bloquea con un mensaje asociado al monto y no se persiste nada.
- **Montos con decimales**: se admiten como máximo dos decimales para ARS, USD y EUR; un monto con más decimales se rechaza con un mensaje claro, sin redondear.
- **Formato del monto**: solo se acepta el formato argentino: coma como separador decimal y punto o espacio como separador de miles. Son válidos `1.234,56`, `1 234,56` y `1234,56`; `1.234` representa 1234 y `1,234` se rechaza por tener tres decimales. Se rechazan `1234.56` (punto decimal), las combinaciones malformadas y los valores con más de dos decimales.
- **Montos muy pequeños o muy grandes**: se aceptan desde 0,01 hasta 999.999.999,99 inclusive; fuera de ese rango se rechazan con un mensaje claro.
- **Campos obligatorios vacíos, individualmente o en combinación**: cada campo afectado muestra su mensaje, el envío se bloquea y no se persiste nada.
- **Fecha vacía o inválida**: se muestra un mensaje asociado a la fecha y se bloquea el envío.
- **Formato de fecha**: se muestra como dd/mm/aaaa. Se aceptan dd/mm/aaaa y d/m/aaaa, normalizando día y mes a dos dígitos; se rechazan los formatos no reconocidos y las fechas inexistentes como 31/02, sin corrección automática ni traslado al mes siguiente.
- **Fecha futura**: se rechaza con un mensaje claro asociado a la fecha y no se persiste el gasto. La regla general es que no se admiten fechas futuras según la zona horaria informada por el dispositivo. Si esa zona falta o es inválida, el backend aplica una tolerancia de un día como excepción acotada para evitar rechazos incorrectos; esa tolerancia no es un permiso para que el usuario registre fechas futuras.
- **Cruce de medianoche con el formulario abierto**: si la fecha precargada no fue modificada, se actualiza al nuevo día actual; si el usuario ya la había modificado, se conserva su fecha y se valida contra el nuevo día actual.
- **Cambio de zona horaria entre la carga y el envío**: la fecha se valida con la zona horaria informada al guardar; si el cambio hace que la fecha quede futura, se rechaza con un mensaje claro y no se persiste el gasto. El cambio no altera gastos ya registrados.
- **Fecha modificada respecto del valor por defecto**: se acepta si es una fecha pasada o el día actual; si es futura o inválida, se rechaza con un mensaje claro.
- **Zona horaria del día actual**: la fecha precargada corresponde al día actual en la zona horaria del dispositivo del usuario; un cambio de zona horaria no altera gastos ya registrados. El backend usa esa misma zona horaria para validar que la fecha no sea futura. Si no la recibe o es inválida, aplica una tolerancia de un día como excepción acotada y no como autorización para registrar fechas futuras.
- **Descripción vacía u omitida**: es válida y el gasto se registra normalmente.
- **Descripción compuesta solo por espacios, tabs o saltos de línea**: después del recorte se considera ausente y el gasto se registra normalmente.
- **Descripción con espacios al inicio o al final**: se recortan antes de medir la longitud y de guardar.
- **Descripción con tabs o saltos de línea internos**: se conservan; los saltos CRLF/CR se normalizan a LF, cada tab o salto cuenta como un carácter y no se colapsan espacios múltiples.
- **Descripción muy larga**: se rechaza con un mensaje claro si supera los 200 caracteres después del recorte y la normalización.
- **Descripción en el límite**: 200 caracteres después del recorte y la normalización es válida; 201 caracteres se rechaza.
- **Moneda precargada y elección fuera de la preferencia**: se precarga la moneda del perfil (ARS si no hay preferencia; si hay varias, la primera en el orden fijo ARS → USD → EUR) y el usuario puede cambiarla por cualquiera de ARS, USD o EUR.
- **Preferencia de moneda ausente, vacía, inválida o no consultable**: se precarga ARS en todos esos casos. Si el perfil no puede consultarse, se muestra un aviso no bloqueante y se permite continuar con ARS.
- **Moneda no soportada o manipulada**: el backend la rechaza si no es ARS, USD o EUR, sin persistir el gasto.
- **Categoría no soportada o manipulada**: el backend la rechaza si no es Alimentos, Transporte, Vivienda, Servicios, Salud, Educación, Ocio, Compras, Mascotas u Otros, sin persistir el gasto.
- **Medio de pago no soportado o manipulado**: el backend lo rechaza si no es Efectivo, Tarjeta de débito, Tarjeta de crédito, Transferencia, Billetera virtual u Otro, sin persistir el gasto.
- **Tipo de gasto manipulado**: el backend lo rechaza si no es "Recurrente" ni "Único", sin persistir el gasto.
- **Tipo de gasto "Recurrente"**: se guarda únicamente como clasificación; no crea gastos futuros, programaciones ni frecuencia.
- **Envío repetido o doble clic**: mientras la operación está en curso el envío permanece bloqueado, el botón de confirmar muestra "Guardando…" o un indicador visible equivalente y no se generan duplicados accidentales.
- **Sesión expirada o usuario no autenticado al enviar**: no se persiste el gasto, se informa la situación y se reutiliza el inicio de sesión existente. Los datos se conservan solo mientras el formulario permanece visible; si la reautenticación obliga a abandonar la pantalla, se descartan y no se restauran después de volver a iniciar sesión. No se guardan borradores.
- **Intento de enviar un gasto con otro usuario como creador**: la operación se rechaza y no se crea ningún gasto a nombre de otra persona.
- **Falla del servidor o de la red**: mensaje claro, sin éxito falso, sin datos parciales y con los datos del formulario conservados.
- **Timeout con persistencia previa**: si el backend ya guardó el gasto pero la respuesta no llega, un reintento del mismo envío no crea un gasto adicional y el sistema evita el duplicado.
- **Precisión monetaria**: un monto como `10,10` se conserva y se muestra exactamente como `10,10`, sin variaciones por redondeo de punto flotante.
- **Gastos idénticos registrados deliberadamente en dos operaciones distintas**: se consideran dos gastos diferentes; el bloqueo de duplicados aplica al reenvío accidental de una misma operación en curso o reintentada tras un timeout con persistencia previa.
- **Guardado exitoso**: se muestra la notificación de éxito y el formulario vuelve a su estado inicial (monto y descripción vacíos; fecha del día actual; moneda según la preferencia del perfil; categoría, tipo y medio de pago sin selección). El usuario permanece en la misma pantalla; no se redirige ni se actualiza un listado de gastos. La notificación se cierra automáticamente a los 5 segundos, salvo que el usuario la cierre antes.

## Requirements *(mandatory)*

### Functional Requirements

**Acceso, sesión y propiedad del gasto**

- **FR-001**: El sistema MUST permitir acceder al formulario de nuevo gasto únicamente a un usuario autenticado, reutilizando el mecanismo de sesión y la protección de rutas existentes.
- **FR-002**: Un usuario no autenticado MUST NOT poder acceder al formulario ni registrar un gasto; el sistema MUST dirigirlo al inicio de sesión existente sin introducir un mecanismo de autenticación nuevo.
- **FR-003**: Al registrar un gasto, el sistema MUST asignar automáticamente como creador al usuario autenticado.
- **FR-004**: El formulario MUST NOT ofrecer al usuario la posibilidad de elegir ni modificar el creador del gasto.
- **FR-005**: Si una solicitud intenta indicar un creador distinto del usuario autenticado, el sistema MUST rechazarla, MUST NOT persistir el gasto y MUST NOT crear ningún registro a nombre de otro usuario.
- **FR-006**: El gasto MUST ser estrictamente personal: MUST NOT tener otros participantes, MUST NOT dividirse, MUST NOT generar deudas y MUST NOT asociarse a amigos ni a grupos. El sistema MUST NOT aceptar datos de participantes, divisiones, deudas, amigos o grupos para un gasto.

**Formulario y valores**

- **FR-007**: El formulario MUST incluir como campos obligatorios monto, moneda, fecha, categoría, tipo de gasto y medio de pago, y MUST incluir la descripción como campo opcional.
- **FR-008**: La interfaz MUST distinguir visualmente los campos obligatorios de los opcionales.
- **FR-009**: La fecha MUST aparecer precargada con el día actual en la zona horaria del dispositivo del usuario y MUST ser editable por el usuario antes de confirmar. Si el formulario permanece abierto y cruza la medianoche, la fecha precargada MUST actualizarse al nuevo día actual solo si el usuario no la modificó; si la modificó, MUST conservarse su valor.
- **FR-010**: El tipo de gasto MUST permitir únicamente los valores "Recurrente" y "Único"; el usuario MUST elegir uno de ellos. "Recurrente" MUST ser solo una clasificación y MUST NOT generar gastos futuros ni exigir una frecuencia.
- **FR-011**: La moneda MUST ser una de ARS, USD o EUR. La interfaz MUST ofrecer solo estas opciones válidas y MUST NOT permitir el ingreso de otros valores. La interfaz MUST precargar la moneda del perfil del usuario (ARS si no hay preferencia; si hay varias, la primera en el orden fijo ARS → USD → EUR) y MUST permitir elegir cualquiera de las tres, aunque no esté en su preferencia. Si la preferencia falta, está vacía o contiene un valor no reconocido, MUST precargar ARS. Si el perfil no puede consultarse, MUST precargar ARS y MUST mostrar un aviso no bloqueante. La categoría MUST ser una de la lista fija: Alimentos, Transporte, Vivienda, Servicios, Salud, Educación, Ocio, Compras, Mascotas u Otros; la interfaz MUST ofrecer solo estas opciones. El medio de pago MUST ser uno de la lista fija: Efectivo, Tarjeta de débito, Tarjeta de crédito, Transferencia, Billetera virtual u Otro; la interfaz MUST ofrecer solo estas opciones. El tipo de gasto MUST ofrecer solo "Recurrente" y "Único".
- **FR-012**: La descripción MUST ser opcional: vacía, omitida o compuesta solo por espacios en blanco Unicode, tabs o saltos de línea después del recorte MUST considerarse válida y guardarse como ausente. El recorte MUST eliminar espacios en blanco Unicode al inicio y al final (incluidas NBSP U+00A0, U+2007 y U+202F) antes de medir la longitud, y MUST guardarse el texto recortado; conserva la intención de Registro de usuario (001, FR-002/FR-003) de recortar antes de medir y guardar, con el criterio Unicode ampliado como criterio único y vinculante. MUST permitirse espacios internos, tabs y saltos de línea; los saltos CRLF/CR MUST normalizarse a LF, cada tab o salto MUST contar como un carácter y los espacios múltiples MUST NOT ser colapsados.

**Validación en la interfaz**

- **FR-013**: La interfaz MUST validar que el monto esté presente, sea numérico y sea estrictamente mayor que cero; MUST rechazar cero, valores negativos, vacíos y no numéricos con un mensaje claro asociado al campo monto. Para interpretar el monto, MUST aceptar solo el formato argentino: coma como separador decimal y punto o espacio como separador de miles; MUST eliminar los separadores de miles y normalizar el valor antes de validar, y MUST rechazar el punto como separador decimal y las combinaciones malformadas.
- **FR-014**: La interfaz MUST validar que el monto tenga como máximo dos decimales para ARS, USD y EUR, sin redondear, y que esté entre 0,01 y 999.999.999,99 inclusive, mostrando mensajes claros asociados al monto. La visualización del monto MUST usar coma como separador decimal.
- **FR-015**: La interfaz MUST validar que cada campo obligatorio esté presente, individualmente y en combinación, mostrando un mensaje claro asociado a cada campo afectado.
- **FR-016**: La interfaz MUST mostrar la fecha como dd/mm/aaaa y MUST aceptar dd/mm/aaaa y d/m/aaaa, normalizando día y mes a dos dígitos. MUST validar que la fecha no esté vacía, tenga un formato reconocido, represente una fecha existente y no sea posterior al día actual en la zona horaria del dispositivo del usuario; MUST mostrar un mensaje claro asociado a la fecha. MUST NOT corregir automáticamente una fecha inexistente ni trasladarla a otro mes. No existe una fecha mínima.
- **FR-017**: La interfaz MUST recortar los espacios en blanco Unicode al inicio y al final de la descripción, MUST normalizar los saltos CRLF/CR a LF y MUST validar que el texto resultante no supere los 200 caracteres, mostrando un mensaje claro asociado a la descripción. MUST NOT colapsar espacios internos ni descartar tabs o saltos de línea internos.
- **FR-018**: Mientras existan errores de validación, la interfaz MUST bloquear el envío del formulario.
- **FR-019**: Los mensajes de error de la interfaz MUST ser claros y comprensibles, MUST asociarse al campo correspondiente y MUST NOT exponer detalles internos ni información sensible.
- **FR-020**: Tras un intento de envío con errores, la interfaz MUST conservar los datos ya ingresados por el usuario.

**Validación y persistencia en el backend**

- **FR-021**: El backend MUST validar de forma independiente la integridad de todos los datos antes de guardarlos, sin confiar en la validación de la interfaz.
- **FR-022**: La validación del backend MUST incluir, como mínimo, presencia de los campos obligatorios, monto numérico estrictamente mayor que cero, fecha válida y no futura, valores permitidos para moneda, categoría, medio de pago y tipo de gasto, monto con como máximo dos decimales y dentro del rango de 0,01 a 999.999.999,99 inclusive, y descripción recortada, con saltos normalizados a LF y de como máximo 200 caracteres. Cualquier valor recibido que no sea uno de los permitidos para moneda, categoría, medio de pago o tipo de gasto MUST rechazarse. Para decidir si la fecha es futura, el backend MUST usar la zona horaria del dispositivo informada al guardar; si falta o es inválida, MUST aplicar una tolerancia de un día respecto de su propia fecha como excepción acotada. Esa tolerancia MUST NOT interpretarse como un permiso para que el usuario registre fechas futuras.
- **FR-023**: El backend MUST tratar los montos con precisión monetaria exacta, sin errores de redondeo por punto flotante.
- **FR-024**: Si los datos son inválidos, el backend MUST rechazar la operación y MUST NOT persistir ningún gasto ni dejar datos parciales o inconsistentes.
- **FR-025**: Si los datos son válidos, el backend MUST persistir el gasto vinculado al usuario autenticado como único creador.
- **FR-026**: Los errores del backend MUST informarse con mensajes claros que no expongan detalles internos, credenciales ni información sensible.

**Envío, duplicados y resiliencia**

- **FR-027**: Mientras una operación de guardado está en curso, la interfaz MUST bloquear los envíos repetidos y el reenvío del mismo formulario, de modo que un doble clic accidental no cree más de un gasto. El botón de confirmar MUST permanecer deshabilitado y MUST mostrar un texto claro como "Guardando…" o un indicador visible equivalente hasta que la operación termine; el formulario MUST permanecer visible. Si el backend ya persistió el gasto pero la respuesta se perdió por timeout, el reintento de la misma operación MUST NOT crear un gasto adicional.
- **FR-028**: Si la sesión expiró o el usuario dejó de estar autenticado al confirmar, el sistema MUST NOT persistir el gasto, MUST informar la situación de forma clara y MUST reutilizar el inicio de sesión existente. MUST conservar los datos ingresados solo mientras el formulario permanezca visible; si la reautenticación obliga a abandonar la pantalla, MUST descartarlos y MUST NOT restaurarlos automáticamente después de volver a iniciar sesión. MUST NOT guardar borradores.
- **FR-029**: Ante una falla del servidor o de la red, el sistema MUST mostrar un mensaje claro, MUST NOT mostrar una notificación de éxito, MUST NOT dejar registros parciales y MUST conservar los datos del formulario para permitir un nuevo intento.
- **FR-030**: El sistema MUST NOT mostrar una notificación de éxito si el gasto no quedó persistido.

**Resultado exitoso**

- **FR-031**: Tras un guardado exitoso, el sistema MUST mostrar al usuario una notificación de éxito. La notificación MUST cerrarse automáticamente a los 5 segundos, salvo que el usuario la cierre antes, y MUST permitir cerrarla manualmente.
- **FR-032**: Tras el guardado exitoso y la notificación de éxito, el sistema MUST dejar el formulario en su estado inicial: monto y descripción vacíos, fecha precargada con el día actual, moneda precargada según FR-011 y categoría, tipo y medio de pago sin selección. MUST mantener al usuario en la misma pantalla para registrar otro gasto; MUST NOT redirigir ni actualizar un listado de gastos.

**Accesibilidad y uso móvil**

- **FR-033**: La interfaz MUST ser operable solo con teclado, MUST asociar una etiqueta a cada campo, MUST mostrar un foco visible y MUST asociar cada mensaje de error con su campo correspondiente.
- **FR-034**: La interfaz MUST ser usable en navegador móvil: todos los campos y el botón de confirmar MUST ser visibles y utilizables sin desplazamiento horizontal.

### Key Entities *(include if data involved)*

- **Gasto personal**: representa un gasto registrado por un usuario. Atributos: monto con precisión monetaria exacta, de hasta dos decimales y entre 0,01 y 999.999.999,99 inclusive, ingresado en formato argentino con coma decimal y mostrado con coma decimal, moneda (ARS, USD o EUR), fecha (pasada o el día actual, sin futuras, mostrada e ingresada como dd/mm/aaaa), categoría (lista fija de Alimentos, Transporte, Vivienda, Servicios, Salud, Educación, Ocio, Compras, Mascotas u Otros), tipo ("Recurrente" o "Único", usado solo como clasificación), medio de pago (lista fija de Efectivo, Tarjeta de débito, Tarjeta de crédito, Transferencia, Billetera virtual u Otro) y descripción opcional de como máximo 200 caracteres después de recortar espacios en blanco Unicode al inicio y al final y de normalizar los saltos CRLF/CR a LF, conservando espacios internos, tabs y saltos de línea. Pertenece a un único usuario creador y no tiene otros participantes, divisiones, deudas ni vínculos con amigos o grupos.
- **Usuario**: usuario autenticado existente que crea el gasto. Esta feature solo lo referencia como creador; no modifica sus datos ni el mecanismo de autenticación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los gastos válidos persistidos queda vinculado exclusivamente al usuario autenticado que los creó; el 0 % queda vinculado a otro usuario o sin creador.
- **SC-002**: El 100 % de los envíos con datos inválidos se bloquea y el 0 % se persiste.
- **SC-003**: El 0 % de los dobles envíos accidentales de una misma operación, incluidos los reintentos tras un timeout con persistencia previa, genera más de un gasto.
- **SC-004**: El 100 % de la siguiente batería representativa de montos, probada en ARS, USD y EUR, se conserva y se muestra exactamente con el valor ingresado, con coma decimal, sin diferencias atribuibles al redondeo de punto flotante: `0,01`, `0,10`, `0,99`, `1,00`, `1,10`, `10,10`, `100,01`, `1.234,56`, `999.999,99`, `123.456.789,01`, `999.999.999,98` y `999.999.999,99`. La verificación cubre ingreso en formato argentino válido, persistencia, lectura y visualización, e incluye ceros finales y ambos extremos del rango admitido.
- **SC-005**: El 100 % de las fallas de servidor o de red muestra un mensaje claro, no reporta éxito falso y conserva los datos ingresados.
- **SC-006**: El 0 % de los mensajes de error muestra detalles internos, credenciales o información sensible en las siguientes pruebas de error: campos obligatorios vacíos; monto no numérico; monto cero o negativo; monto con más de dos decimales; monto fuera de rango (`0,00` y `1.000.000.000,00`); monto con punto decimal; fecha vacía, con formato no reconocido o inexistente (`31/02`); fecha futura; descripción de más de 200 caracteres; valores manipulados de moneda, categoría, medio de pago o tipo; sesión expirada o usuario no autenticado; y falla de red o servidor, incluido el timeout con persistencia previa seguido de reintento. En ningún caso el mensaje de interfaz ni la respuesta de error debe contener trazas de pila, nombres de clases o paquetes, SQL, identificadores internos, tokens o cookies, contraseñas, correo u otros datos personales, ni datos de otro usuario; tampoco debe aparecer la notificación de éxito ni quedar datos parciales.
- **SC-007**: El 100 % de los guardados exitosos muestra la notificación de éxito, deja el formulario en su estado inicial y mantiene al usuario en la misma pantalla; la notificación se cierra a los 5 segundos o antes si el usuario la cierra.
- **SC-008**: El 0 % de las operaciones rechazadas deja gastos parciales o datos inconsistentes.
- **SC-009**: El 100 % de los recorridos del formulario puede completarse solo con teclado, con etiquetas asociadas, foco visible y errores asociados a su campo.
- **SC-010**: El 100 % de los campos y del botón de confirmar es visible y utilizable en un navegador móvil sin desplazamiento horizontal.
- **SC-011**: El 100 % de las fechas válidas se muestra como dd/mm/aaaa y el 100 % de las fechas con formato no reconocido o inexistentes se rechaza sin corrección ni traslado a otro mes.

### Trazabilidad con el backlog

El backlog no está versionado en el repositorio, por lo que no es posible citar su texto literal. Los enunciados 3–7 provienen del mapeo ya registrado en `checklists/requirements.md`; los enunciados 1–2 se reconstruyen a partir del input de la feature y de esa misma trazabilidad. No se agregó ningún requisito nuevo y no se detectan criterios sin cobertura.

| Criterio del backlog | Requisitos funcionales | Escenarios | Criterios de éxito |
|----------------------|------------------------|------------|--------------------|
| 1. Acceso autenticado y registro del gasto | FR-001–FR-005 | US1-1, US3-1, US3-3 | SC-001 |
| 2. Gasto estrictamente personal | FR-006 | US1-5 y caso límite de creador manipulado | SC-001 |
| 3. Validación de campos obligatorios y formatos | FR-013, FR-015–FR-020 | US2-1 a US2-11 | SC-002 |
| 4. Límites y reglas de monto, fecha y descripción | FR-009, FR-014, FR-016, FR-022 | US1-4, US1-10, US1-12, US1-13; US2-3, US2-4, US2-5, US2-9, US2-10, US2-11 | SC-004, SC-011 |
| 5. Validación independiente y persistencia en el backend | FR-021–FR-025 | US2-7; US3-3, US3-4, US3-7 | SC-001, SC-002, SC-008 |
| 6. Errores claros sin información sensible | FR-019, FR-026 | US3-5, US3-6 | SC-006 |
| 7. Resultado posterior al guardado exitoso | FR-031, FR-032 | US1-1, US1-14 | SC-007 |

## Assumptions

- El usuario cuenta con conectividad estable mientras completa el formulario.
- La autenticación, la sesión y la protección de rutas existentes de 001 y 004 son suficientes para identificar al usuario; no se requiere ni se agrega un mecanismo nuevo.
- **Decisión confirmada**: las rutas nuevas de gastos quedan bajo el mismo ámbito de cuenta verificada que `/api/users/**`. El control se resuelve en el servidor contra la base (exige `email_verified_at`) sin confiar en la cookie, igual que las rutas de perfil; como el inicio de sesión ya rechaza cuentas no verificadas, esto no restringe a ningún usuario con sesión válida.
- No se guardan borradores del gasto. Ante una sesión expirada, los datos se conservan solo mientras el formulario permanece visible; si la reautenticación implica abandonar la pantalla, se descartan y el usuario debe reingresarlos.
- El usuario autenticado es la única persona que puede registrar el gasto; no existe en esta iteración ningún flujo de administración o suplantación.
- Un gasto registrado deliberadamente en dos operaciones separadas, aun con datos idénticos, se considera dos gastos distintos; el bloqueo de FR-027 aplica al reenvío accidental de una misma operación, incluido el reintento tras un timeout con persistencia previa.
- Una descripción vacía, omitida o compuesta solo por espacios, tabs o saltos de línea después del recorte se guarda como ausente, sin que ello afecte la validez del gasto. La descripción se recorta en sus extremos, normaliza los saltos CRLF/CR a LF, conserva espacios internos, tabs y saltos de línea, y admite como máximo 200 caracteres después del recorte y la normalización.
- La precisión monetaria exacta se conserva en toda la interfaz, el backend y el almacenamiento.
- "Recurrente" es solo una clasificación del gasto en esta iteración: no genera gastos futuros ni requiere frecuencia.
- La preferencia de monedas del perfil de 001 se usa solo para precargar la moneda del formulario; no limita las monedas que el usuario puede elegir. Si hay varias preferidas, se elige la primera en el orden fijo ARS → USD → EUR, sin depender del orden de selección original. Si falta, está vacía, contiene un valor no reconocido o el perfil no puede consultarse, se precarga ARS; en el último caso se muestra un aviso no bloqueante.
- El día actual se determina con la zona horaria del dispositivo del usuario; no se admiten fechas futuras y no existe una fecha mínima.
- El backend usa la zona horaria del dispositivo informada al guardar para validar fechas futuras; si falta o es inválida, aplica una tolerancia de un día como excepción acotada, no como permiso para registrar fechas futuras.
- Si el formulario cruza la medianoche, la fecha precargada se actualiza solo si el usuario no la modificó; si la modificó, se conserva.
- El monto admite como máximo dos decimales para ARS, USD y EUR y debe estar entre 0,01 y 999.999.999,99 inclusive. Se ingresa solo en formato argentino, con coma decimal y punto o espacio como separador de miles, y se muestra con coma decimal; las entradas con punto decimal o formato malformado se rechazan sin redondear.
- Tras el guardado exitoso, la notificación aparece y se cierra automáticamente a los 5 segundos salvo cierre manual; el formulario vuelve a su estado inicial y el usuario permanece en la misma pantalla; no se redirige ni se actualiza un listado de gastos.
- Mientras la operación de guardado está en curso, el botón de confirmar se muestra deshabilitado con un texto claro como "Guardando…" o un indicador visible equivalente, y el formulario permanece visible.
- La accesibilidad básica y el uso en navegador móvil forman parte de esta iteración; no se exigen umbrales medibles de rendimiento ni localización multi-idioma/multi-región.
- La fecha se muestra como dd/mm/aaaa; la entrada d/m/aaaa se normaliza a dos dígitos y las fechas inexistentes se rechazan sin corrección.

## Dependencies

- **001 (Registro de usuario)**: preferencia de monedas del perfil para precargar la moneda del formulario, además de la identidad del usuario.
- **004 (Inicio de sesión)**: sesión, protección de rutas y pantalla de inicio de sesión existente.
- No existen dependencias con funcionalidades de amigos, grupos, gastos compartidos, deudas o balances, porque aún no existen y están fuera del alcance.

## Out of Scope

- Gastos compartidos, división de gastos (igual, por montos o por porcentajes), grupos, amigos, deudas o balances.
- Listado, edición o eliminación de gastos, salvo lo estrictamente necesario para cumplir los criterios de aceptación.
- Pagos, liquidaciones o conversión de monedas.
- Plantillas o presets de gastos.
- Cambios al mecanismo de autenticación existente.
- Umbrales medibles de rendimiento; esta iteración no fija tiempos de respuesta ni de guardado.
- Localización multi-idioma o multi-región; la interfaz se mantiene en español para Argentina.
- Requisitos específicos de una aplicación móvil nativa; esta iteración cubre el uso en navegador móvil.
