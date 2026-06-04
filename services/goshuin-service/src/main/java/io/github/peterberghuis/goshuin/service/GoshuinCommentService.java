package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.repository.GoshuinCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoshuinCommentService {

    private final GoshuinCommentRepository goshuinCommentRepository;

    public Map<UUID, Integer> getCommentCounts(List<UUID> goshuinIds) {
        if (goshuinIds == null || goshuinIds.isEmpty()) {
            return Map.of();
        }
        return goshuinCommentRepository.countCommentsByGoshuinIds(goshuinIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Long) row[1]).intValue()
                ));
    }
}
