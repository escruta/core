package com.escruta.core.integration;

import com.escruta.core.entities.Note;
import com.escruta.core.entities.NoteGroup;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.User;
import com.escruta.core.repositories.NoteGroupRepository;
import com.escruta.core.repositories.NoteRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Note group repository integration tests")
class NoteGroupIntegrationTest {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotebookRepository notebookRepository;
    @Autowired
    private NoteGroupRepository noteGroupRepository;
    @Autowired
    private NoteRepository noteRepository;

    @Test
    @DisplayName("Notes can be grouped and remain after the group is deleted")
    void noteGroups_shouldPersistAndUngroupNotesOnDelete() {
        User user = new User();
        user.setName("Note Group Test");
        user.setEmail("note-group-" + UUID.randomUUID() + "@example.com");
        user = userRepository.save(user);

        Notebook notebook = new Notebook();
        notebook.setUser(user);
        notebook.setTitle("Note Group Notebook");
        notebook = notebookRepository.save(notebook);

        NoteGroup group = new NoteGroup();
        group.setNotebook(notebook);
        group.setTitle("Ideas");
        group = noteGroupRepository.save(group);

        Note note = new Note();
        note.setNotebook(notebook);
        note.setNoteGroup(group);
        note.setTitle("Grouped note");
        note.setContent("Content");
        note = noteRepository.save(note);

        assertThat(noteGroupRepository.findByNotebookIdOrderByCreatedAtDesc(notebook.getId()))
                .extracting(NoteGroup::getId)
                .containsExactly(group.getId());
        assertThat(noteGroupRepository.findByIdAndNotebookId(group.getId(), notebook.getId()))
                .get()
                .extracting(NoteGroup::getId)
                .isEqualTo(group.getId());
        assertThat(noteRepository.findByNotebookIdAndNoteGroupIdAndNotebook_UserId(
                notebook.getId(),
                group.getId(),
                user.getId()
        )).extracting(Note::getId).containsExactly(note.getId());

        noteGroupRepository.delete(group);
        noteGroupRepository.flush();

        assertThat(noteRepository.findById(note.getId())).isPresent().get().extracting(Note::getNoteGroup).isNull();
    }
}
