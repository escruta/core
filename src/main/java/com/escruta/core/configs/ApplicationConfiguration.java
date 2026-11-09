package com.escruta.core.configs;

import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class ApplicationConfiguration {
    /**
     * Adds a provider-specific session header to every AI request when both
     * the header name and value are configured. Some OpenAI-compatible
     * providers use it for request routing and prompt-cache reuse.
     */
    @Bean
    OpenAiHttpClientBuilderCustomizer aiSessionHeaderCustomizer(
            @Value("${services.ai.session-header:}") String headerName,
            @Value("${services.ai.session-id:}") String sessionId
    ) {
        return builder -> {
            if (!StringUtils.hasText(headerName) || !StringUtils.hasText(sessionId)) {
                return;
            }
            builder.interceptor(chain -> chain.proceed(chain
                    .request()
                    .newBuilder()
                    .header(headerName, sessionId)
                    .build()));
        };
    }
}
