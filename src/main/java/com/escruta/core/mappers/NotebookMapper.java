package com.escruta.core.mappers;

import com.escruta.core.dtos.notebook.NotebookCreationDTO;
import com.escruta.core.dtos.notebook.NotebookUpdateDTO;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.User;
import org.springframework.stereotype.Component;

@Component
public class NotebookMapper {
    public Notebook toNotebook(NotebookCreationDTO dto, User user) {
        Notebook notebook = new Notebook();
        notebook.setUser(user);
        notebook.setIcon(dto.icon());
        notebook.setTitle(dto.title());
        return notebook;
    }

    public void updateNotebookFromDto(NotebookUpdateDTO dto, Notebook notebook) {
        if (dto.icon() != null)
            notebook.setIcon(dto.icon());
        if (dto.title() != null)
            notebook.setTitle(dto.title());
    }
}
