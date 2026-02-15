package com.escruta.core.services;

import com.escruta.core.dtos.ChangePasswordDto;
import com.escruta.core.dtos.RegisterUserDto;
import com.escruta.core.entities.User;
import com.escruta.core.exceptions.DuplicateFieldException;
import com.escruta.core.repositories.AccessTokenRepository;
import com.escruta.core.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2AuthenticatedPrincipal principal;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "Password123";
    private static final String TEST_FULL_NAME = "Test User";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return user ID when user is authenticated")
    void getUserId_shouldReturnUserIdWhenAuthenticated() {
        UUID userId = UUID.randomUUID();
        User user = createTestUser(userId);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("sub")).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        UUID result = userService.getUserId();

        assertThat(result).isEqualTo(userId);
    }

    @Test
    @DisplayName("Should return null when user is not authenticated")
    void getUserId_shouldReturnNullWhenNotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(null);

        UUID result = userService.getUserId();

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null when authentication is not authenticated")
    void getUserId_shouldReturnNullWhenAuthenticationIsNotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        UUID result = userService.getUserId();

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return user when authenticated")
    void getCurrentFullUser_shouldReturnUserWhenAuthenticated() {
        UUID userId = UUID.randomUUID();
        User user = createTestUser(userId);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("sub")).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        User result = userService.getCurrentFullUser();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("Should register new user successfully")
    void register_shouldRegisterNewUser() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setEmail(TEST_EMAIL);
        dto.setPassword(TEST_PASSWORD);
        dto.setFullName(TEST_FULL_NAME);

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(UUID.randomUUID());
            return savedUser;
        });

        User result = userService.register(dto);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(result.getFullName()).isEqualTo(TEST_FULL_NAME);
        assertThat(result.getPassword()).isEqualTo("encodedPassword");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void register_shouldThrowExceptionWhenEmailExists() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setEmail(TEST_EMAIL);
        dto.setPassword(TEST_PASSWORD);
        dto.setFullName(TEST_FULL_NAME);

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(DuplicateFieldException.class)
                .hasMessageContaining("email");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should change user name successfully")
    void changeName_shouldChangeUserName() {
        UUID userId = UUID.randomUUID();
        User user = createTestUser(userId);
        String newName = "New Name";

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("sub")).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        userService.changeName(newName);

        assertThat(user.getFullName()).isEqualTo(newName);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should throw exception when changing name and user not authenticated")
    void changeName_shouldThrowExceptionWhenNotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(null);

        assertThatThrownBy(() -> userService.changeName("New Name"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("not authenticated");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should change password successfully")
    void changePassword_shouldChangePassword() {
        UUID userId = UUID.randomUUID();
        User user = createTestUser(userId);
        user.setPassword("currentEncodedPassword");

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("currentPassword");
        dto.setNewPassword("NewPassword123");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("sub")).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode(dto.getNewPassword())).thenReturn("newEncodedPassword");

        userService.changePassword(dto);

        assertThat(user.getPassword()).isEqualTo("newEncodedPassword");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should throw exception when current password is incorrect")
    void changePassword_shouldThrowExceptionWhenCurrentPasswordIncorrect() {
        UUID userId = UUID.randomUUID();
        User user = createTestUser(userId);
        user.setPassword("encodedPassword");

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("wrongPassword");
        dto.setNewPassword("NewPassword123");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("sub")).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(dto))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete account successfully")
    void deleteAccount_shouldDeleteUserAndTokens() {
        UUID userId = UUID.randomUUID();
        User user = createTestUser(userId);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("sub")).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        userService.deleteAccount();

        verify(accessTokenRepository).deleteByEmail(TEST_EMAIL);
        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("Should throw exception when deleting account and not authenticated")
    void deleteAccount_shouldThrowExceptionWhenNotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(null);

        assertThatThrownBy(() -> userService.deleteAccount())
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("not authenticated");

        verify(accessTokenRepository, never()).deleteByEmail(any());
        verify(userRepository, never()).delete(any());
    }

    private User createTestUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail(TEST_EMAIL);
        user.setFullName(TEST_FULL_NAME);
        user.setPassword(TEST_PASSWORD);
        return user;
    }
}
