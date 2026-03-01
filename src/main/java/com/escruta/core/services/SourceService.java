package com.escruta.core.services;

import com.escruta.core.dtos.source.SourceCreationDTO;
import com.escruta.core.dtos.source.SourceFileCreationDTO;
import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.dtos.source.SourceTextCreationDTO;
import com.escruta.core.dtos.source.SourceUpdateDTO;
import com.escruta.core.dtos.source.SourceWithContentDTO;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.mappers.SourceMapper;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class SourceService {
    private final SourceRepository sourceRepository;
    private final NotebookRepository notebookRepository;
    private final SourceMapper sourceMapper;
    private final RetrievalService retrievalService;
    private final ChatModel chatModel;
    private final ExtractorService extractorService;
    private final AsyncVectorIndexingService asyncVectorIndexingService;

    public boolean hasNoSources(UUID notebookId) {
        return !sourceRepository.existsByNotebookId(notebookId);
    }

    public List<SourceResponseDTO> getSources(UUID notebookId) {
        return sourceRepository.findByNotebookId(notebookId);
    }

    public SourceWithContentDTO getSource(UUID notebookId, UUID sourceId) {
        Optional<Source> source = sourceRepository.findById(sourceId);
        if (source.isEmpty() || !notebookRepository.existsById(notebookId)) {
            return null;
        }
        return source.map(SourceWithContentDTO::new).orElse(null);
    }

    @Transactional
    public SourceWithContentDTO addSource(UUID notebookId, SourceCreationDTO newSourceDto) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);

        if (notebookOptional.isEmpty()) {
            throw new EntityNotFoundException("Notebook not found");
        }

        Source source = sourceMapper.toSource(newSourceDto, notebookOptional.get(), "");
        source.setTitle(newSourceDto.link());
        source.setStatus(SourceStatus.PENDING);
        source = sourceRepository.save(source);

        final UUID finalSourceId = source.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        var response = extractorService.extractMarkdown(newSourceDto.link());

                        Source updatedSource = sourceRepository.findById(finalSourceId).orElseThrow();
                        asyncVectorIndexingService.indexSourceInVectorStore(
                                notebookId,
                                finalSourceId,
                                response.title(),
                                newSourceDto.link(),
                                response.content()
                        );

                        updatedSource.setTitle(response.title());
                        updatedSource.setContent(response.content());
                        updatedSource.setStatus(SourceStatus.READY);
                        sourceRepository.save(updatedSource);
                    } catch (Exception e) {
                        sourceRepository.findById(finalSourceId).ifPresent(s -> {
                            s.setStatus(SourceStatus.FAILED);
                            sourceRepository.save(s);
                        });
                    }
                });
            }
        });

        return new SourceWithContentDTO(source);
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
            MultipartFile file
    ) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);

        if (!extractorService.isSupportedFileType(file.getContentType())) {
            throw new RuntimeException("Unsupported file type: " + file.getContentType());
        }

        if (notebookOptional.isEmpty()) {
            throw new EntityNotFoundException("Notebook not found");
        }

        Source source = sourceMapper.toSource(newSourceDto, notebookOptional.get(), "");
        source.setStatus(SourceStatus.PENDING);
        source = sourceRepository.save(source);

        final UUID finalSourceId = source.getId();
        final byte[] fileBytes;
        final String filename;
        try {
            fileBytes = file.getBytes();
            filename = file.getOriginalFilename();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read file bytes: " + e.getMessage(), e);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        var response = extractorService.extractMarkdown(fileBytes, filename);

                        Source updatedSource = sourceRepository.findById(finalSourceId).orElseThrow();
                        asyncVectorIndexingService.indexSourceInVectorStore(
                                notebookId,
                                finalSourceId,
                                updatedSource.getTitle(),
                                updatedSource.getLink(),
                                response.content()
                        );

                        updatedSource.setTitle(response.title());
                        updatedSource.setContent(response.content());
                        updatedSource.setStatus(SourceStatus.READY);
                        sourceRepository.save(updatedSource);
                    } catch (Exception e) {
                        sourceRepository.findById(finalSourceId).ifPresent(s -> {
                            s.setStatus(SourceStatus.FAILED);
                            sourceRepository.save(s);
                        });
                    }
                });
            }
        });

        return new SourceWithContentDTO(source);
    }

    @Transactional
    public SourceWithContentDTO addSourceFromText(UUID notebookId, SourceTextCreationDTO newSourceDto) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);

        if (notebookOptional.isEmpty()) {
            throw new EntityNotFoundException("Notebook not found");
        }

        Source source = sourceMapper.toSource(newSourceDto, notebookOptional.get());
        source.setStatus(SourceStatus.PENDING);
        source = sourceRepository.save(source);

        final UUID finalSourceId = source.getId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        Source updatedSource = sourceRepository.findById(finalSourceId).orElseThrow();
                        asyncVectorIndexingService.indexSourceInVectorStore(
                                notebookId,
                                finalSourceId,
                                updatedSource.getTitle(),
                                null,
                                updatedSource.getContent()
                        );

                        updatedSource.setStatus(SourceStatus.READY);
                        sourceRepository.save(updatedSource);
                    } catch (Exception e) {
                        sourceRepository.findById(finalSourceId).ifPresent(s -> {
                            s.setStatus(SourceStatus.FAILED);
                            sourceRepository.save(s);
                        });
                    }
                });
            }
        });

        return new SourceWithContentDTO(source);
    }

    private static Prompt getPrompt(Source source) {
        String systemPrompt = """
                You are an expert content summarizer. Your task is to create a concise summary of the provided content.
                The summary should be 2-3 sentences that capture the essential information and main points.
                Focus on the key concepts, findings, or conclusions presented in the content.
                Use **bold** for key terms and *italic* for emphasis (sparingly).
                """;

        UserMessage userMessage = new UserMessage(source.getContent());
        return new Prompt(List.of(new SystemMessage(systemPrompt), userMessage));
    }

    public String generateSummary(UUID notebookId, UUID sourceId) {
        Optional<Source> sourceOptional = sourceRepository.findById(sourceId);
        if (sourceOptional.isEmpty()) {
            throw new EntityNotFoundException("Source not found with id: " + sourceId);
        }

        Source source = sourceOptional.get();
        if (!source.getNotebook().getId().equals(notebookId)) {
            throw new SecurityException("Source does not belong to this notebook.");
        }

        source.setSummary(null);
        sourceRepository.save(source);

        Prompt prompt = getPrompt(source);
        var response = chatModel.call(prompt);
        String summary = response.getResult().getOutput().getText();

        source.setSummary(summary);
        sourceRepository.save(source);
        return summary;
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
