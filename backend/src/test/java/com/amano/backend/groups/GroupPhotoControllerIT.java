package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * US8 contract tests (T082): {@code POST /api/groups/{id}/photo} reuses the
 * existing user-avatar validation mechanism (content-based JPG/PNG magic
 * bytes; oversize → 413 with the existing MaxUploadSize handler — the
 * documented drift vs. the contract's "5MB/400" wording, resolved in favor of
 * reusing the validator without a new pattern). On failure the previous
 * photo is conserved, and every successful upload records PHOTO_CHANGED.
 */
class GroupPhotoControllerIT extends AbstractIntegrationIT {

    /** Real friendships replacing the old stub property (see {@link FriendshipFixtures}). */
    private static final java.util.List<String> FRIEND_FIXTURES = java.util.List.of("friend@example.com");

    public static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * The friend fixture must exist before any method creates a group with it as
     * a member. Creating it per method removes the dependency on
     * {@link #memberCallerIsForbidden()} having run first.
     */
    @BeforeEach
    void ensureFriendFixture() {
        jdbcTemplate.update("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES ('Friend', 'friend@example.com', 'x', now(), CURRENT_TIMESTAMP)
                ON CONFLICT (email) DO UPDATE SET email_verified_at = CURRENT_TIMESTAMP
                """);
    }

    private Cookie verifiedSessionOf(String rawEmail) {
        jdbcTemplate.update("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES ('Ana', ?, 'x', now(), CURRENT_TIMESTAMP)
                ON CONFLICT (email) DO UPDATE SET email_verified_at = CURRENT_TIMESTAMP
                """, rawEmail);
        FriendshipFixtures.link(jdbcTemplate, rawEmail, FRIEND_FIXTURES);
        return new Cookie("amano_auth", jwtService.issueToken(userIdOf(rawEmail)));
    }

    private long userIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long createGroup(Cookie session) throws Exception {
        String created = mockMvc.perform(post("/api/groups")
                .cookie(session)
                .contentType("application/json")
                .content("{\"name\": \"Amigos para viajar\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"friend@example.com\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private static MockMultipartFile pngFile(String name) {
        byte[] body = Arrays.copyOf(PNG_BYTES, PNG_BYTES.length + 16);
        return new MockMultipartFile("file", name, "image/png", body);
    }

    private static MockMultipartFile invalidFile() {
        return new MockMultipartFile("file", "doc.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46}); // "%PDF"
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload(long groupId, MockMultipartFile file, Cookie session) {
        return multipart("/api/groups/" + groupId + "/photo").file(file).cookie(session);
    }

    @Test
    void adminUploadsAValidPngAndGetThePhotoUrl() throws Exception {
        Cookie admin = verifiedSessionOf("photo-admin@example.com");
        long groupId = createGroup(admin);

        mockMvc.perform(upload(groupId, pngFile("logo.png"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photo").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        String photo = jdbcTemplate.queryForObject(
                "SELECT photo FROM groups WHERE id = ?", String.class, groupId);
        assertThat(photo).contains("/api/groups/" + groupId + "/photo");

        // the image is recoverable for display with the real content type
        mockMvc.perform(get("/api/groups/" + groupId + "/photo").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void invalidFileTypeIsRejectedAndThePreviousPhotoIsConserved() throws Exception {
        Cookie admin = verifiedSessionOf("photo-invalid-admin@example.com");
        long groupId = createGroup(admin);

        // previous photo: the same admin uploads a valid PNG first
        mockMvc.perform(upload(groupId, pngFile("previa.png"), admin))
                .andExpect(status().isOk());

        mockMvc.perform(upload(groupId, invalidFile(), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());

        String photo = jdbcTemplate.queryForObject(
                "SELECT photo FROM groups WHERE id = ?", String.class, groupId);
        assertThat(photo).isNotNull(); // the previous photo is conserved
        Integer junk = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_photos WHERE group_id = ? AND content_type = 'application/pdf'",
                Integer.class, groupId);
        assertThat(junk).isZero();
    }

    @Test
    void memberCallerIsForbidden() throws Exception {
        Cookie admin = verifiedSessionOf("photo-member-admin@example.com");
        long groupId = createGroup(admin);
        Cookie member = new Cookie("amano_auth", jwtService.issueToken(userIdOf("friend@example.com")));

        mockMvc.perform(upload(groupId, pngFile("foto.png"), member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").isNotEmpty());

        String photo = jdbcTemplate.queryForObject(
                "SELECT photo FROM groups WHERE id = ?", String.class, groupId);
        assertThat(photo).isNull(); // nothing changed
    }

    @Test
    void anOutsiderReceivesTheNeutralNotFound() throws Exception {
        Cookie outsider = verifiedSessionOf("photo-outsider@example.com");

        mockMvc.perform(upload(999999, pngFile("foto.png"), outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedUploadIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/groups/1/photo")
                .file(pngFile("foto.png")))
                .andExpect(status().isUnauthorized());
    }
}
