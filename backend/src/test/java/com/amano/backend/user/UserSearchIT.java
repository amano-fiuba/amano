package com.amano.backend.user;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;

/**
 * Contract tests of GET /api/users/search (feature 008) against a real
 * PostgreSQL. All tests share the database, so each one uses its own distinctive
 * names/emails and only searches for terms nobody else uses.
 */
class UserSearchIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private static MockPart part(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    /** Creates an account that starts PENDING; returns its id. */
    private long register(String firstName, String lastName, String email) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at)
                VALUES (?, ?, ?, 'x', now()) RETURNING id
                """, Long.class, firstName, lastName, email);
    }

    private long registerVerified(String firstName, String lastName, String email) throws Exception {
        long id = register(firstName, lastName, email);
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        return id;
    }

    private Cookie sessionOf(long userId) {
        return new Cookie("amano_auth", jwtService.issueToken(userId));
    }

    private void withAvatar(long userId) {
        jdbcTemplate.update("INSERT INTO user_avatars (user_id, content_type, data) VALUES (?, 'image/png', ?)",
                userId, new byte[] {1, 2, 3});
    }

    private void request(long sender, long recipient, String status) {
        jdbcTemplate.update("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                VALUES (?, ?, ?, CASE WHEN ? = 'PENDING' THEN NULL ELSE now() END)
                """, sender, recipient, status, status);
    }

    private void avatarUrlInSearch(long caller, String email, long owner, boolean expected) throws Exception {
        var result = mockMvc.perform(get("/api/users/search").param("q", email).cookie(sessionOf(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        if (expected) {
            result.andExpect(jsonPath("$[0].avatarUrl").value("/api/users/" + owner + "/avatar"));
            mockMvc.perform(get("/api/users/" + owner + "/avatar").cookie(sessionOf(caller)))
                    .andExpect(status().isOk());
        }
        else {
            result.andExpect(jsonPath("$[0].avatarUrl").doesNotExist());
            mockMvc.perform(get("/api/users/" + owner + "/avatar").cookie(sessionOf(caller)))
                    .andExpect(status().isNotFound());
        }
    }

    /** 023 SC-008: the search never shows a photo that the avatar endpoint would deny. */
    @Test
    void theSearchFollowsTheSameRuleAsTheAvatarEndpoint() throws Exception {
        long me = registerVerified("Quien", "Busca", "foto.quien@example.com");
        long friend = registerVerified("Foto", "Amiga", "foto.amiga@example.com");
        long mate = registerVerified("Foto", "Companera", "foto.companera@example.com");
        long sender = registerVerified("Foto", "Remitente", "foto.remitente@example.com");
        long recipient = registerVerified("Foto", "Destinataria", "foto.destinataria@example.com");
        long stranger = registerVerified("Foto", "Ajena", "foto.ajena@example.com");
        for (long owner : List.of(friend, mate, sender, recipient, stranger)) {
            withAvatar(owner);
        }
        request(me, friend, "ACCEPTED");
        request(sender, me, "PENDING");
        request(me, recipient, "PENDING");
        long group = jdbcTemplate.queryForObject(
                "INSERT INTO groups (name) VALUES ('Fotos') RETURNING id", Long.class);
        for (long member : List.of(me, mate)) {
            jdbcTemplate.update("INSERT INTO group_members (user_id, group_id, role) VALUES (?, ?, 'MEMBER')",
                    member, group);
        }

        avatarUrlInSearch(me, "foto.amiga@example.com", friend, true);
        avatarUrlInSearch(me, "foto.companera@example.com", mate, true);
        avatarUrlInSearch(me, "foto.remitente@example.com", sender, true);
        avatarUrlInSearch(me, "foto.destinataria@example.com", recipient, false);
        avatarUrlInSearch(me, "foto.ajena@example.com", stranger, false);
    }

    @Test
    void findsByFirstNameLastNameOrEmailIgnoringCaseAndOuterSpaces() throws Exception {
        long caller = registerVerified("Buscadora", "Uno", "buscadora.uno@example.com");
        registerVerified("Valentina", "Quiroga", "vq.contacto@example.com");
        Cookie session = sessionOf(caller);

        mockMvc.perform(get("/api/users/search").param("q", "  VALENT ").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firstName").value("Valentina"));
        mockMvc.perform(get("/api/users/search").param("q", "quiro").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastName").value("Quiroga"));
        mockMvc.perform(get("/api/users/search").param("q", "vq.contacto").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("vq.contacto@example.com"));
    }

    @Test
    void exposesOnlyIdNamesAndEmail() throws Exception {
        long caller = registerVerified("Buscadora", "Dos", "buscadora.dos@example.com");
        registerVerified("Privada", "Datos", "privada.datos@example.com");
        jdbcTemplate.update("UPDATE users SET cbu = '0000003100010000000001' WHERE email = ?",
                "privada.datos@example.com");

        mockMvc.perform(get("/api/users/search").param("q", "privada").cookie(sessionOf(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].firstName").value("Privada"))
                .andExpect(jsonPath("$[0].lastName").value("Datos"))
                .andExpect(jsonPath("$[0].email").value("privada.datos@example.com"))
                .andExpect(jsonPath("$[0].cbuCvu").doesNotExist())
                .andExpect(jsonPath("$[0].cbu").doesNotExist())
                .andExpect(jsonPath("$[0].currencies").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void neverIncludesTheCallerNorUnverifiedAccounts() throws Exception {
        long caller = registerVerified("Zarate", "Llamador", "zarate.llamador@example.com");
        register("Zarate", "Pendiente", "zarate.pendiente@example.com"); // stays unverified
        registerVerified("Zarate", "Verificado", "zarate.verificado@example.com");

        mockMvc.perform(get("/api/users/search").param("q", "zarate").cookie(sessionOf(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lastName").value("Verificado"));
    }

    @Test
    void ordersByFirstNameThenLastNameWithMissingLastNamesLast() throws Exception {
        long caller = registerVerified("Buscadora", "Tres", "buscadora.tres@example.com");
        registerVerified("Mismonombre", null, "mismo.sin@example.com");
        registerVerified("Mismonombre", "Benitez", "mismo.benitez@example.com");
        registerVerified("Mismonombre", "Alvarez", "mismo.alvarez@example.com");

        mockMvc.perform(get("/api/users/search").param("q", "mismonombre").cookie(sessionOf(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].lastName").value("Alvarez"))
                .andExpect(jsonPath("$[1].lastName").value("Benitez"))
                .andExpect(jsonPath("$[2].lastName").doesNotExist());
    }

    @Test
    void returnsAtMost20Results() throws Exception {
        long caller = registerVerified("Buscadora", "Cuatro", "buscadora.cuatro@example.com");
        for (int i = 0; i < 22; i++) {
            String number = String.format("%02d", i);
            registerVerified("Topes" + number, "Limite", "topes" + number + "@example.com");
        }

        mockMvc.perform(get("/api/users/search").param("q", "topes").cookie(sessionOf(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[0].firstName").value("Topes00"))
                .andExpect(jsonPath("$[19].firstName").value("Topes19"));
    }

    @Test
    void wildcardsAreLiteralAndNoMatchIsAnEmptyList() throws Exception {
        long caller = registerVerified("Buscadora", "Cinco", "buscadora.cinco@example.com");
        Cookie session = sessionOf(caller);

        mockMvc.perform(get("/api/users/search").param("q", "%%").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/users/search").param("q", "__").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/users/search").param("q", "nadiesellamaasi").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void invalidTermsAreA400WithTheFieldMessage() throws Exception {
        long caller = registerVerified("Buscadora", "Seis", "buscadora.seis@example.com");
        Cookie session = sessionOf(caller);

        mockMvc.perform(get("/api/users/search").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.q").value(UserSearchService.TOO_SHORT_MESSAGE));
        mockMvc.perform(get("/api/users/search").param("q", " a ").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.q").value(UserSearchService.TOO_SHORT_MESSAGE));
        mockMvc.perform(get("/api/users/search").param("q", "a".repeat(101)).cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.q").value(UserSearchService.TOO_LONG_MESSAGE));
    }

    @Test
    void withoutSessionOrWithUnverifiedAccountIsA401() throws Exception {
        mockMvc.perform(get("/api/users/search").param("q", "an"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autorizado"));

        long pending = register("Pendiente", "Siete", "pendiente.siete@example.com");
        mockMvc.perform(get("/api/users/search").param("q", "an").cookie(sessionOf(pending)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }
}
