package com.amano.backend.groups.dto;

import java.util.List;

/** Response of the reminder settings endpoints (025 contracts/reminder-settings-api.md). */
public class ReminderSettingsResponse {

    private final int sendDays;
    private final String message;
    private final int defaultSendDays;
    private final List<Integer> allowedSendDays;

    public ReminderSettingsResponse(int sendDays, String message, int defaultSendDays,
            List<Integer> allowedSendDays) {
        this.sendDays = sendDays;
        this.message = message;
        this.defaultSendDays = defaultSendDays;
        this.allowedSendDays = allowedSendDays;
    }

    public int getSendDays() {
        return sendDays;
    }

    public String getMessage() {
        return message;
    }

    public int getDefaultSendDays() {
        return defaultSendDays;
    }

    public List<Integer> getAllowedSendDays() {
        return allowedSendDays;
    }
}
