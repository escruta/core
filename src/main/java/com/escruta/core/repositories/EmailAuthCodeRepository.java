package com.escruta.core.repositories;

import com.escruta.core.entities.EmailAuthCode;
import org.springframework.data.repository.CrudRepository;

public interface EmailAuthCodeRepository extends CrudRepository<EmailAuthCode, String> {
}
