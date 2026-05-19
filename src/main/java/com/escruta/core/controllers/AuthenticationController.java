package com.escruta.core.controllers;

import com.escruta.core.dtos.AccessTokenResponse;
import com.escruta.core.dtos.LoginUserDto;
import com.escruta.core.dtos.RegisterUserDto;
import com.escruta.core.services.TokenService;
import com.escruta.core.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        var user = (com.escruta.core.entities.User) authentication.getPrincipal();
        return ResponseEntity.ok(new AccessTokenResponse(tokenService.createToken(user.getId())));
    }

    @PostMapping("/register")
    public ResponseEntity<AccessTokenResponse> register(@Valid @RequestBody RegisterUserDto registerUserDto) {
        var registeredUser = userService.register(registerUserDto);
        var authentication = this.authenticate(registeredUser.getEmail(), registerUserDto.getPassword());
        var user = (com.escruta.core.entities.User) authentication.getPrincipal();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new AccessTokenResponse(tokenService.createToken(user.getId())));
    }

    @PostMapping("/introspect")
    public ResponseEntity<Map<String, Object>> introspect(@RequestParam("token") String token) {
        return tokenService
                .validateToken(token)
                .map(t -> ResponseEntity.ok(Map.<String, Object>of("active",
                        true,
                        "sub",
                        t.getUserId(),
                        "exp",
                        t.getExpiresAt().getEpochSecond()
                )))
                .orElse(ResponseEntity.ok(Map.of("active", false)));
    }
}
