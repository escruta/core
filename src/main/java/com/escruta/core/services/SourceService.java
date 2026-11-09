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
import com.escruta.core.entities.SourceGroup;
import com.escruta.core.entities.SourceJob;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.mappers.SourceMapper;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceGroupRepository;
import com.escruta.core.repositories.SourceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final SourceGroupRepository sourceGroupRepository;
    private final SourceMapper sourceMapper;
    private final HelperService helperService;
    private final SourceJobService sourceJobService;
    private final NotebookContextService notebookContextService;

    @Value("${services.source-uploads.dir}")
    private String uploadDir;

    public List<SourceResponseDTO> getSources(UUID notebookId) {
        return sourceRepository.findSummariesByNotebookId(notebookId);
    }

    public List<SourceResponseDTO> getSourcesByGroup(UUID notebookId, UUID groupId) {
        return sourceRepository.findSummariesByNotebookIdAndSourceGroupId(notebookId, groupId);
    }

    public SourceWithContentDTO getSource(UUID notebookId, UUID sourceId) {
        return sourceRepository.findByIdAndNotebookId(sourceId, notebookId).map(SourceWithContentDTO::new).orElse(null);
    }

    @Transactional
    public SourceWithContentDTO addSource(UUID notebookId, SourceCreationDTO newSourceDto) {
        Notebook notebook = notebookRepository.getReferenceById(notebookId);

        Source source = sourceMapper.toSource(newSourceDto, notebook, "");
        source.setTitle(newSourceDto.link());
        source.setStatus(SourceStatus.PENDING);
        source.setSourceGroup(resolveGroup(notebookId, newSourceDto.groupId()));
        source = sourceRepository.save(source);
        notebookRepository.touchLastActivity(notebookId);
        notebookContextService.evict();

        sourceJobService.startExtractJob(source, null, null);

        return new SourceWithContentDTO(source);
    }

    @Transactional
    public SourceResponseDTO updateSource(UUID notebookId, SourceUpdateDTO newSource) {
        Optional<Source> sourceOptional = sourceRepository.findByIdAndNotebookId(
                UUID.fromString(newSource.id()),
                notebookId
        );

        if (sourceOptional.isPresent()) {
            Source source = sourceOptional.get();
            sourceMapper.updateSourceFromDto(newSource, source);
            if (Boolean.TRUE.equals(newSource.removeGroup())) {
                source.setSourceGroup(null);
            } else if (newSource.groupId() != null) {
                SourceGroup group = resolveGroup(notebookId, newSource.groupId());
                source.setSourceGroup(group);
            }
            sourceRepository.save(source);
            notebookRepository.touchLastActivity(notebookId);
            return new SourceResponseDTO(source);
        }
        throw new SecurityException("User cannot update this source.");
    }

    @Transactional
    public SourceResponseDTO deleteSource(UUID notebookId, UUID sourceId) {
        Optional<SourceResponseDTO> sourceOptional = sourceRepository.findResponseByIdAndNotebookId(
                sourceId,
                notebookId
        );

        if (sourceOptional.isPresent()) {
            try {
                sourceRepository.deleteById(sourceId);
                notebookRepository.touchLastActivity(notebookId);
                notebookContextService.evict();
                return sourceOptional.get();
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
        if (!helperService.isSupportedFileType(file.getContentType(), file.getOriginalFilename())) {
            throw new RuntimeException("Unsupported file type: " + file.getContentType());
        }

        Notebook notebook = notebookRepository.getReferenceById(notebookId);
        Source source = sourceMapper.toSource(newSourceDto, notebook, "");
        source.setStatus(SourceStatus.PENDING);
        source.setSourceGroup(resolveGroup(notebookId, newSourceDto.groupId()));
        source = sourceRepository.save(source);
        notebookRepository.touchLastActivity(notebookId);
        notebookContextService.evict();

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
        Notebook notebook = notebookRepository.getReferenceById(notebookId);

        Source source = sourceMapper.toSource(newSourceDto, notebook);
        source.setStatus(SourceStatus.PENDING);
        source.setSourceGroup(resolveGroup(notebookId, newSourceDto.groupId()));
        source = sourceRepository.save(source);
        notebookRepository.touchLastActivity(notebookId);
        notebookContextService.evict();

        sourceJobService.startExtractJob(source, null, null);

        return new SourceWithContentDTO(source);
    }

    @Transactional
    public JobStartedResponse generateSummary(UUID notebookId, UUID sourceId) {
        if (!sourceRepository.existsByIdAndNotebookId(sourceId, notebookId)) {
            throw new EntityNotFoundException("Source not found with id: " + sourceId);
        }

        sourceRepository.clearSummary(sourceId);
        notebookRepository.touchLastActivity(notebookId);

        SourceJob job = sourceJobService.startSourceSummaryJob(sourceId, notebookId);
        return new JobStartedResponse(
                job.getId(),
                "Summary generation started. It will be published via SSE when ready."
        );
    }

    public String getSummary(UUID notebookId, UUID sourceId) {
        return sourceRepository.findSummaryTextByIdAndNotebookId(sourceId, notebookId).orElse("");
    }

    @Transactional
    public boolean deleteSummary(UUID notebookId, UUID sourceId) {
        int updated = sourceRepository.clearSummaryByIdAndNotebookId(sourceId, notebookId);
        if (updated == 0) {
            return false;
        }

        notebookRepository.touchLastActivity(notebookId);
        return true;
    }

    private SourceGroup resolveGroup(UUID notebookId, UUID groupId) {
        if (groupId == null) {
            return null;
        }
        return sourceGroupRepository.findByIdAndNotebookId(groupId, notebookId).orElse(null);
    }
}
