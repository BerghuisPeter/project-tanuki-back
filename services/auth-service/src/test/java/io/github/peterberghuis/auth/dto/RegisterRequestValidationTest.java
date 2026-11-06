package io.github.peterberghuis.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the contract in {@code openapi.yaml}: the generated {@link RegisterRequest} must reject malformed emails,
 * which only happens while the spec declares {@code format: email} on the field.
 */
class RegisterRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validEmail_HasNoViolations() {
        assertTrue(validator.validate(request("user@example.com")).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "missing-at-sign.example.com", "@example.com", "user@@example.com", "user name@example.com"})
    void malformedEmail_IsRejected(String email) {
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request(email));

        assertEquals(1, violations.size(), "expected a violation for " + email);
        assertEquals("email", violations.iterator().next().getPropertyPath().toString());
    }

    private static RegisterRequest request(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("Password123!");
        request.setLocale("en-US");
        return request;
    }
}
