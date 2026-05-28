package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.dto.*;
import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import io.github.peterberghuis.goshuin.entity.GoshuinI18nEntity;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.entity.TempleI18nEntity;
import io.github.peterberghuis.goshuin.repository.GoshuinRepository;
import io.github.peterberghuis.goshuin.repository.TempleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoshuinServiceImpl implements GoshuinService {

    private final GoshuinRepository goshuinRepository;
    private final TempleRepository templeRepository;

    @Override
    public List<Goshuin> getAllGoshuins() {
        return goshuinRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public Goshuin createGoshuin(GoshuinCreate goshuinCreate) {
        GoshuinEntity entity = new GoshuinEntity();
        entity.setType(goshuinCreate.getType());
        entity.setPages(goshuinCreate.getPages());
        entity.setStartDate(goshuinCreate.getStartDate());
        entity.setEndDate(goshuinCreate.getEndDate());

        TempleEntity temple = templeRepository.findById(goshuinCreate.getTempleId())
                .orElseThrow(() -> new RuntimeException("Temple not found"));
        entity.setTemple(temple);

        if (goshuinCreate.getImageUrls() != null) {
            entity.setImageUrls(goshuinCreate.getImageUrls().stream()
                    .map(URI::toString)
                    .collect(Collectors.toList()));
        }

        if (goshuinCreate.getTranslations() != null) {
            for (Map.Entry<String, GoshuinTranslation> entry : goshuinCreate.getTranslations().entrySet()) {
                GoshuinI18nEntity translationEntity = new GoshuinI18nEntity(
                        entity,
                        entry.getKey(),
                        entry.getValue().getLabel(),
                        entry.getValue().getDescription()
                );
                entity.getTranslations().add(translationEntity);
            }
        }

        GoshuinEntity savedEntity = goshuinRepository.save(entity);
        return mapToDto(savedEntity);
    }

    private Goshuin mapToDto(GoshuinEntity entity) {
        Goshuin dto = new Goshuin();
        dto.setId(entity.getId());
        dto.setType(entity.getType());
        dto.setTemple(mapToSummaryDto(entity.getTemple()));
        dto.setPages(entity.getPages());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());

        Map<String, GoshuinTranslation> translations = new HashMap<>();
        for (GoshuinI18nEntity translationEntity : entity.getTranslations()) {
            GoshuinTranslation translationDto = new GoshuinTranslation();
            translationDto.setLabel(translationEntity.getLabel());
            translationDto.setDescription(translationEntity.getDescription());
            translations.put(translationEntity.getId().getLocale(), translationDto);
        }
        dto.setTranslations(translations);
        dto.setImageUrls(entity.getImageUrls().stream()
                .map(URI::create)
                .collect(Collectors.toList()));

        return dto;
    }

    private TempleLite mapToSummaryDto(TempleEntity entity) {
        if (entity == null) return null;
        TempleLite dto = new TempleLite();
        dto.setId(entity.getId());
        dto.setLongitude(entity.getLongitude().doubleValue());
        dto.setLatitude(entity.getLatitude().doubleValue());

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
