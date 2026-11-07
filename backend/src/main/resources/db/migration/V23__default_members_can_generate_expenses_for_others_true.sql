-- V23__default_members_can_generate_expenses_for_others_true.sql
-- 022 T060 (plan D11): nuevos grupos arrancan con members_can_generate_expenses_for_others = TRUE;
-- filas existentes no se modifican.

ALTER TABLE group_permissions
    ALTER COLUMN members_can_generate_expenses_for_others SET DEFAULT TRUE;
