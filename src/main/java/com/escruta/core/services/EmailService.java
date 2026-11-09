package com.escruta.core.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean mailAuth;

    @Value("${spring.mail.from:noreply@escruta.com}")
    private String mailFrom;

    @Value("${services.mail.log-code:true}")
    private boolean logVerificationCode;

    public void sendVerificationCode(String email, String code) {
        if (mailHost == null || mailHost.isBlank()) {
            log.info("SMTP is not configured; verification code generated for {}", email);
            logCode(email, code);
            return;
        }

        if (mailAuth && (isBlank(mailUsername) || isBlank(mailPassword))) {
            log.warn("SMTP authentication is enabled but username/password are missing; " + "verification email was not sent. Configure ESCRUTA_MAIL_USERNAME and " + "ESCRUTA_MAIL_PASSWORD, or set ESCRUTA_MAIL_SMTP_AUTH=false.");
            logCode(email, code);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Your Escruta verification code");
        message.setText("Your Escruta verification code is: " + code + "\n\nIt expires in 10 minutes.");

        // SMTP is blocking; do not make the authentication request wait for it.
        Thread.startVirtualThread(() -> {
            try {
                mailSender.send(message);
            } catch (Exception e) {
                log.warn("Could not send verification email to {}: {}", email, e.getMessage());
            }
        });
    }

    private void logCode(String email, String code) {
        if (logVerificationCode) {
            log.info("Verification code for {}: {}", email, code);
        } else {
            log.debug("Verification code for {}: {}", email, code);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
