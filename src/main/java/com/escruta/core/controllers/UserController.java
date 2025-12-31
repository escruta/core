package com.escruta.core.controllers;

import com.escruta.core.dtos.BasicUser;
import com.escruta.core.dtos.ChangePasswordDto;
import com.escruta.core.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/users")
@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<BasicUser> getMe() {
        return ResponseEntity.ok(new BasicUser(userService.getCurrentFullUser()));
    }

    @PostMapping("/change-name")
    public ResponseEntity<?> changeName(@RequestParam String newFullName) {
        try {
            userService.changeName(newFullName);
            return ResponseEntity
                    .ok()
                    .build();
        } catch (BadCredentialsException e) {
            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .build();
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordDto changePasswordDto) {
        try {
            userService.changePassword(changePasswordDto);
            return ResponseEntity
                    .ok()
                    .build();
        } catch (BadCredentialsException e) {
            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .build();
        }
    }
}
