package io.github.peterberghuis.profile.validator;

import io.github.peterberghuis.profile.dto.UserProfile;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserProfileValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void whenDisplayNameIsEmpty_thenValidationPasses() {
        UserProfile preferences = new UserProfile();
        preferences.setLocale("en-US");
        preferences.setDisplayName("");

        Set<ConstraintViolation<UserProfile>> violations = validator.validate(preferences);

        assertTrue(violations.isEmpty(), "Validation should pass for empty displayName");
    }

    @Test
    void whenDisplayNameIsNotEmpty_thenValidationPasses() {
        UserProfile preferences = new UserProfile();
        preferences.setLocale("en-US");
        preferences.setDisplayName("User");

        Set<ConstraintViolation<UserProfile>> violations = validator.validate(preferences);

        assertTrue(violations.isEmpty(), "Validation should pass for non-empty displayName");
    }
}
