package io.github.peterberghuis.goshuin.config;

import io.github.peterberghuis.goshuin.dto.AffiliationType;
import io.github.peterberghuis.goshuin.dto.GoshuinFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.format.support.DefaultFormattingConversionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WebConfigTest {

    @Test
    public void testConvertersRegistration() {
        WebConfig webConfig = new WebConfig();
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        webConfig.addFormatters(conversionService);

        assertEquals(GoshuinFormat.WRITTEN, conversionService.convert("written", GoshuinFormat.class));
        assertEquals(GoshuinFormat.PAPER, conversionService.convert("paper", GoshuinFormat.class));
        assertEquals(AffiliationType.BUDDHIST, conversionService.convert("buddhist", AffiliationType.class));
        assertEquals(AffiliationType.SHINTO, conversionService.convert("shinto", AffiliationType.class));
    }

    @Test
    public void testInvalidGoshuinFormat() {
        WebConfig webConfig = new WebConfig();
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        webConfig.addFormatters(conversionService);

        assertThrows(ConversionFailedException.class, () -> {
            conversionService.convert("invalid", GoshuinFormat.class);
        });
    }
}
