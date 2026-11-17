package com.amano.backend.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.SecurityConfig;
import com.amano.backend.user.UserRepository;

@WebMvcTest(HealthController.class)
@Import({ SecurityConfig.class })
@TestPropertySource(properties = {
        // Security starter is now on the classpath (T011): use the real chain so
        // /api/health stays permitAll (instead of the framework's default 401 chain).
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** The verified-account rule of SecurityConfig (feature 003, T040) needs it at load. */
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void healthReturnsStatusUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
