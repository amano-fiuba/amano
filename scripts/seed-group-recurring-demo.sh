#!/usr/bin/env bash
# Carga datos de demo para probar la spec 029: gastos recurrentes de grupo.
# Requiere que seed-demo.sh ya haya corrido (crea las cuentas @amano.demo) y
# que el stack esté levantado. Es re-ejecutable: borra y recrea el grupo
# "Depto Demo 029".
#
# Uso:
#   ./scripts/seed-demo.sh
#   ./scripts/seed-group-recurring-demo.sh
#
# Todas las cuentas usan la contraseña DEMO_PASSWORD (por defecto Demo1234!).

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
    echo "Falta .env en $ROOT_DIR" >&2
    exit 1
fi

env_value() {
    grep -E "^$1=" .env | tail -n1 | cut -d= -f2- | sed -e 's/^["'\''"]//' -e 's/["'\''"]$//'
}

POSTGRES_USER="$(env_value POSTGRES_USER)"
POSTGRES_DB="$(env_value POSTGRES_DB)"

if ! docker compose ps --status running --services | grep -qx db; then
    echo "El servicio db no está corriendo. Levantá el stack con 'docker compose up -d'." >&2
    exit 1
fi

if ! docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
        "SELECT to_regclass('public.group_expenses') IS NOT NULL" | grep -qx t; then
    echo "La tabla group_expenses no existe todavía: esperá a que el backend termine de migrar." >&2
    exit 1
fi

echo "Cargando grupo de demo con gastos recurrentes..."

docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 <<'SQL'
\set QUIET on
SET client_min_messages = warning;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TEMP TABLE demo_today AS
SELECT (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date AS d;

SELECT id AS lucia_id FROM users WHERE email = 'lucia@amano.demo' \gset u_
SELECT id AS martin_id FROM users WHERE email = 'martin@amano.demo' \gset u_
SELECT id AS sofia_id FROM users WHERE email = 'sofia@amano.demo' \gset u_
SELECT id AS tomas_id FROM users WHERE email = 'tomas@amano.demo' \gset u_

DELETE FROM groups WHERE name = 'Depto Demo 029';

INSERT INTO groups (name, description, primary_currency, debt_due_days)
VALUES ('Depto Demo 029',
        'Grupo de demo para probar gastos recurrentes de grupo (spec 029)',
        'ARS',
        7)
RETURNING id AS id \gset g_

INSERT INTO group_members (user_id, group_id, role) VALUES
  (:u_lucia_id, :g_id, 'ADMIN'),
  (:u_martin_id, :g_id, 'MEMBER'),
  (:u_sofia_id, :g_id, 'MEMBER'),
  (:u_tomas_id, :g_id, 'MEMBER');

CREATE TEMP TABLE demo_series AS
SELECT gen_random_uuid() AS key,
       s.description,
       s.amount,
       s.category,
       s.payment_method,
       s.frequency,
       s.status,
       t.d - s.start_days_ago AS start_date,
       t.d - s.cutoff_days_ago AS cutoff_date,
       CASE s.frequency
         WHEN 'WEEKLY'  THEN interval '1 week'
         WHEN 'MONTHLY' THEN interval '1 month'
         ELSE interval '1 year'
       END AS step
FROM (VALUES
  -- description, amount, category, payment_method, frequency, status, start_days_ago, cutoff_days_ago
  ('Alquiler',              350000.00, 'HOUSING',   'TRANSFER',       'MONTHLY', 'ACTIVE',    60,  0),
  ('Internet + celular',     28000.00, 'SERVICES',  'DEBIT_CARD',     'MONTHLY', 'ACTIVE',   120,  0),
  ('Verdulería del barrio',   9000.00, 'FOOD',      'CASH',           'WEEKLY',  'ACTIVE',    56,  0),
  ('Gimnasio',               45000.00, 'HEALTH',    'DEBIT_CARD',     'MONTHLY', 'PAUSED',   180, 40),
  ('Spotify',                 6500.00, 'LEISURE',   'CREDIT_CARD',    'MONTHLY', 'FINISHED', 160, 30)
) AS s(description, amount, category, payment_method, frequency, status, start_days_ago, cutoff_days_ago)
CROSS JOIN demo_today t;

CREATE TEMP TABLE series_ids AS
WITH inserted AS (
  INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
      expense_date, due_date, description, idempotency_key, expense_type, category, payment_method,
      recurrence_frequency, recurrence_status, recurrence_next_date, recurrence_anchor_date)
  SELECT :g_id, :u_lucia_id, :u_lucia_id, s.amount, 'ARS',
      s.start_date,
      s.start_date + 7,
      s.description,
      s.key,
      'RECURRING',
      s.category,
      s.payment_method,
      s.frequency,
      s.status,
      CASE WHEN s.status = 'FINISHED' THEN NULL
           ELSE (SELECT min((s.start_date + n * s.step)::date)
                 FROM generate_series(1, 1000) AS n
                 WHERE (s.start_date + n * s.step)::date > s.cutoff_date)
      END,
      s.start_date
  FROM demo_series s
  RETURNING id, idempotency_key
)
SELECT id, idempotency_key FROM inserted;

INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
    expense_date, due_date, description, idempotency_key, expense_type, category, payment_method,
    recurrence_source_id, recurrence_created_via)
SELECT :g_id, :u_lucia_id, :u_lucia_id, s.amount, 'ARS',
    o.d,
    o.d,
    s.description,
    gen_random_uuid(),
    'RECURRING',
    s.category,
    s.payment_method,
    si.id,
    'JOB'
FROM demo_series s
JOIN series_ids si ON si.idempotency_key = s.key
CROSS JOIN LATERAL (
    SELECT (s.start_date + n * s.step)::date AS d
    FROM generate_series(1, 1000) AS n
) o
WHERE o.d <= s.cutoff_date;

INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id, participant_name, amount)
SELECT ge.id, 0, 'REGISTERED', :u_lucia_id, 'Lucía Fernández', ge.amount / 2
FROM group_expenses ge
WHERE ge.group_id = :g_id AND ge.expense_type = 'RECURRING';

INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id, participant_name, amount)
SELECT ge.id, 1, 'REGISTERED', :u_martin_id, 'Martín Gómez', ge.amount / 2
FROM group_expenses ge
WHERE ge.group_id = :g_id AND ge.expense_type = 'RECURRING';

INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id, debtor_name,
                   creditor_user_id, currency, amount)
SELECT ge.group_id, ge.id, sh.id, 'REGISTERED', :u_martin_id, 'Martín Gómez',
       :u_lucia_id, 'ARS', sh.amount
FROM group_expenses ge
JOIN group_expense_shares sh ON sh.expense_id = ge.id
WHERE ge.group_id = :g_id
  AND ge.expense_type = 'RECURRING'
  AND sh.position = 1;

-- Marca como pagada la deuda de la última ocurrencia generada de una serie
-- ACTIVE, para probar el caso en el que nextDueDate se calcula a partir de
-- recurrence_next_date.
UPDATE debts
SET status = 'PAID',
    paid_amount = amount
WHERE id = (
  SELECT max(d.id)
  FROM debts d
  JOIN group_expenses ge ON ge.id = d.expense_id
  WHERE d.group_id = :g_id
    AND ge.recurrence_source_id IS NOT NULL
    AND ge.recurrence_status = 'ACTIVE'
);

\set QUIET off
\echo ''
\echo '==========================================='
\echo 'Grupo de demo creado'
\echo '==========================================='
\echo 'ID del grupo:' :g_id
\echo ''
\echo 'Ocurrencia pagada (para testear nextDueDate con repeticiones):'
\echo ''
SELECT ge.description AS serie,
       ge.expense_date AS fecha,
       d.amount AS monto,
       d.status AS estado
FROM debts d
JOIN group_expenses ge ON ge.id = d.expense_id
WHERE d.id = (
  SELECT max(d2.id)
  FROM debts d2
  JOIN group_expenses ge2 ON ge2.id = d2.expense_id
  WHERE d2.group_id = :g_id
    AND ge2.recurrence_source_id IS NOT NULL
    AND ge2.recurrence_status = 'ACTIVE'
);
\echo ''
\echo 'Nombre:      Depto Demo 029'
\echo ''
\echo 'URLs (frontend):'
\echo '  Lista de recurrentes: /groups/' :g_id '/recurring-expenses'
\echo '  Detalle del grupo:    /groups/' :g_id
\echo '  Historial:            /groups/' :g_id '/history'
\echo ''
\echo 'API:'
\echo '  GET    /api/groups/' :g_id '/recurring-expenses'
\echo '  PATCH  /api/groups/' :g_id '/recurring-expenses/<seriesId>'
\echo ''
SQL

echo
echo "Listo. Recordá que las cuentas @amano.demo usan la contraseña: ${DEMO_PASSWORD:-Demo1234!}"
echo "Cuenta principal: lucia@amano.demo (ADMIN del grupo)"
