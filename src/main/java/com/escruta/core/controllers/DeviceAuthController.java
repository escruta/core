package com.escruta.core.controllers;

import com.escruta.core.dtos.DeviceStartRequest;
import com.escruta.core.services.DeviceAuthService;
import com.escruta.core.services.DevicePollResult;
import com.escruta.core.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DeviceAuthController {
    private final DeviceAuthService deviceAuthService;
    private final UserService userService;

    @PostMapping("/device/start")
    public ResponseEntity<Void> start(@Valid @RequestBody DeviceStartRequest request) {
        deviceAuthService.register(request.deviceCode());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/device/authorize")
    public ResponseEntity<Void> authorize(@RequestParam("device_code") String deviceCode) {
        deviceAuthService.authorize(deviceCode, userService.getCurrentUser().getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/device/token")
    public ResponseEntity<?> token(@RequestParam("device_code") String deviceCode) {
        DevicePollResult result = deviceAuthService.poll(deviceCode);

        return switch (result.status()) {
            case AUTHORIZED -> ResponseEntity.ok(result.token());
            case PENDING ->
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "authorization_pending"));
            case EXPIRED -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "expired_token"));
        };
    }
}
