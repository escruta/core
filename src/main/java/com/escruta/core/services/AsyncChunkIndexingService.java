package com.escruta.core.services;

import com.escruta.core.entities.SourceChunk;
import com.escruta.core.repositories.SourceChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AsyncChunkIndexingService {
    private static final int CHUNK_SIZE = 2000;
    private static final int CHUNK_OVERLAP = 200;
    private static final int MAX_CHUNKS = 10000;

    private final SourceChunkRepository chunkRepository;

    @Transactional
    public void indexSource(UUID notebookId, UUID sourceId, String title, String link, String content) {
        chunkRepository.deleteBySourceId(sourceId);

        if (content == null || content.isBlank()) {
            return;
        }

        List<String> parts = split(content);
        List<SourceChunk> chunks = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            SourceChunk chunk = new SourceChunk();
            chunk.setSourceId(sourceId);
            chunk.setNotebookId(notebookId);
            chunk.setChunkIndex(i);
            chunk.setTitle(title != null ?
                    title :
                    "Untitled");
            chunk.setLink(link);
            chunk.setContent(parts.get(i));
            chunks.add(chunk);
        }

        if (!chunks.isEmpty()) {
            chunkRepository.saveAll(chunks);
        }
    }

    private List<String> split(String content) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\\n\\s*\\n");

        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current.length() + trimmed.length() + 2 > CHUNK_SIZE && !current.isEmpty()) {
                chunks.add(current.toString());
                if (chunks.size() >= MAX_CHUNKS) {
                    return chunks;
                }
                String overlap = current.length() > CHUNK_OVERLAP ?
                        current.substring(current.length() - CHUNK_OVERLAP) :
                        current.toString();
                current = new StringBuilder(overlap).append("\n\n");
            }
            if (trimmed.length() > CHUNK_SIZE) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
                for (int start = 0; start < trimmed.length(); start += CHUNK_SIZE - CHUNK_OVERLAP) {
                    int end = Math.min(start + CHUNK_SIZE, trimmed.length());
                    chunks.add(trimmed.substring(start, end));
                    if (chunks.size() >= MAX_CHUNKS) {
                        return chunks;
                    }
                }
            } else {
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                current.append(trimmed);
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }
}
