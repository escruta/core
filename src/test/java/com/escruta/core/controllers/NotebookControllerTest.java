package com.escruta.core.controllers;

import com.escruta.core.dtos.notebook.NotebookCreationDTO;
import com.escruta.core.dtos.notebook.NotebookResponseDTO;
import com.escruta.core.dtos.notebook.NotebookUpdateDTO;
import com.escruta.core.services.NotebookOwnershipService;
import com.escruta.core.services.NotebookService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("NotebookController Tests")
class NotebookControllerTest {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @MockitoBean
    private NotebookService notebookService;

    @MockitoBean
    private NotebookOwnershipService notebookOwnershipService;

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return list of notebooks for authenticated user")
    void getUserNotebooks_shouldReturnNotebooksWhenAuthenticated() throws Exception {
        NotebookResponseDTO notebook1 = new NotebookResponseDTO(UUID.randomUUID(), null, "📓", "Notebook 1", null, null);
        NotebookResponseDTO notebook2 = new NotebookResponseDTO(UUID.randomUUID(), null, "📓", "Notebook 2", null, null);

        when(notebookService.getAllUserNotebooks()).thenReturn(List.of(notebook1, notebook2));

        mockMvc
                .perform(get("/notebooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Notebook 1"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should create notebook successfully")
    void createNotebook_shouldCreateSuccessfully() throws Exception {
        NotebookCreationDTO dto = new NotebookCreationDTO("📓", "New Notebook");
        NotebookResponseDTO response = new NotebookResponseDTO(
                UUID.randomUUID(),
                null,
                "📓",
                "New Notebook",
                null,
                null
        );

        when(notebookService.createNotebook(any())).thenReturn(response);

        mockMvc
                .perform(post("/notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Notebook"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 400 when creating notebook with blank title")
    void createNotebook_shouldReturn400WhenTitleBlank() throws Exception {
        NotebookCreationDTO dto = new NotebookCreationDTO("📓", "");

        mockMvc
                .perform(post("/notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should update notebook successfully when owner")
    void updateNotebook_shouldUpdateWhenOwner() throws Exception {
        UUID notebookId = UUID.randomUUID();
        NotebookUpdateDTO dto = new NotebookUpdateDTO(notebookId.toString(), "📒", "Updated Title");
        NotebookResponseDTO response = new NotebookResponseDTO(notebookId, null, "📒", "Updated Title", null, null);

        when(notebookOwnershipService.isUserNotebookOwner(notebookId)).thenReturn(true);
        when(notebookService.updateNotebook(any())).thenReturn(response);

        mockMvc
                .perform(put("/notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 403 when updating notebook without ownership")
    void updateNotebook_shouldReturn403WhenNotOwner() throws Exception {
        UUID notebookId = UUID.randomUUID();
        NotebookUpdateDTO dto = new NotebookUpdateDTO(notebookId.toString(), "📒", "Updated Title");

        when(notebookOwnershipService.isUserNotebookOwner(notebookId)).thenReturn(false);

        mockMvc
                .perform(put("/notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 404 when updating non-existent notebook")
    void updateNotebook_shouldReturn404WhenNotFound() throws Exception {
        UUID notebookId = UUID.randomUUID();
        NotebookUpdateDTO dto = new NotebookUpdateDTO(notebookId.toString(), "📒", "Updated Title");

        when(notebookOwnershipService.isUserNotebookOwner(notebookId)).thenReturn(true);
        when(notebookService.updateNotebook(any())).thenReturn(null);

        mockMvc
                .perform(put("/notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should delete notebook successfully when owner")
    void deleteNotebook_shouldDeleteWhenOwner() throws Exception {
        UUID notebookId = UUID.randomUUID();
        NotebookUpdateDTO dto = new NotebookUpdateDTO(notebookId.toString(), null, null);
        NotebookResponseDTO response = new NotebookResponseDTO(notebookId, null, null, "Deleted Notebook", null, null);

        when(notebookOwnershipService.isUserNotebookOwner(notebookId)).thenReturn(true);
        when(notebookService.deleteNotebook(any())).thenReturn(response);

        mockMvc
                .perform(delete("/notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 403 when deleting notebook without ownership")
    void deleteNotebook_shouldReturn403WhenNotOwner() throws Exception {
        UUID notebookId = UUID.randomUUID();
        NotebookUpdateDTO dto = new NotebookUpdateDTO(notebookId.toString(), null, null);

        when(notebookOwnershipService.isUserNotebookOwner(notebookId)).thenReturn(false);

        mockMvc
                .perform(delete("/notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 404 when deleting non-existent notebook")
    void deleteNotebook_shouldReturn404WhenNotFound() throws Exception {
        UUID notebookId = UUID.randomUUID();
        NotebookUpdateDTO dto = new NotebookUpdateDTO(notebookId.toString(), null, null);

        when(notebookOwnershipService.isUserNotebookOwner(notebookId)).thenReturn(true);
        when(notebookService.deleteNotebook(any())).thenReturn(null);

        mockMvc
                .perform(delete("/notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }
}
