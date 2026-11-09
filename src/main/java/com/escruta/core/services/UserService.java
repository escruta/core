package com.escruta.core.services;

import com.escruta.core.entities.User;
import com.escruta.core.exceptions.DuplicateFieldException;
import com.escruta.core.repositories.AccessTokenRepository;
import com.escruta.core.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final String CURRENT_USER_ATTRIBUTE = UserService.class.getName() + ".CURRENT_USER";
    private static final Object NO_USER = new Object();

    private final UserRepository userRepository;
    private final AccessTokenRepository accessTokenRepository;

    public UUID getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
            String userId = principal.getAttribute("sub");
            if (userId != null && !userId.isBlank()) {
                return UUID.fromString(userId);
            }
        }
        User user = getCurrentUser();
        return user != null ?
                user.getId() :
                null;
    }

    public User getCurrentUser() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object cached = attributes.getAttribute(CURRENT_USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached == NO_USER) {
                return null;
            }
            if (cached instanceof User user) {
                return user;
            }
        }

        User user = loadCurrentUser();
        if (attributes != null) {
            attributes.setAttribute(
                    CURRENT_USER_ATTRIBUTE,
                    user != null ?
                            user :
                            NO_USER,
                    RequestAttributes.SCOPE_REQUEST
            );
        }
        return user;
    }

    private User loadCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
            String userId = principal.getAttribute("sub");
            if (userId == null) {
                return null;
            }
            return userRepository.findById(UUID.fromString(userId)).orElse(null);
        }

        return null;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public User createUser(String name, String email) {
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateFieldException("email", normalizedEmail);
        }

        var user = new User();
        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        return userRepository.save(user);
    }

    @Transactional
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
