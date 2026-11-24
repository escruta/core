package com.escruta.core.repositories;

import com.escruta.core.entities.NotebookLearningMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotebookLearningMemoryRepository extends JpaRepository<NotebookLearningMemory, UUID> {
}
