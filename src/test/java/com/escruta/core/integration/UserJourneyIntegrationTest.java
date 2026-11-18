package com.escruta.core.integration;

import com.escruta.core.dtos.ChatRequest;
import com.escruta.core.dtos.CompleteRegistrationRequest;
import com.escruta.core.dtos.EditMessageRequest;
import com.escruta.core.dtos.RegenerateMessageRequest;
import com.escruta.core.dtos.RequestEmailCodeRequest;
import com.escruta.core.dtos.VerifyEmailCodeRequest;
import com.escruta.core.dtos.notebook.NotebookCreationDTO;
import com.escruta.core.services.EmailService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Integration Tests - User Journey")
class UserJourneyIntegrationTest {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private ChatModel chatModel;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var filterChainProxy = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filterChainProxy).build();
    }

    private static String authToken;
    private static String userEmail;
    private static String notebookId;

    private String requestCodeAndCapture(String email) throws Exception {
        mockMvc
                .perform(post("/auth/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestEmailCodeRequest(email))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendVerificationCode(
                org.mockito.ArgumentMatchers.eq(email),
                codeCaptor.capture()
        );
        String code = codeCaptor.getValue();
        assertThat(code).isNotNull().isNotEmpty();
        return code;
    }

    @Test
    @Order(1)
    @DisplayName("1. Register new user successfully")
    void step1_registerUser() throws Exception {
        userEmail = "integration-test-" + System.currentTimeMillis() + "@example.com";

        String code = requestCodeAndCapture(userEmail);

        MvcResult verifyResult = mockMvc
                .perform(post("/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailCodeRequest(userEmail, code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.verificationToken").exists())
                .andReturn();

        JsonNode verifyResponse = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        String verificationToken = verifyResponse.get("verificationToken").asString();

        MvcResult result = mockMvc
                .perform(post("/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompleteRegistrationRequest(
                                userEmail,
                                verificationToken,
                                "Integration Test User"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        authToken = response.get("token").asString();

        assertThat(authToken).isNotNull().isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("2. Introspect token and verify it's active")
    void step2_introspectToken() throws Exception {
        mockMvc
                .perform(post("/auth/introspect").param("token", authToken))
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
        NotebookCreationDTO dto = new NotebookCreationDTO("📓", "My First Notebook", null);

        MvcResult result = mockMvc
                .perform(post("/notebooks")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My First Notebook"))
                .andReturn();

        notebookId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
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

        mockMvc
                .perform(get("/notebooks/" + notebookId).header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notebookId))
                .andExpect(jsonPath("$.user.email").value(userEmail))
                .andExpect(jsonPath("$.sources").isArray());
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
    @DisplayName("8. Login existing user with code")
    void step8_loginExistingUserWithCode() throws Exception {
        String code = requestCodeAndCapture(userEmail);

        mockMvc
                .perform(post("/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailCodeRequest(userEmail, code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false))
                .andExpect(jsonPath("$.session.token").exists());
    }

    @Test
    @Order(9)
    @DisplayName("9. Verify with wrong code should fail")
    void step9_verifyWithWrongCodeShouldFail() throws Exception {
        requestCodeAndCapture(userEmail);

        mockMvc
                .perform(post("/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailCodeRequest(userEmail, "000000"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(10)
    @DisplayName("10. List active sessions through the Redis index")
    void step10_listSessions() throws Exception {
        mockMvc
                .perform(get("/sessions").header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].sessionId").exists());
    }

    @Test
    @Order(11)
    @DisplayName("11. Send a chat message without lazy initialization errors")
    void step11_sendChatMessage() throws Exception {
        var options = ChatOptions.builder().build();
        lenient().when(chatModel.getOptions()).thenReturn(options);
        lenient()
                .when(chatModel.call(any(Prompt.class)))
                .thenReturn(ChatResponse
                        .builder()
                        .generations(List.of(new Generation(new AssistantMessage("Respuesta de prueba"))))
                        .build());

        mockMvc
                .perform(post("/notebooks/" + notebookId + "/chat")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("¿Qué es esto?", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Respuesta de prueba"));
    }

    @Test
    @Order(12)
    @DisplayName("12. Stream a chat message without lazy initialization errors")
    void step12_streamChatMessage() throws Exception {
        var options = ChatOptions.builder().build();
        lenient().when(chatModel.getOptions()).thenReturn(options);
        lenient()
                .when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ChatResponse
                        .builder()
                        .generations(List.of(new Generation(new AssistantMessage("Hola desde el stream"))))
                        .build()));

        MvcResult result = mockMvc
                .perform(post("/notebooks/" + notebookId + "/chat/stream")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("Hola", null, null, null))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        assertThat(result.getResponse().getContentAsString()).contains("Hola desde el stream");
    }

    @Test
    @Order(13)
    @DisplayName("13. Stream and persist agent thinking steps and model thoughts")
    void step13_streamAndPersistAgentThinking() throws Exception {
        var options = ChatOptions.builder().build();
        lenient().when(chatModel.getOptions()).thenReturn(options);
        AssistantMessage reasoningChunk = AssistantMessage
                .builder()
                .content("")
                .properties(java.util.Map.of("reasoningContent", "Analizando las fuentes..."))
                .build();
        AssistantMessage answerChunk = AssistantMessage.builder().content("Respuesta final").build();
        lenient()
                .when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(
                        ChatResponse.builder().generations(List.of(new Generation(reasoningChunk))).build(),
                        ChatResponse.builder().generations(List.of(new Generation(answerChunk))).build()
                ));

        MvcResult result = mockMvc
                .perform(post("/notebooks/" + notebookId + "/chat/stream")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("Explica esto", null, null, null))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        String sseBody = result.getResponse().getContentAsString();
        assertThat(sseBody).contains("event:step");
        assertThat(sseBody).contains("event:thought");
        assertThat(sseBody).contains("Analizando las fuentes...");
        assertThat(sseBody).contains("Respuesta final");

        String convId = sseBody
                .lines()
                .filter(l -> l.contains("\"conversationId\""))
                .findFirst()
                .map(l -> l.replaceFirst(".*\"conversationId\"\\s*:\\s*\"([^\"]+)\".*", "$1"))
                .orElseThrow();

        mockMvc
                .perform(get("/notebooks/" + notebookId + "/conversations/" + convId).header(
                        "Authorization",
                        "Bearer " + authToken
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[1].id").isNumber())
                .andExpect(jsonPath("$.messages[1].content").value("Respuesta final"))
                .andExpect(jsonPath("$.messages[1].thinkingSteps").isArray())
                .andExpect(jsonPath("$.messages[1].thinkingSteps[0].type").value("thought"))
                .andExpect(jsonPath("$.messages[1].thinkingSteps[0].detail").value("Analizando las fuentes..."))
                .andExpect(jsonPath("$.messages[1].thinkingSteps[1].type").value("text"))
                .andExpect(jsonPath("$.messages[1].thinkingSteps[1].detail").value("Respuesta final"));
    }

    @Test
    @Order(14)
    @DisplayName("14. Edit a sent user message and regenerate an assistant response")
    void step14_editAndRegenerateMessages() throws Exception {
        var options = ChatOptions.builder().build();
        lenient().when(chatModel.getOptions()).thenReturn(options);
        lenient()
                .when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ChatResponse
                        .builder()
                        .generations(List.of(new Generation(new AssistantMessage("Primera respuesta"))))
                        .build()));

        MvcResult firstTurn = mockMvc
                .perform(post("/notebooks/" + notebookId + "/chat/stream")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest(
                                "Primera pregunta",
                                null,
                                null,
                                null
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(firstTurn)).andExpect(status().isOk());

        String firstSse = firstTurn.getResponse().getContentAsString();
        assertThat(firstSse).contains("userMessageId");
        assertThat(firstSse).contains("assistantMessageId");

        String convId = firstSse
                .lines()
                .filter(l -> l.contains("\"conversationId\""))
                .findFirst()
                .map(l -> l.replaceFirst(".*\"conversationId\"\\s*:\\s*\"([^\"]+)\".*", "$1"))
                .orElseThrow();

        lenient()
                .when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ChatResponse
                        .builder()
                        .generations(List.of(new Generation(new AssistantMessage("Segunda respuesta"))))
                        .build()));

        MvcResult secondTurn = mockMvc
                .perform(post("/notebooks/" + notebookId + "/chat/stream")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest(
                                "Segunda pregunta",
                                convId,
                                null,
                                null
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(secondTurn)).andExpect(status().isOk());

        MvcResult convRes = mockMvc
                .perform(get("/notebooks/" + notebookId + "/conversations/" + convId).header(
                        "Authorization",
                        "Bearer " + authToken
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andReturn();

        JsonNode convJson = objectMapper.readTree(convRes.getResponse().getContentAsString());
        long firstUserMsgId = convJson.get("messages").get(0).get("id").asLong();
        long secondAssistantMsgId = convJson.get("messages").get(3).get("id").asLong();

        lenient()
                .when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ChatResponse
                        .builder()
                        .generations(List.of(new Generation(new AssistantMessage("Segunda respuesta regenerada"))))
                        .build()));

        MvcResult regenResult = mockMvc
                .perform(post("/notebooks/" + notebookId + "/conversations/" + convId + "/messages/" + secondAssistantMsgId + "/regenerate/stream")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegenerateMessageRequest(null, null, null, null))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(regenResult)).andExpect(status().isOk());
        assertThat(regenResult.getResponse().getContentAsString()).contains("Segunda respuesta regenerada");

        MvcResult afterRegenRes = mockMvc
                .perform(get("/notebooks/" + notebookId + "/conversations/" + convId).header(
                        "Authorization",
                        "Bearer " + authToken
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.messages[0].content").value("Primera pregunta"))
                .andExpect(jsonPath("$.messages[1].content").value("Primera respuesta"))
                .andExpect(jsonPath("$.messages[2].content").value("Segunda pregunta"))
                .andExpect(jsonPath("$.messages[3].content").value("Segunda respuesta regenerada"))
                .andExpect(jsonPath("$.messages[3].siblingIds.length()").value(2))
                .andReturn();

        long regeneratedAssistantMsgId = objectMapper
                .readTree(afterRegenRes.getResponse().getContentAsString())
                .get("messages")
                .get(3)
                .get("id")
                .asLong();

        mockMvc
                .perform(patch("/notebooks/" + notebookId + "/conversations/" + convId + "/messages/" + secondAssistantMsgId + "/select").header(
                        "Authorization",
                        "Bearer " + authToken
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[3].content").value("Segunda respuesta"))
                .andExpect(jsonPath("$.messages[3].siblingIds.length()").value(2));

        mockMvc
                .perform(patch("/notebooks/" + notebookId + "/conversations/" + convId + "/messages/" + regeneratedAssistantMsgId + "/select").header(
                        "Authorization",
                        "Bearer " + authToken
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[3].content").value("Segunda respuesta regenerada"));

        lenient()
                .when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ChatResponse
                        .builder()
                        .generations(List.of(new Generation(new AssistantMessage("Respuesta tras editar"))))
                        .build()));

        MvcResult editResult = mockMvc
                .perform(put("/notebooks/" + notebookId + "/conversations/" + convId + "/messages/" + firstUserMsgId + "/stream")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditMessageRequest(
                                "Primera pregunta editada",
                                null,
                                null,
                                null,
                                null
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(editResult)).andExpect(status().isOk());
        assertThat(editResult.getResponse().getContentAsString()).contains("Respuesta tras editar");

        MvcResult afterEditRes = mockMvc
                .perform(get("/notebooks/" + notebookId + "/conversations/" + convId).header(
                        "Authorization",
                        "Bearer " + authToken
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].content").value("Primera pregunta editada"))
                .andExpect(jsonPath("$.messages[0].siblingIds.length()").value(2))
                .andExpect(jsonPath("$.messages[1].content").value("Respuesta tras editar"))
                .andExpect(jsonPath("$.messages[1].siblingIds.length()").value(1))
                .andReturn();

        mockMvc
                .perform(patch("/notebooks/" + notebookId + "/conversations/" + convId + "/messages/" + firstUserMsgId + "/select").header(
                        "Authorization",
                        "Bearer " + authToken
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.messages[0].content").value("Primera pregunta"))
                .andExpect(jsonPath("$.messages[0].siblingIds.length()").value(2))
                .andExpect(jsonPath("$.messages[3].content").value("Segunda respuesta regenerada"));

        JsonNode afterEditJson = objectMapper.readTree(afterEditRes.getResponse().getContentAsString());
        long assistantIdAfterEdit = afterEditJson.get("messages").get(1).get("id").asLong();

        mockMvc
                .perform(put("/notebooks/" + notebookId + "/conversations/" + convId + "/messages/" + assistantIdAfterEdit + "/stream")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditMessageRequest(
                                "Intento inválido",
                                null,
                                null,
                                null,
                                null
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc
                .perform(post("/notebooks/" + notebookId + "/conversations/" + convId + "/messages/999999/regenerate/stream")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
