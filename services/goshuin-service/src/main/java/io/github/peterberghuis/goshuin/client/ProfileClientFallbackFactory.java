package io.github.peterberghuis.goshuin.client;

import io.github.peterberghuis.goshuin.dto.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class ProfileClientFallbackFactory
        implements FallbackFactory<ProfileClient> {

    @Override
    public ProfileClient create(Throwable cause) {

        log.error("Profile service failed", cause);

        return new ProfileClient() {

            @Override
            public UserProfile getInternalProfile(UUID userId) {
                return null;
            }

            @Override
            public UserProfile createInternalProfile(
                    UUID userId,
                    UserProfile profileData
            ) {
                return null;
            }

            @Override
            public Map<UUID, UserProfile> getInternalProfiles(List<UUID> userIds) {
                return Collections.emptyMap();
            }
        };
    }
}
