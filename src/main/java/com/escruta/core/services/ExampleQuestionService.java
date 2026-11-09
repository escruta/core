package com.escruta.core.services;

import com.escruta.core.configs.CacheConfiguration;
import com.escruta.core.dtos.ExampleQuestions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExampleQuestionService {
    private static final int DOCUMENT_LIMIT = 15;
    private static final int MAX_QUESTIONS = 3;

    private final NotebookContextService notebookContextService;
    private final ChatModel chatModel;
    private ChatClient chatClient;

    @PostConstruct
    void init() {
        this.chatClient = ChatClient.create(chatModel);
    }

    @Cacheable(cacheNames = CacheConfiguration.EXAMPLE_QUESTIONS, key = "#notebookId.toString()")
    public List<String> getExampleQuestions(UUID notebookId) {
        String context = notebookContextService.getContext(notebookId, DOCUMENT_LIMIT);
        if (context.isBlank()) {
            return List.of();
        }

        ExampleQuestions exampleQuestions = chatClient.prompt().user("""
                You are an expert tutor. Based on the provided materials, generate exactly 3 questions that help a student understand the core concepts and the central theme of this subject.
                
                RULES:
                - Focus on **FUNDAMENTAL CONCEPTS** and key ideas that someone learning this topic must understand
                - Ask about **DEFINITIONS**, **MECHANISMS**, **RELATIONSHIPS** between concepts, or **CAUSE-EFFECT**
                - Questions must be about the **SUBJECT MATTER** itself, not about the text
                - Do NOT mention "article", "document", "text", "source", or "Wikipedia"
                - Do NOT ask what the topic is, what is covered, or ask for lists/summaries
                - Use "why", "how", or "what is the relationship between" type questions
                - Each question must be answerable using ONLY the provided information
                - Make questions specific enough to require understanding, not vague general questions
                
                BAD examples:
                - "What is the main topic?" (too vague)
                - "What does the text say about X?" (about the text)
                - "List the types of X" (just listing)
                
                GOOD examples:
                - "Why does X happen when Y occurs?" (mechanism/cause-effect)
                - "How is X different from Y?" (conceptual distinction)
                - "What is the relationship between X and Y?" (interconnection)
                
                MATERIALS:
                %s
                """.formatted(context)).call().entity(ExampleQuestions.class);

        if (exampleQuestions == null || exampleQuestions.questions() == null) {
            return List.of();
        }

        return exampleQuestions
                .questions()
                .stream()
                .filter(question -> question != null && !question.isBlank())
                .limit(MAX_QUESTIONS)
                .toList();
    }
}
