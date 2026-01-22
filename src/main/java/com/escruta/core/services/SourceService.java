package com.escruta.core.services;

import com.escruta.core.dtos.source.SourceCreationDTO;
import com.escruta.core.dtos.source.SourceFileCreationDTO;
import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.dtos.source.SourceUpdateDTO;
import com.escruta.core.dtos.source.SourceWithContentDTO;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.mappers.SourceMapper;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceRepository;
import io.github.furstenheim.CopyDown;
import org.jsoup.Jsoup;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SourceService {
    private final SourceRepository sourceRepository;
    private final NotebookRepository notebookRepository;
    private final SourceMapper sourceMapper;
    private final RetrievalService retrievalService;
    private final ChatModel chatModel;
    private final FileTextExtractionService fileTextExtractionService;
    private final AsyncVectorIndexingService asyncVectorIndexingService;

    private record WebContent(
            String title,
            String content,
            String html
    ) {
    }

    private WebContent fetchWebContent(String url) {
        try {
            var doc = Jsoup.connect(url).get();
            String title = doc.title();

            if (title.trim().isEmpty()) {
                title = doc.select("meta[property=og:title]").attr("content");
            }
            if (title.trim().isEmpty()) {
                title = generateDefaultTitle(url);
            }

            var mainElement = extractMainElement(doc);
            String textContent = mainElement.text();
            String htmlContent = mainElement.html();

            return new WebContent(title.trim(), textContent, htmlContent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch content from URL: " + url, e);
        }
    }

    private org.jsoup.nodes.Element extractMainElement(org.jsoup.nodes.Document doc) {
        doc
                .select("nav, header, footer, aside, .sidebar, .menu, .navigation, " + ".advertisement, .ads, .ad, script, style, noscript, " + ".footer, .header, .nav, #nav, #header, #footer, #sidebar, " + ".social, .share, .comments, .related, .recommended")
                .remove();

        String[] mainSelectors = {"article", "main", "[role=main]", ".post-content", ".article-content", ".entry-content", ".content", "#content", "#mw-content-text", ".mw-parser-output"};

        for (String selector : mainSelectors) {
            var element = doc.selectFirst(selector);
            if (element != null && element.text().length() > 200) {
                return element;
            }
        }

        return doc.body();
    }

    private String convertHtmlToMarkdown(String htmlContent) {
        CopyDown converter = new CopyDown();
        return converter.convert(htmlContent);
    }

    private String cleanContentWithAI(String content) {
        String systemPrompt = """
                Clean and improve this Markdown content.
                
                RULES:
                - Remove any leftover navigation, menus, or boilerplate text
                - Keep all the important information intact
                - Fix formatting issues
                - Output ONLY the cleaned Markdown, nothing else
                """;

        try {
            UserMessage userMessage = new UserMessage(content);
            Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), userMessage));
            return chatModel.call(prompt).getResult().getOutput().getText();
        } catch (Exception e) {
            return content;
        }
    }

    private String generateDefaultTitle(String url) {
        try {
            String domain = new java.net.URI(url).getHost();
            return "Content from " + domain.replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return "Untitled Web Content";
        }
    }

    public boolean hasSources(UUID notebookId) {
        return sourceRepository.existsByNotebookId(notebookId);
    }

    public List<SourceResponseDTO> getSources(UUID notebookId) {
        return sourceRepository.findByNotebookId(notebookId).stream().map(SourceResponseDTO::new).toList();
    }

    public SourceWithContentDTO getSource(UUID notebookId, UUID sourceId) {
        Optional<Source> source = sourceRepository.findById(sourceId);
        if (source.isEmpty() || !notebookRepository.existsById(notebookId)) {
            return null;
        }
        return source.map(SourceWithContentDTO::new).orElse(null);
    }

    @Transactional
    public SourceWithContentDTO addSource(UUID notebookId, SourceCreationDTO newSourceDto, boolean aiConverter) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);

        try {
            WebContent webContent = fetchWebContent(newSourceDto.link());
            String content = convertHtmlToMarkdown(webContent.html());

            if (aiConverter) {
                content = cleanContentWithAI(content);
            }

            assert notebookOptional.isPresent();
            Source source = sourceMapper.toSource(newSourceDto, notebookOptional.get(), content, aiConverter);
            if (source.getTitle() == null || source.getTitle().trim().isEmpty()) {
                source.setTitle(webContent.title());
            }

            source = sourceRepository.save(source);

            asyncVectorIndexingService.indexSourceInVectorStore(
                    notebookId,
                    source.getId(),
                    source.getTitle(),
                    source.getLink(),
                    content
            );

            return new SourceWithContentDTO(source);

        } catch (Exception e) {
            throw new RuntimeException("Error while adding the source: " + e.getMessage(), e);
        }
    }

    public SourceResponseDTO updateSource(UUID notebookId, SourceUpdateDTO newSource) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);
        Optional<Source> sourceOptional = sourceRepository.findById(UUID.fromString(newSource.id()));

        if (notebookOptional.isPresent() && sourceOptional.isPresent()) {
            Source source = sourceOptional.get();
            sourceMapper.updateSourceFromDto(newSource, source);
            sourceRepository.save(source);
            return new SourceResponseDTO(source);
        }
        throw new SecurityException("User cannot update this source.");
    }

    @Transactional
    public SourceResponseDTO deleteSource(UUID notebookId, UUID sourceId) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);
        Optional<Source> sourceOptional = sourceRepository.findById(sourceId);

        if (notebookOptional.isPresent() && sourceOptional.isPresent()) {
            Source sourceToDelete = sourceOptional.get();
            try {
                retrievalService.deleteIndexedSource(sourceId);
                sourceRepository.deleteById(sourceId);
                return new SourceResponseDTO(sourceToDelete);
            } catch (Exception e) {
                throw new RuntimeException("Error while deleting the source: " + e.getMessage(), e);
            }
        }
        throw new SecurityException("User cannot delete this source.");
    }

    @Transactional
    public SourceWithContentDTO addSourceFromFile(
            UUID notebookId,
            SourceFileCreationDTO newSourceDto,
            MultipartFile file,
            boolean aiConverter
    ) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);

        if (!fileTextExtractionService.isSupportedFileType(file.getContentType())) {
            throw new RuntimeException("Unsupported file type: " + file.getContentType());
        }

        String content;
        try {
            content = fileTextExtractionService.extractTextFromFile(file);
            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException("No text content could be extracted from the file");
            }

            if (aiConverter) {
                content = cleanContentWithAI(content);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error processing file: " + e.getMessage(), e);
        }

        assert notebookOptional.isPresent();
        Source source = sourceMapper.toSource(newSourceDto, notebookOptional.get(), content, aiConverter);
        source = sourceRepository.save(source);

        asyncVectorIndexingService.indexSourceInVectorStore(
                notebookId,
                source.getId(),
                source.getTitle(),
                source.getLink(),
                content
        );

        return new SourceWithContentDTO(source);
    }

    private void generateAndSetSummary(Source source) {
        try {
            Prompt prompt = getPrompt(source);
            var response = chatModel.call(prompt);
            String summary = response.getResult().getOutput().getText();

            source.setSummary(summary);
            sourceRepository.save(source);
        } catch (Exception ignored) {
        }
    }

    private static Prompt getPrompt(Source source) {
        String systemPrompt = """
                You are an expert content summarizer. Your task is to create a concise summary of the provided content.
                The summary should be 2-3 sentences that capture the essential information and main points.
                Focus on the key concepts, findings, or conclusions presented in the content.
                """;

        UserMessage userMessage = new UserMessage(source.getContent());
        return new Prompt(List.of(new SystemMessage(systemPrompt), userMessage));
    }

    public String generateSummary(UUID notebookId, UUID sourceId) {
        Optional<Source> sourceOptional = sourceRepository.findById(sourceId);
        if (sourceOptional.isEmpty()) {
            return null;
        }

        Source source = sourceOptional.get();
        if (!source.getNotebook().getId().equals(notebookId)) {
            throw new SecurityException("Source does not belong to this notebook.");
        }

        generateAndSetSummary(source);
        return source.getSummary();
    }

    public String getSummary(UUID notebookId, UUID sourceId) {
        Optional<Source> sourceOptional = sourceRepository.findById(sourceId);
        if (sourceOptional.isEmpty()) {
            return "";
        }

        Source source = sourceOptional.get();
        if (!source.getNotebook().getId().equals(notebookId)) {
            throw new SecurityException("Source does not belong to this notebook.");
        }

        return source.getSummary() != null ?
                source.getSummary() :
                "";
    }

    public boolean deleteSummary(UUID notebookId, UUID sourceId) {
        Optional<Source> sourceOptional = sourceRepository.findById(sourceId);
        if (sourceOptional.isEmpty()) {
            return false;
        }

        Source source = sourceOptional.get();
        if (!source.getNotebook().getId().equals(notebookId)) {
            throw new SecurityException("Source does not belong to this notebook.");
        }

        source.setSummary(null);
        sourceRepository.save(source);
        return true;
    }
}
