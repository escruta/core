package com.escruta.core.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
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

    @Value("${spring.mail.from:noreply@escruta.com}")
    private String mailFrom;

    public void sendVerificationCode(String email, String code) {
        if (mailHost == null || mailHost.isBlank()) {
            log.info("SMTP not configured. Verification code for {}: {}", email, code);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject("Your Escruta verification code");
            message.setText("Your Escruta verification code is: " + code + "\n\nIt expires in 10 minutes.");
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Could not send verification email to {}. Code: {}", email, code, e);
        }
    }
}
