package com.escruta.core.configs;

import com.escruta.core.repositories.UserRepository;
import com.escruta.core.services.TokenService;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {
    private final UserRepository userRepository;
    private final TokenService tokenService;

    @Value("${security.cors.allowedOrigins}")
    private String allowedOrigins;
    @Value("${security.cors.allowedMethods}")
    private String allowedMethods;
    @Value("${security.cors.allowedHeaders}")
    private String allowedHeaders;
    @Value("${security.cors.allowCredentials}")
    private boolean allowCredentials;

    @Value("${security.cookie.name:escruta_token}")
    private String cookieName;
    @Value("${security.cookie.domain:}")
    private String cookieDomain;
    @Value("${security.cookie.secure:true}")
    private boolean cookieSecure;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers(HttpMethod.POST, "/login", "/register", "/introspect", "/device/start")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/device/token")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .opaqueToken(opaque -> opaque.introspector(opaqueTokenIntrospector())))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler(logoutHandler())
                        .addLogoutHandler(clearCookieLogoutHandler())
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)));

        return http.build();
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        return request -> {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.toLowerCase().startsWith("bearer ")) {
                return header.substring(7).trim();
            }
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if (cookieName.equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
            return null;
        };
    }

    @Bean
    public LogoutHandler logoutHandler() {
        return (request, _, _) -> {
            String token = bearerTokenResolver().resolve(request);
            if (token != null) {
                tokenService.invalidateToken(token);
            }
        };
    }

    @Bean
    public LogoutHandler clearCookieLogoutHandler() {
        return (_, response, _) -> {
            ResponseCookie cookie = ResponseCookie
                    .from(cookieName, "")
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(0)
                    .domain(cookieDomain.isBlank() ?
                            null :
                            cookieDomain)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        };
    }

    @Bean
    public OpaqueTokenIntrospector opaqueTokenIntrospector() {
        return token -> {
            var accessToken = tokenService
                    .validateToken(token)
                    .orElseThrow(() -> new BadCredentialsException("Invalid or expired token"));

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", accessToken.getUserId().toString());
            attributes.put("active", true);

            return new DefaultOAuth2AuthenticatedPrincipal(
                    accessToken.getUserId().toString(),
                    attributes,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return email -> userRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User with the given email not found"));
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOriginPatterns(origins);

        List<String> methods = Arrays.asList(allowedMethods.split(","));
        configuration.setAllowedMethods(methods);

        List<String> headers = Arrays.asList(allowedHeaders.split(","));
        configuration.setAllowedHeaders(headers);

        configuration.setAllowCredentials(allowCredentials);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    public ResponseCookie buildAuthCookie(String rawToken, Instant expiresAt) {
        return ResponseCookie
                .from(cookieName, rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.between(Instant.now(), expiresAt))
                .domain(cookieDomain.isBlank() ?
                        null :
                        cookieDomain)
                .build();
    }
}
