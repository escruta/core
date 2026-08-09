package com.escruta.core.services;

import com.escruta.core.dtos.ExtractorResponse;
import com.escruta.core.dtos.SearchResponse;
import com.escruta.core.dtos.SearchResult;
import com.escruta.core.repositories.SourceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HelperService {
    private final RestClient.Builder restClientBuilder;
    private final SourceRepository sourceRepository;
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

    public SearchResponse search(String query, int maxResults, UUID notebookId) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("query", query);
        body.add("max_results", String.valueOf(maxResults));
        SearchResponse response = postForm(body, "/search", SearchResponse.class);

        Set<String> existingLinks = sourceRepository
                .findLinksByNotebookId(notebookId)
                .stream()
                .map(HelperService::normalizeUrl)
                .collect(Collectors.toSet());

        if (existingLinks.isEmpty()) {
            return response;
        }

        List<SearchResult> filtered = response
                .results()
                .stream()
                .filter(result -> !existingLinks.contains(normalizeUrl(result.link())))
                .toList();
        return new SearchResponse(filtered);
    }

    private static String normalizeUrl(String url) {
        if (url == null)
            return "";
        return url.trim().replaceFirst("^(https?://)?(www\\.)?", "").replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    public ExtractorResponse extractMarkdown(File file, String filename) {
        var resource = new FileSystemResource(file) {
            @Override
            public @NonNull String getFilename() {
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
