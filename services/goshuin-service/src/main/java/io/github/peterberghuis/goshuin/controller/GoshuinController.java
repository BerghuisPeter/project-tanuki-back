package io.github.peterberghuis.goshuin.controller;

import io.github.peterberghuis.common.dto.UploadUrlResponse;
import io.github.peterberghuis.goshuin.api.GoshuinApi;
import io.github.peterberghuis.goshuin.dto.*;
import io.github.peterberghuis.goshuin.service.GoshuinCommentService;
import io.github.peterberghuis.goshuin.service.GoshuinService;
import io.github.peterberghuis.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GoshuinController implements GoshuinApi {

    private final GoshuinService goshuinService;
    private final GoshuinCommentService goshuinCommentService;

    @Override
    public ResponseEntity<GoshuinSearchResponse> searchGoshuins(
            Integer limit,
            GoshuinFormat format,
            List<Integer> pages,
            AffiliationType affiliation,
            String query,
            Boolean mine,
            GoshuinSort sort,
            Double lat,
            Double lng,
            String cursorToken,
            Boolean includeNonCompleted) {
        UUID userId = (mine != null && mine) ? SecurityUtils.getUserIdFromContext() : null;
        return ResponseEntity.ok(goshuinService.searchGoshuins(format, pages, affiliation, query, sort, limit, lat, lng, cursorToken, userId, includeNonCompleted));
    }

    @Override
    public ResponseEntity<Goshuin> createGoshuin(GoshuinCreate goshuinCreate) {
        UUID userId = SecurityUtils.getUserIdFromContext();
        return ResponseEntity.status(HttpStatus.CREATED).body(goshuinService.createGoshuin(userId, goshuinCreate));
    }

    @Override
    public ResponseEntity<UploadUrlResponse> getGoshuinUploadUrl(String contentType) {
        UUID userId = SecurityUtils.getUserIdFromContext();
        return ResponseEntity.ok(goshuinService.getGoshuinUploadUrl(userId, contentType));
    }

    @Override
    public ResponseEntity<List<Map<String, String>>> getGoshuinComments(UUID id) {
        return ResponseEntity.ok(goshuinCommentService.getComments(id));
    }
}
