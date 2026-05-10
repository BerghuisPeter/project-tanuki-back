package io.github.peterberghuis.profile.validator;

import io.github.peterberghuis.profile.dto.UserPreferences;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserPreferencesValidatorTest {

    private final UserPreferencesValidator validator = new UserPreferencesValidator();

    @Test
    void validate_WhenDisplayNameTooLong_ShouldThrowException() {
        UserPreferences preferences = new UserPreferences();
        preferences.setDisplayName("a".repeat(46));
        preferences.setLocale("en-US");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(preferences));
    }

    @Test
    void validate_WhenDisplayNameIs45Characters_ShouldNotThrow() {
        UserPreferences preferences = new UserPreferences();
        preferences.setDisplayName("a".repeat(45));
        preferences.setLocale("en-US");

        assertDoesNotThrow(() -> validator.validate(preferences));
    }
}
