package io.github.peterberghuis.profile.validator;

import io.github.peterberghuis.profile.dto.UserProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserProfileValidatorTest {

    private final UserProfileValidator validator = new UserProfileValidator();

    @Test
    void validate_WhenDisplayNameTooLong_ShouldThrowException() {
        UserProfile preferences = new UserProfile();
        preferences.setDisplayName("a".repeat(46));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(preferences));
    }

    @Test
    void validate_WhenDisplayNameIs45Characters_ShouldNotThrow() {
        UserProfile preferences = new UserProfile();
        preferences.setDisplayName("a".repeat(45));

        assertDoesNotThrow(() -> validator.validate(preferences));
    }
}
