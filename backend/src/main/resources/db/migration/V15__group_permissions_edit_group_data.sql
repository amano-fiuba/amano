-- V15: casilla positiva para que los miembros editen los datos del grupo
-- (foto, nombre y descripción). Default FALSE: solo administradores.
ALTER TABLE group_permissions
    ADD COLUMN members_can_edit_group_data BOOLEAN NOT NULL DEFAULT FALSE;
