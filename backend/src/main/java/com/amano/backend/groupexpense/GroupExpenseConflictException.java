package com.amano.backend.groupexpense;

public class GroupExpenseConflictException extends RuntimeException {
    private final String code;

    public GroupExpenseConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
