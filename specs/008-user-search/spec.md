# Feature Specification: Buscador de usuarios registrados

**Feature Branch**: `feat/user-search`

**Created**: 2026-10-24

**Status**: Draft

**Input**: User description: "Buscador de usuarios registrados. Un usuario autenticado y con cuenta verificada puede buscar a otros usuarios registrados para encontrarlos (es la base para que después se puedan enviar solicitudes de amistad y agregar miembros a grupos, que quedan FUERA de alcance). Decisiones: la búsqueda es por nombre, apellido o email, parcial y sin distinguir mayúsculas/minúsculas ni espacios al inicio/fin, requiere al menos 2 caracteres (si no, no se busca y se muestra un aviso); solo aparecen cuentas verificadas y nunca el propio usuario; máximo 20 resultados ordenados por nombre y apellido; cada resultado muestra solo nombre, apellido y email (nunca CBU/CVU, monedas, contraseña ni datos privados); se usa el avatar por defecto para todos los resultados porque el endpoint de avatar actual solo expone el avatar propio (mostrar avatares ajenos queda fuera de alcance). Sin resultados: mensaje "No se encontraron usuarios". Error de red: mensaje fijo de reintento. Backend: nuevo endpoint GET bajo /api/users/** para heredar la regla de cuenta verificada de 003 (sin sesión o cuenta no verificada -> 401). Frontend: pantalla protegida con un campo de búsqueda y la lista de resultados, accesible desde la barra de navegación (SiteNav). Sin tablas nuevas: solo lee la tabla users existente. Fuera de alcance: enviar solicitudes de amistad, gestionar solicitudes, red de contactos, grupos, avatares ajenos, paginación."

## Relación con otras especificaciones

- **Reutiliza de 001 (Registro de usuario) y 004 (Inicio de sesión)**: la sesión mediante la cookie `amano_auth`, el perfil del usuario autenticado y la pantalla protegida como patrón de acceso. La búsqueda solo está disponible para una persona autenticada.
- **Reutiliza de 003 (Verificación de correo electrónico)**: la regla de negocio que impide el acceso de cuentas no verificadas. El nuevo endpoint bajo `/api/users/**` hereda esa regla; sin sesión o con una cuenta no verificada responde 401.
- **No modifica** el comportamiento de las especificaciones anteriores. La búsqueda es de solo lectura y no crea relaciones entre usuarios.

Los identificadores FR-### y SC-### de esta especificación son propios de 008 y no coinciden necesariamente en número con los de las especificaciones anteriores.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Buscar usuarios registrados y ver resultados (Priority: P1)

Una persona autenticada y con cuenta verificada entra a la pantalla "Buscar usuarios" desde la barra de navegación, escribe al menos 2 caracteres de un nombre, apellido o correo electrónico y obtiene una lista de coincidencias. La búsqueda es parcial y no distingue mayúsculas de minúsculas ni espacios al inicio o al final. La lista muestra como máximo 20 resultados ordenados por nombre y, a igual nombre, por apellido. Cada resultado expone únicamente nombre, apellido, correo electrónico y el avatar por defecto.

**Why this priority**: Es el valor central de la funcionalidad: permitir que una persona encuentre a otras cuentas registradas. Sin esta búsqueda no existe ninguna de las demás historias.

**Independent Test**: Puede probarse completamente iniciando sesión con una cuenta verificada, abriendo la pantalla de búsqueda, ingresando un término de al menos 2 caracteres y comprobando que aparecen las coincidencias esperadas con los datos permitidos y en el orden definido.

**Acceptance Scenarios**:

1. **Given** una persona autenticada y verificada en la pantalla "Buscar usuarios", **When** ingresa un término de 2 o más caracteres que coincide parcialmente con el nombre, el apellido o el correo de otras cuentas verificadas, **Then** ve una lista con las coincidencias, cada una mostrando solo nombre, apellido, correo electrónico y avatar por defecto.
2. **Given** una búsqueda con un término escrito en mayúsculas o minúsculas, **When** se comparan los datos, **Then** el resultado es el mismo sin distinguir mayúsculas de minúsculas.
3. **Given** un término con espacios al inicio y/o al final, **When** se procesa la búsqueda, **Then** los espacios se recortan y la búsqueda se realiza con el término resultante.
4. **Given** más de 20 cuentas coincidentes, **When** se muestran los resultados, **Then** se listan como máximo 20, ordenados por nombre y apellido.
5. **Given** coincidencias con el mismo nombre, **When** se ordenan los resultados, **Then** se desempata por apellido.
6. **Given** una coincidencia con la cuenta de la propia persona, **When** se muestran los resultados, **Then** su propia cuenta nunca aparece en la lista.

---

### User Story 2 - Aviso cuando el término es demasiado corto (Priority: P2)

Si la persona escribe menos de 2 caracteres (después de recortar espacios), el sistema no ejecuta ninguna búsqueda y muestra un aviso que explica que debe ingresar al menos 2 caracteres. Al llegar a 2 caracteres la búsqueda se habilita.

**Why this priority**: Evita búsquedas demasiado amplias y da una guía clara sobre el mínimo requerido; sin esta validación la búsqueda seguiría siendo útil, por lo que es secundaria.

**Independent Test**: Puede probarse escribiendo 0 o 1 carácter, verificando que no se ejecuta ninguna consulta y que aparece el aviso, y luego escribiendo 2 caracteres para comprobar que la búsqueda se ejecuta.

**Acceptance Scenarios**:

1. **Given** la pantalla de búsqueda sin texto o con un solo carácter, **When** la persona intenta buscar, **Then** no se ejecuta ninguna consulta y se muestra un aviso indicando que se requieren al menos 2 caracteres.
2. **Given** un término formado únicamente por espacios, **When** se recorta y queda vacío, **Then** no se ejecuta ninguna búsqueda y se muestra el mismo aviso.
3. **Given** un término de exactamente 2 caracteres tras recortar espacios, **When** se busca, **Then** la búsqueda se ejecuta con normalidad.

---

### User Story 3 - Acceso protegido y resultados limitados a cuentas verificadas (Priority: P3)

Solo una persona autenticada con cuenta verificada puede usar el buscador. Sin sesión o con una cuenta no verificada, el acceso es rechazado con 401 y la pantalla no muestra datos. Además, la lista de resultados solo incluye cuentas verificadas, de modo que ninguna cuenta no verificada sea descubrible.

**Why this priority**: Protege la privacidad y respeta la regla de negocio de 003. Es condición de confianza del buscador, aunque la búsqueda básica ya aporta valor.

**Independent Test**: Puede probarse solicitando el endpoint y abriendo la pantalla sin sesión, con una cuenta no verificada y con una cuenta verificada, comprobando en cada caso el rechazo o el acceso, y verificando que una cuenta no verificada nunca aparece entre los resultados.

**Acceptance Scenarios**:

1. **Given** una solicitud de búsqueda sin sesión válida, **When** se procesa, **Then** el sistema responde 401 y no devuelve resultados.
2. **Given** una solicitud de búsqueda con una cuenta no verificada, **When** se procesa, **Then** el sistema responde 401 y no devuelve resultados.
3. **Given** una cuenta no verificada cuyos datos coinciden con el término buscado, **When** una persona verificada busca, **Then** esa cuenta no aparece en los resultados.
4. **Given** una persona sin sesión que intenta abrir la pantalla de búsqueda, **When** la aplicación evalúa el acceso, **Then** se la redirige al flujo de acceso sin mostrar el campo de búsqueda ni resultados.

---

### User Story 4 - Estados sin resultados y de error de red (Priority: P4)

Cuando la búsqueda no encuentra coincidencias, la pantalla muestra "No se encontraron usuarios". Si la consulta falla por un problema de red o de disponibilidad del servidor, la pantalla muestra un mensaje fijo de reintento, sin inventar datos ni dejar resultados obsoletos.

**Why this priority**: Completa la experiencia con estados claros y evita confusiones; la búsqueda con resultados ya entrega el valor principal.

**Independent Test**: Puede probarse buscando un término sin coincidencias y verificando el mensaje exacto, y simulando un fallo de red para verificar el mensaje fijo de reintento y la posibilidad de volver a intentar.

**Acceptance Scenarios**:

1. **Given** una búsqueda válida sin coincidencias, **When** la consulta finaliza, **Then** la pantalla muestra exactamente "No se encontraron usuarios".
2. **Given** una búsqueda válida, **When** la consulta falla por error de red o de servidor, **Then** la pantalla muestra un mensaje fijo de reintento y no muestra resultados incompletos o ficticios.
3. **Given** el estado de error de red, **When** la persona vuelve a intentar la búsqueda, **Then** la consulta se ejecuta nuevamente.

---

### Edge Cases

- **Término vacío o solo espacios**: no se busca y se muestra el aviso de mínimo 2 caracteres.
- **Término de 1 carácter tras recortar**: no se busca y se muestra el aviso.
- **Término de 2 caracteres**: la búsqueda se ejecuta con normalidad.
- **Coincidencia solo en el correo electrónico**: la cuenta aparece en los resultados, con solo nombre, apellido y correo visibles.
- **Cuenta no verificada coincidente**: nunca aparece en los resultados.
- **Cuenta propia coincidente**: nunca aparece en los resultados.
- **Más de 20 coincidencias**: se muestran como máximo 20; no hay paginación ni acceso a resultados adicionales en esta feature.
- **Empate de nombre y apellido**: el orden entre esos resultados no está definido por esta especificación; cualquier orden estable es aceptable.
- **Espacios internos**: se consideran parte del término; solo se recortan los espacios al inicio y al final.
- **Error de red durante una búsqueda con resultados previos**: no se muestran resultados obsoletos; se muestra el mensaje fijo de reintento.
- **Cambio del término mientras una consulta está en curso**: la pantalla refleja el resultado correspondiente a la última búsqueda solicitada, sin mezclar resultados de términos distintos.
- **Sesión expirada durante el uso del buscador**: la siguiente consulta es rechazada y la persona es redirigida al flujo de acceso; no quedan datos visibles.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST ofrecer una pantalla protegida de búsqueda de usuarios, accesible desde la barra de navegación (`SiteNav`), con un campo de búsqueda y una lista de resultados.
- **FR-002**: La búsqueda MUST considerar coincidencias parciales en nombre, apellido o correo electrónico.
- **FR-003**: La búsqueda MUST ignorar mayúsculas y minúsculas y MUST recortar los espacios al inicio y al final del término antes de buscar.
- **FR-004**: La búsqueda MUST requerir al menos 2 caracteres después del recorte de espacios; con menos de 2 caracteres el sistema NO MUST ejecutar la consulta y MUST mostrar un aviso que indique el mínimo requerido.
- **FR-005**: Los resultados MUST incluir únicamente cuentas verificadas y MUST excluir siempre la cuenta de la persona que realiza la búsqueda.
- **FR-006**: El sistema MUST devolver como máximo 20 resultados, ordenados por nombre y, a igual nombre, por apellido.
- **FR-007**: Cada resultado MUST mostrar únicamente nombre, apellido, correo electrónico y el avatar por defecto; NO MUST mostrar CBU/CVU, monedas, contraseña ni ningún otro dato privado.
- **FR-008**: Todos los resultados MUST usar el avatar por defecto; mostrar avatares de otras cuentas queda fuera de alcance.
- **FR-009**: Cuando una búsqueda válida no tenga coincidencias, la pantalla MUST mostrar exactamente "No se encontraron usuarios".
- **FR-010**: Ante un error de red o de disponibilidad del servidor, la pantalla MUST mostrar un mensaje fijo de reintento y MUST NOT mostrar resultados incompletos, obsoletos o ficticios.
- **FR-011**: El backend MUST exponer un nuevo endpoint `GET` bajo `/api/users/**` para la búsqueda, que herede la regla de cuenta verificada de 003: sin sesión o con una cuenta no verificada MUST responder 401 y MUST NOT devolver resultados.
- **FR-012**: La feature MUST NOT crear tablas nuevas ni modificar el esquema; solo MUST leer la tabla de usuarios existente.
- **FR-013**: La feature MUST NOT enviar solicitudes de amistad, gestionar solicitudes, administrar una red de contactos ni agregar miembros a grupos.
- **FR-014**: Los datos personales expuestos por la búsqueda MUST limitarse a los campos indicados en FR-007; el correo electrónico es el único dato de contacto visible.
- **FR-015**: La pantalla de búsqueda MUST ser de solo lectura: ninguna acción de la pantalla modifica datos de otras cuentas.
- **FR-016**: La feature MUST NOT ofrecer paginación; los resultados se limitan al máximo definido en FR-006.

### Key Entities *(include if feature involves data)*

- **Cuenta de Usuario** (existente, solo lectura): nombre, apellido, correo electrónico y estado de verificación. Es la única fuente de datos de la búsqueda; no se crean ni modifican registros.
- **Resultado de Búsqueda**: representación acotada de una cuenta coincidente que contiene únicamente nombre, apellido, correo electrónico y avatar por defecto.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona autenticada y verificada encuentra coincidencias relevantes escribiendo al menos 2 caracteres y ve los resultados en menos de 2 segundos en condiciones normales de red.
- **SC-002**: El 100 % de las búsquedas devuelve como máximo 20 resultados y respeta el orden por nombre y apellido.
- **SC-003**: El 100 % de los resultados corresponde a cuentas verificadas y excluye la cuenta de la persona que busca.
- **SC-004**: El 100 % de las solicitudes sin sesión o con cuenta no verificada es rechazado con 401 y no recibe resultados.
- **SC-005**: Ningún resultado expone CBU/CVU, monedas, contraseña ni datos privados distintos de nombre, apellido y correo electrónico, en el 100 % de los casos.
- **SC-006**: El 100 % de las búsquedas con menos de 2 caracteres se detiene con el aviso correspondiente, sin ejecutar consultas.
- **SC-007**: Una búsqueda válida sin coincidencias muestra "No se encontraron usuarios" en el 100 % de los casos, y un error de red muestra el mensaje fijo de reintento sin resultados obsoletos.
- **SC-008**: La pantalla de búsqueda es alcanzable desde la barra de navegación en 1 interacción y carga su estado inicial en menos de 2 segundos.

## Assumptions

- La sesión, la cookie `amano_auth` y el concepto de cuenta verificada son los definidos en 001, 003 y 004; esta feature no los reimplementa.
- El usuario autenticado que realiza la búsqueda ya está identificado por su sesión; el servidor lo excluye de los resultados sin que el cliente envíe su identificador.
- La búsqueda se realiza sobre los datos ya almacenados de las cuentas; no requiere sincronización, indexación externa ni datos adicionales.
- Las coincidencias se evalúan sobre el texto tal como está almacenado; el tratamiento de acentos o diacríticos no se especifica y no se exige como equivalencia en esta feature.
- El avatar por defecto es el ya utilizado por la aplicación cuando una cuenta no tiene avatar propio o cuando no corresponde mostrar avatares ajenos.
- Los textos de la interfaz se escriben en español, en línea con el resto de la aplicación.
- No se crean tablas ni migraciones nuevas; la feature es de solo lectura.
- Las solicitudes de amistad, la gestión de solicitudes, la red de contactos, los grupos, los avatares ajenos y la paginación quedan explícitamente fuera de alcance.
