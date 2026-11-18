package com.amano.backend.friendship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;

/**
 * Contract and integration tests of the friendship deletion (feature 016,
 * contracts/delete-friend-api.md) against a real PostgreSQL. Each test creates
 * its own accounts, friendships and optional group/expense rows, so the shared
 * database never couples them.
 */
class DeleteFriendIT extends AbstractIntegrationIT {

    private static final String FRIENDS_URL = "/api/users/me/friends";
    private static final String REQUESTS_URL = "/api/users/me/friendship-requests";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private long account(String firstName, boolean verified) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, cbu,
                                   terms_accepted_at, email_verified_at)
                VALUES (?, 'Prueba', ?, 'hash', '0000003100010000000001', now(),
                        CASE WHEN ? THEN now() END)
                RETURNING id
                """, Long.class, firstName,
                "delete." + UUID.randomUUID() + "@example.com", verified);
    }

    private long verified(String firstName) {
        return account(firstName, true);
    }

    private void request(long sender, long recipient, String status, String respondedAt) {
        jdbcTemplate.update("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                VALUES (?, ?, ?, CAST(? AS TIMESTAMPTZ))
                """, sender, recipient, status, respondedAt);
    }

    private void friends(long sender, long recipient) {
        request(sender, recipient, "ACCEPTED", "2026-10-31T14:05:00Z");
    }

    private Cookie cookie(long userId) {
        return new Cookie("amano_auth", jwtService.issueToken(userId));
    }

    private ResultActions deleteAs(long userId, String friendId) throws Exception {
        return mockMvc.perform(delete(FRIENDS_URL + "/" + friendId)
                .cookie(cookie(userId)));
    }

    private ResultActions eligibilityAs(long userId, String friendId) throws Exception {
        return mockMvc.perform(get(FRIENDS_URL + "/" + friendId + "/deletion-eligibility")
                .cookie(cookie(userId)));
    }

    private ResultActions listAs(long userId) throws Exception {
        return mockMvc.perform(get(FRIENDS_URL).cookie(cookie(userId)));
    }

    private int acceptedRows(long a, long b) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM friendship_requests
                WHERE status = 'ACCEPTED'
                  AND ((sender_id = ? AND recipient_id = ?)
                    OR (sender_id = ? AND recipient_id = ?))
                """, Integer.class, a, b, b, a);
    }

    private int rowsWithStatus(long a, long b, String status) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM friendship_requests
                WHERE status = ?
                  AND ((sender_id = ? AND recipient_id = ?)
                    OR (sender_id = ? AND recipient_id = ?))
                """, Integer.class, status, a, b, b, a);
    }

    // ---- US1 ----

    @Test
    void deletesTheFriendshipForBothUsers() throws Exception {
        long me = verified("Elimina");
        long friend = verified("Eliminado");
        friends(me, friend);

        deleteAs(me, String.valueOf(friend))
                .andExpect(status().isNoContent());

        assertThat(acceptedRows(me, friend)).isZero();
        listAs(me).andExpect(jsonPath("$", hasSize(0)));
        listAs(friend).andExpect(jsonPath("$", hasSize(0)));
        eligibilityAs(friend, String.valueOf(me))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletionCanHappenWhenTheOtherUserSentTheRequest() throws Exception {
        long me = verified("Receptora");
        long friend = verified("Emisor");
        friends(friend, me);

        deleteAs(me, String.valueOf(friend))
                .andExpect(status().isNoContent());

        assertThat(acceptedRows(me, friend)).isZero();
    }

    @Test
    void onlyTheAcceptedRowIsDeleted() throws Exception {
        long me = verified("Conserva");
        long friend = verified("Historial");
        long pending = verified("Pendiente");
        long rejected = verified("Rechazada");
        friends(me, friend);
        request(me, pending, "PENDING", null);
        request(rejected, me, "REJECTED", "2026-10-30T10:00:00Z");

        deleteAs(me, String.valueOf(friend))
                .andExpect(status().isNoContent());

        assertThat(rowsWithStatus(me, friend, "ACCEPTED")).isZero();
        assertThat(rowsWithStatus(me, pending, "PENDING")).isEqualTo(1);
        assertThat(rowsWithStatus(me, rejected, "REJECTED")).isEqualTo(1);
    }

    @Test
    void canSendANewRequestAfterDeleting() throws Exception {
        long me = verified("Reintenta");
        long friend = verified("Reaceptado");
        friends(me, friend);
        deleteAs(me, String.valueOf(friend)).andExpect(status().isNoContent());

        mockMvc.perform(post(REQUESTS_URL)
                .cookie(cookie(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientId\":" + friend + "}"))
                .andExpect(status().isCreated());
    }

    // ---- US2 ----

    @Test
    void eligibilityIsPositiveWithoutDirectDebt() throws Exception {
        long me = verified("Elegible");
        long friend = verified("Amiga");
        friends(me, friend);

        eligibilityAs(me, String.valueOf(friend))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void withoutDirectDebtsDeletionProceeds() throws Exception {
        long me = verified("SinDeuda");
        long friend = verified("AmigaDos");
        friends(me, friend);

        // 027: no expense with a friend between them, so there is no direct debt.
        deleteAs(me, String.valueOf(friend))
                .andExpect(status().isNoContent());
    }

    // ---- US3 ----

    @Test
    void deletingANonFriendIsANeutral404() throws Exception {
        long me = verified("NoAmiga");
        long stranger = verified("Desconocida");

        deleteAs(me, String.valueOf(stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(FriendNotFoundException.MESSAGE));

        eligibilityAs(me, String.valueOf(stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(FriendNotFoundException.MESSAGE));
    }

    @Test
    void aSecondDeletionIsANeutral404() throws Exception {
        long me = verified("Doble");
        long friend = verified("Baja");
        friends(me, friend);

        deleteAs(me, String.valueOf(friend)).andExpect(status().isNoContent());
        deleteAs(me, String.valueOf(friend))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(FriendNotFoundException.MESSAGE));
    }

    @Test
    void aNonNumericFriendIdIsA400WithoutTouchingData() throws Exception {
        long me = verified("Invalido");

        deleteAs(me, "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.friendId")
                        .value(FriendService.FRIEND_INVALID_MESSAGE));

        eligibilityAs(me, "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.friendId")
                        .value(FriendService.FRIEND_INVALID_MESSAGE));
    }

    @Test
    void withoutSessionOrWithUnverifiedAccountIsA401() throws Exception {
        long unverified = account("SinVerificar", false);

        mockMvc.perform(delete(FRIENDS_URL + "/1"))
                .andExpect(status().isUnauthorized());
        deleteAs(unverified, "1")
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(FRIENDS_URL + "/1/deletion-eligibility"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keepsPreviousGroupsAndExpensesIntact() throws Exception {
        long me = verified("Grupo");
        long friend = verified("Miembro");
        friends(me, friend);

        long groupId = jdbcTemplate.queryForObject(
                "INSERT INTO groups (name) VALUES ('Viaje') RETURNING id", Long.class);
        jdbcTemplate.update("""
                INSERT INTO group_members (user_id, group_id, role) VALUES (?, ?, 'ADMIN')
                """, me, groupId);
        jdbcTemplate.update("""
                INSERT INTO group_members (user_id, group_id, role) VALUES (?, ?, 'MEMBER')
                """, friend, groupId);
        jdbcTemplate.update("""
                INSERT INTO expenses (user_id, amount, currency, expense_date, category,
                                      expense_type, payment_method, idempotency_key)
                VALUES (?, 1500.00, 'ARS', CURRENT_DATE, 'FOOD', 'ONE_TIME', 'CASH', ?)
                """, me, UUID.randomUUID());

        deleteAs(me, String.valueOf(friend))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_members WHERE group_id = ?", Integer.class, groupId))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE user_id = ?", Integer.class, me))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM groups WHERE id = ?", Integer.class, groupId))
                .isEqualTo(1);
    }
}
