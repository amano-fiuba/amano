-- V16 (spec 021): se elimina la casilla "No podrán editar ajustes del grupo".
-- La reemplazó members_can_edit_group_data (V15) y ya no tenía efecto.
ALTER TABLE group_permissions
    DROP COLUMN members_cannot_edit_settings;
