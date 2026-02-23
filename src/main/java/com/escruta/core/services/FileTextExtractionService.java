package com.escruta.core.services;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class FileTextExtractionService {
    public String extractTextFromFile(MultipartFile file) {
        try {
            var documents = getDocuments(file);

            var content = new StringBuilder();
            for (var document : documents) {
                content.append(document.getFormattedContent()).append("\n\n");
            }

            return content.toString().trim();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to extract text from file: " + t.getMessage(), t);
        }
    }

    private static List<Document> getDocuments(MultipartFile file) throws IOException {
        var resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        List<Document> documents;

        if ("application/pdf".equals(file.getContentType())) {
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            documents = pdfReader.get();
        } else {
            TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
            documents = tikaReader.get();
        }

        if (documents.isEmpty()) {
            throw new RuntimeException("No text content could be extracted from the file");
        }
        return documents;
    }

    public boolean isSupportedFileType(String contentType) {
        if (contentType == null) {
            return false;
        }

        return contentType.equals("application/pdf") || contentType.equals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document") || contentType.equals(
                "text/plain") || contentType.equals("text/markdown");
    }
}
