package io.github.peterberghuis.goshuin.config;

import io.github.peterberghuis.goshuin.dto.AffiliationType;
import io.github.peterberghuis.goshuin.dto.GoshuinFormat;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToGoshuinFormatConverter());
        registry.addConverter(new StringToAffiliationTypeConverter());
    }

    private static class StringToGoshuinFormatConverter implements Converter<String, GoshuinFormat> {
        @Override
        public GoshuinFormat convert(String source) {
            return GoshuinFormat.fromValue(source);
        }
    }

    private static class StringToAffiliationTypeConverter implements Converter<String, AffiliationType> {
        @Override
        public AffiliationType convert(String source) {
            return AffiliationType.fromValue(source);
        }
    }
}
