package com.escruta.core.services;

import com.escruta.core.dtos.ChangePasswordDto;
import com.escruta.core.dtos.RegisterUserDto;
import com.escruta.core.entities.User;
import com.escruta.core.exceptions.DuplicateFieldException;
import com.escruta.core.repositories.AccessTokenRepository;
import com.escruta.core.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import com.escruta.core.events.UserDeletedEvent;
import com.escruta.core.entities.Notebook;
import org.springframework.context.ApplicationEventPublisher;
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
    private final AccessTokenRepository accessTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

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

    public User register(RegisterUserDto input) {
        if (userRepository.existsByEmail(input.getEmail())) {
            throw new DuplicateFieldException("email", input.getEmail());
        }

        var user = new User();
        user.setName(input.getName());
        user.setEmail(input.getEmail());
        user.setPassword(passwordEncoder.encode(input.getPassword()));
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

    public void changePassword(ChangePasswordDto changePasswordDto) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new BadCredentialsException("User not authenticated");
        }
        if (!passwordEncoder.matches(changePasswordDto.getCurrentPassword(), currentUser.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        currentUser.setPassword(passwordEncoder.encode(changePasswordDto.getNewPassword()));
        userRepository.save(currentUser);
    }

    @Transactional
    public void deleteAccount() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new BadCredentialsException("User not authenticated");
        }
        var notebookIds = currentUser.getNotebooks().stream().map(Notebook::getId).toList();
        accessTokenRepository.deleteByUserId(currentUser.getId());
        userRepository.delete(currentUser);
        eventPublisher.publishEvent(new UserDeletedEvent(this, notebookIds));
    }
}
