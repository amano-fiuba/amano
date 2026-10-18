package com.amano.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/**
 * Accessor coverage for the persistence entities (they carry no business
 * logic; the getter/setter wiring is verified here directly).
 */
class UserEntitiesTest {

    @Test
    void userAccessorsRoundTripEveryField() {
        User user = new User();
        OffsetDateTime terms = OffsetDateTime.now();
        OffsetDateTime created = OffsetDateTime.now();

        user.setId(7L);
        user.setFirstName("Ana");
        user.setLastName("Pérez");
        user.setEmail("ana.perez@example.com");
        user.setPasswordHash("$2a$hash");
        user.setCbu("2850591001234567890123");
        user.setTermsAcceptedAt(terms);

        assertThat(user.getId()).isEqualTo(7L);
        assertThat(user.getFirstName()).isEqualTo("Ana");
        assertThat(user.getLastName()).isEqualTo("Pérez");
        assertThat(user.getEmail()).isEqualTo("ana.perez@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(user.getCbu()).isEqualTo("2850591001234567890123");
        assertThat(user.getTermsAcceptedAt()).isEqualTo(terms);
        assertThat(user.getCreatedAt()).isNull();     // set by @CreationTimestamp at insert
        assertThat(user.getUpdatedAt()).isNull();     // set by @UpdateTimestamp at update
        assertThat(user.getAvatar()).isNull();        // resolved by the owner of user_avatars
        assertThat(user.getCurrencies()).isNotNull(); // allowed subset of ARS/USD/EUR

        user.getCurrencies().add(Currency.EUR);
        assertThat(user.getCurrencies()).containsExactly(Currency.EUR);
    }

    @Test
    void currencyEnumCarriesExactlyTheAllowedCodes() {
        assertThat(Currency.values()).containsExactly(Currency.ARS, Currency.USD, Currency.EUR);
    }

    @Test
    void currencyConversionMapsBothWays() {
        CurrencyConversion conversion = new CurrencyConversion();

        assertThat(conversion.convertToDatabaseColumn(null)).isNull();
        assertThat(conversion.convertToEntityAttribute(null)).isNull();
        assertThat(conversion.convertToDatabaseColumn(Currency.USD)).isEqualTo("USD");
        assertThat(conversion.convertToEntityAttribute("EUR")).isEqualTo(Currency.EUR);
    }

    @Test
    void userAvatarAccessorsRoundTripEveryField() {
        User owner = new User();
        owner.setId(7L);

        UserAvatar avatar = new UserAvatar();
        avatar.setId(7L);
        avatar.setUser(owner);
        avatar.setContentType("image/png");
        avatar.setData(new byte[] { 1, 2, 3 });

        assertThat(avatar.getId()).isEqualTo(7L);
        assertThat(avatar.getUser()).isSameAs(owner);
        assertThat(avatar.getContentType()).isEqualTo("image/png");
        assertThat(avatar.getData()).containsExactly(1, 2, 3);
        assertThat(avatar.getCreatedAt()).isNull(); // set by @CreationTimestamp at insert
    }
}
