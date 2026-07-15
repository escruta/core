package com.escruta.core.services;

import com.escruta.core.dtos.ExtractorResponse;
import com.escruta.core.dtos.SearchResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HelperService {
    private final RestClient.Builder restClientBuilder;
    private RestClient restClient;

    @Value("${services.helper.base-url}")
    private String baseUrl;

    @Value("${services.helper.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    public SearchResponse search(String query, int maxResults) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("query", query);
        body.add("max_results", String.valueOf(maxResults));
        return postForm(body, "/search", SearchResponse.class);
    }

    public ExtractorResponse extractMarkdown(byte[] fileBytes, String filename) {
        var resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", resource);
        return postForm(body, "/extract", ExtractorResponse.class);
    }

    public ExtractorResponse extractMarkdown(String url) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("url", url);
        return postForm(body, "/extract", ExtractorResponse.class);
    }

    private <T> T postForm(MultiValueMap<String, Object> body, String uri, Class<T> responseType) {
        return restClient
                .post()
                .uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("ESCRUTA_INTERNAL_API_KEY", apiKey)
                .body(body)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError, (_, response) -> {
                            throw new RuntimeException("Unauthorized: " + response.getStatusCode());
                        }
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError, (_, response) -> {
                            throw new RuntimeException("Server Error: " + response.getStatusCode());
                        }
                )
                .body(responseType);
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
