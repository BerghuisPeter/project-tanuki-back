package io.github.peterberghuis.auth.client;

import io.github.peterberghuis.auth.dto.UserProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "profile-service", fallbackFactory = ProfileClientFallbackFactory.class)
public interface ProfileClient {

    @GetMapping("/internal/profiles/{userId}")
    UserProfile getInternalProfile(@PathVariable("userId") UUID userId);

    @PostMapping("/internal/profiles/{userId}")
    UserProfile createInternalProfile(@PathVariable("userId") UUID userId, @RequestBody UserProfile profileData);
}
