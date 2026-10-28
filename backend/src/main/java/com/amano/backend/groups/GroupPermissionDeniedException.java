package com.amano.backend.groups;

/**
 * The caller's role + casillas (decided by {@link GroupPermissionChecker}) do
 * not allow the requested group action (US2/US3). Mapped to 403 with a clear
 * reason (spec: se informa el motivo con un mensaje claro en la UI).
 */
public class GroupPermissionDeniedException extends RuntimeException {

    public GroupPermissionDeniedException(String message) {
        super(message);
    }
}
