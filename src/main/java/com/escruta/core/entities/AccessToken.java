package com.escruta.core.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "access_tokens")
@Getter
@Setter
public class AccessToken {
    @Id
    private String token;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Instant expiresAt;
}
