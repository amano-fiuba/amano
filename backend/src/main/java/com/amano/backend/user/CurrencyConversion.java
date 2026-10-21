package com.amano.backend.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter between {@link Currency} and its 3-letter ISO code stored in
 * {@code user_currencies.currency_code} (task T009, data-model.md).
 */
@Converter
public class CurrencyConversion implements AttributeConverter<Currency, String> {

    @Override
    public String convertToDatabaseColumn(Currency attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public Currency convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Currency.valueOf(dbData);
    }
}
