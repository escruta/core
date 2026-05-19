package com.escruta.core.repositories;

import com.escruta.core.entities.AccessToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessTokenRepository extends CrudRepository<AccessToken, String> {
    void deleteByUserId(java.util.UUID userId);
}
