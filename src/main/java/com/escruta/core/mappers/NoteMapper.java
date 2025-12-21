package com.escruta.core.mappers;

import com.escruta.core.dtos.note.NoteCreationDTO;
import com.escruta.core.dtos.note.NoteUpdateDTO;
import com.escruta.core.entities.Note;
import com.escruta.core.entities.Notebook;
import org.springframework.stereotype.Component;

@Component
public class NoteMapper {
    public Note toNote(NoteCreationDTO dto, Notebook notebook) {
        Note note = new Note();
        note.setNotebook(notebook);
        note.setIcon(dto.icon());
        note.setTitle(dto.title());
        note.setContent(dto.content());
        return note;
    }

    public void updateNoteFromDto(NoteUpdateDTO dto, Note note) {
        if (dto.icon() != null) note.setIcon(dto.icon());
        if (dto.title() != null) note.setTitle(dto.title());
        if (dto.content() != null) note.setContent(dto.content());
    }
}
