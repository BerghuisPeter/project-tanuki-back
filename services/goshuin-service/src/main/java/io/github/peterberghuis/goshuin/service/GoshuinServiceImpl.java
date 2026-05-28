package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.dto.Goshuin;
import io.github.peterberghuis.goshuin.dto.TempleSummary;
import io.github.peterberghuis.goshuin.dto.TempleTranslation;
import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.entity.TempleI18nEntity;
import io.github.peterberghuis.goshuin.repository.GoshuinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoshuinServiceImpl implements GoshuinService {

    private final GoshuinRepository goshuinRepository;

    @Override
    public List<Goshuin> getAllGoshuins() {
        return goshuinRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private Goshuin mapToDto(GoshuinEntity entity) {
        Goshuin dto = new Goshuin();
        dto.setId(entity.getId());
        dto.setDate(entity.getReceivingDate());
        dto.setTemple(mapToSummaryDto(entity.getTemple()));
        return dto;
    }

    private TempleSummary mapToSummaryDto(TempleEntity entity) {
        if (entity == null) return null;
        TempleSummary dto = new TempleSummary();
        dto.setId(entity.getId());

        Map<String, TempleTranslation> translations = new HashMap<>();
        for (TempleI18nEntity translationEntity : entity.getTranslations()) {
            TempleTranslation translationDto = new TempleTranslation();
            translationDto.setName(translationEntity.getName());
            translationDto.setAddress(translationEntity.getAddress());
            translationDto.setDescription(translationEntity.getDescription());
            translations.put(translationEntity.getId().getLocale(), translationDto);
        }
        dto.setTranslations(translations);

        return dto;
    }
}
