package com.escruta.core.configs;

import com.escruta.core.services.SseNotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Configuration
public class RedisConfiguration {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        RedisSerializer<Object> json = RedisSerializer.json();
        template.setValueSerializer(json);
        template.setHashValueSerializer(json);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisMessageListenerContainer sseRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            SseNotificationService sseNotificationService,
            ObjectMapper objectMapper
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, _) -> {
                    try {
                        String json = new String(message.getBody(), StandardCharsets.UTF_8);
                        SseNotificationService.SseMessage sseMessage = objectMapper.readValue(
                                json,
                                SseNotificationService.SseMessage.class
                        );
                        sseNotificationService.publishLocal(
                                sseMessage.userId(),
                                sseMessage.eventName(),
                                sseMessage.data()
                        );
                    } catch (Exception e) {
                        // A malformed message must not stop the listener.
                    }
                }, new ChannelTopic(SseNotificationService.REDIS_CHANNEL)
        );
        return container;
    }
}
