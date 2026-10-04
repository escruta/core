package com.escruta.core.repositories;

import com.escruta.core.entities.AccessToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessTokenRepository extends CrudRepository<AccessToken, String> {
    List<AccessToken> findAllByUserId(UUID userId);

    Optional<AccessToken> findBySessionId(String sessionId);

    void deleteByUserId(UUID userId);
}
