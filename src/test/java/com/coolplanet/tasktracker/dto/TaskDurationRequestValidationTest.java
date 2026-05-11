package com.coolplanet.tasktracker.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDurationRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void buildValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void valid_whenDurationIsPositive() {
        Set<ConstraintViolation<TaskDurationRequest>> violations =
                validator.validate(new TaskDurationRequest(1500L));

        assertThat(violations).isEmpty();
    }

    @Test
    void valid_whenDurationIsZero() {
        Set<ConstraintViolation<TaskDurationRequest>> violations =
                validator.validate(new TaskDurationRequest(0L));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest(name = "duration={0}")
    @ValueSource(longs = {1L, 100L, 1_000L, 60_000L, Long.MAX_VALUE})
    void valid_forArbitraryPositiveValues(long durationMs) {
        Set<ConstraintViolation<TaskDurationRequest>> violations =
                validator.validate(new TaskDurationRequest(durationMs));

        assertThat(violations).isEmpty();
    }

    @Test
    void invalid_whenDurationIsNull() {
        Set<ConstraintViolation<TaskDurationRequest>> violations =
                validator.validate(new TaskDurationRequest(null));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("durationMs");
    }

    @ParameterizedTest(name = "duration={0}")
    @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
    void invalid_whenDurationIsNegative(long durationMs) {
        Set<ConstraintViolation<TaskDurationRequest>> violations =
                validator.validate(new TaskDurationRequest(durationMs));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("durationMs");
    }
}
