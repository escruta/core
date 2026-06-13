package com.escruta.core.services;

import com.escruta.core.dtos.SearchResponse;
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

import java.net.http.HttpClient;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final RestClient.Builder restClientBuilder;
    private RestClient restClient;

    @Value("${services.search.base-url}")
    private String baseUrl;

    @Value("${services.search.api-key}")
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
        return fetchContent(body);
    }

    public SearchResponse search(String query) {
        return search(query, 10);
    }

    private SearchResponse fetchContent(MultiValueMap<String, Object> body) {
        return restClient
                .post()
                .uri("/search")
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
                .body(SearchResponse.class);
    }
}
