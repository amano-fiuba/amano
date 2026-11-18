package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;

/**
 * Incomes are immutable (specs/033-income-management, FR-006, US3): no endpoint
 * edits or deletes them.
 */
class IncomeImmutabilityIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private long newUser() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES ('inmutable', ?, 'x', now(), now()) RETURNING id
                """, Long.class, "inmutable-" + System.nanoTime() + "@amano.dev");
    }

    private long newIncome(long userId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO incomes (user_id, amount, currency, income_date, category,
                                     payment_method, idempotency_key)
                VALUES (?, 100.00, 'ARS', ?, 'SALARY', 'TRANSFER', ?) RETURNING id
                """, Long.class, userId, LocalDate.now().minusDays(1), UUID.randomUUID());
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, Cookie session) {
        return builder.cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"1.00\"}");
    }

    @Test
    void patchPutAndDeleteAreRejectedWith405AndTheIncomeStaysIntact() throws Exception {
        long userId = newUser();
        long id = newIncome(userId);
        Cookie session = new Cookie("amano_auth", jwtService.issueToken(userId));
        Map<String, Object> before = jdbcTemplate.queryForMap("SELECT * FROM incomes WHERE id = ?", id);

        for (String path : new String[] {"/api/incomes/" + id, "/api/incomes"}) {
            mockMvc.perform(withBody(patch(path), session)).andExpect(status().isMethodNotAllowed());
            mockMvc.perform(withBody(put(path), session)).andExpect(status().isMethodNotAllowed());
            mockMvc.perform(delete(path).cookie(session)).andExpect(status().isMethodNotAllowed());
        }

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM incomes WHERE id = ?", id)).isEqualTo(before);
    }

    @Test
    void theControllerDeclaresNoModificationMappings() {
        assertThat(IncomeController.class.getAnnotation(RequestMapping.class)).isNull();
        for (Method method : IncomeController.class.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(PatchMapping.class)).as(method.getName()).isFalse();
            assertThat(method.isAnnotationPresent(PutMapping.class)).as(method.getName()).isFalse();
            assertThat(method.isAnnotationPresent(DeleteMapping.class)).as(method.getName()).isFalse();
        }
        assertThat(Arrays.stream(IncomeController.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(RequestMapping.class))
                .filter(java.util.Objects::nonNull)).isEmpty();
    }
}
