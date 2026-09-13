package com.escruta.core.entities;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.time.Instant;

@RedisHash("email_auth_codes")
@Getter
@Setter
public class EmailAuthCode {
    @Id
    private String email;

    private String codeHash;

    private Instant expiresAt;

    private int attempts;

    @TimeToLive
    private Long timeToLive;
}
