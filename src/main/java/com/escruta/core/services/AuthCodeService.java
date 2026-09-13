package com.escruta.core.services;

import com.escruta.core.entities.AccessToken;
import com.escruta.core.entities.EmailAuthCode;
import com.escruta.core.entities.EmailVerification;
import com.escruta.core.entities.User;
import com.escruta.core.exceptions.DuplicateFieldException;
import com.escruta.core.repositories.EmailAuthCodeRepository;
import com.escruta.core.repositories.EmailVerificationRepository;
import com.escruta.core.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthCodeService {
    private final EmailAuthCodeRepository emailAuthCodeRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    @Value("${security.auth-code.length:6}")
    private int codeLength;

    @Value("${security.auth-code.ttl-seconds:600}")
    private long codeTtlSeconds;

    @Value("${security.auth-code.max-attempts:5}")
    private int maxAttempts;

    @Value("${security.auth-code.verification-token-ttl-seconds:900}")
    private long verificationTokenTtlSeconds;

    public record VerificationOutcome(boolean newUser, User user, AccessToken session, String verificationToken) {
    }

    public record RegistrationOutcome(User user, AccessToken session) {
    }

    public void requestCode(String rawEmail) {
        String email = normalize(rawEmail);
        String code = generateCode();
        EmailAuthCode authCode = new EmailAuthCode();
        authCode.setEmail(email);
        authCode.setCodeHash(hash(code));
        authCode.setExpiresAt(Instant.now().plusSeconds(codeTtlSeconds));
        authCode.setAttempts(0);
        authCode.setTimeToLive(codeTtlSeconds);
        emailAuthCodeRepository.save(authCode);
        emailService.sendVerificationCode(email, code);
    }

    @Transactional
    public VerificationOutcome verifyCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        EmailAuthCode authCode = emailAuthCodeRepository.findById(email).orElseThrow(
                () -> new BadCredentialsException("Invalid or expired code"));

        if (authCode.getExpiresAt().isBefore(Instant.now())) {
            emailAuthCodeRepository.deleteById(email);
            throw new BadCredentialsException("Invalid or expired code");
        }

        if (!hash(code.trim()).equals(authCode.getCodeHash())) {
            authCode.setAttempts(authCode.getAttempts() + 1);
            if (authCode.getAttempts() >= maxAttempts) {
                emailAuthCodeRepository.deleteById(email);
            } else {
                emailAuthCodeRepository.save(authCode);
            }
            throw new BadCredentialsException("Invalid or expired code");
        }

        emailAuthCodeRepository.deleteById(email);

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            AccessToken session = tokenService.createToken(user.getId());
            return new VerificationOutcome(false, user, session, null);
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String verificationToken = base64Encoder.encodeToString(randomBytes);

        EmailVerification verification = new EmailVerification();
        verification.setToken(hash(verificationToken));
        verification.setEmail(email);
        verification.setExpiresAt(Instant.now().plusSeconds(verificationTokenTtlSeconds));
        verification.setTimeToLive(verificationTokenTtlSeconds);
        emailVerificationRepository.save(verification);
        verification.setToken(verificationToken);

        return new VerificationOutcome(true, null, null, verificationToken);
    }

    @Transactional
    public RegistrationOutcome completeRegistration(String rawEmail, String verificationToken, String name) {
        String email = normalize(rawEmail);
        EmailVerification verification = emailVerificationRepository
                .findById(hash(verificationToken))
                .filter(v -> v.getExpiresAt().isAfter(Instant.now()))
                .filter(v -> v.getEmail().equals(email))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired verification"));

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateFieldException("email", email);
        }

        User user = new User();
        user.setEmail(email);
        user.setName(name.trim());
        User saved = userRepository.save(user);

        emailVerificationRepository.deleteById(verification.getToken());

        AccessToken session = tokenService.createToken(saved.getId());
        return new RegistrationOutcome(saved, session);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, codeLength);
        int number = secureRandom.nextInt(bound);
        return String.format("%0" + codeLength + "d", number);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return new String(Hex.encode(hashed));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
