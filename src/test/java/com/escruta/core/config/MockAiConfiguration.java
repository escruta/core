package com.escruta.core.config;

import org.springframework.ai.chat.model.ChatModel;
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
}
