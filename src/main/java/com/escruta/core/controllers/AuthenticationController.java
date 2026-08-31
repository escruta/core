package com.escruta.core.controllers;

import com.escruta.core.configs.SecurityConfiguration;
import com.escruta.core.dtos.AccessTokenResponse;
import com.escruta.core.dtos.LoginUserDto;
import com.escruta.core.dtos.RegisterUserDto;
import com.escruta.core.entities.AccessToken;
import com.escruta.core.services.TokenService;
import com.escruta.core.services.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final SecurityConfiguration securityConfiguration;

    private Authentication authenticate(String email, String password) {
        var request = UsernamePasswordAuthenticationToken.unauthenticated(email, password);
        return this.authenticationManager.authenticate(request);
    }

    private com.escruta.core.entities.User authenticatedUser(Authentication authentication) {
        var principal = authentication.getPrincipal();
        if (!(principal instanceof com.escruta.core.entities.User user)) {
            throw new BadCredentialsException("Invalid authentication principal");
        }
        return user;
    }

    private void setAuthCookie(HttpServletResponse response, AccessToken accessToken) {
        ResponseCookie cookie = securityConfiguration.buildAuthCookie(
                accessToken.getToken(),
                accessToken.getExpiresAt()
        );
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(
            @Valid @RequestBody LoginUserDto loginUserDto,
            HttpServletResponse response
    ) {
        var authentication = this.authenticate(loginUserDto.getEmail(), loginUserDto.getPassword());
        var user = authenticatedUser(authentication);
        var accessToken = tokenService.createToken(user.getId());
        setAuthCookie(response, accessToken);
        return ResponseEntity.ok(new AccessTokenResponse(accessToken));
    }

    @PostMapping("/register")
    public ResponseEntity<AccessTokenResponse> register(
            @Valid @RequestBody RegisterUserDto registerUserDto,
            HttpServletResponse response
    ) {
        var registeredUser = userService.register(registerUserDto);
        var authentication = this.authenticate(registeredUser.getEmail(), registerUserDto.getPassword());
        var user = authenticatedUser(authentication);
        var accessToken = tokenService.createToken(user.getId());
        setAuthCookie(response, accessToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AccessTokenResponse(accessToken));
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
