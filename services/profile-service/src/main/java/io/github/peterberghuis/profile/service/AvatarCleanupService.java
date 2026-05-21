package io.github.peterberghuis.profile.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import io.github.peterberghuis.profile.event.AvatarChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarCleanupService {

    private final Storage storage;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAvatarChangedEvent(AvatarChangedEvent event) {
        log.info("Deleting old avatar from bucket: {}, path: {}", event.getBucketName(), event.getBlobName());
        try {
            storage.delete(BlobId.of(event.getBucketName(), event.getBlobName()));
        } catch (Exception e) {
            log.error("Failed to delete old avatar from bucket: {}, path: {}", event.getBucketName(), event.getBlobName(), e);
        }
    }
}
