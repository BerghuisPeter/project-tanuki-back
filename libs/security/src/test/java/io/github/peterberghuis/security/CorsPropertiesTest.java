package io.github.peterberghuis.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsPropertiesTest {

    @Test
    void testGetAllowedOriginsList_Semicolon() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins("https://project-tanuki.net;https://www.project-tanuki.net");

        List<String> result = properties.getAllowedOriginsList();

        assertEquals(2, result.size());
        assertTrue(result.contains("https://project-tanuki.net"));
        assertTrue(result.contains("https://www.project-tanuki.net"));
    }

    @Test
    void testGetAllowedOriginsList_Comma() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins("https://project-tanuki.net,https://www.project-tanuki.net");

        List<String> result = properties.getAllowedOriginsList();

        assertEquals(2, result.size());
        assertTrue(result.contains("https://project-tanuki.net"));
        assertTrue(result.contains("https://www.project-tanuki.net"));
    }

    @Test
    void testGetAllowedOriginsList_Mixed() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins("https://a.com;https://b.com,https://c.com");

        List<String> result = properties.getAllowedOriginsList();

        assertEquals(3, result.size());
        assertTrue(result.contains("https://a.com"));
        assertTrue(result.contains("https://b.com"));
        assertTrue(result.contains("https://c.com"));
    }

    @Test
    void testGetAllowedOriginsList_Empty() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins("");

        List<String> result = properties.getAllowedOriginsList();

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAllowedOriginsList_Null() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(null);

        List<String> result = properties.getAllowedOriginsList();

        assertTrue(result.isEmpty());
    }
}
