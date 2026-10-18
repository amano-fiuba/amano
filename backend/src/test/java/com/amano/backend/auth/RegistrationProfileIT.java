package com.amano.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * US3 contract integration tests (task T032), written BEFORE the avatar
 * implementation per TDD. Replicates contracts/auth-api.md + users-api.md with
 * a real PostgreSQL: FR-003, FR-009, FR-010, FR-011, FR-021, FR-022 and the
 * five quickstart §5 scenarios.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class RegistrationProfileIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x02, 0x01, 0x00, 0x00 };

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52 };

    /** Declared image/jpeg but real GIF magic bytes (lying MIME, D6). */
    private static final byte[] GIF_BYTES_LYING_AS_JPEG = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00 };

    private static MockPart part(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static MockPart[] mandatoryFields(String email) {
        return new MockPart[] {
                part("firstName", "Ana"),
                part("email", email),
                part("password", "Password123!"),
                part("confirmPassword", "Password123!"),
                part("acceptTerms", "true"),
        };
    }

    private static MockMultipartFile avatarPart(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("avatar", filename, contentType, bytes);
    }

    @Test
    void registersWithRealJpegAndServesOwnAvatarBinary() throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/auth/register")
                        .file(avatarPart("ana.jpg", "image/jpeg", JPEG_BYTES))
                        .part(mandatoryFields("Ana.ConAvatar@Example.COM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasCustomAvatar").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        int userId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).intValue();
        assertThat(created.getResponse().getContentAsString())
                .contains("\"avatarUrl\":\"/api/users/" + userId + "/avatar\"");

        mockMvc.perform(get("/api/users/" + userId + "/avatar")
                        .cookie(created.getResponse().getCookie("amano_auth")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG_BYTES))
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, max-age=3600"));

        // Owner sees the profile with avatar metadata (FR-021).
        mockMvc.perform(get("/api/users/me").cookie(created.getResponse().getCookie("amano_auth")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCustomAvatar").value(true))
                .andExpect(jsonPath("$.avatarUrl").value("/api/users/" + userId + "/avatar"));
    }

    @Test
    void registersWithRealPngAndServesImagePng() throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/auth/register")
                        .file(avatarPart("ana.png", "image/png", PNG_BYTES))
                        .part(mandatoryFields("Ana.Png@Example.COM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasCustomAvatar").value(true))
                .andReturn();

        int userId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).intValue();

        mockMvc.perform(get("/api/users/" + userId + "/avatar")
                        .cookie(created.getResponse().getCookie("amano_auth")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG_BYTES))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void withoutAvatarReturns404ForTheBinaryAndDefaultProfile() throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("Ana.SinAvatar@Example.COM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasCustomAvatar").value(false))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist())
                .andReturn();

        int userId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).intValue();

        mockMvc.perform(get("/api/users/" + userId + "/avatar")
                        .cookie(created.getResponse().getCookie("amano_auth")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    /** Lying declared MIME is detected via magic bytes (FR-010/FR-022, D6). */
    @Test
    void lyingAvatarContentTypeIsRejectedAtomically() throws Exception {
        String email = "Ana.Mentirosa@Example.COM";
        mockMvc.perform(multipart("/api/auth/register")
                        .file(avatarPart("trampa.jpg", "image/jpeg", GIF_BYTES_LYING_AS_JPEG))
                        .part(mandatoryFields(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.properties.fields.avatar")
                        .value("El archivo debe ser una imagen JPG o PNG válida"));

        // FR-014 atomicity: the failed attempt left no user row behind.
        mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasCustomAvatar").value(false));
    }

    /** Oversize avatar rejected BEFORE persisting, with the contract's 413
     * (auth-api.md "Payload demasiado grande": the Tomcat limit path and the
     * validator share the same handler in T026). FR-010; DB CHECK backstop. */
    @Test
    void oversizedAvatarIsRejectedAs413() throws Exception {
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(JPEG_BYTES, 0, oversized, 0, JPEG_BYTES.length);

        String email = "Ana.Grande@Example.COM";
        mockMvc.perform(multipart("/api/auth/register")
                        .file(avatarPart("grande.jpg", "image/jpeg", oversized))
                        .part(mandatoryFields(email)))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.title").value("Payload demasiado grande"))
                .andExpect(jsonPath("$.detail")
                        .value("El archivo es demasiado grande (máximo 2 MB)"));

        // Atomicity again: same email can be used right after the rejection.
        mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields(email)))
                .andExpect(status().isCreated());
    }

    /** Optional profile data: currencies multi (Q1) and normalized CBU (Q3). */
    @Test
    void currenciesAndNormalizedCbuArePersistedAndExposed() throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/auth/register")
                        .part(mandatoryFields("Ana.Completa@Example.COM"))
                        .part(part("lastName", "Pérez"),
                                part("currencies", "ARS"),
                                part("currencies", "USD"),
                                part("currencies", "EUR"),
                                part("cbuCvu", "285-05910-0123 4567890123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastName").value("Pérez"))
                .andExpect(jsonPath("$.currencies.length()").value(3))
                .andExpect(jsonPath("$.cbuCvu").value("2850591001234567890123"))
                .andReturn();

        mockMvc.perform(get("/api/users/me").cookie(created.getResponse().getCookie("amano_auth")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies.length()").value(3))
                .andExpect(jsonPath("$.cbuCvu").value("2850591001234567890123"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    /** No session, no private data (FR-009): even /me requires the cookie. */
    @Test
    void meWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autorizado"));
    }
}
