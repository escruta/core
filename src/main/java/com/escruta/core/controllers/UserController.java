package com.escruta.core.controllers;

import com.escruta.core.dtos.BasicUser;
import com.escruta.core.dtos.ChangePasswordDto;
import com.escruta.core.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/users")
@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<BasicUser> getMe() {
        return ResponseEntity.ok(new BasicUser(userService.getCurrentUser()));
    }

    @PostMapping("/change-name")
    public ResponseEntity<?> changeName(@RequestParam String newName) {
        userService.changeName(newName);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordDto changePasswordDto) {
        userService.changePassword(changePasswordDto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteAccount() {
        userService.deleteAccount();
        return ResponseEntity.noContent().build();
    }
}
