# A Mano Constitution

## Core Principles

### I. Respeto por la arquitectura y separación de responsabilidades

Las implementaciones MUST respetar la arquitectura existente, las
responsabilidades de cada componente y los límites entre frontend, backend y
demás subsistemas. Antes de diseñar una solución, los agentes MUST inspeccionar
el código relevante y reutilizar los mecanismos y convenciones existentes
cuando sean adecuados. Las modificaciones estructurales MUST estar justificadas
por los requisitos o por una necesidad técnica demostrable. No deben
introducirse refactorizaciones ajenas al alcance de una historia sin aprobación
explícita.

_Rationale_: reutilizar lo existente reduce regresiones y evita fragmentar la
base de código con soluciones paralelas para un mismo problema.

### II. Desarrollo guiado por especificaciones y trazabilidad

Las funcionalidades MUST seguir el proceso SDD establecido por Spec Kit,
utilizando constitution, specify, plan, tasks e implementación, junto con las
etapas de clarificación y análisis cuando correspondan. Los requisitos MUST
expresarse mediante comportamientos esperados, criterios de aceptación
verificables, reglas de negocio y casos límite relevantes. Los planes técnicos
MUST explicar cómo se satisfacen los requisitos respetando la arquitectura y el
stack existentes. Las tareas MUST ser concretas, verificables y trazables a
requisitos o criterios de aceptación; cuando corresponda, deben identificar los
componentes o archivos afectados. Los cambios de alcance MUST reflejarse en los
artefactos SDD antes de implementar el comportamiento nuevo.

_Rationale_: la trazabilidad permite revisar, analizar y verificar qué se
implementó y por qué.

### III. Entrega incremental por historia de usuario

Los agentes y desarrolladores MUST trabajar incrementalmente, implementando una
historia de usuario o una unidad de trabajo aprobada por vez. No se debe
implementar un conjunto completo de historias independientes en una sola
ejecución cuando eso dificulte la revisión, las pruebas o la trazabilidad. Las
dependencias entre historias MUST identificarse durante la planificación. Una
historia no debe considerarse lista para implementar si depende de cambios
previos que todavía no están integrados en la rama de trabajo correspondiente.
La finalización de una historia habilita su revisión e integración; no implica
que todas las historias del backlog deban implementarse antes de compartir el
resultado.

_Rationale_: entregas pequeñas y revisables reducen el riesgo y facilitan la
verificación temprana.

### IV. Alcance controlado y fidelidad al backlog

Las implementaciones MUST ajustarse al backlog, a la especificación aprobada y
a sus criterios de aceptación. No se deben agregar funcionalidades no
solicitadas, optimizaciones especulativas, nuevas dependencias ni cambios
arquitectónicos innecesarios por iniciativa del agente. Los requisitos
ambiguos, contradictorios o incompletos MUST identificarse y aclararse antes de
tomar decisiones que alteren el comportamiento esperado. Las decisiones
pendientes MUST documentarse explícitamente. No deben convertirse en
requisitos confirmados por inferencia.

_Rationale_: respetar el alcance evita desviaciones no revisadas y mantiene la
correspondencia entre lo pedido y lo entregado.

### V. Calidad, pruebas y cobertura

Toda funcionalidad nueva o modificada MUST contar con pruebas automatizadas
apropiadas para el comportamiento implementado, los criterios de aceptación y
los riesgos de regresión. El equipo adopta un umbral mínimo de cobertura de
líneas del 85 % para el backend y el frontend. Los cambios MUST respetar ese
umbral y las reglas de cobertura efectivamente configuradas en cada módulo. No
se deben reducir umbrales, excluir código arbitrariamente ni desactivar pruebas
para obtener una build exitosa. Las pruebas MUST verificar tanto los escenarios
esperados como los casos límite relevantes y los errores previstos por la
especificación. Cuando una modificación afecte funcionalidades existentes, MUST
verificarse que las pruebas de regresión pertinentes continúen pasando. Una
funcionalidad no debe considerarse terminada si las verificaciones obligatorias
fallan o si el resultado de alguna verificación relevante no ha sido
comprobado. La verificación de cobertura MUST ejecutarse con las herramientas
configuradas en el proyecto: JaCoCo en el backend, dentro de `mvn verify`, y el
proveedor de cobertura de Vitest en el frontend, mediante
`npm run test:coverage`. La exclusión de puntos de entrada (por ejemplo, la clase
de arranque de Spring Boot y `main.jsx`) MUST estar justificada y documentada; no
se admite excluir código arbitrariamente para alcanzar el umbral. No debe
asumirse que existe un control de cobertura que no haya sido comprobado.

_Rationale_: las pruebas y la cobertura son el respaldo verificable de que el
comportamiento implementado cumple la especificación.

### VI. Integridad de las reglas de negocio

Las reglas de negocio MUST implementarse conforme a las especificaciones
aprobadas y mantenerse consistentes entre frontend, backend y API. Para las
funcionalidades de gastos, grupos, amistades, roles, pagos y deudas, las
validaciones MUST respetar las restricciones, permisos, estados y transiciones
definidos en el backlog y en las especificaciones correspondientes. Las
operaciones que modifiquen datos relacionados MUST preservar la consistencia
del dominio y evitar estados inválidos según las reglas especificadas. No se
deben inventar invariantes financieras, políticas de permisos ni transiciones
de estado que todavía no hayan sido acordadas; las ambigüedades deben
resolverse durante la especificación de la historia correspondiente.

_Rationale_: las decisiones de dominio deben ser explícitas y compartidas, no
suposiciones del implementador.

### VII. Contratos REST y compatibilidad

El backend MUST exponer y procesar las operaciones REST de acuerdo con los
contratos definidos por el proyecto. Los cambios en endpoints, parámetros,
solicitudes, respuestas, códigos HTTP y validaciones MUST ser coherentes con
los requisitos y estar documentados en los artefactos técnicos
correspondientes. Frontend y backend MUST utilizar contratos compatibles. Las
modificaciones MUST evitar discrepancias entre los datos que produce un
subsistema y los que espera el otro. Los cambios incompatibles con
funcionalidades existentes MUST identificarse y justificarse explícitamente
antes de ser aprobados; no se deben introducir cambios incompatibles de manera
accidental.

_Rationale_: un contrato desalineado rompe la integración aunque cada lado
funcione de forma aislada.

### VIII. Validación y manejo de errores

Los datos de entrada MUST validarse en las fronteras apropiadas del sistema,
especialmente en las operaciones del backend y en las interacciones del
usuario. Los errores previsibles MUST gestionarse de forma controlada, con
respuestas adecuadas en la API y mensajes comprensibles en la interfaz cuando
corresponda. Las validaciones del frontend no sustituyen las validaciones
necesarias en el backend. Las operaciones rechazadas por datos inválidos,
permisos insuficientes o reglas de negocio MUST informar el resultado sin
producir modificaciones inconsistentes. No se deben exponer detalles internos,
credenciales ni información sensible en mensajes de error o registros.

_Rationale_: validar en las fronteras y fallar de forma controlada protege la
integridad de los datos y evita filtraciones.

### IX. Seguridad y control de acceso

Las funcionalidades MUST respetar los mecanismos de autenticación,
autorización y gestión de identidades que ya existan en el proyecto. Las
operaciones sobre perfiles, grupos, miembros, gastos, pagos y demás información
MUST verificar los permisos requeridos por la especificación. No se debe
confiar únicamente en que el frontend oculte botones u opciones para impedir
acciones no autorizadas. No se deben incorporar mecanismos de seguridad nuevos
ni asumir una estrategia de autenticación no confirmada. Las necesidades que no
estén cubiertas por la implementación actual deben identificarse y tratarse
explícitamente.

_Rationale_: la autorización debe aplicarse donde se ejecuta la operación, no
solo en la interfaz; y no debe inventarse una estrategia de autenticación.

### X. Código mantenible y convenciones compartidas

El código MUST respetar las convenciones existentes, mantener responsabilidades
claras y evitar duplicación innecesaria. Los cambios MUST limitarse a los
componentes y archivos necesarios para cumplir la historia aprobada. Antes de
agregar una dependencia, MUST evaluarse si las capacidades existentes permiten
resolver el requisito de forma adecuada. Los cambios entregados MUST estar
libres de código de depuración innecesario, imports sin utilizar, código
temporal y modificaciones accidentales. Las herramientas de análisis estático,
linters y compilaciones existentes MUST ejecutarse cuando correspondan y sus
resultados deben informarse con veracidad.

_Rationale_: el código compartido debe ser legible y consistente para todo el
equipo y para los agentes.

### XI. Entorno reproducible e integración continua

Las modificaciones MUST ser compatibles con los mecanismos existentes de
construcción, pruebas y ejecución del proyecto. Las verificaciones automatizadas
configuradas para el repositorio MUST pasar antes de integrar los cambios en la
rama de destino. Si existe una pipeline de CI, su estado MUST verificarse en los
Pull Requests y respetarse las protecciones configuradas para impedir merges
con verificaciones obligatorias fallidas. Si falta una verificación necesaria,
debe señalarse como una limitación o mejora pendiente; no debe afirmarse que
existe una pipeline o un control que no haya sido comprobado.

_Rationale_: una build reproducible y verificada por CI es la base para
integrar con confianza.

### XII. Revisión por pares e integración controlada

Los cambios MUST integrarse mediante Pull Requests dirigidos a la rama
correspondiente. Los Pull Requests de features MUST dirigirse a `sprint-n`,
mientras que la promoción final del sprint MUST dirigirse a `main`. Las
revisiones MUST comprobar:

- Correspondencia con la especificación y sus criterios de aceptación.
- Respeto por la arquitectura y el alcance aprobado.
- Adecuación de las pruebas y resultados de cobertura.
- Compatibilidad de los contratos y comportamiento existente.
- Ausencia de cambios accidentales o de información sensible.
- Resultado de las verificaciones automatizadas obligatorias.

Cuando las reglas del repositorio o del equipo exijan aprobación de otro
integrante, el autor no debe aprobar su propio Pull Request en sustitución de
esa revisión.

_Rationale_: la revisión por pares y la integración controlada protegen `main`
como rama estable y verificable.

### XIII. Commits y trazabilidad de cambios

Los commits MUST seguir Conventional Commits y describir el propósito real del
cambio con mensajes claros y concisos. Deben utilizarse tipos apropiados, por
ejemplo `feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci` y `chore`,
según la naturaleza del cambio. Los cambios MUST mantener una relación
comprensible con la historia, sus tareas y el Pull Request correspondiente.
Cuando existan identificadores de tareas o issues, deben referenciarse siguiendo
las convenciones del repositorio. No se debe exigir un commit por cada tarea ni
referencias a issues inexistentes si el equipo no ha adoptado esas reglas
explícitamente.

_Rationale_: un historial de commits consistente hace auditable la evolución
del proyecto.

## Contexto Tecnológico y Flujo de Trabajo

**Stack confirmado por el repositorio.** El proyecto es un monorepo con backend
en Java y Spring Boot, frontend en React (con Vite) y comunicación mediante API
REST. La persistencia usa PostgreSQL con JPA y migraciones gestionadas por
Flyway. La construcción del backend es con Maven y las pruebas usan JUnit,
Spring Boot Test y Testcontainers (pruebas rápidas `*Test` y de integración
`*IT`); en el frontend las pruebas usan Vitest y React Testing Library
(`*.test.jsx`). La cobertura se mide con JaCoCo en el backend (integrado en
`mvn verify`) y con el proveedor de cobertura de Vitest en el frontend. La
ejecución integrada usa Docker Compose y la integración continua se ejecuta con
GitHub Actions. Las versiones vigentes son las que fijen los archivos del
repositorio; esta constitución no fija versiones nuevas.

**Idiomas y convenciones de nombres.** Las especificaciones, planes, tareas y
demás artefactos SDD se escriben en español. El código fuente, los
identificadores y los nombres técnicos nuevos se escriben en inglés, respetando
las convenciones existentes (por ejemplo, paquetes bajo `com.amano.backend`,
clases y pruebas con los sufijos `*Test` e `*IT`, y archivos de prueba
`*.test.jsx`).

**Bases de datos y migraciones.** Las migraciones gestionadas por Flyway se
ejecutan al iniciar el backend; una migración con errores impide el arranque.
Las migraciones ya integradas no deben editarse: los cambios se introducen con
una migración nueva.

**Flujo de ramas.**

- `main` es la rama protegida y representa la rama estable.
- `sprint-n` es la rama de integración del sprint.
- Las features se desarrollan en ramas propias creadas desde `sprint-n`, con el
  formato `feat/<feature-name>`.
- Las features terminadas se integran en `sprint-n` mediante Pull Requests.
- La promoción de `sprint-n` a `main` se realiza mediante Pull Request,
  respetando las protecciones y verificaciones configuradas.
- No se debe asumir que una feature necesita integrarse directamente en `main`
  para permitir el desarrollo de otras features del mismo sprint.

**Verificaciones locales y de CI.** Antes de abrir un Pull Request deben
ejecutarse, cuando correspondan, las mismas verificaciones configuradas en el
repositorio: `mvn -B verify` en `backend/` y `npm ci && npm run lint && npm run
test:coverage && npm run build` en `frontend/`. Ambas incluyen la verificación de
cobertura mínima del 85 %. Las verificaciones obligatorias deben estar en verde
antes de integrar.

## Definición de Listo y Terminado (DoR y DoD)

### Definition of Ready (DoR)

Una historia de usuario está lista para implementarse cuando:

1. Su objetivo y alcance están claros.
2. Sus criterios de aceptación son verificables.
3. Las reglas de negocio, validaciones y casos límite relevantes están
   identificados.
4. Las dependencias y bloqueos conocidos están identificados.
5. El plan técnico respeta la arquitectura y las tecnologías existentes.
6. Las tareas están descompuestas en unidades concretas y trazables.
7. Las ambigüedades que podrían cambiar decisiones importantes están resueltas
   o documentadas explícitamente.
8. La especificación y las verificaciones de calidad aplicables han sido
   revisadas.

La existencia de un elemento en el backlog no implica automáticamente que
cumpla estas condiciones.

### Definition of Done (DoD)

Una historia de usuario se considera terminada cuando:

1. Todos los requisitos y criterios de aceptación aprobados están
   implementados.
2. Todas las tareas correspondientes están completadas y actualizadas en
   `tasks.md`.
3. Las pruebas automatizadas pertinentes pasan.
4. La cobertura respeta el umbral mínimo del 85 % y las reglas configuradas en
   cada módulo.
5. Las verificaciones de compilación y build correspondientes al cambio pasan.
6. Los contratos REST y las integraciones afectadas son coherentes.
7. Se han comprobado las regresiones relevantes.
8. La documentación y los artefactos SDD afectados están actualizados.
9. No quedan modificaciones accidentales, artefactos de depuración ni
   información sensible.
10. El cambio ha sido enviado a su rama remota y se ha abierto el Pull Request
    correspondiente.
11. Las verificaciones obligatorias de CI pasan, si están configuradas.
12. Se han satisfecho los requisitos de revisión y aprobación aplicables al
    Pull Request.

Una tarea no debe marcarse como terminada únicamente porque el código fue
escrito: debe haberse verificado el comportamiento requerido.

## Governance

La constitución es la referencia normativa para las decisiones de ingeniería,
la planificación y las comprobaciones de conformidad de Spec Kit. Cuando una
especificación o un plan entre en conflicto con un principio, el conflicto MUST
identificarse y resolverse antes de continuar con la implementación afectada.

**Enmiendas.** Toda modificación de la constitución MUST estar justificada,
reflejada en el historial de Git y acompañada de la actualización de versión y
del informe de impacto que exija el template instalado de Spec Kit. Las
enmiendas se registran en `.specify/memory/constitution.md`.

**Versionado.** Los cambios de versión MUST seguir versionado semántico:

- MAJOR: eliminación o redefinición de principios o de gobernanza incompatible
  hacia atrás.
- MINOR: incorporación de un principio o sección, o expansión material de una
  guía existente.
- PATCH: aclaraciones, correcciones de redacción o refinamientos no
  semánticos.

Las fechas de ratificación y modificación MUST ser reales o quedar
identificadas explícitamente como pendientes cuando no puedan verificarse.

**Cumplimiento.** Las especificaciones, planes, tareas e implementaciones MUST
verificarse contra esta constitución. El plan técnico de cada feature MUST
incluir la comprobación de conformidad constitucional correspondiente. La
constitución MUST permanecer lo suficientemente general como para aplicarse a
las diferentes historias del backlog, sin reemplazar sus especificaciones
particulares.

**Version**: 1.0.1 | **Ratified**: 2026-10-18 | **Last Amended**: 2026-10-18
