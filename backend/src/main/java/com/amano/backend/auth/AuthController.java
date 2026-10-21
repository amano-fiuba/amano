package com.amano.backend.auth;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amano.backend.config.AuthCookie;
import com.amano.backend.config.JwtService;
import com.amano.backend.user.User;
import com.amano.backend.user.UserAccountResponse;
import com.amano.backend.user.UserMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Public auth endpoints under /api/auth.
 * - POST /register (feature 003): atomic multipart submit; the 201 carries only
 *   the normalized email, with no session cookie.
 * - POST /login (feature 004): real login, replaces the provisional stub of 003.
 *   On success it issues the session cookie (JwtService + AuthCookie) and returns
 *   the profile with the shape of GET /api/users/me. Errors (400/401/403) are
 *   mapped by ProblemDetailExceptionHandler.
 * - POST /logout (feature 004): expires the HttpOnly session cookie. Public and
 *   idempotent: without a session it is a harmless 204.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthController(RegistrationService registrationService, LoginService loginService,
            JwtService jwtService, UserMapper userMapper) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @ModelAttribute RegisterRequest request,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar) {
        User user = registrationService.register(request, avatar);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("email", user.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<UserAccountResponse> login(
            @RequestBody(required = false) LoginRequest request, HttpServletRequest httpRequest) {
        User user = loginService.authenticate(request);

        String jwt = jwtService.issueToken(user.getId());
        ResponseCookie cookie = AuthCookie.sessionCookie(
                jwt, jwtService.expiration(), httpRequest.isSecure());
        UserAccountResponse profile = userMapper.summaryOf(user.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(profile);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        ResponseCookie expired = AuthCookie.expiredCookie(httpRequest.isSecure());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .build();
    }
}
