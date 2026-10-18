# Primeros pasos con A Mano

Guía para que todo el grupo tenga el mismo entorno y pueda levantar el proyecto en su computadora. Seguí los pasos en orden. Si algo falla, mirá la sección [Problemas frecuentes](#problemas-frecuentes) antes de pedir ayuda.

Los comandos están pensados para **Fedora Linux**. En otros sistemas la idea es la misma, pero la instalación de herramientas cambia.

## 1. Versiones que usamos

Para evitar el clásico "en mi compu funciona", todos usamos estas versiones:

| Herramienta    | Versión             | Cómo verificarla         |
| -------------- | ------------------- | ------------------------ |
| Git            | cualquiera reciente | `git --version`          |
| Node.js        | 24.18.0             | `node -v`                |
| npm            | 11.16.0             | `npm -v`                 |
| Java (OpenJDK) | 25                  | `java -version`          |
| Maven          | 3.9.11              | `mvn -v`                 |
| Docker         | 29.9.1              | `docker --version`       |
| Docker Compose | 5.6.0               | `docker compose version` |

Versiones que fija el propio proyecto (no hace falta instalarlas a mano):

| Componente                         | Versión                                                         |
| ---------------------------------- | --------------------------------------------------------------- |
| Spring Boot                        | 4.1.1                                                           |
| PostgreSQL (imagen Docker)         | 18 (`postgres:18-alpine`)                                       |
| Java en el contenedor del backend  | 25 (`eclipse-temurin:25-jdk` y `25-jre`)                        |
| Node en el contenedor del frontend | 24 (`node:24-alpine`)                                           |
| Servidor web del frontend          | nginx (`nginx:stable-alpine`)                                   |
| React + Vite                       | las de `frontend/package.json`                                  |
| Pruebas del frontend               | Vitest y React Testing Library (en `frontend/package.json`)     |
| Pruebas del backend                | JUnit, Spring Boot Test y Testcontainers (en `backend/pom.xml`) |

Requisitos mínimos reales, por si tu versión difiere un poco: Node 20.19 o 22.12 o superior (Vite), Java 17 o superior (Spring Boot 4 llega hasta Java 25; el proyecto está configurado para 25) y Maven 3.6.3 o superior. Aun así, intentá igualar las versiones de la tabla.

Tu usuario tiene que poder usar Docker sin `sudo`. Comprobalo con:

```
docker ps
```

Si dice `permission denied`, agregá tu usuario al grupo `docker` y reiniciá la sesión:

```
sudo usermod -aG docker $USER
```

## 2. Clonar el repositorio

```
mkdir -p ~/proyectos
cd ~/proyectos
git clone git@github.com:amano-fiuba/amano.git
cd amano
```

Si todavía no configuraste una clave SSH con GitHub, podés clonar por HTTPS:

```
git clone https://github.com/amano-fiuba/amano.git
```

Configurá tu identidad de Git si no lo hiciste (se verá en tus commits):

```
git config --global user.name "Tu Nombre"
git config --global user.email "tu-email@ejemplo.com"
```

## 3. Estructura del repositorio

```
amano/
├── .github/workflows/
│   ├── ci.yml                ← verificaciones automáticas (GitHub Actions)
│   └── publish-images.yml    ← publica las imágenes de producción (ARM64)
├── backend/             ← Spring Boot + Maven (Java)
│   ├── src/main/        ← código de la aplicación
│   ├── src/test/        ← pruebas (*Test rápidas, *IT de integración)
│   ├── Dockerfile       ← cómo se construye la imagen del backend
│   └── .dockerignore
├── frontend/            ← React + Vite
│   ├── src/             ← código y pruebas (*.test.jsx)
│   ├── Dockerfile       ← cómo se construye la imagen del frontend
│   ├── nginx.conf       ← configuración de nginx (sirve la app y reenvía /api)
│   └── .dockerignore
├── .env.example         ← modelo de variables de entorno (sin secretos)
├── Caddyfile            ← proxy HTTPS de producción (Caddy)
├── compose.yaml         ← desarrollo: db, backend y frontend (se construyen acá)
├── compose.prod.yaml    ← producción (Raspberry Pi): imágenes publicadas + Caddy
├── first-steps.md       ← esta guía
└── README.md
```

## 4. Variables de entorno (`.env`)

El archivo `.env` guarda tu configuración local, incluida la contraseña de la base de datos. **Nunca se sube a GitHub** (está en `.gitignore`). Cada persona crea el suyo.

Desde la raíz del repositorio:

```
cp .env.example .env
```

Después generá una contraseña y escribila en `.env`:

```
openssl rand -hex 16
```

Abrí `.env` y reemplazá el valor de `POSTGRES_PASSWORD` por lo que generó el comando, sin comillas ni espacios:

```
POSTGRES_DB=amano
POSTGRES_USER=amano
POSTGRES_PASSWORD=la_contraseña_que_generaste
POSTGRES_PORT=5432
```

`POSTGRES_PORT` es el puerto de **tu máquina** donde se publica la base. Si el 5432 ya lo usa otro programa, cambialo (por ejemplo a `5433`). Entre contenedores, el puerto interno siempre es 5432.

El `.env.example` también trae unas variables de producción comentadas (`COMPOSE_FILE`, `DATA_DIR` y `DOMAIN`). **Dejalas comentadas en tu computadora**: son para la Raspberry Pi. Si activaras `COMPOSE_FILE`, `docker compose` usaría el archivo de producción en lugar del de desarrollo.

> **Importante:** usá una contraseña solo con letras y números. Caracteres como `&`, `$`, `#`, `!`, `^` o espacios rompen la lectura del archivo desde la terminal (el shell los interpreta como comandos). Es la causa de un error que ya tuvimos.

## 5. Dos formas de ejecutar el proyecto

El proyecto se puede ejecutar de dos maneras. Vas a usar las dos:

| Modo                             | Para qué sirve                                     | Qué corre en contenedores         |
| -------------------------------- | -------------------------------------------------- | --------------------------------- |
| **Desarrollo** (secciones 6 a 8) | Programar con recarga automática y cambios rápidos | Solo la base de datos             |
| **Stack completo** (sección 9)   | Probar todo junto, igual que en producción         | Base de datos, backend y frontend |

Regla práctica: para el trabajo diario usá el modo desarrollo. Antes de abrir un Pull Request, o para ver cómo se comporta todo integrado, probá el stack completo.

## 6. Modo desarrollo: levantar la base de datos

Desde la raíz del repositorio:

```
docker compose up -d db
docker compose ps
```

Indicar `db` levanta **solo** la base de datos (sin él, Compose levantaría los tres servicios). Esperá a que aparezca como `healthy` (tarda unos segundos). La base escucha **solo en tu máquina** (`127.0.0.1`), no en la red.

Comandos útiles (valen para todos los servicios):

```
docker compose logs db       # ver los logs de la base
docker compose stop          # apagar sin borrar nada
docker compose down          # borrar los contenedores, conserva los datos
docker compose down -v       # borrar contenedores Y DATOS (empezar de cero)
```

## 7. Modo desarrollo: levantar el backend

Spring Boot necesita las variables de `.env`, pero Maven no lee ese archivo por sí solo. Por eso las cargamos en el momento. Desde `backend/`:

```
cd backend
(set -a; source ../.env; set +a; mvn spring-boot:run)
```

Los paréntesis hacen que las variables existan solo durante ese comando, sin ensuciar tu terminal.

Cuando veas `Started BackendApplication in ... seconds`, el backend está corriendo en <http://localhost:8080>. Para comprobar que responde:

```
curl http://localhost:8080/api/health
```

Debería devolver `{"status":"UP"}`. Detené el backend con `Ctrl+C`.

Las pruebas del backend **no necesitan** que cargues el `.env` ni que la base esté levantada (ver la sección 10).

## 8. Modo desarrollo: levantar el frontend

Desde `frontend/`:

```
cd frontend
npm install
npm run dev
```

La aplicación queda en <http://localhost:5173>. Detenela con `Ctrl+C`.

`npm install` solo hace falta la primera vez y cada vez que cambie `package.json`.

> Todavía el frontend no hace llamadas a la API. Cuando agreguemos la primera, habrá que configurar Vite para reenviar `/api` al backend local (hoy esa tarea la hace nginx en el stack completo).

## 9. Stack completo con Docker

Levanta los tres servicios (base de datos, backend y frontend) como contenedores. Desde la raíz del repositorio:

```
docker compose up -d --build
docker compose ps
```

- `--build` reconstruye las imágenes si cambió código. La **primera vez tarda varios minutos** porque descarga Java, Node y todas las dependencias; las siguientes son mucho más rápidas.
- `ps` debería mostrar `db` como `healthy` y `backend` y `frontend` como `running`.

Dónde se ve cada cosa:

| Qué                                        | Dirección                                                   |
| ------------------------------------------ | ----------------------------------------------------------- |
| Aplicación (frontend)                      | <http://localhost:8081>                                     |
| API (a través de nginx)                    | <http://localhost:8081/api/>... (por ejemplo `/api/health`) |
| Base de datos (para herramientas externas) | `127.0.0.1:5432` (o tu `POSTGRES_PORT`)                     |

El backend **no** publica un puerto propio: se accede a él solo a través de nginx, que reenvía a `/api/`. Por eso no choca con el `mvn spring-boot:run` del modo desarrollo.

Una buena prueba de punta a punta, que pasa por nginx, el proxy y el backend:

```
curl -i http://localhost:8081/api/health
```

Cómo funciona por dentro (para entender qué pasa):

- **Backend:** una imagen en dos etapas. La primera compila con Maven (sin ejecutar tests; los tests los corre GitHub Actions) y la segunda ejecuta solo el `.jar` con Java 25.
- **Frontend:** otra imagen en dos etapas. La primera compila la aplicación con Vite y la segunda la sirve con nginx.
- **Conexión a la base:** dentro de la red de Compose, el backend encuentra la base con el nombre `db` (variable `DB_HOST=db`). No se toca el código para eso.

Comandos útiles:

```
docker compose logs -f backend   # seguir los logs del backend (Ctrl+C para salir)
docker compose logs frontend     # logs de nginx
docker compose restart frontend  # reiniciar solo el frontend
docker compose down              # apagar todo conservando los datos
```

Para volver al modo desarrollo después de usar el stack completo, apagá los contenedores de la aplicación y dejá solo la base:

```
docker compose stop backend frontend
```

## 10. Pruebas

Hay distintos tipos de prueba, de más rápidas a más completas:

| Tipo                                | Qué levanta                                                                | ¿Necesita base de datos? | Dónde vive                          |
| ----------------------------------- | -------------------------------------------------------------------------- | ------------------------ | ----------------------------------- |
| **Unitaria**                        | Solo tu clase, con JUnit                                                   | No                       | `backend/src/test`, nombre `*Test`  |
| **Web** (`@WebMvcTest`)             | Solo la capa web de Spring                                                 | No                       | `backend/src/test`, nombre `*Test`  |
| **Integración** (`@SpringBootTest`) | La aplicación completa, con una PostgreSQL real que levanta Testcontainers | Sí, pero se crea sola    | `backend/src/test`, nombre `*IT`    |
| **Componente (frontend)**           | Un componente React en un navegador simulado                               | No                       | `frontend/src`, nombre `*.test.jsx` |

### Backend

Desde `backend/`, **sin cargar el `.env` y sin base levantada**:

```
mvn test       # solo las rápidas (*Test), sin Docker
mvn verify     # rápidas + integración (*IT); necesita Docker en ejecución
```

Las pruebas de integración usan Testcontainers: crean su propio contenedor de PostgreSQL (`postgres:18-alpine`), lo usan y lo borran. No tocan tu base de desarrollo. Lo único que piden es que Docker esté funcionando.

Convención: lo que termina en `Test` corre con `mvn test`; lo que termina en `IT` corre solo con `mvn verify`.

### Frontend

Desde `frontend/`:

```
npm test               # ejecuta las pruebas una vez
npm run test:watch     # queda escuchando y re-ejecuta al guardar
npm run lint           # revisa el estilo y errores comunes con ESLint
npm run build          # compila para producción (comprueba que todo arma)
```

### Antes de abrir un Pull Request

Corré en tu máquina lo mismo que va a correr GitHub, así evitás ver el check en rojo:

```
cd backend && mvn -B verify
cd ../frontend && npm ci && npm run lint && npm test && npm run build
```

## 11. Integración continua (GitHub Actions)

El archivo `.github/workflows/ci.yml` hace que GitHub verifique el código en una máquina limpia cada vez que abrís o actualizás un Pull Request, y también cuando algo llega a `main`.

| Job        | Qué ejecuta                                                               |
| ---------- | ------------------------------------------------------------------------- |
| `backend`  | Java 25 y `mvn -B verify` (pruebas rápidas, de integración y empaquetado) |
| `frontend` | Node 24, `npm ci`, `npm run lint`, `npm test` y `npm run build`           |

Los dos corren en paralelo. En el Pull Request aparecen como **backend** y **frontend**. Si alguno queda en rojo:

1. Entrá a **Details** del check que falló.
2. Buscá el primer paso con una cruz roja y leé el final de su log.
3. Reproducí el error en tu máquina con los comandos de la sección anterior.

Esto es **CI** (integración continua): verificar que el código compila y pasa las pruebas.

Hay un segundo workflow, `publish-images.yml`, que **no** corre en los Pull Requests: se ejecuta solo cuando algo llega a `main`. Construye las imágenes de backend y frontend para ARM64 (la arquitectura de la Raspberry Pi) y las publica en GHCR, el registro de imágenes de GitHub, con los tags `latest` y `sha-<commit>`. Es el primer paso del **CD** (despliegue continuo), que se explica en la sección 13.

## 12. Flujo de trabajo en equipo

La rama `main` está **protegida**: nadie puede subir commits directo, ni siquiera quien administra el repositorio. Todo cambio entra por un Pull Request que cumpla estas reglas:

- Los checks `backend` y `frontend` tienen que estar en verde.
- La rama tiene que estar actualizada respecto de `main` (si no lo está, GitHub ofrece el botón **Update branch**).
- Los comentarios de revisión tienen que estar resueltos.

Pasos:

1. Antes de empezar, actualizá `main`:

   ```
   git checkout main
   git pull
   ```

2. Creá una rama para tu tarea. Usá un nombre descriptivo con prefijo:

   ```
   git checkout -b feat/crear-grupo
   ```

3. Hacé commits chicos y claros. Seguimos el formato _Conventional Commits_: `feat:` (funcionalidad nueva), `fix:` (corrección), `docs:` (documentación), `chore:` (tareas de mantenimiento), `test:` (pruebas), `ci:` (automatizaciones).

   ```
   git add archivo1 archivo2
   git commit -m "feat: add group creation endpoint"
   ```

   Preferí `git add` con archivos concretos antes que `git add .`, para no subir algo por accidente.

4. Subí tu rama:

   ```
   git push -u origin feat/crear-grupo
   ```

   En la salida, GitHub imprime un enlace para abrir el Pull Request. Abrilo, poné un título claro y creá el Pull Request hacia `main`.

5. Esperá a que los checks queden en verde. Si fallan, corregí en tu rama y volvé a subir: el check se vuelve a ejecutar solo.
6. Pedí a alguien del grupo que lo revise. Por ahora GitHub no lo exige con un número mínimo de aprobaciones, pero es la costumbre del equipo.
7. Cuando esté todo en verde y revisado, integralo con **Merge pull request**. Recordá que **todo lo que llega a `main` se despliega solo en la Raspberry Pi** (sección 13): no integres nada que no estés dispuesto a ver publicado.
8. Actualizá tu copia local y borrá la rama:

   ```
   git checkout main
   git pull
   git branch -d feat/crear-grupo
   ```

Si intentás subir directo a `main`, GitHub lo rechaza con un mensaje de `Repository rule violations`. Es lo esperado: pasá tu commit a una rama y abrí un Pull Request.

### Qué nunca debe subirse

- El archivo `.env` o cualquier archivo con contraseñas o tokens.
- Carpetas generadas: `node_modules/`, `target/`, `dist/`. Ya están en los `.gitignore`.

Antes de cada commit, mirá qué estás por subir:

```
git status --short
```

Si aparece `.env`, `node_modules` o `target`, no los agregues y avisá.

## 13. Despliegue en la Raspberry Pi

La aplicación corre en una Raspberry Pi y se actualiza sola cada vez que se integra algo a `main`. Dirección pública: <https://amano-fiuba.duckdns.org>.

Cómo viaja un cambio:

1. Se integra un Pull Request a `main`.
2. GitHub Actions ejecuta `publish-images.yml`: construye las imágenes de backend y frontend para ARM64 y las publica en GHCR (`ghcr.io/amano-fiuba/amano-backend` y `ghcr.io/amano-fiuba/amano-frontend`). Tarda unos minutos.
3. La Raspberry revisa cada 5 minutos si hay novedades: descarga el código y las imágenes nuevas y reinicia solo los servicios que cambiaron.

En total, un cambio se ve publicado unos 10 minutos después del merge. Podés seguir el primer paso en la pestaña **Actions** del repositorio, en el workflow **Publish images**.

Qué archivo es para qué:

| Archivo             | Qué hace                                                                                                 | Dónde se usa   |
| ------------------- | -------------------------------------------------------------------------------------------------------- | -------------- |
| `compose.yaml`      | Construye todo desde el código y publica la app en `localhost:8081`                                      | Tu computadora |
| `compose.prod.yaml` | Usa las imágenes ya publicadas, sin construir nada. Solo Caddy abre puertos (80 y 443) y maneja el HTTPS | Raspberry Pi   |
| `Caddyfile`         | Configuración de Caddy: reenvía todo al frontend                                                         | Raspberry Pi   |

**No uses `compose.prod.yaml` en tu computadora**: las imágenes son ARM64 y no corren en una PC común, y además intentaría abrir los puertos 80 y 443.

Qué **no** está en el repositorio, a propósito: el `.env` de producción (con contraseñas), los datos (base de datos y certificados) y el script que hace la actualización automática. Viven solo en la Raspberry.

Cosas que conviene saber:

- **Todo lo que llega a `main` queda publicado en Internet.** Hoy la app no tiene login: no se deben cargar datos reales de personas hasta que lo resolvamos.
- Las migraciones de Flyway corren solas cuando arranca el backend. Una migración con errores impide que el backend arranque, y por lo tanto rompe el despliegue. No edites migraciones que ya se integraron: creá una nueva.
- Si un despliegue sale mal, avisá a quien administra la Raspberry. Se puede volver a una versión anterior fijando el tag `sha-...` de la imagen.

## Problemas frecuentes

**`Could not resolve placeholder 'POSTGRES_PASSWORD'`** al arrancar el backend en modo desarrollo.
No cargaste las variables. Usá el comando de la sección 7, con `source ../.env`.

**`FATAL: password authentication failed for user "amano"`**
La contraseña que envía el backend no coincide con la que tiene la base. PostgreSQL toma la contraseña solo la primera vez que crea el volumen, así que si cambiaste `.env` después, hay que recrear la base (esto borra tus datos locales):

```
docker compose down -v
docker compose up -d db
```

Si el problema persiste, revisá que la contraseña no tenga caracteres especiales.

**`.env:4: parse error near '&'`** (o similar con otro símbolo)
Tu contraseña tiene un carácter que el shell interpreta. Generá una nueva con `openssl rand -hex 16` y recreá la base como en el punto anterior.

**`permission denied` al usar `docker`**
Mirá el final de la sección 1 (grupo `docker`).

**Las pruebas de integración (`mvn verify`) fallan con un error de Docker o de Testcontainers**
Comprobá que Docker esté funcionando con `docker ps`. Las pruebas de integración necesitan Docker, aunque no necesitan que tu base de desarrollo esté levantada. Si el error menciona `client version ... is too old`, es una incompatibilidad entre versiones de Docker y Testcontainers: avisá al grupo.

**Un check queda en rojo en el Pull Request**
Mirá la sección 11: entrá a **Details**, buscá el paso que falló y reproducí el error local con los comandos de "Antes de abrir un Pull Request".

**El Pull Request dice que la rama está desactualizada**
Otro Pull Request se integró antes. Apretá **Update branch** en la página del Pull Request (o hacé `git merge origin/main` en tu rama) y esperá a que los checks corran de nuevo.

**El puerto 5432 ya está en uso**
Tenés otro PostgreSQL corriendo en tu máquina. Podés apagarlo o cambiar `POSTGRES_PORT` en tu `.env` (por ejemplo a `5433`) y volver a levantar. El backend en modo desarrollo usa esa misma variable.

**El puerto 8080 o 8081 ya está en uso**
En modo desarrollo, el backend usa el 8080: si hay otro programa ahí, no arrancará. En el stack completo el frontend usa el 8081 de tu máquina; si está ocupado, cambiá `"127.0.0.1:8081:80"` en `compose.yaml` por otro puerto libre, por ejemplo `"127.0.0.1:8082:80"`.

**`502 Bad Gateway` al entrar a `/api/...` en el stack completo**
Nginx no pudo llegar al backend. Puede pasar si el backend todavía está arrancando (esperá unos segundos y mirá `docker compose logs backend`) o si lo reconstruiste sin reiniciar el frontend. En ese caso:

```
docker compose restart frontend
```

**El backend del stack completo no arranca**
Mirá los logs con `docker compose logs backend` y buscá las líneas `Caused by`. Si es un error de contraseña, aplica lo de arriba.

**La primera construcción falla o es muy lenta**
La primera vez descarga imágenes y dependencias, y depende de tu conexión. Si falla por un corte de red, volvé a ejecutar `docker compose up -d --build`: Docker reutiliza lo que ya descargó.

**La base no persiste datos o el contenedor no arranca**
La imagen de PostgreSQL 18 guarda los datos en una ruta distinta a la de versiones anteriores. No cambies el volumen de `compose.yaml` a `/var/lib/postgresql/data`: tiene que ser `/var/lib/postgresql`.

## Estado del proyecto

Hecho:

- Repositorio con monorepo (`frontend/` y `backend/`).
- Frontend React + Vite funcionando, con pruebas (Vitest y React Testing Library).
- Backend Spring Boot conectado a PostgreSQL (JPA y Flyway para las migraciones), con el endpoint `/api/health`.
- Pruebas del backend en tres niveles: unitarias, web y de integración con PostgreSQL real (Testcontainers).
- `compose.yaml` con los tres servicios: base de datos, backend y frontend (con nginx).
- Imágenes Docker multi-etapa para backend y frontend, compatibles con ARM64 (pensadas para la Raspberry Pi).
- GitHub Actions: CI que corre las pruebas del backend y del frontend en cada Pull Request.
- Rama `main` protegida: solo se integra con Pull Request, checks en verde y rama actualizada.
- Aplicación desplegada en una Raspberry Pi, con HTTPS automático (Caddy): <https://amano-fiuba.duckdns.org>.
- Imágenes ARM64 publicadas en GHCR por GitHub Actions en cada integración a `main`.
- Despliegue continuo: la Raspberry se actualiza sola unos minutos después de cada integración a `main`.
- Exigir al menos una aprobación por Pull Request, cuando todo el grupo tenga acceso al repositorio.

Pendiente (esta guía se va a actualizar a medida que avance):

- Autenticación (login) antes de guardar datos reales de personas.
- Primeras migraciones de base de datos y modelo de datos.
- Copias de seguridad (backups) de la base de datos.
