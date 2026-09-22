package com.escruta.core.mappers;

import com.escruta.core.dtos.sourcegroup.SourceGroupResponseDTO;
import com.escruta.core.entities.SourceGroup;
import org.springframework.stereotype.Component;

@Component
public class SourceGroupMapper {
    public SourceGroupResponseDTO toResponseDTO(SourceGroup group) {
        if (group == null)
            return null;

        return new SourceGroupResponseDTO(group);
    }
}
