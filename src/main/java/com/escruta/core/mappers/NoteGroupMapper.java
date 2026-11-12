package com.escruta.core.mappers;

import com.escruta.core.dtos.notegroup.NoteGroupResponseDTO;
import com.escruta.core.entities.NoteGroup;
import org.springframework.stereotype.Component;

@Component
public class NoteGroupMapper {
    public NoteGroupResponseDTO toResponseDTO(NoteGroup group) {
        if (group == null)
            return null;

        return new NoteGroupResponseDTO(group);
    }
}
