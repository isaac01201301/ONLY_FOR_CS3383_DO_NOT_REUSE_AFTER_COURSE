/** Exercises task model state, placement metadata, and lifecycle transitions. */
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

class LifecycleAndTaskModelTest {

    private static final ZoneId TEST_ZONE = ZoneId.systemDefault();
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), TEST_ZONE);

    @BeforeEach
    void setUp() {
        AppTimeReference.setClock(FIXED_CLOCK);
        TaskManagementSystem.resetInstance();
    }

    @AfterEach
    void tearDown() {
        AppTimeReference.setClock(Clock.systemDefaultZone());
        TaskManagementSystem.resetInstance();
    }

    @Test
    void taskDefaultsAndMetadataBehaveAsExpected() {
        Task task = new Task("Daily task", 1.0, 1,
                LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(9, 0), null, null);

        assertEquals(TaskLifeCycle.UNSCHEDULED, task.getLifeCycle());
        assertEquals(Workflow.UNFINISHED, task.getWorkflow());
        assertTrue(task.isFixedTime());
        assertFalse(task.hasDeadline());
        assertFalse(task.isFlexible());
        assertEquals(2, task.getDurationSlots());
    }

    @Test
    void taskDurationAndPlacementMutatorsWork() {
        Task task = new Task("Task", 1.5, 3, null, null, null, null);
        assertEquals(90, task.getDurationMinutes());
        assertEquals(3, task.getDurationSlots());

        task.setDurationHours(2.0);
        assertEquals(120, task.getDurationMinutes());

        task.setPlacedDate(LocalDate.of(2026, 1, 20));
        task.setPlacedTime(LocalTime.of(10, 30));
        assertEquals(LocalDate.of(2026, 1, 20), task.getPlacedDate());
        assertEquals(LocalTime.of(10, 30), task.getPlacedTime());
    }

    @Test
    void taskLoadFromDatabaseKeepsIdAndPlacementValues() {
        Task task = Task.loadTaskFromdb(
                "db-1",
                "DB task",
                1.5,
                2,
                LocalDate.of(2026, 1, 30),
                LocalTime.of(9, 0),
                LocalDate.of(2026, 2, 2),
                LocalTime.of(18, 0),
                LocalDate.of(2026, 1, 30),
                LocalTime.of(10, 0));

        assertEquals("db-1", task.getID());
        assertEquals(LocalDate.of(2026, 1, 30), task.getStartingDate());
        assertEquals(LocalTime.of(10, 0), task.getPlacedTime());
    }

    @Test
    void taskLifecycleAndTouchedTimestampsUpdateCorrectly() {
        Task task = new Task("Cycle", 1.0, 1,
                LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(9, 0), null, null);

        task.setWorkflow(Workflow.FINISHED);
        task.setLifeCycle(TaskLifeCycle.ONGOING);
        task.setPlacementUpdatedAt(Instant.now());
        task.touchLastUpdated();
        task.touchPlacementUpdated();

        assertEquals(Workflow.FINISHED, task.getWorkflow());
        assertEquals(TaskLifeCycle.ONGOING, task.getLifeCycle());
        assertNotNull(task.getPlacementUpdatedAt());
        assertNotNull(task.getLastUpdatedAt());
    }

    @Test
    void refreshLifeCycleTransitionsAcrossStates() {
        TaskManagementSystem system = TaskManagementSystem.getInstance();
        LocalDate today = LocalDate.now(FIXED_CLOCK);

        Task future = new Task("Future", 1.0, 3, null, null, null, null);
        future.setPlacedDate(today.plusDays(1));
        future.setPlacedTime(LocalTime.of(10, 0));
        RefreshLifeCycle.refreshLifeCycle(future);
        assertEquals(TaskLifeCycle.ONGOING, future.getLifeCycle());

        Task past = new Task("Past", 1.0, 3, null, null, null, null);
        past.setPlacedDate(today.minusDays(1));
        past.setPlacedTime(LocalTime.of(9, 0));
        RefreshLifeCycle.refreshLifeCycle(past);
        assertEquals(TaskLifeCycle.PAST, past.getLifeCycle());

        Task unscheduled = new Task("Unscheduled", 1.0, 3, null, null, null, null);
        unscheduled.setPlacedDate(null);
        unscheduled.setPlacedTime(null);
        RefreshLifeCycle.refreshLifeCycle(unscheduled);
        assertEquals(TaskLifeCycle.UNSCHEDULED, unscheduled.getLifeCycle());

        future.setLifeCycle(TaskLifeCycle.UNSCHEDULED);
        past.setLifeCycle(TaskLifeCycle.UNSCHEDULED);
        system.getPlacedTasks().add(future);
        system.getPlacedTasks().add(past);
        assertTrue(RefreshLifeCycle.refreshAll(system));
    }

    @Test
    void popupTaskCarriesTheExpectedMetadata() {
        LocalDate start = LocalDate.of(2026, 1, 20);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalDate deadline = LocalDate.of(2026, 1, 22);
        LocalTime deadlineTime = LocalTime.of(18, 0);

        FetchPopOutTask pop = new FetchPopOutTask("Task", 1.5, 2, start, startTime, deadline, deadlineTime,
                LocalDate.of(2026, 1, 21), LocalTime.of(10, 30));

        assertEquals("Task", pop.getTaskName());
        assertEquals(1.5, pop.getDuration());
        assertEquals(2, pop.getState());
        assertTrue(pop.isDeadline());
        assertEquals(LocalDate.of(2026, 1, 21), pop.getPlacedDate());
        assertEquals(LocalTime.of(10, 30), pop.getPlacedTime());

        Task task = new Task("Direct", 2.0, 1, null, null, null, null);
        FetchPopOutTask clear = new FetchPopOutTask(task);
        assertSame(task, clear.getTask());
        assertEquals(task.getTaskName(), clear.getTask().getTaskName());
    }

    @Test
    void taskCloneAndHookHelpersBehaveAsExpected() {
        Task base = new Task("Base", 1.0, 2, null, null, LocalDate.of(2026, 1, 22), LocalTime.of(18, 0));
        base.setParentId("parent-1");
        base.setDescription("hello");
        base.setDescriptionDone(true);

        Task copy = new Task("Copy", 1.5, 3, null, null, null, null);
        copy.copyExtrasFrom(base);

        assertEquals("hello", copy.getDescription());
        assertTrue(copy.isDescriptionDone());
        assertEquals("parent-1", copy.getParentId());
        assertTrue(base.isQuick());
        assertTrue(base.canGoUnscheduled());
        assertEquals("Beta 1.2", AppVersion.LABEL);

        final java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        AddTaskHooks.afterAdd(task -> seen.set(task.getTaskName()));
        AddTaskHooks.fireAfterAdd(base);
        AddTaskHooks.fireAfterAdd(null);
        assertEquals("Base", seen.get());

        Task encrypted = new Task("Encrypted", 1.0, 1, LocalDate.now(FIXED_CLOCK).plusDays(1), LocalTime.of(9, 0), null, null);
        encrypted.setDescription("AP1.secret");
        assertEquals("AP1.secret", encrypted.getDescription());

        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 9000; i++) {
            longText.append('x');
        }
        encrypted.setDescription(longText.toString());
        assertEquals(8000, encrypted.getDescription().length());
    }
}
