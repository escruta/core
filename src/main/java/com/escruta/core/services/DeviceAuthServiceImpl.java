package com.escruta.core.services;

import com.escruta.core.dtos.AccessTokenResponse;
import com.escruta.core.entities.AccessToken;
import com.escruta.core.entities.DeviceAuthorization;
import com.escruta.core.repositories.DeviceAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DeviceAuthServiceImpl implements DeviceAuthService {
    private final DeviceAuthorizationRepository repository;
    private final TokenService tokenService;

    private static final int TTL_SECONDS = 600;

    @Override
    @Transactional
    public void register(String deviceCode) {
        if (repository.existsById(deviceCode)) {
            return;
        }
        DeviceAuthorization authorization = new DeviceAuthorization();
        authorization.setDeviceCode(deviceCode);
        authorization.setAuthorized(false);
        authorization.setConsumed(false);
        authorization.setTimeToLive((long) TTL_SECONDS);
        repository.save(authorization);
    }

    @Override
    @Transactional
    public void authorize(String deviceCode, java.util.UUID userId) {
        DeviceAuthorization authorization = repository.findById(deviceCode).orElseGet(() -> {
            DeviceAuthorization fresh = new DeviceAuthorization();
            fresh.setDeviceCode(deviceCode);
            fresh.setTimeToLive((long) TTL_SECONDS);
            return fresh;
        });

        if (authorization.isConsumed()) {
            return;
        }

        if (!authorization.isAuthorized()) {
            AccessToken accessToken = tokenService.createToken(userId);
            authorization.setUserId(userId);
            authorization.setToken(accessToken.getToken());
            authorization.setTokenExpiresAt(accessToken.getExpiresAt());
            authorization.setAuthorized(true);
        }

        repository.save(authorization);
    }

    @Override
    public DevicePollResult poll(String deviceCode) {
        DeviceAuthorization authorization = repository.findById(deviceCode).orElse(null);

        if (authorization == null) {
            return new DevicePollResult(DevicePollResult.Status.EXPIRED, null);
        }

        if (authorization.isConsumed()) {
            return new DevicePollResult(DevicePollResult.Status.EXPIRED, null);
        }

        if (!authorization.isAuthorized() || authorization.getToken() == null) {
            return new DevicePollResult(DevicePollResult.Status.PENDING, null);
        }

        authorization.setConsumed(true);
        repository.save(authorization);

        AccessToken accessToken = new AccessToken();
        accessToken.setToken(authorization.getToken());
        accessToken.setExpiresAt(authorization.getTokenExpiresAt() != null ?
                authorization.getTokenExpiresAt() :
                Instant.now().plusSeconds(TTL_SECONDS));

        return new DevicePollResult(DevicePollResult.Status.AUTHORIZED, new AccessTokenResponse(accessToken));
    }
}
