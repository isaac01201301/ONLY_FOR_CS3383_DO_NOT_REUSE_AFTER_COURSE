/** Validates add-task input rules before commands are created. */
package org.ashoreprospect;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskValidationTest {

    private static final ZoneId TEST_ZONE = ZoneId.systemDefault();
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), TEST_ZONE);

    @BeforeEach
    void setUp() {
        AppTimeReference.setClock(FIXED_CLOCK);
    }

    @AfterEach
    void tearDown() {
        AppTimeReference.setClock(Clock.systemDefaultZone());
    }

    @Test
    void nameAndDurationRulesAreEnforced() {
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                null, 1.0, 1, LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(9, 0), null, null));
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "   ", 1.0, 1, LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(9, 0), null, null));
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Bad", 0, 1, LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(9, 0), null, null));
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Bad", -1, 1, LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(9, 0), null, null));
    }

    @Test
    void fixedTasksRequireStartAndRejectDeadline() {
        LocalDate today = LocalDate.now(FIXED_CLOCK);

        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Meeting", 1.0, 1, null, null, null, null));
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Meeting", 1.0, 1, today.plusDays(1), LocalTime.of(9, 0), today.plusDays(2), LocalTime.of(10, 0)));
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Past meeting", 1.0, 1, today.minusDays(1), LocalTime.of(9, 0), null, null));
    }

    @Test
    void deadlineTasksRequireDeadlineAndRejectStart() {
        LocalDate today = LocalDate.now(FIXED_CLOCK);

        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Assignment", 1.0, 2, today.plusDays(1), LocalTime.of(9, 0), today.plusDays(2), LocalTime.of(18, 0)));
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Past deadline", 1.0, 2, null, null, today.minusDays(1), LocalTime.of(18, 0)));
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Missing deadline", 1.0, 2, null, null, null, null));
    }

    @Test
    void flexibleTasksAllowNoDatesAndRejectImproperDateShape() {
        assertTrue(ValidateAddTaskInput.validateAddTaskInput(
                "Flexible task", 1.5, 3, null, null, null, null));
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Flexible task", 1.0, 3, LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(9, 0), null, null));
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Flexible task", 1.0, 3, null, null, LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(18, 0)));
    }

    @Test
    void stateAndLargeDurationAreHandledByValidationRules() {
        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Bad state", 1.0, 99, LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(9, 0), null, null));
        assertTrue(ValidateAddTaskInput.validateAddTaskInput(
                "Clamped duration", 25.0, 1, LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(9, 0), null, null));
    }
}
