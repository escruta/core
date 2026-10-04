package com.escruta.core.controllers;

import com.escruta.core.dtos.session.SessionResponseDTO;
import com.escruta.core.services.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {
    private final SessionService sessionService;

    @GetMapping
    public ResponseEntity<List<SessionResponseDTO>> getSessions() {
        return ResponseEntity.ok(sessionService.getSessions());
    }

    @DeleteMapping
    public ResponseEntity<Void> revokeOtherSessions() {
        sessionService.revokeOtherSessions();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        if (sessionService.revokeSession(sessionId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
