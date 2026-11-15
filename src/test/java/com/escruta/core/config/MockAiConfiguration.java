package com.escruta.core.config;

import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MockAiConfiguration {
    @Bean
    @Primary
    public ChatModel mockChatModel() {
        ChatModel model = Mockito.mock(ChatModel.class);
        Mockito.when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        return model;
    }
}
