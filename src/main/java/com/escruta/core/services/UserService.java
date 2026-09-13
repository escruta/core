package com.escruta.core.services;

import com.escruta.core.entities.User;
import com.escruta.core.exceptions.DuplicateFieldException;
import com.escruta.core.repositories.AccessTokenRepository;
import com.escruta.core.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AccessTokenRepository accessTokenRepository;

    public UUID getUserId() {
        var user = getCurrentUser();
        return (user != null) ?
                user.getId() :
                null;
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
            String userId = principal.getAttribute("sub");
            assert userId != null;
            return userRepository.findById(UUID.fromString(userId)).orElse(null);
        }

        return null;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    public User createUser(String name, String email) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateFieldException("email", normalizedEmail);
        }

        var user = new User();
        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        return userRepository.save(user);
    }

    public void changeName(String newName) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new BadCredentialsException("User not authenticated");
        }
        currentUser.setName(newName);
        userRepository.save(currentUser);
    }

    @Transactional
    public void deleteAccount() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new BadCredentialsException("User not authenticated");
        }
        accessTokenRepository.deleteByUserId(currentUser.getId());
        userRepository.delete(currentUser);
    }
}
