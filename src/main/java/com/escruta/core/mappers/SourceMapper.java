package com.escruta.core.mappers;

import com.escruta.core.dtos.source.SourceCreationDTO;
import com.escruta.core.dtos.source.SourceFileCreationDTO;
import com.escruta.core.dtos.source.SourceTextCreationDTO;
import com.escruta.core.dtos.source.SourceUpdateDTO;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.enums.SourceType;
import org.springframework.stereotype.Component;

@Component
public class SourceMapper {
    public Source toSource(SourceCreationDTO dto, Notebook notebook, String content) {
        Source source = new Source();
        source.setNotebook(notebook);
        source.setLink(dto.link());
        source.setContent(content);
        if (dto.link() != null && dto
                .link()
                .matches("^(https?://)?(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/).*$")) {
            source.setType(SourceType.YOUTUBE_VIDEO);
        } else {
            source.setType(SourceType.WEBSITE);
        }
        return source;
    }

    public Source toSource(SourceFileCreationDTO dto, Notebook notebook, String content) {
        Source source = new Source();
        source.setNotebook(notebook);
        source.setIcon(dto.icon());
        source.setTitle(dto.title());
        source.setContent(content);
        source.setType(SourceType.FILE);
        return source;
    }

    public Source toSource(SourceTextCreationDTO dto, Notebook notebook) {
        Source source = new Source();
        source.setNotebook(notebook);
        source.setIcon(dto.icon());
        source.setTitle(dto.title());
        source.setContent(dto.content());
        source.setType(SourceType.TEXT);
        return source;
    }

    public void updateSourceFromDto(SourceUpdateDTO dto, Source source) {
        if (dto.icon() != null)
            source.setIcon(dto.icon());
        if (dto.title() != null)
            source.setTitle(dto.title());
    }
}
