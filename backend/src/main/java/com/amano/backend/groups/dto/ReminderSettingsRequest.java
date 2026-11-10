package com.amano.backend.groups.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

/**
 * Payload of {@code PUT /api/groups/{id}/reminder-settings} (025
 * contracts/reminder-settings-api.md). Partial update: an absent field keeps
 * its previous value, while an explicit {@code null} message clears it. The
 * presence flags let the service tell "absent" apart from "null".
 */
public class ReminderSettingsRequest {

    private Integer sendDays;

    private String message;

    private boolean sendDaysPresent;

    private boolean messagePresent;

    public ReminderSettingsRequest() {
    }

    @JsonSetter("sendDays")
    public void setSendDays(Integer sendDays) {
        this.sendDays = sendDays;
        this.sendDaysPresent = true;
    }

    @JsonSetter("message")
    public void setMessage(String message) {
        this.message = message;
        this.messagePresent = true;
    }

    public Integer getSendDays() {
        return sendDays;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSendDaysPresent() {
        return sendDaysPresent;
    }

    public boolean isMessagePresent() {
        return messagePresent;
    }
}
