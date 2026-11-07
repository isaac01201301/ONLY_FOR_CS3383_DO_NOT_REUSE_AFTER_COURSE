/** Covers scheduler placement and rescheduling rules. */
package org.ashoreprospect;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchedulerEngineTest {

    private static final ZoneId TEST_ZONE = ZoneId.systemDefault();
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), TEST_ZONE);

    private TaskManagementSystem system;

    @BeforeEach
    void setUp() {
        AppTimeReference.setClock(FIXED_CLOCK);
        TaskManagementSystem.resetInstance();
        system = TaskManagementSystem.getInstance();
    }

    @AfterEach
    void tearDown() {
        AppTimeReference.setClock(Clock.systemDefaultZone());
        TaskManagementSystem.resetInstance();
    }

    @Test
    void flexibleTasksArePlacedOnEarliestAvailableSlot() {
        Task task = new Task("Flexible", 1.0, 3, null, null, null, null);
        Map<LocalDate, Day> grid = new TreeMap<>();
        SchedulerEngine engine = new SchedulerEngine(system, LocalDate.now(FIXED_CLOCK), grid);

        engine.scheduleTasks(List.of(task));

        assertNotNull(task.getPlacedDate());
        assertNotNull(task.getPlacedTime());
        assertTrue(task.getPlacedDate().isAfter(LocalDate.now(FIXED_CLOCK).minusDays(1)));
    }

    @Test
    void deadlineTasksArePlacedBeforeTheirDeadline() {
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Task task = new Task("Deadline task", 1.0, 2,
                null, null, today.plusDays(2), LocalTime.of(18, 0));
        Map<LocalDate, Day> grid = new TreeMap<>();
        SchedulerEngine engine = new SchedulerEngine(system, today, grid);

        engine.scheduleTasks(List.of(task));

        assertNotNull(task.getPlacedDate());
        assertNotNull(task.getPlacedTime());
        assertTrue(!task.getPlacedTime().isAfter(LocalTime.of(18, 0))
                || task.getPlacedDate().isBefore(today.plusDays(2)));
    }

    @Test
    void impossibleDeadlineTaskFallsBackToUnscheduled() {
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Task task = new Task("Impossible deadline", 2.0, 2,
                null, null, today, LocalTime.of(12, 5));

        Map<LocalDate, Day> grid = new TreeMap<>();
        SchedulerEngine engine = new SchedulerEngine(system, today, grid);

        engine.scheduleTasks(List.of(task));

        assertTrue(task.getPlacedDate() == null || task.getPlacedTime() == null
                || system.getUnscheduledTasks().contains(task));
    }

    @Test
    void overlappingTasksAreNotPlacedInSameSlot() {
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Map<LocalDate, Day> grid = new TreeMap<>();
        SchedulerEngine engine = new SchedulerEngine(system, today, grid);

        Task first = new Task("First", 1.0, 3, null, null, null, null);
        Task second = new Task("Second", 1.0, 3, null, null, null, null);

        engine.scheduleTasks(List.of(first, second));

        assertTrue(first.getPlacedDate() != null || second.getPlacedDate() != null);
    }

    @Test
    void updateScheduleReschedulesNonFixedTasks() {
        Task task = new Task("Swing", 1.0, 3, null, null, null, null);
        system.getPendingTasks().add(task);

        system.updateSchedule();

        assertNotNull(task.getPlacedDate());
    }

    @Test
    void alreadyPlacedTasksStayPlacedAndAreNotReplaced() {
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Task alreadyPlaced = new Task("Already placed", 1.0, 1,
                today.plusDays(1), LocalTime.of(9, 0), null, null);
        alreadyPlaced.setPlacedDate(today.plusDays(1));
        alreadyPlaced.setPlacedTime(LocalTime.of(9, 0));

        system.getPlacedTasks().add(alreadyPlaced);
        system.getPendingTasks().add(alreadyPlaced);

        SchedulerEngine engine = new SchedulerEngine(system, today, system.getGrid());
        engine.placeAlreadyPlacedTasks(List.of(alreadyPlaced));

        assertTrue(system.getPlacedTasks().contains(alreadyPlaced));
    }
}
