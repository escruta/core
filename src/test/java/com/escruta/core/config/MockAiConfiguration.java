package com.escruta.core.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.mockito.Mockito;

@Configuration
public class MockAiConfiguration {
    @Bean
    @Primary
    public ChatModel mockChatModel() {
        return Mockito.mock(ChatModel.class);
    }

    @Bean
    @Primary
    public EmbeddingModel mockEmbeddingModel() {
        return Mockito.mock(EmbeddingModel.class);
    }

    @Bean
    @Primary
    public VectorStore mockVectorStore() {
        return Mockito.mock(VectorStore.class);
    }
}
