package com.escruta.core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.qdrant.client.QdrantClient;

@SpringBootTest
@ActiveProfiles("test")
class EscrutaCoreTests {
    @MockitoBean
    private QdrantClient qdrantClient;

    @Test
    void contextLoads() {
    }
}
