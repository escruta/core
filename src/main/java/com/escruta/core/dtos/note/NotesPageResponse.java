package com.escruta.core.dtos.note;

import java.util.List;

public record NotesPageResponse(
        List<NoteResponseDTO> notes,
        long total,
        boolean hasMore
) {
}
