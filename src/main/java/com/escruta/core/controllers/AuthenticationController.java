package com.escruta.core.controllers;

import com.escruta.core.dtos.AccessTokenResponse;
import com.escruta.core.dtos.LoginUserDto;
import com.escruta.core.dtos.RegisterUserDto;
import com.escruta.core.services.TokenService;
import com.escruta.core.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final TokenService tokenService;

    private Authentication authenticate(String email, String password) {
        var request = UsernamePasswordAuthenticationToken.unauthenticated(email, password);
        return this.authenticationManager.authenticate(request);
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginUserDto loginUserDto) {
        var authentication = this.authenticate(loginUserDto.getEmail(), loginUserDto.getPassword());
        return ResponseEntity.ok(new AccessTokenResponse(tokenService.createToken(authentication.getName())));
    }

    @PostMapping("/register")
    public ResponseEntity<AccessTokenResponse> register(@Valid @RequestBody RegisterUserDto registerUserDto) {
        try {
            var registeredUser = userService.register(registerUserDto);
            var authentication = this.authenticate(registeredUser.getEmail(), registerUserDto.getPassword());
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new AccessTokenResponse(tokenService.createToken(authentication.getName())));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/introspect")
    public ResponseEntity<?> introspect(@RequestParam("token") String token) {
        return tokenService
                .validateToken(token)
                .map(t -> ResponseEntity.ok(Map.of("active",
                        true,
                        "sub",
                        t.getEmail(),
                        "exp",
                        t.getExpiresAt().getEpochSecond()
                )))
                .orElse(ResponseEntity.ok(Map.of("active", false)));
    }
}
