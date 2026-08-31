package com.escruta.core.services;

public interface DeviceAuthService {
    void register(String deviceCode);

    void authorize(String deviceCode, java.util.UUID userId);

    DevicePollResult poll(String deviceCode);
}
