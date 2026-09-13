package com.escruta.core.controllers;

import com.escruta.core.entities.User;
import com.escruta.core.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UserController Tests")
class UserControllerTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @MockitoBean
    private UserService userService;

    @Test
    @WithMockUser
    @DisplayName("Should return current user when authenticated")
    void getMe_shouldReturnUserWhenAuthenticated() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setName("Test User");

        when(userService.getCurrentUser()).thenReturn(user);

        mockMvc
                .perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("Test User"));
    }

    @Test
    @WithMockUser
    @DisplayName("Should change name successfully when authenticated")
    void changeName_shouldChangeNameWhenAuthenticated() throws Exception {
        mockMvc.perform(post("/users/change-name").param("newName", "New Name")).andExpect(status().isOk());

        verify(userService).changeName("New Name");
    }

    @Test
    @WithMockUser
    @DisplayName("Should delete account successfully when authenticated")
    void deleteAccount_shouldDeleteWhenAuthenticated() throws Exception {
        mockMvc.perform(delete("/users/me")).andExpect(status().isNoContent());

        verify(userService).deleteAccount();
    }
}
