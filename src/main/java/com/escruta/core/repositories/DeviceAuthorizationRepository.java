package com.escruta.core.repositories;

import com.escruta.core.entities.DeviceAuthorization;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceAuthorizationRepository extends CrudRepository<DeviceAuthorization, String> {
}
