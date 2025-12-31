package com.escruta.core.repositories;

import com.escruta.core.entities.AccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccessTokenRepository extends JpaRepository<AccessToken, String> {
    Optional<AccessToken> findByToken(String token);
}
