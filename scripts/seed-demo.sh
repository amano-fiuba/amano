#!/usr/bin/env bash
# Carga datos de demo en la base de A Mano (usuarios verificados, gastos únicos y
# series recurrentes en los tres estados). Se corre en el host, desde cualquier
# directorio, con el stack ya levantado:
#
#   ./scripts/seed-demo.sh
#
# Usa `docker compose exec` contra el servicio `db`, así que respeta el .env de la
# raíz (en la Raspberry, COMPOSE_FILE=compose.prod.yaml). Es re-ejecutable: borra
# primero las cuentas @amano.demo (los gastos caen en cascada) y las vuelve a crear.
# No toca ninguna otra cuenta.
#
# Todas las cuentas usan la contraseña de DEMO_PASSWORD (por defecto Demo1234!).
# Las fechas son relativas a "hoy" en America/Argentina/Buenos_Aires, igual que la
# generación diaria de recurrentes, así que el seed sirve el día que se corra.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
    echo "Falta .env en $ROOT_DIR" >&2
    exit 1
fi

# Solo las variables que necesitamos, sin ejecutar el .env entero.
env_value() {
    grep -E "^$1=" .env | tail -n1 | cut -d= -f2- | sed -e 's/^["'\'']//' -e 's/["'\'']$//'
}
POSTGRES_USER="$(env_value POSTGRES_USER)"
POSTGRES_DB="$(env_value POSTGRES_DB)"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo1234!}"

if ! docker compose ps --status running --services | grep -qx db; then
    echo "El servicio db no está corriendo. Levantá el stack con 'docker compose up -d'." >&2
    exit 1
fi

# El backend crea las tablas con Flyway al arrancar; sin ellas no hay nada que sembrar.
if ! docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
        "SELECT to_regclass('public.expenses') IS NOT NULL" | grep -qx t; then
    echo "La tabla expenses no existe todavía: esperá a que el backend termine de migrar." >&2
    exit 1
fi

echo "Cargando datos de demo en '$POSTGRES_DB'..."

docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 -v demo_password="$DEMO_PASSWORD" <<'SQL'
\set QUIET on
SET client_min_messages = warning;
BEGIN;

-- crypt()/gen_salt('bf') generan BCrypt $2a$, que BCryptPasswordEncoder acepta.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TEMP TABLE demo_today ON COMMIT DROP AS
SELECT (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date AS d;

-- Re-ejecutable: los gastos y monedas caen en cascada.
DELETE FROM users WHERE email LIKE '%@amano.demo';

-- ---------------------------------------------------------------- usuarios
INSERT INTO users (first_name, last_name, email, password_hash, cbu,
                   terms_accepted_at, email_verified_at, created_at, updated_at)
SELECT u.first_name, u.last_name, u.email,
       crypt(:'demo_password', gen_salt('bf', 10)), u.cbu,
       now() - u.age, now() - u.age, now() - u.age, now() - u.age
FROM (VALUES
    ('Lucía',    'Fernández', 'lucia@amano.demo',    '0170099220000067797370', interval '60 days'),
    ('Martín',   'Gómez',     'martin@amano.demo',   '2850590940090418135201', interval '45 days'),
    ('Sofía',    'Rodríguez', 'sofia@amano.demo',    NULL,                     interval '30 days'),
    ('Tomás',    'López',     'tomas@amano.demo',    '0110599520000001234567', interval '20 days'),
    ('Valentina','Martínez',  'valentina@amano.demo',NULL,                     interval '10 days'),
    ('Joaquín',  NULL,        'joaquin@amano.demo',  NULL,                     interval '5 days')
) AS u(first_name, last_name, email, cbu, age);

INSERT INTO user_currencies (user_id, currency_code)
SELECT us.id, c.code
FROM (VALUES
    ('lucia@amano.demo',  'ARS'), ('lucia@amano.demo',  'USD'),
    ('martin@amano.demo', 'ARS'), ('martin@amano.demo', 'USD'), ('martin@amano.demo', 'EUR'),
    ('tomas@amano.demo',  'USD')
) AS c(email, code)
JOIN users us ON us.email = c.email;

-- ------------------------------------------------------------ gastos únicos
INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                      payment_method, description, idempotency_key, created_at)
SELECT us.id, e.amount, e.currency, t.d - e.days_ago, e.category, 'ONE_TIME',
       e.payment, e.description, gen_random_uuid(), (t.d - e.days_ago) + time '12:00'
FROM (VALUES
    ('lucia@amano.demo',  18500.00, 'ARS',  1, 'FOOD',      'DEBIT_CARD',     'Supermercado del finde'),
    ('lucia@amano.demo',   4200.00, 'ARS',  2, 'TRANSPORT', 'VIRTUAL_WALLET', 'Uber a la facu'),
    ('lucia@amano.demo',  32000.00, 'ARS',  5, 'LEISURE',   'CREDIT_CARD',    'Entradas al recital'),
    ('lucia@amano.demo',     45.00, 'USD',  9, 'SHOPPING',  'CREDIT_CARD',    'Zapatillas (compra online)'),
    ('lucia@amano.demo',  12750.50, 'ARS', 14, 'HEALTH',    'DEBIT_CARD',     'Farmacia'),
    ('lucia@amano.demo',   9800.00, 'ARS', 21, 'PETS',      'CASH',           'Alimento para el perro'),
    ('lucia@amano.demo',  25000.00, 'ARS', 33, 'EDUCATION', 'TRANSFER',       'Apuntes y fotocopias'),
    ('martin@amano.demo', 15600.00, 'ARS',  0, 'FOOD',      'CASH',           'Pizza con amigos'),
    ('martin@amano.demo',    120.00, 'EUR', 12, 'LEISURE',  'CREDIT_CARD',    'Pasajes de tren'),
    ('martin@amano.demo',  7300.00, 'ARS', 18, 'TRANSPORT', 'DEBIT_CARD',     'Carga SUBE'),
    ('sofia@amano.demo',   22000.00, 'ARS',  3, 'SHOPPING', 'VIRTUAL_WALLET', NULL),
    ('tomas@amano.demo',      80.00, 'USD',  6, 'OTHER',    'CASH',           'Regalo de cumpleaños')
) AS e(email, amount, currency, days_ago, category, payment, description)
JOIN users us ON us.email = e.email
CROSS JOIN demo_today t;

-- ------------------------------------------------------- gastos recurrentes
-- Una serie es la fila con frecuencia+estado (su expense_date es el inicio, n = 0);
-- las repeticiones son las fechas start + n períodos (n >= 1) hasta el corte, con
-- recurrence_source_id apuntando a la serie, igual que RecurringExpenseService.
--   ACTIVE:   corte = hoy, próxima fecha = primera posterior a hoy.
--   PAUSED:   corte = día de la pausa, próxima fecha = primera posterior a ese día.
--   FINISHED: corte = día de la baja, sin próxima fecha.
CREATE TEMP TABLE demo_series ON COMMIT DROP AS
SELECT gen_random_uuid() AS key, us.id AS user_id, s.amount, s.currency,
       t.d - s.start_days_ago AS start_date, s.category, s.payment, s.description,
       s.freq, s.status, t.d - s.cutoff_days_ago AS cutoff,
       CASE s.freq WHEN 'WEEKLY'  THEN interval '1 week'
                   WHEN 'MONTHLY' THEN interval '1 month'
                   ELSE interval '1 year' END AS step
FROM (VALUES
    -- email, monto, moneda, inicio (días atrás), categoría, medio, descripción, frecuencia, estado, corte (días atrás)
    ('lucia@amano.demo',  350000.00, 'ARS', 200, 'HOUSING',   'TRANSFER',       'Alquiler',              'MONTHLY', 'ACTIVE',   0),
    ('lucia@amano.demo',   11999.00, 'ARS', 150, 'LEISURE',   'CREDIT_CARD',    'Netflix',               'MONTHLY', 'ACTIVE',   0),
    ('lucia@amano.demo',   28000.00, 'ARS', 120, 'SERVICES',  'DEBIT_CARD',     'Internet + celular',    'MONTHLY', 'ACTIVE',   0),
    ('lucia@amano.demo',    9000.00, 'ARS',  56, 'FOOD',      'CASH',           'Verdulería del barrio', 'WEEKLY',  'ACTIVE',   0),
    ('lucia@amano.demo',   45000.00, 'ARS', 180, 'HEALTH',    'DEBIT_CARD',     'Gimnasio',              'MONTHLY', 'PAUSED',  40),
    ('lucia@amano.demo',      99.00, 'USD', 400, 'EDUCATION', 'CREDIT_CARD',    'Curso de inglés online','YEARLY',  'ACTIVE',   0),
    ('lucia@amano.demo',    6500.00, 'ARS', 160, 'LEISURE',   'CREDIT_CARD',    'Spotify',               'MONTHLY', 'FINISHED', 30),
    ('martin@amano.demo', 280000.00, 'ARS',  95, 'HOUSING',   'TRANSFER',       'Expensas',              'MONTHLY', 'ACTIVE',   0),
    ('martin@amano.demo',  15000.00, 'ARS',  60, 'TRANSPORT', 'VIRTUAL_WALLET', 'Estacionamiento',       'WEEKLY',  'PAUSED',  10),
    ('tomas@amano.demo',       10.00, 'USD',  90, 'SERVICES', 'CREDIT_CARD',    'iCloud',                'MONTHLY', 'ACTIVE',   0)
) AS s(email, amount, currency, start_days_ago, category, payment, description, freq, status, cutoff_days_ago)
JOIN users us ON us.email = s.email
CROSS JOIN demo_today t;

INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                      payment_method, description, idempotency_key, created_at,
                      recurrence_frequency, recurrence_status, recurrence_next_date)
SELECT s.user_id, s.amount, s.currency, s.start_date, s.category, 'RECURRING',
       s.payment, s.description, s.key, s.start_date + time '12:00',
       s.freq, s.status,
       CASE WHEN s.status = 'FINISHED' THEN NULL ELSE (
           SELECT min((s.start_date + n * s.step)::date)
           FROM generate_series(1, 1000) AS n
           WHERE (s.start_date + n * s.step)::date > s.cutoff
       ) END
FROM demo_series s;

INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                      payment_method, description, idempotency_key, created_at,
                      recurrence_source_id)
SELECT e.user_id, e.amount, e.currency, o.d, e.category, 'RECURRING',
       e.payment_method, e.description, gen_random_uuid(), o.d + time '00:15', e.id
FROM demo_series s
JOIN expenses e ON e.user_id = s.user_id AND e.idempotency_key = s.key
CROSS JOIN LATERAL (
    SELECT (s.start_date + n * s.step)::date AS d
    FROM generate_series(1, 1000) AS n
) o
WHERE o.d <= s.cutoff;

COMMIT;

\set QUIET off
\echo
\echo 'Resumen:'
SELECT u.email,
       count(e.*) FILTER (WHERE e.expense_type = 'ONE_TIME')            AS unicos,
       count(e.*) FILTER (WHERE e.recurrence_frequency IS NOT NULL)     AS series,
       count(e.*) FILTER (WHERE e.recurrence_source_id IS NOT NULL)     AS repeticiones
FROM users u
LEFT JOIN expenses e ON e.user_id = u.id
WHERE u.email LIKE '%@amano.demo'
GROUP BY u.email, u.created_at
ORDER BY u.created_at;
SQL

echo
echo "Listo. Todas las cuentas @amano.demo entran con la contraseña: $DEMO_PASSWORD"
echo "Cuenta principal para la demo: lucia@amano.demo"
