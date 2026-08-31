package com.escruta.core.entities;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@RedisHash("device_authorizations")
@Getter
@Setter
public class DeviceAuthorization {
    @Id
    private String deviceCode;
    private java.util.UUID userId;
    private String token;
    private Instant tokenExpiresAt;
    private boolean authorized;
    private boolean consumed;

    @TimeToLive
    private Long timeToLive;
}
