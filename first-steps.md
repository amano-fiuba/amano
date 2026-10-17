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
├── frontend/            ← React + Vite
├── .env.example         ← modelo de variables de entorno (sin secretos)
├── compose.yaml         ← servicios para desarrollo local (PostgreSQL)
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

> **Importante:** usá una contraseña solo con letras y números. Caracteres como `&`, `$`, `#`, `!`, `^` o espacios rompen la lectura del archivo desde la terminal (el shell los interpreta como comandos). Es la causa de un error que ya tuvimos.

## 5. Levantar la base de datos

Desde la raíz del repositorio:

```bash
docker compose up -d
docker compose ps
```

Esperá a que el servicio `db` aparezca como `healthy` (tarda unos segundos). La base escucha **solo en tu máquina** (`127.0.0.1`), no en la red.

Comandos útiles:

```bash
docker compose logs db       # ver los logs de la base
docker compose stop          # apagar sin borrar nada
docker compose up -d         # volver a encender
docker compose down          # borrar los contenedores, conserva los datos
docker compose down -v       # borrar contenedores Y DATOS (empezar de cero)
```

## 6. Levantar el backend

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

## 7. Levantar el frontend

Desde `frontend/`:

```bash
cd frontend
npm install
npm run dev
```

La aplicación queda en http://localhost:5173. Detenela con `Ctrl+C`.

`npm install` solo hace falta la primera vez y cada vez que cambie `package.json`.

## 8. Flujo de trabajo en equipo

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

**`Could not resolve placeholder 'POSTGRES_PASSWORD'`** al arrancar el backend.
No cargaste las variables. Usá el comando de la sección 6, con `source ../.env`.

**`FATAL: password authentication failed for user "amano"`**
La contraseña que envía el backend no coincide con la que tiene la base. PostgreSQL toma la contraseña solo la primera vez que crea el volumen, así que si cambiaste `.env` después, hay que recrear la base (esto borra tus datos locales):
```bash
docker compose down -v
docker compose up -d
```
Si el problema persiste, revisá que la contraseña no tenga caracteres especiales.

**`.env:4: parse error near '&'`** (o similar con otro símbolo)
Tu contraseña tiene un carácter que el shell interpreta. Generá una nueva con `openssl rand -hex 16` y recreá la base como en el punto anterior.

**`permission denied` al usar `docker`**
Mirá el final de la sección 1 (grupo `docker`).

**El puerto 5432 ya está en uso**
Tenés otro PostgreSQL corriendo en tu máquina. Podés apagarlo o cambiar `POSTGRES_PORT` en tu `.env` (por ejemplo a `5433`) y volver a levantar con `docker compose up -d`. El backend usa esa misma variable.

**La base no persiste datos o el contenedor no arranca**
La imagen de PostgreSQL 18 guarda los datos en una ruta distinta a la de versiones anteriores. No cambies el volumen de `compose.yaml` a `/var/lib/postgresql/data`: tiene que ser `/var/lib/postgresql`.

**El test `contextLoads` falla al correr `mvn test`**
Es esperado si no cargaste las variables o la base está apagada. Ese test levanta toda la aplicación. Se va a reorganizar cuando incorporemos las pruebas y GitHub Actions.

## Estado del proyecto

Hecho:

- Repositorio con monorepo (`frontend/` y `backend/`).
- Frontend React + Vite funcionando.
- Backend Spring Boot conectado a PostgreSQL (JPA y Flyway para las migraciones).
- PostgreSQL local con Docker Compose.

Pendiente (esta guía se va a actualizar a medida que avance):

- Reorganización de las pruebas y GitHub Actions.
- Protección de la rama `main` y obligatoriedad de Pull Requests.
- Primeras migraciones de base de datos y modelo de datos.
- Despliegue en una Raspberry Pi.
