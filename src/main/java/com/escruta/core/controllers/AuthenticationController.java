package com.escruta.core.controllers;

import com.escruta.core.dtos.AccessTokenResponse;
import com.escruta.core.dtos.BasicUser;
import com.escruta.core.dtos.CompleteRegistrationRequest;
import com.escruta.core.dtos.RequestEmailCodeRequest;
import com.escruta.core.dtos.VerifyEmailCodeRequest;
import com.escruta.core.dtos.VerifyEmailCodeResponse;
import com.escruta.core.services.AuthCodeService;
import com.escruta.core.services.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthCodeService authCodeService;
    private final TokenService tokenService;

    @PostMapping("/request-code")
    public ResponseEntity<Void> requestCode(@Valid @RequestBody RequestEmailCodeRequest request) {
        authCodeService.requestCode(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-code")
    public ResponseEntity<VerifyEmailCodeResponse> verifyCode(
            @Valid @RequestBody VerifyEmailCodeRequest request
    ) {
        var outcome = authCodeService.verifyCode(request.email(), request.code());
        if (outcome.newUser()) {
            return ResponseEntity.ok(VerifyEmailCodeResponse.newUser(outcome.verificationToken()));
        }
        return ResponseEntity.ok(VerifyEmailCodeResponse.existingUser(
                new AccessTokenResponse(outcome.session()),
                new BasicUser(outcome.user())
        ));
    }

    @PostMapping("/complete-registration")
    public ResponseEntity<AccessTokenResponse> completeRegistration(
            @Valid @RequestBody CompleteRegistrationRequest request
    ) {
        var outcome = authCodeService.completeRegistration(
                request.email(), request.verificationToken(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AccessTokenResponse(outcome.session()));
    }

    @PostMapping("/introspect")
    public ResponseEntity<Map<String, Object>> introspect(@RequestParam("token") String token) {
        return tokenService
                .validateToken(token)
                .map(t -> ResponseEntity.ok(Map.<String, Object>of(
                        "active",
                        true,
                        "sub",
                        t.getUserId(),
                        "exp",
                        t.getExpiresAt().getEpochSecond()
                )))
                .orElse(ResponseEntity.ok(Map.of("active", false)));
    }
}
