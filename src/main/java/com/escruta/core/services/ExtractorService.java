package com.escruta.core.services;

import com.escruta.core.dtos.ExtractorResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.net.http.HttpClient;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExtractorService {
    private final RestClient.Builder restClientBuilder;
    private RestClient restClient;

    @Value("${services.extractor.base-url}")
    private String baseUrl;

    @Value("${services.extractor.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    public ExtractorResponse extractMarkdown(MultipartFile file) {
        var resource = file.getResource();
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", resource);
        return fetchContent(body);
    }

    public ExtractorResponse extractMarkdown(String url) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("url", url);
        return fetchContent(body);
    }

    private ExtractorResponse fetchContent(MultiValueMap<String, Object> body) {
        return restClient
                .post()
                .uri("/extract")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("ESCRUTA_INTERNAL_API_KEY", apiKey)
                .body(body)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError, (request, response) -> {
                            throw new RuntimeException("Unauthorized: " + response.getStatusCode());
                        }
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError, (request, response) -> {
                            throw new RuntimeException("Server Error: " + response.getStatusCode());
                        }
                )
                .body(ExtractorResponse.class);
    }

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown",
            "audio/mpeg",
            "audio/wav",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint"
    );

    public boolean isSupportedFileType(String contentType) {
        return contentType != null && SUPPORTED_TYPES.contains(contentType);
    }
}
