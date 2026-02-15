package com.escruta.core.services;

import com.escruta.core.repositories.NotebookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotebookOwnershipService Tests")
class NotebookOwnershipServiceTest {
    @Mock
    private NotebookRepository notebookRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private NotebookOwnershipService notebookOwnershipService;

    @Test
    @DisplayName("Should return true when user is notebook owner")
    void isUserNotebookOwner_shouldReturnTrueWhenUserIsOwner() {
        UUID notebookId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(userService.getUserId()).thenReturn(userId);
        when(notebookRepository.existsByIdAndUserId(notebookId, userId)).thenReturn(true);

        boolean result = notebookOwnershipService.isUserNotebookOwner(notebookId);

        assertThat(result).isTrue();
        verify(notebookRepository, times(1)).existsByIdAndUserId(notebookId, userId);
    }

    @Test
    @DisplayName("Should return false when user is not notebook owner")
    void isUserNotebookOwner_shouldReturnFalseWhenUserIsNotOwner() {
        UUID notebookId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(userService.getUserId()).thenReturn(userId);
        when(notebookRepository.existsByIdAndUserId(notebookId, userId)).thenReturn(false);

        boolean result = notebookOwnershipService.isUserNotebookOwner(notebookId);

        assertThat(result).isFalse();
        verify(notebookRepository, times(1)).existsByIdAndUserId(notebookId, userId);
    }

    @Test
    @DisplayName("Should return false when user is null")
    void isUserNotebookOwner_shouldReturnFalseWhenUserIsNull() {
        UUID notebookId = UUID.randomUUID();

        when(userService.getUserId()).thenReturn(null);

        boolean result = notebookOwnershipService.isUserNotebookOwner(notebookId);

        assertThat(result).isFalse();
        verify(notebookRepository, times(1)).existsByIdAndUserId(notebookId, null);
    }

    @Test
    @DisplayName("Should call repository with correct parameters")
    void isUserNotebookOwner_shouldCallRepositoryWithCorrectParameters() {
        UUID notebookId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID userId = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

        when(userService.getUserId()).thenReturn(userId);
        when(notebookRepository.existsByIdAndUserId(any(), any())).thenReturn(true);

        notebookOwnershipService.isUserNotebookOwner(notebookId);

        verify(notebookRepository).existsByIdAndUserId(notebookId, userId);
    }
}
