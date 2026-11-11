package com.amano.backend.payment;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Input payload of {@code POST /api/payments} (018 plan.md §Contrato REST).
 * Payer, receiver, group and currency are NOT part of it: they come from the
 * debt (plan.md D3). Kept as a class, like the 007/017 requests, so every
 * undeclared property is captured and rejected on its own field.
 */
public class RegisterPaymentRequest {

    private Long debtId;
    private String amount;
    private String date;
    private String deviceTimeZone;

    private final Map<String, Object> unknownProperties = new LinkedHashMap<>();

    public Long getDebtId() {
        return debtId;
    }

    public void setDebtId(Long debtId) {
        this.debtId = debtId;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDeviceTimeZone() {
        return deviceTimeZone;
    }

    public void setDeviceTimeZone(String deviceTimeZone) {
        this.deviceTimeZone = deviceTimeZone;
    }

    @JsonAnySetter
    public void putUnknownProperty(String name, Object value) {
        unknownProperties.put(name, value);
    }

    public Map<String, Object> getUnknownProperties() {
        return unknownProperties;
    }
}
