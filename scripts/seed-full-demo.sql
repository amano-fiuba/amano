-- =============================================================================
-- Seed completo de datos de prueba para A Mano.
--
-- =============================================================================
\set QUIET on
SET client_min_messages = warning;
BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- crypt()/gen_salt('bf') -> BCrypt $2a$

CREATE TEMP TABLE demo_today ON COMMIT DROP AS
SELECT (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date AS d;

-- ============================================================ limpieza demo
CREATE TEMP TABLE old_groups ON COMMIT DROP AS
SELECT id FROM groups WHERE name LIKE '[Demo] %' OR name = 'Depto Demo 029';
CREATE TEMP TABLE old_users ON COMMIT DROP AS
SELECT id FROM users WHERE email LIKE '%@amano.demo';
CREATE TEMP TABLE old_expenses ON COMMIT DROP AS
SELECT id FROM group_expenses
WHERE group_id IN (SELECT id FROM old_groups)
   OR created_by_user_id IN (SELECT id FROM old_users)
   OR payer_user_id IN (SELECT id FROM old_users)
   OR friend_user_id IN (SELECT id FROM old_users);
CREATE TEMP TABLE old_debts ON COMMIT DROP AS
SELECT id FROM debts
WHERE group_id IN (SELECT id FROM old_groups)
   OR expense_id IN (SELECT id FROM old_expenses)
   OR debtor_user_id IN (SELECT id FROM old_users)
   OR creditor_user_id IN (SELECT id FROM old_users);

DELETE FROM reminders      WHERE group_id IN (SELECT id FROM old_groups)
                              OR debt_id IN (SELECT id FROM old_debts)
                              OR expense_id IN (SELECT id FROM old_expenses);
DELETE FROM debt_whistles  WHERE group_id IN (SELECT id FROM old_groups)
                              OR debt_id IN (SELECT id FROM old_debts);
-- 035: bandeja y preferencias de los usuarios demo (las FK a users también
-- cascadean, pero se limpia explícitamente para repetir el seed sin duplicados).
DELETE FROM notifications WHERE user_id IN (SELECT id FROM old_users);
DELETE FROM user_notification_preferences WHERE user_id IN (SELECT id FROM old_users);
DELETE FROM payments       WHERE group_id IN (SELECT id FROM old_groups)
                              OR debt_id IN (SELECT id FROM old_debts)
                              OR payer_user_id IN (SELECT id FROM old_users)
                              OR receiver_user_id IN (SELECT id FROM old_users);
DELETE FROM debts          WHERE id IN (SELECT id FROM old_debts);
DELETE FROM group_expense_shares WHERE expense_id IN (SELECT id FROM old_expenses);
DELETE FROM group_expenses WHERE id IN (SELECT id FROM old_expenses);
DELETE FROM group_activity WHERE group_id IN (SELECT id FROM old_groups)
                              OR actor_user_id IN (SELECT id FROM old_users);
DELETE FROM group_permissions WHERE group_id IN (SELECT id FROM old_groups);
DELETE FROM group_members  WHERE group_id IN (SELECT id FROM old_groups)
                              OR user_id IN (SELECT id FROM old_users);
DELETE FROM simulated_profiles WHERE group_id IN (SELECT id FROM old_groups);
DELETE FROM groups         WHERE id IN (SELECT id FROM old_groups);
-- users: gastos, ingresos, monedas, amistades y tokens caen en cascada.
DELETE FROM users          WHERE id IN (SELECT id FROM old_users);

-- ================================================================== usuarios
INSERT INTO users (first_name, last_name, email, password_hash, cbu,
                   terms_accepted_at, email_verified_at, created_at, updated_at)
SELECT u.first_name, u.last_name, u.email,
       crypt(:'demo_password', gen_salt('bf', 10)), u.cbu,
       now() - u.age, CASE WHEN u.verified THEN now() - u.age END, now() - u.age, now() - u.age
FROM (VALUES
    ('Lucía',     'Fernández', 'lucia@amano.demo',     '0170099220000067797370', interval '120 days', true),
    ('Martín',    'Gómez',     'martin@amano.demo',    '2850590940090418135201', interval '110 days', true),
    ('Sofía',     'Rodríguez', 'sofia@amano.demo',     NULL,                     interval '100 days', true),
    ('Tomás',     'López',     'tomas@amano.demo',     '0110599520000001234567', interval '90 days',  true),
    ('Valentina', 'Martínez',  'valentina@amano.demo', NULL,                     interval '80 days',  true),
    ('Joaquín',   NULL,        'joaquin@amano.demo',   NULL,                     interval '70 days',  true),
    ('Camila',    'Suárez',    'pendiente@amano.demo', NULL,                     interval '1 day',    false)
) AS u(first_name, last_name, email, cbu, age, verified);

INSERT INTO user_currencies (user_id, currency_code)
SELECT us.id, c.code
FROM (VALUES
    ('lucia@amano.demo', 'ARS'), ('lucia@amano.demo', 'USD'), ('lucia@amano.demo', 'EUR'),
    ('martin@amano.demo', 'ARS'), ('martin@amano.demo', 'USD'), ('martin@amano.demo', 'EUR'),
    ('sofia@amano.demo', 'ARS'),
    ('tomas@amano.demo', 'ARS'), ('tomas@amano.demo', 'USD'),
    ('valentina@amano.demo', 'ARS'), ('valentina@amano.demo', 'USD'),
    ('joaquin@amano.demo', 'ARS'), ('joaquin@amano.demo', 'BRL')
) AS c(email, code)
JOIN users us ON us.email = c.email;

-- ================================================================= amistades
INSERT INTO friendship_requests (sender_id, recipient_id, status, created_at, responded_at)
SELECT s.id, r.id, f.status, now() - f.age, CASE WHEN f.status = 'PENDING' THEN NULL ELSE now() - f.age + interval '1 hour' END
FROM (VALUES
    ('lucia@amano.demo',  'martin@amano.demo',    'ACCEPTED', interval '100 days'),
    ('lucia@amano.demo',  'sofia@amano.demo',     'ACCEPTED', interval '95 days'),
    ('valentina@amano.demo', 'lucia@amano.demo',  'ACCEPTED', interval '70 days'),
    ('tomas@amano.demo',  'lucia@amano.demo',     'PENDING',  interval '3 days'),
    ('lucia@amano.demo',  'joaquin@amano.demo',   'PENDING',  interval '2 days'),
    ('martin@amano.demo', 'sofia@amano.demo',     'REJECTED', interval '40 days')
) AS f(sender, recipient, status, age)
JOIN users s ON s.email = f.sender
JOIN users r ON r.email = f.recipient;

-- ==================================================== gastos personales únicos
INSERT INTO expenses (user_id, amount, currency, expense_date, category, expense_type,
                      payment_method, description, idempotency_key, created_at)
SELECT us.id, e.amount, e.currency, t.d - e.days_ago, e.category, 'ONE_TIME',
       e.payment, e.description, gen_random_uuid(), (t.d - e.days_ago) + time '12:00'
FROM (VALUES
    ('lucia@amano.demo',  18500.00, 'ARS',   1, 'FOOD',      'DEBIT_CARD',     'Supermercado del finde'),
    ('lucia@amano.demo',   4200.00, 'ARS',   2, 'TRANSPORT', 'VIRTUAL_WALLET', 'Uber a la facu'),
    ('lucia@amano.demo',  32000.00, 'ARS',   5, 'LEISURE',   'CREDIT_CARD',    'Entradas al recital'),
    ('lucia@amano.demo',     45.00, 'USD',   9, 'SHOPPING',  'CREDIT_CARD',    'Zapatillas (compra online)'),
    ('lucia@amano.demo',  12750.50, 'ARS',  14, 'HEALTH',    'DEBIT_CARD',     'Farmacia'),
    ('lucia@amano.demo',   9800.00, 'ARS',  21, 'PETS',      'CASH',           'Alimento para el perro'),
    ('lucia@amano.demo',  25000.00, 'ARS',  33, 'EDUCATION', 'TRANSFER',       'Apuntes y fotocopias'),
    ('lucia@amano.demo',     60.00, 'EUR',  47, 'LEISURE',   'CREDIT_CARD',    'Museo en Madrid'),
    ('lucia@amano.demo',  41000.00, 'ARS',  62, 'SERVICES',  'DEBIT_CARD',     'Reparación del celular'),
    ('lucia@amano.demo',  15300.00, 'ARS',  95, 'OTHER',     'CASH',           NULL),
    ('martin@amano.demo', 15600.00, 'ARS',   0, 'FOOD',      'CASH',           'Pizza con amigos'),
    ('martin@amano.demo',   120.00, 'EUR',  12, 'LEISURE',   'CREDIT_CARD',    'Pasajes de tren'),
    ('martin@amano.demo',  7300.00, 'ARS',  18, 'TRANSPORT', 'DEBIT_CARD',     'Carga SUBE'),
    ('martin@amano.demo',   250.00, 'USD',  30, 'EDUCATION', 'CREDIT_CARD',    'Curso online'),
    ('sofia@amano.demo',  22000.00, 'ARS',   3, 'SHOPPING',  'VIRTUAL_WALLET', NULL),
    ('sofia@amano.demo',   8900.00, 'ARS',  11, 'FOOD',      'DEBIT_CARD',     'Almuerzos de la semana'),
    ('tomas@amano.demo',     80.00, 'USD',   6, 'OTHER',     'CASH',           'Regalo de cumpleaños'),
    ('valentina@amano.demo', 5600.00, 'ARS', 4, 'PETS',      'DEBIT_CARD',     'Veterinaria'),
    ('joaquin@amano.demo',  2300.00, 'BRL', 20, 'FOOD',      'CREDIT_CARD',    'Churrasquería')
) AS e(email, amount, currency, days_ago, category, payment, description)
JOIN users us ON us.email = e.email
CROSS JOIN demo_today t;

-- ================================================== gastos personales recurrentes
-- Una serie es la fila con frecuencia+estado; las repeticiones son start + n períodos
-- hasta el corte, con recurrence_source_id apuntando a la serie.
CREATE TEMP TABLE demo_series ON COMMIT DROP AS
SELECT gen_random_uuid() AS key, us.id AS user_id, s.amount, s.currency,
       t.d - s.start_days_ago AS start_date, s.category, s.payment, s.description,
       s.freq, s.status, t.d - s.cutoff_days_ago AS cutoff,
       CASE s.freq WHEN 'WEEKLY'  THEN interval '1 week'
                   WHEN 'MONTHLY' THEN interval '1 month'
                   ELSE interval '1 year' END AS step
FROM (VALUES
    ('lucia@amano.demo',  350000.00, 'ARS', 200, 'HOUSING',   'TRANSFER',       'Alquiler',               'MONTHLY', 'ACTIVE',   0),
    ('lucia@amano.demo',   11999.00, 'ARS', 150, 'LEISURE',   'CREDIT_CARD',    'Netflix',                'MONTHLY', 'ACTIVE',   0),
    ('lucia@amano.demo',   28000.00, 'ARS', 120, 'SERVICES',  'DEBIT_CARD',     'Internet + celular',     'MONTHLY', 'ACTIVE',   0),
    ('lucia@amano.demo',    9000.00, 'ARS',  56, 'FOOD',      'CASH',           'Verdulería del barrio',  'WEEKLY',  'ACTIVE',   0),
    ('lucia@amano.demo',   45000.00, 'ARS', 180, 'HEALTH',    'DEBIT_CARD',     'Gimnasio',               'MONTHLY', 'PAUSED',  40),
    ('lucia@amano.demo',      99.00, 'USD', 400, 'EDUCATION', 'CREDIT_CARD',    'Curso de inglés online', 'YEARLY',  'ACTIVE',   0),
    ('lucia@amano.demo',    6500.00, 'ARS', 160, 'LEISURE',   'CREDIT_CARD',    'Spotify',                'MONTHLY', 'FINISHED', 30),
    ('martin@amano.demo', 280000.00, 'ARS',  95, 'HOUSING',   'TRANSFER',       'Expensas',               'MONTHLY', 'ACTIVE',   0),
    ('martin@amano.demo',  15000.00, 'ARS',  60, 'TRANSPORT', 'VIRTUAL_WALLET', 'Estacionamiento',        'WEEKLY',  'PAUSED',  10),
    ('tomas@amano.demo',      10.00, 'USD',  90, 'SERVICES',  'CREDIT_CARD',    'iCloud',                 'MONTHLY', 'ACTIVE',   0)
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

-- ========================================================== ingresos personales
-- Efectivizados (fecha <= hoy) y proyectados (fecha futura), multi-moneda.
INSERT INTO incomes (user_id, amount, currency, income_date, category, payment_method,
                     description, idempotency_key, created_at)
SELECT us.id, i.amount, i.currency, t.d + i.day_offset, i.category, i.payment, i.description,
       gen_random_uuid(), LEAST(now(), (t.d + i.day_offset) + time '09:00')
FROM (VALUES
    -- efectivizados
    ('lucia@amano.demo',  900000.00, 'ARS', -150, 'SALARY',    'TRANSFER',       'Sueldo (hace 5 meses)'),
    ('lucia@amano.demo',  920000.00, 'ARS', -120, 'SALARY',    'TRANSFER',       'Sueldo (hace 4 meses)'),
    ('lucia@amano.demo',  950000.00, 'ARS',  -90, 'SALARY',    'TRANSFER',       'Sueldo (hace 3 meses)'),
    ('lucia@amano.demo',  980000.00, 'ARS',  -60, 'SALARY',    'TRANSFER',       'Sueldo (hace 2 meses)'),
    ('lucia@amano.demo', 1020000.00, 'ARS',  -30, 'SALARY',    'TRANSFER',       'Sueldo del mes pasado'),
    ('lucia@amano.demo',     400.00, 'USD',  -45, 'FREELANCE', 'TRANSFER',       'Proyecto web para cliente'),
    ('lucia@amano.demo',     650.00, 'USD',  -10, 'FREELANCE', 'VIRTUAL_WALLET', 'Diseño de logo'),
    ('lucia@amano.demo',   85000.00, 'ARS',  -20, 'SALE',      'CASH',           'Venta de bicicleta'),
    ('lucia@amano.demo',   12400.00, 'ARS',   -5, 'REFUND',    'TRANSFER',       'Reintegro de la prepaga'),
    ('martin@amano.demo', 1300000.00,'ARS',  -30, 'SALARY',    'TRANSFER',       'Sueldo del mes pasado'),
    ('martin@amano.demo',   1200.00, 'USD',  -15, 'FREELANCE', 'TRANSFER',       'Consultoría'),
    ('martin@amano.demo',  50000.00, 'ARS',   -3, 'OTHER',     'CASH',           NULL),
    ('sofia@amano.demo',   600000.00,'ARS',  -28, 'SALARY',    'TRANSFER',       'Sueldo'),
    ('tomas@amano.demo',   300.00,   'USD',  -12, 'FREELANCE', 'VIRTUAL_WALLET', 'Clases particulares'),
    -- proyectados (futuros)
    ('lucia@amano.demo',  1060000.00,'ARS',    2, 'SALARY',    'TRANSFER',       'Sueldo de este mes'),
    ('lucia@amano.demo',  1100000.00,'ARS',   32, 'SALARY',    'TRANSFER',       'Sueldo del mes próximo'),
    ('lucia@amano.demo',     800.00, 'USD',   20, 'FREELANCE', 'TRANSFER',       'Proyecto a cobrar (proyectado)'),
    ('lucia@amano.demo',  300000.00, 'ARS',   45, 'OTHER',     'TRANSFER',       'Aguinaldo estimado'),
    ('martin@amano.demo', 1350000.00,'ARS',    5, 'SALARY',    'TRANSFER',       'Sueldo de este mes'),
    ('martin@amano.demo',   1500.00, 'USD',   25, 'FREELANCE', 'TRANSFER',       'Cobro de consultoría'),
    ('sofia@amano.demo',   620000.00,'ARS',    4, 'SALARY',    'TRANSFER',       'Sueldo de este mes')
) AS i(email, amount, currency, day_offset, category, payment, description)
JOIN users us ON us.email = i.email
CROSS JOIN demo_today t;

-- ===================================================================== grupos
INSERT INTO groups (name, description, primary_currency, debt_due_days, created_at, updated_at)
VALUES
    ('[Demo] Depto Palermo',  'Gastos del departamento compartido',        'ARS', 10,   now() - interval '70 days', now()),
    ('[Demo] Viaje a Río',    'Viaje de verano, cuentas en dólares',       'USD', 15,   now() - interval '55 days', now()),
    ('[Demo] Cumple de Sofi', 'Regalo y torta, validación de pagos activa','ARS', 7,    now() - interval '25 days', now());

INSERT INTO group_members (user_id, group_id, role, joined_at, left_at)
SELECT us.id, g.id, m.role, now() - m.joined, CASE WHEN m.left_ago IS NULL THEN NULL ELSE now() - m.left_ago END
FROM (VALUES
    ('[Demo] Depto Palermo',  'lucia@amano.demo',     'ADMIN',  interval '70 days', NULL::interval),
    ('[Demo] Depto Palermo',  'martin@amano.demo',    'MEMBER', interval '69 days', NULL),
    ('[Demo] Depto Palermo',  'sofia@amano.demo',     'MEMBER', interval '68 days', NULL),
    ('[Demo] Depto Palermo',  'tomas@amano.demo',     'MEMBER', interval '60 days', NULL),
    ('[Demo] Viaje a Río',    'lucia@amano.demo',     'ADMIN',  interval '55 days', NULL),
    ('[Demo] Viaje a Río',    'valentina@amano.demo', 'MEMBER', interval '54 days', NULL),
    ('[Demo] Viaje a Río',    'joaquin@amano.demo',   'MEMBER', interval '54 days', NULL),
    ('[Demo] Viaje a Río',    'martin@amano.demo',    'MEMBER', interval '53 days', NULL),
    ('[Demo] Viaje a Río',    'tomas@amano.demo',     'MEMBER', interval '53 days', interval '20 days'),
    ('[Demo] Cumple de Sofi', 'sofia@amano.demo',     'ADMIN',  interval '25 days', NULL),
    ('[Demo] Cumple de Sofi', 'lucia@amano.demo',     'MEMBER', interval '25 days', NULL),
    ('[Demo] Cumple de Sofi', 'valentina@amano.demo', 'MEMBER', interval '24 days', NULL)
) AS m(gname, email, role, joined, left_ago)
JOIN groups g ON g.name = m.gname
JOIN users us ON us.email = m.email;

INSERT INTO group_permissions (group_id, members_cannot_make_expenses, members_cannot_invite,
                               members_can_manage_simulated_profiles,
                               members_can_generate_expenses_for_others,
                               payments_require_validation, members_can_edit_group_data)
SELECT g.id, false, (g.name = '[Demo] Viaje a Río'), (g.name = '[Demo] Depto Palermo'), true,
       (g.name = '[Demo] Cumple de Sofi'), false
FROM groups g WHERE g.name LIKE '[Demo] %';

-- Perfil simulado (persona sin cuenta) en el depto
INSERT INTO simulated_profiles (group_id, name, email)
SELECT g.id, 'Carlos (invitado)', 'carlos@sim.demo' FROM groups g WHERE g.name = '[Demo] Depto Palermo';

-- ================================================== gastos grupales y 1:1
-- gname NULL => gasto entre amigos (friend_email). rate: cotización a la moneda
-- del grupo (se ignora si coinciden). parts: participantes (incluye al pagador).
CREATE TEMP TABLE seed_ge (
    key uuid NOT NULL DEFAULT gen_random_uuid(),
    ge_id bigint, gname text, friend_email text, payer_email text NOT NULL,
    amount numeric(11,2) NOT NULL, currency text NOT NULL, rate numeric(18,8),
    days_ago int NOT NULL, descr text NOT NULL, category text, method text,
    parts text[] NOT NULL, etype text NOT NULL DEFAULT 'ONE_TIME',
    freq text, source_descr text
) ON COMMIT DROP;

INSERT INTO seed_ge (gname, friend_email, payer_email, amount, currency, rate, days_ago, descr, category, method, parts, etype, freq)
VALUES
    -- ---- Depto Palermo (ARS)
    ('[Demo] Depto Palermo', NULL, 'lucia@amano.demo',  350000, 'ARS', NULL, 62, 'Alquiler (mes pasado)', 'HOUSING',  'TRANSFER',   ARRAY['lucia@amano.demo','martin@amano.demo','sofia@amano.demo','tomas@amano.demo'], 'ONE_TIME', NULL),
    ('[Demo] Depto Palermo', NULL, 'martin@amano.demo',  87500, 'ARS', NULL, 40, 'Supermercado grande',    'FOOD',     'DEBIT_CARD', ARRAY['lucia@amano.demo','martin@amano.demo','sofia@amano.demo','tomas@amano.demo'], 'ONE_TIME', NULL),
    ('[Demo] Depto Palermo', NULL, 'sofia@amano.demo',   45000, 'ARS', NULL, 25, 'Luz y gas',              'SERVICES', 'TRANSFER',   ARRAY['lucia@amano.demo','martin@amano.demo','sofia@amano.demo'], 'ONE_TIME', NULL),
    ('[Demo] Depto Palermo', NULL, 'tomas@amano.demo',     120, 'USD', 1180.50, 18, 'Muebles online',      'SHOPPING', 'CREDIT_CARD',ARRAY['lucia@amano.demo','martin@amano.demo','tomas@amano.demo'], 'ONE_TIME', NULL),
    ('[Demo] Depto Palermo', NULL, 'lucia@amano.demo',   62000, 'ARS', NULL,  6, 'Pizza y cine (con Carlos)', 'LEISURE','CASH',       ARRAY['lucia@amano.demo','martin@amano.demo','carlos@sim.demo'], 'ONE_TIME', NULL),
    ('[Demo] Depto Palermo', NULL, 'lucia@amano.demo',   33000, 'ARS', NULL,  1, 'Limpieza profunda',      'SERVICES', 'TRANSFER',   ARRAY['lucia@amano.demo','martin@amano.demo','sofia@amano.demo','tomas@amano.demo'], 'ONE_TIME', NULL),
    -- serie recurrente del depto (las repeticiones se generan más abajo)
    ('[Demo] Depto Palermo', NULL, 'lucia@amano.demo',   28000, 'ARS', NULL, 75, 'Internet del depto',     'SERVICES', 'DEBIT_CARD', ARRAY['lucia@amano.demo','martin@amano.demo','sofia@amano.demo','tomas@amano.demo'], 'RECURRING', 'MONTHLY'),
    -- ---- Viaje a Río (USD)
    ('[Demo] Viaje a Río', NULL, 'lucia@amano.demo',    1800, 'USD', NULL, 50, 'Hotel',                    'HOUSING',  'CREDIT_CARD',ARRAY['lucia@amano.demo','valentina@amano.demo','joaquin@amano.demo','martin@amano.demo','tomas@amano.demo'], 'ONE_TIME', NULL),
    ('[Demo] Viaje a Río', NULL, 'valentina@amano.demo',540000, 'ARS', 0.00083000, 45, 'Pasajes aéreos',  'TRANSPORT','TRANSFER',   ARRAY['lucia@amano.demo','valentina@amano.demo','joaquin@amano.demo','martin@amano.demo','tomas@amano.demo'], 'ONE_TIME', NULL),
    ('[Demo] Viaje a Río', NULL, 'joaquin@amano.demo',    900, 'BRL', 0.18500000, 28, 'Cena en Ipanema',   'FOOD',     'CASH',       ARRAY['lucia@amano.demo','valentina@amano.demo','joaquin@amano.demo','martin@amano.demo'], 'ONE_TIME', NULL),
    ('[Demo] Viaje a Río', NULL, 'martin@amano.demo',     150, 'EUR', 1.08500000, 27, 'Excursión al Pan de Azúcar','LEISURE','CREDIT_CARD',ARRAY['lucia@amano.demo','valentina@amano.demo','joaquin@amano.demo','martin@amano.demo'], 'ONE_TIME', NULL),
    ('[Demo] Viaje a Río', NULL, 'lucia@amano.demo',    35000, 'ARS', 0.00079000, 12, 'Traslado al aeropuerto','TRANSPORT','CASH',     ARRAY['lucia@amano.demo','valentina@amano.demo','joaquin@amano.demo','martin@amano.demo'], 'ONE_TIME', NULL),
    -- ---- Cumple de Sofi (ARS)
    ('[Demo] Cumple de Sofi', NULL, 'lucia@amano.demo', 48000, 'ARS', NULL, 14, 'Regalo',                  'SHOPPING', 'CREDIT_CARD',ARRAY['lucia@amano.demo','sofia@amano.demo','valentina@amano.demo'], 'ONE_TIME', NULL),
    ('[Demo] Cumple de Sofi', NULL, 'valentina@amano.demo', 27000, 'ARS', NULL, 13, 'Torta y bebidas',     'FOOD',     'VIRTUAL_WALLET', ARRAY['lucia@amano.demo','sofia@amano.demo','valentina@amano.demo'], 'ONE_TIME', NULL),
    -- ---- 1:1 entre amigos (sin grupo)
    (NULL, 'martin@amano.demo',    'lucia@amano.demo',  24000, 'ARS', NULL,  8, 'Cena de los dos',        'FOOD',     'DEBIT_CARD', ARRAY['lucia@amano.demo','martin@amano.demo'], 'ONE_TIME', NULL),
    (NULL, 'lucia@amano.demo',     'valentina@amano.demo', 80, 'USD', NULL, 16, 'Entradas de teatro (USD)','LEISURE','CREDIT_CARD',ARRAY['valentina@amano.demo','lucia@amano.demo'], 'ONE_TIME', NULL);

-- repeticiones mensuales de la serie del depto (mismo mecanismo que el job)
INSERT INTO seed_ge (gname, payer_email, amount, currency, days_ago, descr, category, method, parts, etype, source_descr)
SELECT s.gname, s.payer_email, s.amount, s.currency,
       (t.d - (( (t.d - s.days_ago) + n * interval '1 month')::date)), s.descr, s.category, s.method, s.parts,
       'RECURRING', s.descr
FROM seed_ge s
CROSS JOIN demo_today t
CROSS JOIN generate_series(1, 12) AS n
WHERE s.freq = 'MONTHLY'
  AND ((t.d - s.days_ago) + n * interval '1 month')::date <= t.d;

CREATE TEMP TABLE seed_groups ON COMMIT DROP AS
SELECT g.id, g.name, g.primary_currency, g.debt_due_days FROM groups g WHERE g.name LIKE '[Demo] %';

-- moneda destino: la del grupo o, en 1:1, la del propio gasto
CREATE TEMP TABLE seed_calc ON COMMIT DROP AS
SELECT s.key,
       g.id AS group_id,
       fu.id AS friend_id,
       pu.id AS payer_id,
       COALESCE(g.primary_currency, s.currency) AS target_cur,
       g.debt_due_days,
       t.d - s.days_ago AS edate,
       CASE WHEN COALESCE(g.primary_currency, s.currency) = s.currency THEN 1 ELSE s.rate END AS eff_rate
FROM seed_ge s
LEFT JOIN seed_groups g ON g.name = s.gname
LEFT JOIN users fu ON fu.email = s.friend_email
JOIN users pu ON pu.email = s.payer_email
CROSS JOIN demo_today t;

-- Serie primero (para tener su id), luego repeticiones y gastos sueltos
INSERT INTO group_expenses (group_id, friend_user_id, created_by_user_id, payer_user_id, amount, currency,
        converted_amount, exchange_rate, rate_date, rate_source, expense_date, due_date, description,
        idempotency_key, split_method, category, expense_type, payment_method,
        recurrence_frequency, recurrence_status, recurrence_next_date, recurrence_anchor_date, created_at)
SELECT c.group_id, c.friend_id, c.payer_id, c.payer_id, s.amount, s.currency,
       round(s.amount * c.eff_rate, 2), c.eff_rate, c.edate,
       CASE WHEN c.eff_rate = 1 AND c.target_cur = s.currency THEN 'SAME_CURRENCY' ELSE 'BCRA' END,
       c.edate, c.edate + c.debt_due_days, s.descr, s.key, 'EQUAL', s.category, s.etype, s.method,
       s.freq, 'ACTIVE', (c.edate + interval '1 month')::date, c.edate, c.edate + time '12:00'
FROM seed_ge s JOIN seed_calc c ON c.key = s.key
WHERE s.freq IS NOT NULL;

INSERT INTO group_expenses (group_id, friend_user_id, created_by_user_id, payer_user_id, amount, currency,
        converted_amount, exchange_rate, rate_date, rate_source, expense_date, due_date, description,
        idempotency_key, split_method, category, expense_type, payment_method,
        recurrence_source_id, recurrence_created_via, created_at)
SELECT c.group_id, c.friend_id, c.payer_id, c.payer_id, s.amount, s.currency,
       round(s.amount * c.eff_rate, 2), c.eff_rate, c.edate,
       CASE WHEN c.eff_rate = 1 AND c.target_cur = s.currency THEN 'SAME_CURRENCY' ELSE 'BCRA' END,
       c.edate, CASE WHEN s.source_descr IS NOT NULL THEN c.edate ELSE c.edate + c.debt_due_days END,
       s.descr, s.key, 'EQUAL', s.category, s.etype, s.method,
       src.id, CASE WHEN s.source_descr IS NOT NULL THEN 'JOB' END, c.edate + time '12:00'
FROM seed_ge s
JOIN seed_calc c ON c.key = s.key
LEFT JOIN seed_ge ss ON ss.descr = s.source_descr AND ss.freq IS NOT NULL
LEFT JOIN group_expenses src ON src.idempotency_key = ss.key
WHERE s.freq IS NULL;

UPDATE seed_ge s SET ge_id = ge.id
FROM group_expenses ge WHERE ge.idempotency_key = s.key;

-- Participantes: el pagador va en la posición 0 y absorbe el resto del redondeo
CREATE TEMP TABLE seed_part ON COMMIT DROP AS
SELECT s.key, s.ge_id, c.group_id, c.target_cur, c.payer_id,
       (row_number() OVER (PARTITION BY s.key ORDER BY (p.email <> s.payer_email), p.ord) - 1)::int AS pos,
       count(*) OVER (PARTITION BY s.key) AS n,
       round(s.amount * c.eff_rate, 2) AS converted,
       p.email
FROM seed_ge s
JOIN seed_calc c ON c.key = s.key
CROSS JOIN LATERAL unnest(s.parts) WITH ORDINALITY AS p(email, ord);

INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                                  simulated_profile_id, participant_name, amount)
SELECT p.ge_id, p.pos,
       CASE WHEN u.id IS NOT NULL THEN 'REGISTERED' ELSE 'SIMULATED' END,
       u.id, sp.id,
       COALESCE(trim(u.first_name || ' ' || COALESCE(u.last_name, '')), sp.name),
       CASE WHEN p.pos = 0 THEN p.converted - round(p.converted / p.n, 2) * (p.n - 1)
            ELSE round(p.converted / p.n, 2) END
FROM seed_part p
LEFT JOIN users u ON u.email = p.email
LEFT JOIN simulated_profiles sp ON u.id IS NULL AND sp.email = p.email AND sp.group_id = p.group_id;

INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id,
                   debtor_simulated_profile_id, debtor_name, creditor_user_id, currency, amount,
                   created_at, updated_at)
SELECT p.group_id, p.ge_id, sh.id, sh.participant_type, sh.user_id, sh.simulated_profile_id,
       sh.participant_name, p.payer_id, p.target_cur, sh.amount,
       ge.created_at, ge.created_at
FROM seed_part p
JOIN group_expense_shares sh ON sh.expense_id = p.ge_id AND sh.position = p.pos
JOIN group_expenses ge ON ge.id = p.ge_id
WHERE p.pos > 0;

-- =========================================== estados de deuda y pagos / liquidaciones
-- plan: PAID | VERIFIED | REPORTED | PARTIAL | OVERDUE | REJECTED (resto queda PENDING)
CREATE TEMP TABLE seed_plan (descr text, debtor_email text, plan text) ON COMMIT DROP;
INSERT INTO seed_plan VALUES
    ('Alquiler (mes pasado)',        'martin@amano.demo',    'VERIFIED'),
    ('Alquiler (mes pasado)',        'sofia@amano.demo',     'PAID'),
    ('Alquiler (mes pasado)',        'tomas@amano.demo',     'OVERDUE'),
    ('Supermercado grande',          'lucia@amano.demo',     'VERIFIED'),
    ('Supermercado grande',          'sofia@amano.demo',     'REPORTED'),
    ('Supermercado grande',          'tomas@amano.demo',     'OVERDUE'),
    ('Luz y gas',                    'lucia@amano.demo',     'REPORTED'),
    ('Luz y gas',                    'martin@amano.demo',    'PARTIAL'),
    ('Muebles online',               'martin@amano.demo',    'REJECTED'),
    ('Hotel',                        'valentina@amano.demo', 'VERIFIED'),
    ('Hotel',                        'joaquin@amano.demo',   'PAID'),
    ('Hotel',                        'martin@amano.demo',    'REPORTED'),
    ('Pasajes aéreos',               'lucia@amano.demo',     'VERIFIED'),
    ('Pasajes aéreos',               'joaquin@amano.demo',   'PARTIAL'),
    ('Cena en Ipanema',              'martin@amano.demo',    'PAID'),
    ('Regalo',                       'valentina@amano.demo', 'REPORTED'),
    ('Torta y bebidas',              'sofia@amano.demo',     'VERIFIED');
-- (los gastos 1:1 entre amigos quedan PENDING: payments.group_id es NOT NULL)

CREATE TEMP TABLE seed_pay ON COMMIT DROP AS
SELECT d.id AS debt_id, d.group_id, d.debtor_user_id, d.debtor_name, d.creditor_user_id,
       d.currency, d.amount, pl.plan, ge.expense_date,
       trim(cu.first_name || ' ' || COALESCE(cu.last_name, '')) AS creditor_name,
       gen_random_uuid() AS pkey,
       LEAST(ge.expense_date + 3, (SELECT d FROM demo_today)) AS pay_date
FROM seed_plan pl
JOIN group_expenses ge ON ge.description = pl.descr AND ge.id IN (SELECT ge_id FROM seed_ge)
JOIN debts d ON d.expense_id = ge.id
JOIN users du ON du.id = d.debtor_user_id AND du.email = pl.debtor_email
JOIN users cu ON cu.id = d.creditor_user_id;

-- Pagos: confirmados (PAID/VERIFIED/PARTIAL legacy) y reportados/rechazados
INSERT INTO payments (group_id, debt_id, payer_type, payer_user_id, payer_name, receiver_user_id,
                      receiver_name, registered_by_user_id, amount, currency, payment_date,
                      idempotency_key, status, decided_by_user_id, decided_at, legacy, created_at)
SELECT p.group_id, p.debt_id, 'REGISTERED', p.debtor_user_id, p.debtor_name, p.creditor_user_id,
       p.creditor_name, p.debtor_user_id,
       CASE WHEN p.plan = 'PARTIAL' THEN round(p.amount / 2, 2) ELSE p.amount END,
       p.currency, p.pay_date, p.pkey,
       CASE p.plan WHEN 'REPORTED' THEN 'REPORTED' WHEN 'REJECTED' THEN 'REJECTED' ELSE 'CONFIRMED' END,
       CASE WHEN p.plan IN ('VERIFIED', 'PAID', 'PARTIAL', 'REJECTED') THEN p.creditor_user_id END,
       CASE WHEN p.plan IN ('VERIFIED', 'PAID', 'PARTIAL', 'REJECTED') THEN p.pay_date + time '18:00' END,
       (p.plan = 'PARTIAL'),
       p.pay_date + time '10:00'
FROM seed_pay p
WHERE p.plan <> 'OVERDUE';

UPDATE debts d SET
    status = CASE p.plan WHEN 'VERIFIED' THEN 'VERIFIED' WHEN 'PAID' THEN 'PAID'
                         WHEN 'PARTIAL' THEN 'PARTIALLY_PAID' WHEN 'REPORTED' THEN 'PAYMENT_REPORTED'
                         WHEN 'OVERDUE' THEN 'OVERDUE' ELSE 'PENDING' END,
    paid_amount = CASE p.plan WHEN 'VERIFIED' THEN d.amount WHEN 'PAID' THEN d.amount
                              WHEN 'PARTIAL' THEN round(d.amount / 2, 2) ELSE 0 END,
    updated_at = now()
FROM seed_pay p WHERE p.debt_id = d.id;

-- ================================================================== actividad
INSERT INTO group_activity (group_id, event_type, actor_user_id, details_json, created_at)
SELECT g.id, 'MEMBER_ADDED', u.id, NULL, gm.joined_at
FROM group_members gm JOIN groups g ON g.id = gm.group_id AND g.name LIKE '[Demo] %'
JOIN users u ON u.id = gm.user_id;

INSERT INTO group_activity (group_id, event_type, actor_user_id, details_json, created_at)
SELECT gm.group_id, 'MEMBER_LEFT', gm.user_id, NULL, gm.left_at
FROM group_members gm WHERE gm.left_at IS NOT NULL
  AND gm.group_id IN (SELECT id FROM seed_groups);

INSERT INTO group_activity (group_id, event_type, actor_user_id, details_json, created_at)
SELECT ge.group_id, 'EXPENSE_CREATED', ge.created_by_user_id, NULL, ge.created_at
FROM group_expenses ge WHERE ge.id IN (SELECT ge_id FROM seed_ge) AND ge.group_id IS NOT NULL
  AND ge.recurrence_source_id IS NULL;

INSERT INTO group_activity (group_id, event_type, actor_user_id, details_json, created_at)
SELECT p.group_id,
       CASE p.status WHEN 'CONFIRMED' THEN 'PAYMENT_CONFIRMED' WHEN 'REJECTED' THEN 'PAYMENT_REJECTED'
                     ELSE 'PAYMENT_REGISTERED' END,
       COALESCE(p.decided_by_user_id, p.registered_by_user_id), NULL, COALESCE(p.decided_at, p.created_at)
FROM payments p WHERE p.group_id IN (SELECT id FROM seed_groups);

-- Propuestas de liquidación pendientes de respuesta
INSERT INTO group_activity (group_id, event_type, actor_user_id, details_json, created_at)
SELECT p.group_id, 'LIQUIDATION_PROPOSED', p.registered_by_user_id, NULL, p.created_at
FROM payments p WHERE p.status = 'REPORTED' AND p.group_id IN (SELECT id FROM seed_groups);

INSERT INTO group_activity (group_id, event_type, actor_user_id, details_json, created_at)
SELECT d.group_id, 'DEBT_OVERDUE', d.creditor_user_id, NULL, now() - interval '2 days'
FROM debts d WHERE d.status = 'OVERDUE' AND d.group_id IN (SELECT id FROM seed_groups);

-- ======================================== notificaciones y preferencias (035)
-- Datos para ver el popover (badge "9+"), la bandeja, los filtros, los deep links
-- y el modal de preferencias sin tener que generar eventos a mano.
INSERT INTO user_notification_preferences
        (user_id, in_app, email, reminders_whistles, debt_due_collections,
         group_activity, friend_activity, updated_at)
SELECT u.id,
       true,
       CASE WHEN u.email = 'lucia@amano.demo' THEN false ELSE true END,
       true,
       true,
       CASE WHEN u.email = 'martin@amano.demo' THEN false ELSE true END,
       true,
       now() - interval '1 day'
FROM users u
WHERE u.email LIKE '%@amano.demo' AND u.email_verified_at IS NOT NULL;

CREATE TEMP TABLE notify_ctx ON COMMIT DROP AS
SELECT
    (SELECT id FROM groups WHERE name = '[Demo] Depto Palermo') AS depto_id,
    (SELECT id FROM groups WHERE name = '[Demo] Viaje a Río') AS viaje_id,
    (SELECT id FROM debts
      WHERE group_id = (SELECT id FROM groups WHERE name = '[Demo] Depto Palermo')
        AND status = 'OVERDUE' ORDER BY id LIMIT 1) AS overdue_debt_id,
    (SELECT id FROM debts
      WHERE group_id = (SELECT id FROM groups WHERE name = '[Demo] Depto Palermo')
      ORDER BY id LIMIT 1) AS depto_debt_id,
    (SELECT id FROM group_expenses
      WHERE group_id = (SELECT id FROM groups WHERE name = '[Demo] Depto Palermo')
      ORDER BY id LIMIT 1) AS depto_expense_id,
    (SELECT id FROM friendship_requests
      WHERE status = 'PENDING'
        AND recipient_id = (SELECT id FROM users WHERE email = 'lucia@amano.demo')
      ORDER BY id LIMIT 1) AS lucia_request_id,
    (SELECT id FROM friendship_requests WHERE status = 'PENDING' ORDER BY id LIMIT 1)
        AS pending_request_id;

INSERT INTO notifications (user_id, type, title, message, target_kind,
                           group_id, debt_id, expense_id, friend_request_id,
                           created_at, read_at)
SELECT u.id, n.type, n.title, n.message, n.target_kind,
       CASE n.target_kind WHEN 'GROUP' THEN c.depto_id WHEN 'DEBT' THEN c.depto_id END,
       CASE n.target_kind WHEN 'DEBT' THEN COALESCE(c.overdue_debt_id, c.depto_debt_id) END,
       CASE n.target_kind WHEN 'GROUP' THEN c.depto_expense_id END,
       CASE n.target_kind WHEN 'FRIEND_REQUEST' THEN COALESCE(c.lucia_request_id, c.pending_request_id) END,
       now() - n.minutes_ago * interval '1 minute',
       CASE WHEN n.unread THEN NULL ELSE now() - n.minutes_ago * interval '1 minute' END
FROM (VALUES
    -- Lucía: 11 sin leer (el badge debe mostrar "9+") y una leída al final.
    ('lucia@amano.demo', 'GROUP_EXPENSE',    'Nuevo gasto en el depto',        'Martín registró un gasto en el grupo.',            'GROUP',          2, true),
    ('lucia@amano.demo', 'REMINDER',         'Recordatorio de cobro',          'Tenés un cobro pendiente en el grupo.',            'GROUP',         15, true),
    ('lucia@amano.demo', 'DEBT_WHISTLE',     'Silbato de cobro',               'Un compañero te silbó una deuda.',                 'DEBT',          40, true),
    ('lucia@amano.demo', 'DEBT_OVERDUE',     'Deuda vencida',                  'Hay una deuda vencida en el grupo.',               'DEBT',          90, true),
    ('lucia@amano.demo', 'PAYER_DUE_NOTICE', 'Aviso para pagador',             'Se acerca el vencimiento de tu pago.',             'DEBT',         180, true),
    ('lucia@amano.demo', 'FRIEND_REQUEST',   'Solicitud de amistad',           'Tomás quiere ser tu amigo.',                      'FRIEND_REQUEST', 300, true),
    ('lucia@amano.demo', 'FRIEND_ACTIVITY',  'Actividad social',               'Martín aceptó una solicitud de amistad.',         'FRIEND_REQUEST', 600, true),
    ('lucia@amano.demo', 'PAYMENT_RECORDED', 'Pago registrado',                'Se registró un pago que te involucra.',            'DEBT',         1200, true),
    ('lucia@amano.demo', 'GROUP_EXPENSE',    'Limpieza profunda',              'Se agregó un gasto al grupo.',                    'GROUP',        1500, true),
    ('lucia@amano.demo', 'GROUP_EXPENSE',    'Aviso sin destino',              'Demo: el grupo ya no existe (muestra snackbar).',   'NONE',         1800, true),
    ('lucia@amano.demo', 'REMINDER',         'Recordatorio semanal',           'Revisá tus deudas del grupo.',                    'GROUP',        2200, true),
    ('lucia@amano.demo', 'GROUP_EXPENSE',    'Pizza y cine',                   'Gasto del grupo ya revisado.',                    'GROUP',        3000, false),
    -- Martín: actividad de grupo desactivada en sus preferencias.
    ('martin@amano.demo', 'DEBT_OVERDUE',    'Deuda vencida',                  'Tenés una deuda vencida en el depto.',            'DEBT',          20, true),
    ('martin@amano.demo', 'FRIEND_REQUEST',  'Solicitud pendiente',            'Tenés una solicitud de amistad para responder.',  'FRIEND_REQUEST', 240, true),
    ('martin@amano.demo', 'GROUP_EXPENSE',   'Supermercado grande',            'Gasto registrado por Sofía.',                    'GROUP',         900, false),
    -- Sofía, Valentina, Joaquín y Tomás: el resto de los tipos y estados.
    ('sofia@amano.demo',  'FRIEND_REQUEST',  'Solicitud de amistad',           'Valentina quiere ser tu amiga.',                  'FRIEND_REQUEST',  60, true),
    ('sofia@amano.demo',  'PAYMENT_RECORDED','Pago confirmado',                'Confirmaron un pago en el grupo.',                'DEBT',         480, false),
    ('valentina@amano.demo','GROUP_EXPENSE', 'Nuevo gasto del viaje',          'Lucía cargó el hotel del viaje.',                 'GROUP',         30, true),
    ('valentina@amano.demo','DEBT_OVERDUE',  'Cobro pendiente',                'Hay un cobro pendiente en el viaje.',             'DEBT',         720, true),
    ('joaquin@amano.demo','PAYER_DUE_NOTICE','Aviso de pago',                  'Tu pago vence en pocos días.',                    'DEBT',         120, false),
    ('tomas@amano.demo',  'DEBT_WHISTLE',    'Silbato recibido',               'Te silbaron una deuda en el depto.',              'DEBT',          10, true)
) AS n(user_email, type, title, message, target_kind, minutes_ago, unread)
JOIN users u ON u.email = n.user_email
CROSS JOIN notify_ctx c;

COMMIT;

\set QUIET off
\echo
\echo 'Resumen de datos demo:'
SELECT u.email,
       (SELECT count(*) FROM expenses e WHERE e.user_id = u.id)                         AS gastos,
       (SELECT count(*) FROM incomes i WHERE i.user_id = u.id AND i.income_date >  (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date) AS ing_proyectados,
       (SELECT count(*) FROM incomes i WHERE i.user_id = u.id AND i.income_date <= (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date) AS ing_efectivizados,
       (SELECT count(*) FROM group_members gm WHERE gm.user_id = u.id AND gm.left_at IS NULL) AS grupos
FROM users u WHERE u.email LIKE '%@amano.demo' ORDER BY u.created_at;

SELECT g.name AS grupo, g.primary_currency AS moneda,
       (SELECT count(*) FROM group_expenses e WHERE e.group_id = g.id) AS gastos,
       (SELECT count(*) FROM debts d WHERE d.group_id = g.id)          AS deudas,
       (SELECT count(*) FROM payments p WHERE p.group_id = g.id)       AS pagos
FROM groups g WHERE g.name LIKE '[Demo] %' ORDER BY g.id;

\echo
\echo 'Notificaciones demo (035):'
SELECT u.email,
       (SELECT count(*) FROM notifications n WHERE n.user_id = u.id) AS notificaciones,
       (SELECT count(*) FROM notifications n WHERE n.user_id = u.id AND n.read_at IS NULL) AS sin_leer,
       (SELECT p.email FROM user_notification_preferences p WHERE p.user_id = u.id) AS correo_activo,
       (SELECT p.group_activity FROM user_notification_preferences p WHERE p.user_id = u.id) AS actividad_grupo
FROM users u WHERE u.email LIKE '%@amano.demo' ORDER BY u.email;
