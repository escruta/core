package com.escruta.core.services;

import com.escruta.core.dtos.source.SourceCreationDTO;
import com.escruta.core.dtos.source.SourceFileCreationDTO;
import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.dtos.source.SourceTextCreationDTO;
import com.escruta.core.dtos.source.SourceUpdateDTO;
import com.escruta.core.dtos.source.SourceWithContentDTO;
import com.escruta.core.dtos.tools.JobStartedResponse;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.SourceJob;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.mappers.SourceMapper;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import com.escruta.core.events.SourceDeletedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SourceService {
    private final SourceRepository sourceRepository;
    private final NotebookRepository notebookRepository;
    private final SourceMapper sourceMapper;
    private final HelperService helperService;
    private final SourceJobService sourceJobService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${services.source-uploads.dir}")
    private String uploadDir;

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

        sourceJobService.startExtractJob(source, null, null);

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
                sourceRepository.deleteById(sourceId);
                eventPublisher.publishEvent(new SourceDeletedEvent(this, sourceId));
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

        if (!helperService.isSupportedFileType(file.getContentType())) {
            throw new RuntimeException("Unsupported file type: " + file.getContentType());
        }

        if (notebookOptional.isEmpty()) {
            throw new EntityNotFoundException("Notebook not found");
        }

        Source source = sourceMapper.toSource(newSourceDto, notebookOptional.get(), "");
        source.setStatus(SourceStatus.PENDING);
        source = sourceRepository.save(source);

        String fileName = file.getOriginalFilename();
        Path tempFile = writeUploadToTemp(file);
        sourceJobService.startExtractJob(source, tempFile.toString(), fileName);

        return new SourceWithContentDTO(source);
    }

    private Path writeUploadToTemp(MultipartFile file) {
        try {
            Files.createDirectories(Path.of(uploadDir));
            Path temp = Files.createTempFile(Path.of(uploadDir), "upload-", ".bin");
            file.transferTo(temp);
            return temp;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
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

        sourceJobService.startExtractJob(source, null, null);

        return new SourceWithContentDTO(source);
    }

    @Transactional
    public JobStartedResponse generateSummary(UUID notebookId, UUID sourceId) {
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

        SourceJob job = sourceJobService.startSourceSummaryJob(source);
        return new JobStartedResponse(
                job.getId(),
                "Summary generation started. It will be published via SSE when ready."
        );
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
