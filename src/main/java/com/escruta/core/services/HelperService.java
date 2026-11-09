package com.escruta.core.services;

import com.escruta.core.dtos.ExtractorResponse;
import com.escruta.core.dtos.SearchResponse;
import com.escruta.core.dtos.SearchResult;
import com.escruta.core.repositories.SourceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HelperService {
    private final RestClient.Builder restClientBuilder;
    private final SourceRepository sourceRepository;
    private final ExecutorService httpExecutor = Executors.newThreadPerTaskExecutor(Thread
            .ofVirtual()
            .name("helper-http-", 0)
            .factory());
    private RestClient restClient;

    @Value("${services.helper.base-url}")
    private String baseUrl;

    @Value("${services.helper.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        var httpClient = HttpClient
                .newBuilder()
                .executor(httpExecutor)
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(120));
        this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @PreDestroy
    public void shutdown() {
        httpExecutor.shutdown();
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

    private static final java.util.regex.Pattern URL_SCHEME_AND_WWW = java.util.regex.Pattern.compile(
            "^(https?://)?(www\\.)?");
    private static final java.util.regex.Pattern URL_TRAILING_SLASHES = java.util.regex.Pattern.compile("/+$");

    private static String normalizeUrl(String url) {
        if (url == null)
            return "";
        String withoutScheme = URL_SCHEME_AND_WWW.matcher(url.trim()).replaceFirst("");
        return URL_TRAILING_SLASHES.matcher(withoutScheme).replaceAll("").toLowerCase(Locale.ROOT);
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
                            throw new RuntimeException("Helper request rejected: " + response.getStatusCode() + " " + readErrorBody(
                                    response));
                        }
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError, (_, response) -> {
                            throw new RuntimeException("Helper request failed: " + response.getStatusCode() + " " + readErrorBody(
                                    response));
                        }
                )
                .body(responseType);
    }

    private static String readErrorBody(ClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "application/pdf",
            "application/x-pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "text/csv",
            "text/html",
            "audio/mpeg",
            "audio/mp3",
            "audio/wav",
            "audio/x-wav",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint"
    );

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".pdf",
            ".doc",
            ".docx",
            ".txt",
            ".md",
            ".markdown",
            ".csv",
            ".html",
            ".htm",
            ".mp3",
            ".wav",
            ".xls",
            ".xlsx",
            ".ppt",
            ".pptx"
    );

    public boolean isSupportedFileType(String contentType, String filename) {
        if (contentType != null) {
            String normalized = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (SUPPORTED_TYPES.contains(normalized)) {
                return true;
            }
        }
        if (filename != null) {
            int dotIndex = filename.lastIndexOf('.');
            if (dotIndex >= 0) {
                String ext = filename.substring(dotIndex).toLowerCase(Locale.ROOT);
                return SUPPORTED_EXTENSIONS.contains(ext);
            }
        }
        return false;
    }
}
