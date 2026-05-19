package com.escruta.core.integration;

import com.escruta.core.dtos.LoginUserDto;
import com.escruta.core.dtos.RegisterUserDto;
import com.escruta.core.dtos.notebook.NotebookCreationDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Integration Tests - User Journey")
class UserJourneyIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String authToken;
    private static String userEmail;

    @Test
    @Order(1)
    @DisplayName("1. Register new user successfully")
    void step1_registerUser() throws Exception {
        userEmail = "integration-test-" + System.currentTimeMillis() + "@example.com";

        RegisterUserDto dto = new RegisterUserDto();
        dto.setEmail(userEmail);
        dto.setPassword("Password123");
        dto.setName("Integration Test User");

        MvcResult result = mockMvc
                .perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        authToken = response.get("token").asText();

        assertThat(authToken).isNotNull().isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("2. Introspect token and verify it's active")
    void step2_introspectToken() throws Exception {
        mockMvc
                .perform(post("/introspect").param("token", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sub").exists());
    }

    @Test
    @Order(3)
    @DisplayName("3. Get current user profile")
    void step3_getUserProfile() throws Exception {
        mockMvc
                .perform(get("/users/me").header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(userEmail))
                .andExpect(jsonPath("$.name").value("Integration Test User"));
    }

    @Test
    @Order(4)
    @DisplayName("4. Create a notebook")
    void step4_createNotebook() throws Exception {
        NotebookCreationDTO dto = new NotebookCreationDTO("📓", "My First Notebook");

        mockMvc
                .perform(post("/notebooks")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My First Notebook"));
    }

    @Test
    @Order(5)
    @DisplayName("5. List user notebooks")
    void step5_listNotebooks() throws Exception {
        mockMvc
                .perform(get("/notebooks").header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("My First Notebook"));
    }

    @Test
    @Order(6)
    @DisplayName("6. Change user name")
    void step6_changeName() throws Exception {
        mockMvc
                .perform(post("/users/change-name")
                        .header("Authorization", "Bearer " + authToken)
                        .param("newName", "Updated Name"))
                .andExpect(status().isOk());

        mockMvc
                .perform(get("/users/me").header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    @Order(7)
    @DisplayName("7. Access protected endpoint without token should fail")
    void step7_accessWithoutTokenShouldFail() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    @DisplayName("8. Login with valid credentials")
    void step8_loginWithValidCredentials() throws Exception {
        LoginUserDto dto = new LoginUserDto();
        dto.setEmail(userEmail);
        dto.setPassword("Password123");

        mockMvc
                .perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @Order(9)
    @DisplayName("9. Login with invalid credentials should fail")
    void step9_loginWithInvalidCredentialsShouldFail() throws Exception {
        LoginUserDto dto = new LoginUserDto();
        dto.setEmail(userEmail);
        dto.setPassword("WrongPassword123");

        mockMvc
                .perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }
}
