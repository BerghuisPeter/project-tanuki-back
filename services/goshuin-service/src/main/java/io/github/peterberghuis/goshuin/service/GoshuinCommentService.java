package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.client.ProfileClient;
import io.github.peterberghuis.goshuin.dto.UserProfile;
import io.github.peterberghuis.goshuin.entity.GoshuinCommentEntity;
import io.github.peterberghuis.goshuin.repository.GoshuinCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoshuinCommentService {

    private final GoshuinCommentRepository goshuinCommentRepository;
    private final ProfileClient profileClient;

    public List<Map<String, String>> getComments(UUID goshuinId) {
        List<GoshuinCommentEntity> comments = goshuinCommentRepository.findAllByGoshuinId(goshuinId);
        if (comments.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = comments.stream()
                .map(GoshuinCommentEntity::getUserId)
                .distinct()
                .toList();
        Map<UUID, UserProfile> profiles = profileClient.getInternalProfiles(userIds);

        return comments.stream()
                .map(comment -> {
                    UserProfile profile = profiles != null ? profiles.get(comment.getUserId()) : null;
                    String locale = (profile != null && profile.getLocale() != null) ? profile.getLocale() : "en";
                    return Map.of(locale, comment.getContent());
                })
                .toList();
    }
}
