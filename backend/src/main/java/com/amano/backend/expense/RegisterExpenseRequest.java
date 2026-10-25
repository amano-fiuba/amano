package com.amano.backend.expense;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Input payload of {@code POST /api/expenses} (contracts/expenses-api.md). Kept as
 * a class (not a record) so the unknown-property hook can capture every property
 * the request does not declare; feature US3 uses that map to reject reserved
 * properties (FR-005/FR-006). All values stay as received: normalization and
 * validation happen in {@link ExpenseService}, never here.
 */
public class RegisterExpenseRequest {

    private String amount;
    private String currency;
    private String date;
    private String category;
    private String type;
    /** Feature 009 (FR-001/FR-002): required for RECURRING, forbidden for ONE_TIME. */
    private String frequency;
    private String paymentMethod;
    private String description;
    private String deviceTimeZone;

    /** Every undeclared property, captured by {@link #putUnknownProperty}. */
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
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

    public Map<String, Object> getUnknownProperties() {
        return unknownProperties;
    }

    @JsonAnySetter
    public void putUnknownProperty(String name, Object value) {
        unknownProperties.put(name, value);
    }
}
