package com.escruta.core.entities;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.time.Instant;

@RedisHash("email_verifications")
@Getter
@Setter
public class EmailVerification {
    @Id
    private String token;

    private String email;

    private Instant expiresAt;

    @TimeToLive
    private Long timeToLive;
}
