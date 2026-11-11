package com.amano.backend.payment;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Input payload of {@code POST /api/groups/{groupId}/debts/{debtId}/payments}
 * (022 contract {@code payment-validation.md}). The debt comes from the path,
 * and payer, receiver, group and currency always come from the debt. The
 * contract carries the idempotency key in the body; the 018 endpoint keeps
 * using the {@code Idempotency-Key} header.
 */
public class RegisterDebtPaymentRequest {

    private String amount;
    private String idempotencyKey;

    private final Map<String, Object> unknownProperties = new LinkedHashMap<>();

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @JsonAnySetter
    public void putUnknownProperty(String name, Object value) {
        unknownProperties.put(name, value);
    }

    public Map<String, Object> getUnknownProperties() {
        return unknownProperties;
    }
}
