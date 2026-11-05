package com.amano.backend.groupexpense;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Input payload of {@code POST /api/groups/{id}/expenses}
 * (contracts/group-expenses-api.md). Kept as a class, like the 007
 * {@code RegisterExpenseRequest}, so every undeclared property is captured and
 * rejected on its own field. Values stay as received: normalization and
 * validation happen in {@link GroupExpenseService}.
 */
public class CreateGroupExpenseRequest {

    /** One participant: {@code REGISTERED} with {@code userId} or {@code SIMULATED} with {@code simulatedProfileId}. */
    public record Participant(String type, Long userId, Long simulatedProfileId) {
    }

    private String amount;
    private String currency;
    private String date;
    private String description;
    private String deviceTimeZone;
    private Long payerUserId;
    private List<Participant> participants;

    private final Map<String, Object> unknownProperties = new LinkedHashMap<>();

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDeviceTimeZone() {
        return deviceTimeZone;
    }

    public void setDeviceTimeZone(String deviceTimeZone) {
        this.deviceTimeZone = deviceTimeZone;
    }

    public Long getPayerUserId() {
        return payerUserId;
    }

    public void setPayerUserId(Long payerUserId) {
        this.payerUserId = payerUserId;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }

    @JsonAnySetter
    public void putUnknownProperty(String name, Object value) {
        unknownProperties.put(name, value);
    }

    public Map<String, Object> getUnknownProperties() {
        return unknownProperties;
    }
}
