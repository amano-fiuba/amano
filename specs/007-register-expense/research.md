# Research: Registro de gasto personal (007)

**Feature**: `007-register-expense` | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Este documento resuelve los puntos que la especificación delegó al plan. Para cada uno se
registra la **decisión**, el **rationale** y las **alternativas evaluadas**. Cuando una
decisión no está confirmada por la spec, se marca como **supuesto explícito** y se enumera en
`plan.md` → "Decisiones pendientes y supuestos explícitos" (Constitución IV).

Punto de partida verificado en el repositorio:

- Backend: Java 25, Spring Boot 4.1.1, Jackson 3 (`tools.jackson`), JPA
  (`ddl-auto=validate`, `open-in-view=false`), Flyway (`V1`–`V3`), seguridad stateless con
  cookie JWT (`amano_auth`) y filtro `JwtCookieAuthenticationFilter`.
- Errores: RFC 7807 `ProblemDetail` vía `ProblemDetailExceptionHandler`; validación de servicio
  con `FieldValidationException` (`properties.fields`).
- Migraciones: numeración `V#__snake_case.sql`, solo `CREATE/ALTER` (sin `DELETE`), CHECK con
  nombre `ck_*`, FK `fk_*`, índices `uq_*`/`ix_*`.
- Frontend: React 19 + react-router 8 + react-hook-form 7 + Vite, cliente único
  `src/api/authClient.js` con retornos `{ ok, ... }`, utilidades puras en `src/validation/`,
  pruebas Vitest/RTL `*.test.jsx`. **No existe librería ni componente de toasts**: los avisos
  son mensajes inline `role="status"` / `role="alert"`.
- Cobertura: JaCoCo `LINE 0.85` en `pom.xml`; Vitest `lines/statements 85` en `vite.config.js`.
  CI: `.github/workflows/ci.yml` corre `mvn -B verify` y
  `npm ci && npm run lint && npm run test:coverage && npm run build`.

---

## D1. Protección contra duplicados (FR-027, US3-4, US3-7, SC-003)

**Decisión**: clave de idempotencia generada por el cliente (`crypto.randomUUID()`) y enviada
en la cabecera HTTP `Idempotency-Key` (UUID) en cada `POST /api/expenses`. El backend la
persiste en `expenses.idempotency_key UUID NOT NULL` con la restricción
`UNIQUE (user_id, idempotency_key)`.

- **Generación y renovación (frontend)**: la clave se crea la primera vez que el usuario
  confirma una operación y se conserva en estado para esa operación. Un **reintento** de la
  misma operación (tras timeout o fallo de red) reutiliza la misma clave. La clave se renueva
  **después de un guardado exitoso** (junto con el reseteo de FR-032) y, por lo tanto, todo
  gasto iniciado luego de un reseteo es una operación distinta. Un rechazo de validación no
  persiste nada, así que reutilizar la clave en el siguiente intento es inocuo.
- **Reintento (backend)**: si ya existe un gasto del mismo usuario con esa clave, el backend
  **no valida ni inserta de nuevo**: devuelve la representación persistida con **200 OK**
  (mismo recurso, sin segundo gasto). La primera creación responde **201 Created**.
- **Carrera entre dos solicitudes simultáneas con la misma clave**: ambas consultan por
  `(user_id, key)` y no encuentran nada; ambas intentan insertar. PostgreSQL serializa por el
  índice único: la segunda inserción espera a que la primera termine y, si la primera
  confirma, falla con violación de unicidad. El servicio `@Transactional` propaga
  `DataIntegrityViolationException`; el controlador la captura (la transacción ya quedó
  revertida), relee el gasto ganador por `(user_id, key)` en una transacción nueva y responde
  **200** con ese recurso. Si la relectura no encontrara nada (el ganador abortó y nuestra
  inserción no se ejecutó por la excepción), se considera error inesperado (500).
- **Dos gastos idénticos deliberados**: al renovar la clave tras cada éxito, dos operaciones
  legítimamente separadas tienen claves distintas y producen dos filas, respetando el
  Assumption del spec (FR-027 solo bloquea el reenvío de *una misma* operación).
- **Estado "Guardando…" y bloqueo de la UI** (FR-027): complementan la garantía pero no la
  reemplazan; cubren el doble clic dentro de la misma pestaña, no el reintento del cliente
  ni el timeout.

**Alternativas evaluadas**:

1. **Solo bloqueo del botón / `isSubmitting`**: rechazada. No protege el caso (b) del spec
   (timeout con persistencia previa) ni un segundo cliente/dispositivo.
2. **Deduplicar por campos** (mismo monto/fecha/categoría…): rechazada. Impediría dos gastos
   idénticos deliberados, que el spec declara válidos.
3. **Tabla separada de idempotencia** (`expense_idempotency`): rechazada. Añade tabla y join
   sin mejorar la semántica ni resolver la carrera de otra forma; una columna con índice único
   es suficiente a esta escala.
4. **Cabecera estándar `Idempotency-Key` vs. campo del cuerpo**: elegida la **cabecera**.
   Mantiene la representación del recurso libre de preocupaciones de transporte, es una
   convención ampliamente reconocida y no exige cambios de CORS (la API es del mismo origen vía
   proxy `/api`).
5. **Replay 200 vs. 201**: elegido **200**. Es explícito ("el recurso ya existía") y el cliente
   acepta ambos como éxito; devolver 201 sugeriría una creación nueva.

---

## D2. Dinero y precisión (FR-014, FR-022, FR-023, SC-004)

**Decisión**:

- **Columna**: `amount NUMERIC(11,2) NOT NULL` (9 dígitos enteros + 2 decimales ⇒ máximo
  `999999999.99`, exactamente el rango admitido) con `CHECK (amount >= 0.01)`. El tope superior
  queda garantizado por la precisión; se documenta y se prueba.
- **Java**: `BigDecimal` (`precision = 11, scale = 2`) en la entidad, nunca `double`/`float`.
  Comparaciones con `compareTo` (no `equals`, que incluye escala).
- **Validación de escala**: se rechaza `scale() > 2` **sin redondear** (no se usa
  `setScale` ni `RoundingMode`); el valor canónico recibido siempre tiene hasta 2 decimales.
- **Wire format**: el monto se transporta como **string decimal canónico** en JSON, con punto
  como separador decimal y exactamente dos decimales (p. ej. `"10.10"`, `"0.01"`,
  `"999999999.99"`). El DTO de entrada y el de salida exponen `String amount`; el servicio
  convierte a/desde `BigDecimal` con `new BigDecimal(canonical)` (nunca `double`).
- **Por qué string**: evita que Jackson serialice `BigDecimal` como número JSON y que
  `JSON.parse`/`Number` en el navegador colapse `10.10` a `10.1`, perdiendo la escala exigida
  por SC-004. El frontend **nunca** usa `Number`/`parseFloat` para el valor enviado: construye
  y valida la cadena canónica con manipulación de strings.
- **Canónico**: `entero + "." + centavos` con `centavos` de 2 dígitos (`1234` → `"1234.00"`,
  `"1.234,56"` → `"1234.56"`).
- **Frontend**: `parseArgentineAmount(raw)` devuelve `{ ok, canonical, error }`; la
  visualización usa coma (`10,10`) reconstruida desde la cadena, sin pasar por `Number`.

**Alternativas evaluadas**:

1. **`BigDecimal` como número JSON** (default de Jackson): rechazada por la pérdida de escala
   en JS descrita.
2. **`@JsonFormat(shape = STRING)` sobre `BigDecimal`**: válida, pero acopla la forma al
   framework y deja dos representaciones (número interno + string). Se prefiere el `String`
   explícito en el DTO, más simple de testear y de documentar.
3. **Enteros de centavos** (`BIGINT` de centavos): rechazada. Exige conversión mental y
   duplicaría el formato; `NUMERIC` ya garantiza exactitud.
4. **Redondear a 2 decimales** al persistir: rechazada por FR-014 ("sin redondear"): un valor
   con más decimales debe rechazarse, no ajustarse.

---

## D3. Parseo del monto en formato argentino (FR-013, FR-014)

**Decisión**: algoritmo puro en `frontend/src/validation/expenseValidation.js`
(`parseArgentineAmount`), con la misma doctrina que `registerValidation.js` (módulo puro,
testeable de forma aislada). El backend **no** acepta formato argentino: recibe el decimal
canónico string de D2.

Algoritmo (sobre el texto con `trim` de espacios exteriores):

1. Si está vacío ⇒ error "El monto es obligatorio".
2. Rechazar si contiene caracteres fuera de `[0-9., ]` (solo dígitos, coma, punto, espacio).
3. Rechazar si hay **más de una coma** (separador decimal único).
4. Separar en parte entera y fracción por la coma (si existe). Si hay coma, ambas partes deben
   tener dígitos (rechaza `1234,` y `,5`).
5. La fracción debe tener **1 o 2 dígitos**; con 3 o más se rechaza (`1,234` ⇒ error por tres
   decimales). Sin coma ⇒ fracción vacía = `00`.
6. La parte entera, tras quitar puntos y espacios usados como separador de miles, debe:
   - no mezclar `.` y espacio en el mismo número;
   - respetar agrupación de a 3 desde la derecha, con primer grupo de 1 a 3 dígitos
     (`^\d{1,3}(\.\d{3})*$`, `^\d{1,3}( \d{3})*$` o `^\d+$`);
   - dar al menos un dígito.
   Esto convierte `1.234` → `1234` y `1 234` → `1234`, y **rechaza** `1234.56` (grupo final de
   2) y `1.23.456` (grupo malformado).
7. Rechazar cero (`entero` todo en `0` **y** fracción `00`) y negativos (el `-` ya quedó fuera
   del conjunto permitido).
8. Validar rango `0,01`–`999.999.999,99` por comparación de **cadenas normalizadas** (sin
   ceros a la izquierda), sin `Number`: primero longitud de la parte entera contra `999999999`
   y, si coincide, comparación lexicográfica; luego el caso límite con fracción.
9. Canónico: `enteroNormalizado + "." + fraccion.padStart(2,"0")`.

Ejemplos de referencia (todos cubiertos por `expenseValidation.test.js`):
`1.234` → `1234.00` · `1 234,56` → `1234.56` · `1234,56` → `1234.56` · `1,234` → error (3
decimales) · `1234.56` → error (punto decimal/grupo inválido) · `0` y `0,00` → error · `0,01` →
válido · `999.999.999,99` → válido · `1.000.000.000,00` → fuera de rango · `-5` → error.

**Alternativas evaluadas**:

1. **Interpretación laxa** (quitar todo `.`/espacio y aceptar lo que quede): rechazada porque
   aceptaría `1.23.456` como 123456, contra "combinaciones malformadas se rechazan".
2. **Aceptar punto decimal como alias**: rechazada por la clarificación Q2 y por FR-013.
3. **`parseFloat` + validaciones**: rechazada por precisión (riesgo de punto flotante) y por
   D2.

**Supuesto explícito**: la spec no define exhaustivamente las agrupaciones; se adopta la regla
estricta del paso 6 y se documenta para confirmación.

---

## D4. Fechas y zona horaria (FR-009, FR-016, FR-022)

**Decisión**:

- **Persistencia**: `expense_date DATE NOT NULL` (sin hora).
- **API**: `String` en formato ISO `yyyy-MM-dd` en el request y el response (`LocalDate` en el
  backend con formato explícito). El frontend nunca usa `Date` para el valor enviado.
- **Zona del dispositivo**: el frontend obtiene el identificador IANA con
  `Intl.DateTimeFormat().resolvedOptions().timeZone` (p. ej.
  `America/Argentina/Buenos_Aires`) y lo envía en el cuerpo como `deviceTimeZone`. Es un dato
  de validación **no persistido**.
- **Validación backend**: `ExpenseDateValidator` calcula `hoy = LocalDate.now(zona)`. Si
  `fecha.isAfter(hoy)` ⇒ 400 con mensaje asociado al campo `date`.
- **Zona ausente o inválida**: `ZoneId.of(valor)` dentro de `try/catch (DateTimeException |
  NullPointerException)`; ante fallo, se aplica la **tolerancia de un día**:
  `límite = LocalDate.now(ZoneOffset.UTC).plusDays(1)`, aceptando `fecha <= límite`. Nunca se
  expone la excepción (SC-006): el error interno de parseo se descarta y se devuelve el mismo
  camino.
- **Parseo/validación frontend** (`parseArgentineDate`): regex `^(\d{1,2})\/(\d{1,2})\/(\d{4})$`;
  normaliza día/mes a dos dígitos; valida existencia construyendo la fecha con
  `new Date(Date.UTC(y, m-1, d))` y comprobando el round-trip (`getUTCFullYear/Month/Date`),
  lo que rechaza `31/02` **sin rollover**; rechaza formatos no reconocidos; compara contra el
  día actual del dispositivo (misma zona) y rechaza futuras. Canónico `yyyy-MM-dd`.
- **Medianoche**: hook `useCurrentDate(timeZone)` con `setInterval` (comprobación cada 30 s) y
  un listener de `visibilitychange` (para pestañas en segundo plano). Si el campo de fecha no
  fue tocado por el usuario (`!dirty`), se actualiza al nuevo día; si lo fue, se conserva. Se
  prueba con `vi.useFakeTimers()`.
- **Cambio de zona entre carga y envío**: la validación usa la zona informada *al guardar*; si
  la fecha quedó futura, se rechaza (Edge Case del spec).

**Alternativas evaluadas**:

1. **Guardar `TIMESTAMPTZ`/`TIMESTAMP`**: rechazada. El dominio es un día sin hora;
   `DATE` evita ambigüedades de zona al leer.
2. **Fecha como número (epoch) o `Date` de JS**: rechazada por pérdida de claridad y riesgo de
   corrimiento de día.
3. **Guardar la zona en el gasto**: rechazada por alcance (la zona es solo insumo de
   validación, no un atributo del gasto).
4. **Timer a la próxima medianoche exacta** en lugar de intervalo: rechazada por fragilidad
   ante suspensión de la pestaña; el intervalo + `visibilitychange` es simple y robusto.

---

## D5. Descripción (FR-012, FR-017, FR-022)

**Decisión**:

- **Recorte**: función propia `stripUnicodeWhitespace` en backend y frontend, que elimina al
  inicio y al final todo *code point* que cumpla `Character.isWhitespace(cp) ||
  Character.isSpaceChar(cp)` (backend) y el conjunto equivalente explícito en JS.
- **Diferencia documentada**: `String.trim()` solo quita caracteres ≤ U+0020 (ASCII);
  `String.strip()` usa `Character.isWhitespace`, que **excluye** NBSP U+00A0, U+2007 y U+202F;
  `Character.isSpaceChar` cubre las categorías Zs/Zl/Zp e **incluye** NBSP. Ninguna API suelta
  cubre "espacios en blanco Unicode" completo, así que se usa el predicado combinado. En JS,
  `String.prototype.trim()` (ECMAScript `WhiteSpace` + `LineTerminator`) **sí** incluye NBSP,
  por lo que **no** coincide exactamente con `strip()`: se implementa el conjunto explícito en
  ambos lados para garantizar paridad y se testea con NBSP.
- **Normalización de saltos**: `\r\n` → `\n` y luego `\r` → `\n` (mismo orden en ambos lados).
- **Conteo**: se cuentan **code points** (Java `codePointCount`, JS `[...texto].length`), no
  unidades UTF-16 ni bytes. Máximo **200** medido **después** de recortar y normalizar. Coincide
  con `char_length()` de PostgreSQL para UTF-8, que cuenta caracteres.
- **Vacuidad**: tras recortar y normalizar, si queda vacío ⇒ se persiste `NULL` (ausente).
- **Formato de transporte**: `description` opcional; `null`/ausente si no se informó. El
  frontend envía el texto normalizado; el backend **re-normaliza** y revalida (nunca confía).
- **Persistencia**: `description VARCHAR(200) NULL` con
  `CHECK (description IS NULL OR char_length(description) <= 200)`.

**Alternativas evaluadas**:

1. **`String.trim()` solo**: rechazada; no cumple "espacios en blanco Unicode".
2. **`String.strip()` solo**: rechazada; deja NBSP y otros separadores de categoría Zs.
3. **Colapsar espacios internos**: rechazada por FR-012 ("MUST NOT ser colapsados").
4. **Contar unidades UTF-16**: rechazada; un emoji o carácter fuera del BMP contaría doble en
   JS. El conteo por code points es consistente con `char_length` en PostgreSQL.

**Supuesto explícito**: ver "Decisiones pendientes" #1 sobre la tensión entre FR-012 y el
criterio de 001.

---

## D6. Catálogos cerrados

**Decisión**:

- **Moneda**: se **reutiliza** `com.amano.backend.user.Currency` (ARS/USD/EUR) y su
  `CurrencyConversion`; no se crea un enum nuevo. Son códigos ISO estables por sí mismos.
- **Nuevos enums** (`com.amano.backend.expense`), persistidos por su **nombre en inglés** como
  código estable, independiente de la etiqueta en español:
  - `ExpenseCategory`: `FOOD, TRANSPORT, HOUSING, SERVICES, HEALTH, EDUCATION, LEISURE,
    SHOPPING, PETS, OTHER`.
  - `ExpenseType`: `RECURRING, ONE_TIME` (etiquetas "Recurrente" / "Único").
  - `PaymentMethod`: `CASH, DEBIT_CARD, CREDIT_CARD, TRANSFER, VIRTUAL_WALLET, OTHER`.
  - **Por qué código estable**: las etiquetas en español pueden cambiar o localizarse; el
    código persistido no. Es el mismo principio ya usado para `currency_code`.
- **Persistencia**: `VARCHAR` con `CHECK (col IN (...))` en la migración (estilo de
  `ck_user_currencies_code`), y mapeo JPA con `@Enumerated(EnumType.STRING)`.
- **API**: se exponen **los códigos**, no las etiquetas. El request y el response usan los
  códigos (`"FOOD"`, `"ONE_TIME"`, …).
- **Validación**: en el DTO entran como `String` y el servicio los resuelve con `valueOf` en
  `try/catch (IllegalArgumentException)`, acumulando un error por campo
  (`FieldValidationException`). Cualquier valor fuera del catálogo ⇒ 400 y **nada persistido**
  (FR-022). Así se obtiene un mensaje por campo claro, sin depender del error genérico de
  deserialización de enums.
- **Duplicación frontend/backend**: las etiquetas en español viven solo en el frontend
  (`expenseCatalog.js`), que mapea código → etiqueta para los `<select>`. La **lista de
  códigos** existe en ambos lados; la consistencia se cubre con un test de cada lado que fija
  la lista exacta esperada (si un lado cambia sin el otro, ambos fallan). Se documenta la
  duplicación como riesgo controlado, no se genera código compartido (no hay mecanismo entre
  Java y JS en el repo).
- **Interfaz**: solo ofrece opciones válidas (FR-011); el backend igualmente rechaza valores
  manipulados (Constitución VIII).

**Alternativas evaluadas**:

1. **Tablas de referencia en PostgreSQL** (`expense_categories`, …): rechazada. Añade tablas,
   joins y migraciones de datos para una lista fija; los CHECK sobre `VARCHAR` alcanzan.
2. **Persistir etiquetas en español**: rechazada; acopla los datos a un idioma y complica
   cambios de copy.
3. **Compartir la lista vía OpenAPI/codegen**: rechazada; el repo documenta contratos en
   markdown y no usa codegen; agregarlo sería una dependencia/estructura nueva.
4. **Enums directamente en el DTO**: rechazada; el error de un enum inválido se produce en la
   deserialización (sin mensaje por campo). Se prefiere `String` + validación de servicio.

---

## D7. Moneda precargada desde el perfil (FR-011)

**Decisión**: reutilizar el cliente existente `me()` (`GET /api/users/me`) tal como hace
`ProfileEditPage`; sin endpoint nuevo ni lógica duplicada. La página `NewExpensePage` llama a
`me()` al montar:

- Éxito ⇒ `preferredCurrency(profile.currencies)` con orden fijo **ARS → USD → EUR** y ARS si
  la lista falta, está vacía o trae un valor no reconocido.
- Falla de red ⇒ ARS + **aviso no bloqueante** (mensaje informativo, el formulario sigue
  usable).
- 401 ⇒ `ProtectedRoute` redirige al login existente.
- La precarga **no** limita las opciones: el `<select>` siempre ofrece ARS, USD y EUR (FR-011).

`preferredCurrency` es una función pura en `expenseValidation.js` (o `expenseCatalog.js`),
testeada con listas vacías, desordenadas, con valores inválidos y con las tres monedas.

**Alternativas evaluadas**:

1. **Usar solo `user.currencies` del `AuthContext`** (ya cargado): válida, pero no detecta el
   caso "perfil no consultable" con un aviso propio ni refresca. Se prefiere `me()` fresco,
   consistente con 002/005, con el `AuthContext` como fallback visual.
2. **Nuevo endpoint `GET /api/users/me/currency`**: rechazada por Constitución I/X (duplicaría
   un dato ya expuesto).

---

## D8. Propiedad y alcance personal (FR-003 a FR-006, SC-001)

**Decisión**:

- El creador se toma **siempre** del principal de la sesión (`SecurityContextHolder`),
  exactamente como `UserController.currentUserId()`; el request **no** tiene campo de creador.
- El DTO captura propiedades desconocidas con `@JsonAnySetter` en un mapa
  (`unknownProperties`); por eso `RegisterExpenseRequest` se implementa como una **clase**
  pequeña (no un `record`, que no admite el campo mutable del mapa). El servicio rechaza con
  400 si aparece alguna clave reservada:
  `userId`, `creatorId`, `creator`, `user`, `participants`, `participantIds`, `splits`,
  `divisions`, `shares`, `debts`, `friends`, `groups`. Respuesta: ProblemDetail 400 con
  `properties.fields` señalando la clave ofensora y **ningún gasto persistido**.
- Por qué `@JsonAnySetter` y no `FAIL_ON_UNKNOWN_PROPERTIES`: el default de Spring Boot ignora
  campos desconocidos y **005 depende de eso** (`ProfileEditIT` envía `email` y espera que se
  ignore). Activar el fallo global rompería 005; capturar y rechazar solo las claves reservadas
  es un cambio local y conforme al alcance.
- El modelo de datos **no** incluye tablas ni columnas de participantes, divisiones, deudas,
  amigos ni grupos (ver data-model.md).

**Alternativas evaluadas**:

1. **Ignorar campos desconocidos** (default): rechazada; FR-005 exige *rechazar* una solicitud
   que intente fijar otro creador.
2. **`FAIL_ON_UNKNOWN_PROPERTIES` global**: rechazada; rompe el comportamiento tolerante de
   005 y amplía el alcance a todas las features.
3. **Resolver el creador con `@AuthenticationPrincipal`**: válida, pero el repo usa
   `SecurityContextHolder`; se mantiene la convención existente.

---

## D9. Contrato REST y errores (FR-019, FR-026, SC-006)

**Decisión**:

- **Endpoint**: `POST /api/expenses` (recurso plural, consistente con `/api/users`).
  `consumes`/`produces` `application/json` con soporte de `application/problem+json` en error.
- **Seguridad**: se amplía el ámbito verificado en `SecurityConfig` de `/api/users/**` a
  `/api/users/**, /api/expenses/**` (mismo `verifiedAccount`), de modo que una cuenta pendiente
  no pueda registrar gastos. Cambio mínimo, sin tocar los matchers existentes.
- **Cabecera**: `Idempotency-Key` obligatoria (UUID). Ausente o malformada ⇒ 400.
- **Request/Response y códigos**: ver [contracts/expenses-api.md](./contracts/expenses-api.md).
  Resumen: `201` creación, `200` replay idempotente, `400` validación/forma, `401` sin sesión,
  `415` media type, `500` inesperado. **No** se usa `409`.
- **Errores por campo**: `ProblemDetail` con `properties.fields` (formato vigente), para que el
  frontend asocie el mensaje al campo.
- **Cambios mínimos en el manejador global** (justificados, Constitución VIII):
  `ProblemDetailExceptionHandler` hoy no maneja `HttpMessageNotReadableException` (JSON
  malformado) ni `MissingRequestHeaderException` (falta `Idempotency-Key`); ambos caerían en
  `handleUnexpected` ⇒ **500**. Se agregan dos handlers que devuelven **400** con mensaje
  genérico en español y sin detalle interno. Se verifica que los handlers existentes no
  cambian y se agrega prueba de regresión.
- **Sesión expirada / no autenticado**: `401` idéntico al vigente (entry point/acces denied de
  `SecurityConfig`); el frontend reutiliza `ProtectedRoute` y el login de 004.
- **Contrato**: markdown en `specs/007-register-expense/contracts/expenses-api.md`, siguiendo la
  forma de `005-edit-user-profile/contracts/users-api.md` (el repo no usa OpenAPI).
- **No se agrega endpoint de lectura**: fuera de alcance; la IT verifica con `JdbcTemplate`.

**Alternativas evaluadas**:

1. **`POST /api/users/me/expenses`**: rechazada; el gasto se identifica por sí mismo y el
   creador sale de la sesión, no de la URL.
2. **Clave de idempotencia en el cuerpo**: ver D1 (elegida la cabecera).
3. **Dejar los 500 para JSON malformado**: rechazada por SC-006/VIII.
4. **Agregar `HttpMessageNotReadableException` a `handleValidation`**: rechazada; su forma no
   tiene `bindingResult` y el mensaje por campo no aplica; un handler propio es más claro.

---

## D10. Persistencia y migración

**Decisión**: migración nueva **`V4__create_expenses.sql`** (siguiente a `V3`), sin editar
`V1`–`V3`, con el estilo existente (encabezado que cita `data-model.md`, comentarios en
español, sin `DELETE`). Tabla `expenses`:

- `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- `user_id BIGINT NOT NULL` + `FK → users(id) ON DELETE CASCADE` (el gasto muere con la cuenta)
- `amount NUMERIC(11,2) NOT NULL` + `CHECK (amount >= 0.01)`
- `currency VARCHAR(3) NOT NULL` + `CHECK (currency IN ('ARS','USD','EUR'))`
- `expense_date DATE NOT NULL`
- `category VARCHAR(20) NOT NULL` + CHECK de códigos de `ExpenseCategory`
- `expense_type VARCHAR(20) NOT NULL` + `CHECK (expense_type IN ('RECURRING','ONE_TIME'))`
- `payment_method VARCHAR(20) NOT NULL` + CHECK de códigos de `PaymentMethod`
- `description VARCHAR(200) NULL` + `CHECK (description IS NULL OR char_length(description) <= 200)`
- `idempotency_key UUID NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `CONSTRAINT uq_expenses_user_idempotency_key UNIQUE (user_id, idempotency_key)`

**Índices**: el índice único `(user_id, idempotency_key)` cubre tanto la búsqueda de replay
como los accesos por `user_id` (prefijo), por lo que **no** se agrega un índice adicional sobre
`user_id`. No hay otros accesos previstos (no hay listado). Detalle completo en
[data-model.md](./data-model.md).

**Atomicidad (FR-024/SC-008)**: toda la validación y normalización ocurre **antes** de
insertar; el guardado es un único `INSERT` dentro de una transacción. Un rechazo no ejecuta
ninguna escritura; no hay operaciones intermedias ni tablas auxiliares que puedan quedar
inconsistentes.

**Alternativas evaluadas**:

1. **`idempotency_key UUID` vs. `VARCHAR`**: elegido `UUID` nativo (valida y compara en la
   base; el frontend genera UUID v4 con `crypto.randomUUID()`).
2. **Guardar la clave de idempotencia en tabla aparte**: ver D1 (rechazada).
3. **Índice adicional sobre `user_id`**: rechazado por innecesario (prefijo del único).
4. **`TIMESTAMPTZ` para la fecha del gasto**: rechazado (ver D4).

---

## Riesgos y notas

- **Paridad de normalización frontend/backend**: el recorte Unicode, el conteo por code points
  y el parseo de monto existen en ambos lados; si divergen, la validación de la interfaz y la
  del backend podrían discrepar. Se mitiga con pruebas espejadas y con la regla de que el
  backend es la fuente de verdad (una discrepancia siempre termina en 400 del backend, nunca en
  datos inválidos persistidos).
- **Duplicación de catálogos**: ver D6; riesgo controlado por tests que fijan la lista exacta.
- **Idempotencia a escala**: a la escala de una app personal, el índice único y el reintento en
  el controlador son suficientes; no se introduce un almacén de idempotencia distribuido.
- **No hay toasts**: el "toast" de éxito de FR-031/FR-032 se implementa como un mensaje inline
  con `role="status"`, cierre por botón y temporizador de 5 s, coherente con los avisos
  existentes (`form-success`/`role="alert"`). No se agrega una librería de notificaciones.
