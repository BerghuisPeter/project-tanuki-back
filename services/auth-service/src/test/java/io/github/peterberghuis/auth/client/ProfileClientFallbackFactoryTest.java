package io.github.peterberghuis.auth.client;

import io.github.peterberghuis.auth.dto.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
public class ProfileClientFallbackFactoryTest {

    @InjectMocks
    private ProfileClientFallbackFactory fallbackFactory;

    @Test
    void fallbackFactory_ShouldReturnNull_WhenMethodsAreCalled() {
        // Arrange
        Throwable cause = new RuntimeException("Test exception");
        ProfileClient fallback = fallbackFactory.create(cause);

        // Act & Assert
        assertNull(fallback.getInternalProfile(UUID.randomUUID()));
        assertNull(fallback.createInternalProfile(UUID.randomUUID(), new UserProfile()));
    }
}
