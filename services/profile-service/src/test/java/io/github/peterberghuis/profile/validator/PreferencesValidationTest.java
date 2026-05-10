package io.github.peterberghuis.profile.validator;

import io.github.peterberghuis.profile.dto.UserPreferences;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PreferencesValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void whenDisplayNameIsEmpty_thenValidationPasses() {
        UserPreferences preferences = new UserPreferences("en-US");
        preferences.setDisplayName("");

        Set<ConstraintViolation<UserPreferences>> violations = validator.validate(preferences);

        assertTrue(violations.isEmpty(), "Validation should pass for empty displayName");
    }

    @Test
    void whenDisplayNameIsNotEmpty_thenValidationPasses() {
        UserPreferences preferences = new UserPreferences("en-US");
        preferences.setDisplayName("User");

        Set<ConstraintViolation<UserPreferences>> violations = validator.validate(preferences);

        assertTrue(violations.isEmpty(), "Validation should pass for non-empty displayName");
    }
}
