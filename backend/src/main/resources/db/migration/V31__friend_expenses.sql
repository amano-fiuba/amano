-- V31__friend_expenses.sql
-- Feature 027, US4 (gasto con amigo), ver specs/027-expense-entry-modes/data-model.md.
--
-- Un gasto con amigo reutiliza group_expenses, sus partes y debts sin grupo:
-- group_id queda NULL y friend_user_id guarda al otro participante. Exactamente
-- uno de los dos tiene valor. Las deudas de esos gastos (group_id NULL) son las
-- "deudas directas" que bloquean eliminar la amistad (016). Todas las consultas
-- de grupo filtran por group_id, así que no ven estas filas.

ALTER TABLE group_expenses ALTER COLUMN group_id DROP NOT NULL;
ALTER TABLE group_expenses ADD COLUMN friend_user_id BIGINT NULL REFERENCES users (id);
ALTER TABLE group_expenses ADD CONSTRAINT ck_group_expenses_context
    CHECK ((group_id IS NULL) <> (friend_user_id IS NULL));
CREATE INDEX ix_group_expenses_friend_pair ON group_expenses (friend_user_id, created_by_user_id)
    WHERE group_id IS NULL;

ALTER TABLE debts ALTER COLUMN group_id DROP NOT NULL;
CREATE INDEX ix_debts_direct_pair ON debts (debtor_user_id, creditor_user_id) WHERE group_id IS NULL;
