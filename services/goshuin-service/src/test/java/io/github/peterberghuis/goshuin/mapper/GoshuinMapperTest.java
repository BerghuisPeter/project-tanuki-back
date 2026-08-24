package io.github.peterberghuis.goshuin.mapper;

import io.github.peterberghuis.goshuin.dto.Goshuin;
import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoshuinMapperTest {

    private final GoshuinMapper goshuinMapper = new GoshuinMapper();

    @Test
    void mapToDto_WithNullTempleCoordinates_ShouldNotThrowNPE() {
        // Arrange
        TempleEntity temple = new TempleEntity();
        temple.setId(UUID.randomUUID());
        temple.setAffiliationType("shinto");
        temple.setLongitude(null);
        temple.setLatitude(null);
        temple.setTranslations(Collections.emptySet());

        GoshuinEntity goshuin = new GoshuinEntity();
        goshuin.setId(UUID.randomUUID());
        goshuin.setTemple(temple);
        goshuin.setFormat("written");
        goshuin.setTranslations(Collections.emptySet());
        goshuin.setImages(Collections.emptyList());

        // Act & Assert
        assertDoesNotThrow(() -> goshuinMapper.mapToDto(goshuin, null));

        Goshuin result = goshuinMapper.mapToDto(goshuin, null);
        assertNull(result.getTemple().getLongitude());
        assertNull(result.getTemple().getLatitude());
    }
}
