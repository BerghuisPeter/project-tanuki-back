package io.github.peterberghuis.profile.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AvatarChangedEvent {
    private final String bucketName;
    private final String blobName;
}
