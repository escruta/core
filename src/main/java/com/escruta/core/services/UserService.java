package com.escruta.core.services;

import com.escruta.core.dtos.ChangePasswordDto;
import com.escruta.core.dtos.RegisterUserDto;
import com.escruta.core.entities.User;
import com.escruta.core.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UUID getUserId() {
        var user = getCurrentFullUser();
        return (user != null) ?
                user.getId() :
                null;
    }

    public User getCurrentFullUser() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
            String email = principal.getAttribute("sub");
            return userRepository
                    .findByEmail(email)
                    .orElse(null);
        }

        return null;
    }

    public User register(RegisterUserDto input) {
        var user = new User();
        user.setFullName(input.getFullName());
        user.setEmail(input.getEmail());
        user.setPassword(passwordEncoder.encode(input.getPassword()));
        return userRepository.save(user);
    }

    public void changeName(String newName) {
        User currentUser = getCurrentFullUser();
        if (currentUser == null) {
            throw new BadCredentialsException("User not authenticated");
        }
        currentUser.setFullName(newName);
        userRepository.save(currentUser);
    }

    public void changePassword(ChangePasswordDto changePasswordDto) {
        User currentUser = getCurrentFullUser();
        if (currentUser == null) {
            throw new BadCredentialsException("User not authenticated");
        }
        if (!passwordEncoder.matches(changePasswordDto.getCurrentPassword(), currentUser.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        currentUser.setPassword(passwordEncoder.encode(changePasswordDto.getNewPassword()));
        userRepository.save(currentUser);
    }
}
