package com.escruta.core.entities;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.redis.core.index.Indexed;

@RedisHash("access_tokens")
@Getter
@Setter
public class AccessToken {
    @Id
    private String token;

    @Indexed
    private java.util.UUID userId;

    private java.time.Instant expiresAt;

    @TimeToLive
    private Long timeToLive;
}
