package io.github.peterberghuis.goshuin.controller;

import io.github.peterberghuis.goshuin.api.GoshuinApi;
import io.github.peterberghuis.goshuin.dto.AffiliationType;
import io.github.peterberghuis.goshuin.dto.GoshuinFormat;
import io.github.peterberghuis.goshuin.dto.GoshuinSearchResponse;
import io.github.peterberghuis.goshuin.dto.GoshuinSort;
import io.github.peterberghuis.goshuin.service.GoshuinCommentService;
import io.github.peterberghuis.goshuin.service.GoshuinService;
import lombok.RequiredArgsConstructor;
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
            String query, GoshuinSort sort,
            String cursorToken) {
        return ResponseEntity.ok(goshuinService.searchGoshuins(format, pages, affiliation, query, sort, limit, cursorToken));
    }

    @Override
    public ResponseEntity<Map<String, Integer>> getGoshuinCommentCounts(List<UUID> goshuinIds) {
        Map<UUID, Integer> counts = goshuinCommentService.getCommentCounts(goshuinIds);
        Map<String, Integer> result = counts.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        Map.Entry::getValue
                ));
        return ResponseEntity.ok(result);
    }
}
