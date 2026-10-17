# Primeros pasos con Amano

Guía para que todo el grupo tenga el mismo entorno y pueda levantar el proyecto en su computadora. Seguí los pasos en orden. Si algo falla, mirá la sección [Problemas frecuentes](#problemas-frecuentes) antes de pedir ayuda.

Los comandos están pensados para **Fedora Linux**. En otros sistemas la idea es la misma, pero la instalación de herramientas cambia.

## 1. Versiones que usamos

Para evitar el clásico "en mi compu funciona", todos usamos estas versiones:

| Herramienta | Versión | Cómo verificarla |
|---|---|---|
| Git | cualquiera reciente | `git --version` |
| Node.js | 24.18.0 | `node -v` |
| npm | 11.16.0 | `npm -v` |
| Java (OpenJDK) | 25 | `java -version` |
| Maven | 3.9.11 | `mvn -v` |
| Docker | 29.9.1 | `docker --version` |
| Docker Compose | 5.6.0 | `docker compose version` |

Versiones que fija el propio proyecto (no hace falta instalarlas a mano):

| Componente | Versión |
|---|---|
| Spring Boot | 4.1.1 |
| PostgreSQL (imagen Docker) | 18 (`postgres:18-alpine`) |
| Java en el contenedor del backend | 25 (`eclipse-temurin:25-jdk` y `25-jre`) |
| Node en el contenedor del frontend | 24 (`node:24-alpine`) |
| Servidor web del frontend | nginx (`nginx:stable-alpine`) |
| React + Vite | las de `frontend/package.json` |

Requisitos mínimos reales, por si tu versión difiere un poco: Node 20.19 o 22.12 o superior (Vite), Java 17 o superior (Spring Boot 4 llega hasta Java 25; el proyecto está configurado para 25) y Maven 3.6.3 o superior. Aun así, intentá igualar las versiones de la tabla.

Tu usuario tiene que poder usar Docker sin `sudo`. Comprobalo con:

```bash
docker ps
```

Si dice `permission denied`, agregá tu usuario al grupo `docker` y reiniciá la sesión:

```bash
sudo usermod -aG docker $USER
```

## 2. Clonar el repositorio

```bash
mkdir -p ~/proyectos
cd ~/proyectos
git clone git@github.com:ifabregat/A-Mano.git
cd A-Mano
```

Si todavía no configuraste una clave SSH con GitHub, podés clonar por HTTPS:

```bash
git clone https://github.com/ifabregat/A-Mano.git
```

Configurá tu identidad de Git si no lo hiciste (se verá en tus commits):

```bash
git config --global user.name "Tu Nombre"
git config --global user.email "tu-email@ejemplo.com"
```

## 3. Estructura del repositorio

```
A-Mano/
├── .github/workflows/   ← automatizaciones de GitHub Actions (próximamente)
├── backend/             ← Spring Boot + Maven (Java)
│   ├── Dockerfile       ← cómo se construye la imagen del backend
│   └── .dockerignore
├── frontend/            ← React + Vite
│   ├── Dockerfile       ← cómo se construye la imagen del frontend
│   ├── nginx.conf       ← configuración de nginx (sirve la app y reenvía /api)
│   └── .dockerignore
├── .env.example         ← modelo de variables de entorno (sin secretos)
├── compose.yaml         ← los tres servicios: db, backend y frontend
├── first-steps.md       ← esta guía
└── README.md
```

## 4. Variables de entorno (`.env`)

El archivo `.env` guarda tu configuración local, incluida la contraseña de la base de datos. **Nunca se sube a GitHub** (está en `.gitignore`). Cada persona crea el suyo.

Desde la raíz del repositorio:

```bash
cp .env.example .env
```

Después generá una contraseña y escribila en `.env`:

```bash
openssl rand -hex 16
```

Abrí `.env` y reemplazá el valor de `POSTGRES_PASSWORD` por lo que generó el comando, sin comillas ni espacios:

```dotenv
POSTGRES_DB=amano
POSTGRES_USER=amano
POSTGRES_PASSWORD=la_contraseña_que_generaste
POSTGRES_PORT=5432
```

`POSTGRES_PORT` es el puerto de **tu máquina** donde se publica la base. Si el 5432 ya lo usa otro programa, cambialo (por ejemplo a `5433`). Entre contenedores, el puerto interno siempre es 5432.

> **Importante:** usá una contraseña solo con letras y números. Caracteres como `&`, `$`, `#`, `!`, `^` o espacios rompen la lectura del archivo desde la terminal (el shell los interpreta como comandos). Es la causa de un error que ya tuvimos.

## 5. Dos formas de ejecutar el proyecto

El proyecto se puede ejecutar de dos maneras. Vas a usar las dos:

| Modo | Para qué sirve | Qué corre en contenedores |
|---|---|---|
| **Desarrollo** (secciones 6 a 8) | Programar con recarga automática y cambios rápidos | Solo la base de datos |
| **Stack completo** (sección 9) | Probar todo junto, igual que en producción | Base de datos, backend y frontend |

Regla práctica: para el trabajo diario usá el modo desarrollo. Antes de abrir un Pull Request, o para ver cómo se comporta todo integrado, probá el stack completo.

## 6. Modo desarrollo: levantar la base de datos

Desde la raíz del repositorio:

```bash
docker compose up -d db
docker compose ps
```

Indicar `db` levanta **solo** la base de datos (sin él, Compose levantaría los tres servicios). Esperá a que aparezca como `healthy` (tarda unos segundos). La base escucha **solo en tu máquina** (`127.0.0.1`), no en la red.

Comandos útiles (valen para todos los servicios):

```bash
docker compose logs db       # ver los logs de la base
docker compose stop          # apagar sin borrar nada
docker compose down          # borrar los contenedores, conserva los datos
docker compose down -v       # borrar contenedores Y DATOS (empezar de cero)
```

## 7. Modo desarrollo: levantar el backend

Spring Boot necesita las variables de `.env`, pero Maven no lee ese archivo por sí solo. Por eso las cargamos en el momento. Desde `backend/`:

```bash
cd backend
(set -a; source ../.env; set +a; mvn spring-boot:run)
```

Los paréntesis hacen que las variables existan solo durante ese comando, sin ensuciar tu terminal.

Cuando veas `Started BackendApplication in ... seconds`, el backend está corriendo en http://localhost:8080. Mostrar una página de error 404 en esa dirección es normal por ahora: todavía no hay rutas definidas. Detenelo con `Ctrl+C`.

Para correr las pruebas del backend (con la base levantada):

```bash
(set -a; source ../.env; set +a; mvn test)
```

## 8. Modo desarrollo: levantar el frontend

Desde `frontend/`:

```bash
cd frontend
npm install
npm run dev
```

La aplicación queda en http://localhost:5173. Detenela con `Ctrl+C`.

`npm install` solo hace falta la primera vez y cada vez que cambie `package.json`.

> Todavía el frontend no hace llamadas a la API. Cuando agreguemos la primera, habrá que configurar Vite para reenviar `/api` al backend local (hoy esa tarea la hace nginx en el stack completo).

## 9. Stack completo con Docker

Levanta los tres servicios (base de datos, backend y frontend) como contenedores. Desde la raíz del repositorio:

```bash
docker compose up -d --build
docker compose ps
```

- `--build` reconstruye las imágenes si cambió código. La **primera vez tarda varios minutos** porque descarga Java, Node y todas las dependencias; las siguientes son mucho más rápidas.
- `ps` debería mostrar `db` como `healthy` y `backend` y `frontend` como `running`.

Dónde se ve cada cosa:

| Qué | Dirección |
|---|---|
| Aplicación (frontend) | http://localhost:8081 |
| API (a través de nginx) | http://localhost:8081/api/... |
| Base de datos (para herramientas externas) | `127.0.0.1:5432` (o tu `POSTGRES_PORT`) |

El backend **no** publica un puerto propio: se accede a él solo a través de nginx, que reenvía a `/api/`. Por eso no choca con el `mvn spring-boot:run` del modo desarrollo.

Cómo funciona por dentro (para entender qué pasa):

- **Backend:** una imagen en dos etapas. La primera compila con Maven (sin ejecutar tests; los tests los correrá GitHub Actions) y la segunda ejecuta solo el `.jar` con Java 25.
- **Frontend:** otra imagen en dos etapas. La primera compila la aplicación con Vite y la segunda la sirve con nginx.
- **Conexión a la base:** dentro de la red de Compose, el backend encuentra la base con el nombre `db` (variable `DB_HOST=db`). No se toca el código para eso.

Comandos útiles:

```bash
docker compose logs -f backend   # seguir los logs del backend (Ctrl+C para salir)
docker compose logs frontend     # logs de nginx
docker compose restart frontend  # reiniciar solo el frontend
docker compose down              # apagar todo conservando los datos
```

Para volver al modo desarrollo después de usar el stack completo, apagá los contenedores de la aplicación y dejá solo la base:

```bash
docker compose stop backend frontend
```

## 10. Flujo de trabajo en equipo

La idea es que `main` siempre contenga código que compila y pasa las pruebas. Para eso:

1. Antes de empezar, actualizá `main`:
   ```bash
   git checkout main
   git pull
   ```
2. Creá una rama para tu tarea. Usá un nombre descriptivo con prefijo:
   ```bash
   git checkout -b feat/crear-grupo
   ```
3. Hacé commits chicos y claros. Seguimos el formato *Conventional Commits*: `feat:` (funcionalidad nueva), `fix:` (corrección), `docs:` (documentación), `chore:` (tareas de mantenimiento), `test:` (pruebas).
   ```bash
   git add archivo1 archivo2
   git commit -m "feat: add group creation endpoint"
   ```
   Preferí `git add` con archivos concretos antes que `git add .`, para no subir algo por accidente.
4. Subí tu rama y abrí un **Pull Request** hacia `main` desde GitHub:
   ```bash
   git push -u origin feat/crear-grupo
   ```
5. Alguien del grupo lo revisa. Se integra a `main` cuando está aprobado.

No hagas commits directos a `main`. Cuando se active la protección de la rama, GitHub lo va a impedir de todos modos.

### Qué nunca debe subirse

- El archivo `.env` o cualquier archivo con contraseñas o tokens.
- Carpetas generadas: `node_modules/`, `target/`, `dist/`. Ya están en los `.gitignore`.

Antes de cada commit, mirá qué estás por subir:

```bash
git status --short
```

Si aparece `.env`, `node_modules` o `target`, no los agregues y avisá.

## Problemas frecuentes

**`Could not resolve placeholder 'POSTGRES_PASSWORD'`** al arrancar el backend en modo desarrollo.
No cargaste las variables. Usá el comando de la sección 7, con `source ../.env`.

**`FATAL: password authentication failed for user "amano"`**
La contraseña que envía el backend no coincide con la que tiene la base. PostgreSQL toma la contraseña solo la primera vez que crea el volumen, así que si cambiaste `.env` después, hay que recrear la base (esto borra tus datos locales):
```bash
docker compose down -v
docker compose up -d db
```
Si el problema persiste, revisá que la contraseña no tenga caracteres especiales.

**`.env:4: parse error near '&'`** (o similar con otro símbolo)
Tu contraseña tiene un carácter que el shell interpreta. Generá una nueva con `openssl rand -hex 16` y recreá la base como en el punto anterior.

**`permission denied` al usar `docker`**
Mirá el final de la sección 1 (grupo `docker`).

**El puerto 5432 ya está en uso**
Tenés otro PostgreSQL corriendo en tu máquina. Podés apagarlo o cambiar `POSTGRES_PORT` en tu `.env` (por ejemplo a `5433`) y volver a levantar. El backend en modo desarrollo usa esa misma variable.

**El puerto 8080 o 8081 ya está en uso**
En modo desarrollo, el backend usa el 8080: si hay otro programa ahí, no arrancará. En el stack completo el frontend usa el 8081 de tu máquina; si está ocupado, cambiá `"127.0.0.1:8081:80"` en `compose.yaml` por otro puerto libre, por ejemplo `"127.0.0.1:8082:80"`.

**`502 Bad Gateway` al entrar a `/api/...` en el stack completo**
Nginx no pudo llegar al backend. Puede pasar si el backend todavía está arrancando (esperá unos segundos y mirá `docker compose logs backend`) o si lo reconstruiste sin reiniciar el frontend. En ese caso:
```bash
docker compose restart frontend
```

**El backend del stack completo no arranca**
Mirá los logs con `docker compose logs backend` y buscá las líneas `Caused by`. Si es un error de contraseña, aplica lo de arriba.

**La primera construcción falla o es muy lenta**
La primera vez descarga imágenes y dependencias, y depende de tu conexión. Si falla por un corte de red, volvé a ejecutar `docker compose up -d --build`: Docker reutiliza lo que ya descargó.

**La base no persiste datos o el contenedor no arranca**
La imagen de PostgreSQL 18 guarda los datos en una ruta distinta a la de versiones anteriores. No cambies el volumen de `compose.yaml` a `/var/lib/postgresql/data`: tiene que ser `/var/lib/postgresql`.

**El test `contextLoads` falla al correr `mvn test`**
Es esperado si no cargaste las variables o la base está apagada. Ese test levanta toda la aplicación. Se va a reorganizar cuando incorporemos las pruebas y GitHub Actions.

## Estado del proyecto

Hecho:

- Repositorio con monorepo (`frontend/` y `backend/`).
- Frontend React + Vite funcionando.
- Backend Spring Boot conectado a PostgreSQL (JPA y Flyway para las migraciones).
- `compose.yaml` con los tres servicios: base de datos, backend y frontend (con nginx).
- Imágenes Docker multi-etapa para backend y frontend, compatibles con ARM64 (pensadas para la Raspberry Pi).

Pendiente (esta guía se va a actualizar a medida que avance):

- Reorganización de las pruebas y GitHub Actions.
- Protección de la rama `main` y obligatoriedad de Pull Requests.
- Primeras migraciones de base de datos y modelo de datos.
- Despliegue en una Raspberry Pi.
