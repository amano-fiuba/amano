package com.amano.backend.friendship;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;

/**
 * Contract tests of GET /api/users/me/friends (feature 013,
 * contracts/friends-api.md) against a real PostgreSQL. Each test creates its own
 * accounts and friendships, so the shared database never couples them.
 */
class FriendListIT extends AbstractIntegrationIT {

    private static final String FRIENDS_URL = "/api/users/me/friends";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private long account(String firstName, String lastName, boolean verified) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, cbu,
                                   terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'hash', '0000003100010000000001', now(),
                        CASE WHEN ? THEN now() END)
                RETURNING id
                """, Long.class, firstName, lastName,
                "friend." + UUID.randomUUID() + "@example.com", verified);
    }

    private long verified(String firstName, String lastName) {
        return account(firstName, lastName, true);
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

    private ResultActions listAs(long userId) throws Exception {
        return mockMvc.perform(get(FRIENDS_URL)
                .cookie(new Cookie("amano_auth", jwtService.issueToken(userId))));
    }

    // ---- US1 ----

    @Test
    void listsTheOtherAccountOfFriendshipsInBothDirections() throws Exception {
        long me = verified("Centro", "Uno");
        long iSent = verified("Enviada", "Uno");
        long theySent = verified("Recibida", "Uno");
        friends(me, iSent);
        friends(theySent, me);

        listAs(me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(iSent))
                .andExpect(jsonPath("$[0].firstName").value("Enviada"))
                .andExpect(jsonPath("$[1].id").value(theySent))
                .andExpect(jsonPath("$[1].firstName").value("Recibida"));

        // The same friendship seen from the other side lists "me".
        listAs(iSent)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(me));
    }

    @Test
    void exposesOnlyIdNamesEmailTheAcceptanceDateAndTheCbu() throws Exception {
        long me = verified("Privacidad", "Dos");
        long friend = verified("Amiga", "Dos");
        friends(me, friend);

        listAs(me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(friend))
                .andExpect(jsonPath("$[0].firstName").value("Amiga"))
                .andExpect(jsonPath("$[0].lastName").value("Dos"))
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].friendsSince").exists())
                .andExpect(jsonPath("$[0].cbu").doesNotExist())
                // Friends can copy it to transfer (null when not informed).
                .andExpect(jsonPath("$[0].cbuCvu").value("0000003100010000000001"))
                .andExpect(jsonPath("$[0].currencies").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].emailVerifiedAt").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist());
    }

    @Test
    void ordersByFirstNameThenLastNameIgnoringCaseWithMissingLastNamesLast() throws Exception {
        long me = verified("Orden", "Tres");
        long sinApellido = verified("mismo", null);
        long benitez = verified("Mismo", "benitez");
        long alvarez = verified("MISMO", "Alvarez");
        long zeta = verified("Zeta", "Tres");
        long alfa = verified("alfa", "Tres");
        for (long friend : new long[] {zeta, sinApellido, benitez, alfa, alvarez}) {
            friends(friend, me);
        }

        listAs(me)
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].id").value(alfa))
                .andExpect(jsonPath("$[1].id").value(alvarez))
                .andExpect(jsonPath("$[2].id").value(benitez))
                .andExpect(jsonPath("$[3].id").value(sinApellido))
                .andExpect(jsonPath("$[3].lastName").doesNotExist())
                .andExpect(jsonPath("$[4].id").value(zeta));
    }

    // ---- US2 ----

    @Test
    void excludesPendingRejectedAndOtherPeoplesFriendships() throws Exception {
        long me = verified("Filtro", "Cuatro");
        long friend = verified("Amigo", "Cuatro");
        long pendingSent = verified("Pendiente", "Enviada");
        long pendingReceived = verified("Pendiente", "Recibida");
        long rejected = verified("Rechazada", "Cuatro");
        long stranger1 = verified("Ajena", "Uno");
        long stranger2 = verified("Ajena", "Dos");
        friends(me, friend);
        request(me, pendingSent, "PENDING", null);
        request(pendingReceived, me, "PENDING", null);
        request(rejected, me, "REJECTED", "2026-10-30T10:00:00Z");
        friends(stranger1, stranger2);

        listAs(me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(friend));
    }

    @Test
    void withoutFriendsIsAnEmptyArray() throws Exception {
        long me = verified("Solitaria", "Cinco");
        request(me, verified("Pendiente", "Cinco"), "PENDING", null);

        listAs(me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- Avatares ajenos ----

    private void avatar(long userId) {
        jdbcTemplate.update("INSERT INTO user_avatars (user_id, content_type, data) VALUES (?, 'image/png', ?)",
                userId, new byte[] {1, 2, 3});
    }

    private long groupWith(long... members) {
        long group = jdbcTemplate.queryForObject(
                "INSERT INTO groups (name) VALUES ('Viaje') RETURNING id", Long.class);
        for (long member : members) {
            jdbcTemplate.update("INSERT INTO group_members (user_id, group_id, role) VALUES (?, ?, 'MEMBER')",
                    member, group);
        }
        return group;
    }

    private ResultActions avatarAs(long viewer, long owner) throws Exception {
        return mockMvc.perform(get("/api/users/" + owner + "/avatar")
                .cookie(new Cookie("amano_auth", jwtService.issueToken(viewer))));
    }

    @Test
    void listsTheAvatarUrlOnlyOfFriendsWithACustomAvatar() throws Exception {
        long me = verified("ConFotos", "Siete");
        long withPhoto = verified("Aconfoto", "Siete");
        long withoutPhoto = verified("Bsinfoto", "Siete");
        friends(me, withPhoto);
        friends(withoutPhoto, me);
        avatar(withPhoto);

        listAs(me)
                .andExpect(jsonPath("$[0].avatarUrl").value("/api/users/" + withPhoto + "/avatar"))
                .andExpect(jsonPath("$[1].avatarUrl").doesNotExist());
    }

    @Test
    void friendsInEitherDirectionCanSeeTheAvatar() throws Exception {
        long me = verified("Vista", "Ocho");
        long friend = verified("Amiga", "Ocho");
        friends(me, friend);
        avatar(friend);
        avatar(me);

        avatarAs(me, friend).andExpect(status().isOk());
        avatarAs(friend, me).andExpect(status().isOk());
    }

    @Test
    void activeGroupMatesCanSeeTheAvatarUntilOneLeaves() throws Exception {
        long me = verified("Grupo", "Nueve");
        long mate = verified("Companera", "Nueve");
        long group = groupWith(me, mate);
        avatar(mate);

        avatarAs(me, mate).andExpect(status().isOk());

        jdbcTemplate.update("UPDATE group_members SET left_at = now() WHERE group_id = ? AND user_id = ?",
                group, mate);
        avatarAs(me, mate).andExpect(status().isNotFound());
    }

    @Test
    void unrelatedAccountsCannotSeeTheAvatar() throws Exception {
        long me = verified("Ajena", "Diez");
        long stranger = verified("Extrana", "Diez");
        avatar(stranger);

        avatarAs(me, stranger).andExpect(status().isNotFound());
    }

    @Test
    void aPendingRequestShowsTheSenderToTheRecipientOnly() throws Exception {
        long recipient = verified("Destino", "Once");
        long sender = verified("Remitente", "Once");
        request(sender, recipient, "PENDING", null);
        avatar(sender);
        avatar(recipient);

        avatarAs(recipient, sender).andExpect(status().isOk());
        avatarAs(sender, recipient).andExpect(status().isNotFound());
    }

    @Test
    void theInboxCarriesTheSenderAvatarUrl() throws Exception {
        long recipient = verified("Bandeja", "Doce");
        long withPhoto = verified("Aconfoto", "Doce");
        long withoutPhoto = verified("Bsinfoto", "Doce");
        request(withoutPhoto, recipient, "PENDING", null);
        request(withPhoto, recipient, "PENDING", null);
        avatar(withPhoto);

        mockMvc.perform(get("/api/users/me/friendship-requests/received")
                .cookie(new Cookie("amano_auth", jwtService.issueToken(recipient))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sender.id == %s)].sender.avatarUrl", withPhoto)
                        .value("/api/users/" + withPhoto + "/avatar"));
    }

    // ---- US3 ----

    @Test
    void withoutSessionOrWithUnverifiedAccountIsA401() throws Exception {
        long unverified = account("SinVerificar", "Seis", false);

        mockMvc.perform(get(FRIENDS_URL))
                .andExpect(status().isUnauthorized());
        listAs(unverified)
                .andExpect(status().isUnauthorized());
    }
}
