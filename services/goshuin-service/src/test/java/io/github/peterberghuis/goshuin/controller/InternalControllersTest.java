package io.github.peterberghuis.goshuin.controller;

import io.github.peterberghuis.common.exception.GlobalExceptionHandler;
import io.github.peterberghuis.goshuin.internal.dto.GoshuinEnrichmentUpdate;
import io.github.peterberghuis.goshuin.internal.dto.InternalTemple;
import io.github.peterberghuis.goshuin.internal.dto.TempleEnrichmentUpdate;
import io.github.peterberghuis.goshuin.service.InternalEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalControllersTest {

    private final UUID id = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    @Mock
    private InternalEnrichmentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalTempleController(service), new InternalGoshuinController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getTemple_ReturnsJson() throws Exception {
        InternalTemple temple = new InternalTemple();
        temple.setId(id);
        temple.setOriginalLocale("ja");
        when(service.getTemple(id)).thenReturn(temple);

        mockMvc.perform(get("/internal/v1/temples/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.originalLocale").value("ja"));
    }

    @Test
    void getTemple_Unknown_Returns404WithErrorResponse() throws Exception {
        when(service.getTemple(id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Temple not found: " + id));

        mockMvc.perform(get("/internal/v1/temples/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Temple not found: " + id));
    }

    @Test
    void patchTemple_ReturnsNoContentAndPassesBody() throws Exception {
        mockMvc.perform(patch("/internal/v1/temples/{id}/enrichment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": "%s",
                                  "status": "COMPLETE",
                                  "latitude": 35.7148,
                                  "longitude": 139.7967,
                                  "translations": {"en": {"name": "Senso-ji", "city": "Tokyo"}}
                                }
                                """.formatted(jobId)))
                .andExpect(status().isNoContent());

        ArgumentCaptor<TempleEnrichmentUpdate> captor = ArgumentCaptor.forClass(TempleEnrichmentUpdate.class);
        verify(service).updateTemple(any(UUID.class), captor.capture());
        TempleEnrichmentUpdate update = captor.getValue();
        assertEquals(jobId, update.getJobId());
        assertEquals("COMPLETE", update.getStatus().getValue());
        assertEquals(35.7148, update.getLatitude());
        assertEquals("Senso-ji", update.getTranslations().get("en").getName());
    }

    @Test
    void patchTemple_PendingStatus_IsRejectedWith400() throws Exception {
        mockMvc.perform(patch("/internal/v1/temples/{id}/enrichment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PENDING\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).updateTemple(any(), any());
    }

    @Test
    void patchTemple_MalformedBody_Returns400() throws Exception {
        mockMvc.perform(patch("/internal/v1/temples/{id}/enrichment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchTemple_ServiceRejection_Returns400() throws Exception {
        doThrow(new IllegalArgumentException("latitude and longitude must be sent together"))
                .when(service).updateTemple(any(), any());

        mockMvc.perform(patch("/internal/v1/temples/{id}/enrichment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": 35.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("latitude and longitude must be sent together"));
    }

    @Test
    void patchGoshuin_ReturnsNoContentAndPassesBody() throws Exception {
        mockMvc.perform(patch("/internal/v1/goshuins/{id}/enrichment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PROCESSING\", \"translations\": {\"en\": {\"label\": \"Main Hall\"}}}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<GoshuinEnrichmentUpdate> captor = ArgumentCaptor.forClass(GoshuinEnrichmentUpdate.class);
        verify(service).updateGoshuin(any(UUID.class), captor.capture());
        assertEquals("PROCESSING", captor.getValue().getStatus().getValue());
        assertEquals("Main Hall", captor.getValue().getTranslations().get("en").getLabel());
    }

    @Test
    void patchGoshuin_Unknown_Returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Goshuin not found: " + id))
                .when(service).updateGoshuin(any(), any());

        mockMvc.perform(patch("/internal/v1/goshuins/{id}/enrichment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
