package com.amano.backend.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amano.backend.config.AuthCookie;
import com.amano.backend.config.JwtService;
import com.amano.backend.user.User;
import com.amano.backend.user.UserAccountResponse;
import com.amano.backend.user.UserMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * POST /api/auth/register (task T019): single atomic multipart submit; on success
 * returns 201 + Location + the session cookie (FR-014/FR-016) and the public profile.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthController(RegistrationService registrationService, JwtService jwtService,
            UserMapper userMapper) {
        this.registrationService = registrationService;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    public ResponseEntity<UserAccountResponse> register(
            @ModelAttribute RegisterRequest request,
            HttpServletRequest httpRequest) {
        User user = registrationService.register(request);

        HttpHeaders headers = new HttpHeaders();
        // 201 contract block: Location + session cookie; Secure only over HTTPS.
        headers.setLocation(java.net.URI.create("/api/users/me"));
        headers.set(HttpHeaders.SET_COOKIE, AuthCookie.sessionCookie(
                jwtService.issueToken(user.getId()), jwtService.expiration(),
                httpRequest.isSecure()).toString());
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(headers)
                .body(userMapper.summaryOf(user.getId()));
    }
}
