package org.ashoreprospect;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.ashoreprospect.crypto.Vault;
import org.ashoreprospect.net.Endpoints;
import org.ashoreprospect.net.LocalSession;
import org.ashoreprospect.net.PasswordRules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProjectCoverageBoostTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-15T12:00:00Z"), ZoneId.systemDefault());

    @AfterEach
    void cleanup() {
        AppTimeReference.setClock(Clock.systemDefaultZone());
        Vault.lock();
        Vault.eraseLocalKeys();
        try {
            Files.deleteIfExists(Endpoints.dir().resolve("session.txt"));
            Files.deleteIfExists(Endpoints.dir().resolve("login.txt"));
        } catch (Exception ignored) {
        }
    }

    @Test
    void appTimeReferenceMathAndCalendarHelpersWork() {
        AppTimeReference.setClock(FIXED_CLOCK);
        LocalDate expectedDate = LocalDate.ofInstant(FIXED_CLOCK.instant(), ZoneId.systemDefault());
        LocalTime expectedTime = LocalTime.ofInstant(FIXED_CLOCK.instant(), ZoneId.systemDefault());

        assertEquals(LocalDate.of(2026, 1, 12), AppTimeReference.getStartOfWeek(LocalDate.of(2026, 1, 15)));
        assertEquals(expectedDate, AppTimeReference.getCurrentDate());
        assertEquals(expectedTime, AppTimeReference.getCurrentTime());
        assertEquals(48, AppTimeReference.SLOTS_PER_DAY);
        assertTrue(AppTimeReference.slotIsNextCalendarDay(38));
        assertEquals(expectedDate, AppTimeReference.calendarDateForSlot(expectedDate, 10));
        assertEquals(expectedDate.plusDays(1), AppTimeReference.calendarDateForSlot(expectedDate, 38));
        assertEquals(LocalTime.of(6, 0), AppTimeReference.slotToLocalTime(0));
        assertEquals(0, AppTimeReference.localTimeToSlot(LocalTime.of(6, 0)));
        assertEquals(2, AppTimeReference.hoursToSlots(1.0));
        assertEquals(2, AppTimeReference.minutesToSlots(60));
        assertEquals(0.5, AppTimeReference.slotsToHours(1), 0.0001);
        assertEquals(1, AppTimeReference.occupancySlots(LocalTime.of(7, 0), 30));
        assertEquals(720, AppTimeReference.relativeMinutes(LocalTime.of(18, 0)));
        assertTrue(AppTimeReference.rangeInside(
                expectedDate,
                expectedTime.minusHours(1),
                60,
                expectedDate,
                expectedTime.minusMinutes(30),
                30));
    }

    @Test
    void taskValidationCoversFixedDeadlineAndFlexibleRules() {
        AppTimeReference.setClock(FIXED_CLOCK);

        assertTrue(ValidateAddTaskInput.validateAddTaskInput(
                "Fixed task",
                1.5,
                1,
                LocalDate.of(2026, 1, 16),
                LocalTime.of(9, 0),
                null,
                null));

        assertTrue(ValidateAddTaskInput.validateAddTaskInput(
                "Deadline task",
                2.0,
                2,
                null,
                null,
                LocalDate.of(2026, 1, 20),
                LocalTime.of(18, 0)));

        assertTrue(ValidateAddTaskInput.validateAddTaskInput(
                "Flexible task",
                3.0,
                3,
                null,
                null,
                null,
                null));

        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "",
                1.0,
                1,
                LocalDate.of(2026, 1, 16),
                LocalTime.of(9, 0),
                null,
                null));

        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Bad duration",
                0.0,
                1,
                LocalDate.of(2026, 1, 16),
                LocalTime.of(9, 0),
                null,
                null));

        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Past fixed",
                1.0,
                1,
                LocalDate.of(2025, 12, 31),
                LocalTime.of(9, 0),
                null,
                null));

        assertFalse(ValidateAddTaskInput.validateAddTaskInput(
                "Bad state",
                1.0,
                99,
                null,
                null,
                null,
                null));
    }

    @Test
    void passwordAndSessionHelpersBehaveAsExpected() {
        assertNull(PasswordRules.reject("Abcdef1!"));
        assertEquals(PasswordRules.HINT, PasswordRules.reject("password"));
        assertEquals(PasswordRules.HINT, PasswordRules.reject("abc"));

        LocalSession.save("user-42", "demo", "token-abc");
        LocalSession.savePrefs(true, "demo");
        LocalSession.setRandomChallenge(true);
        LocalSession.setReminders(false);
        LocalSession.setReminderSound(false);
        LocalSession.setRealtimeOverlay(false);
        LocalSession.setIdleMinutes(30);
        LocalSession.setReminderLeads(List.of(0, 5, 10));

        var saved = LocalSession.load();
        assertNotNull(saved);
        assertEquals("user-42", saved.userId);
        assertEquals("demo", saved.username);
        assertEquals("token-abc", saved.token);
        assertTrue(LocalSession.wantRemember());
        assertTrue(LocalSession.wantRandomChallenge());
        assertFalse(LocalSession.wantReminders());
        assertFalse(LocalSession.wantReminderSound());
        assertFalse(LocalSession.wantRealtimeOverlay());
        assertEquals(30, LocalSession.idleMinutes());
        assertEquals(List.of(0, 5, 10), LocalSession.reminderLeads());
        assertEquals("0,5,10", LocalSession.joinLeads(List.of(0, 5, 10)));

        assertTrue(Endpoints.isLocalHost() || Endpoints.nodeBase() != null);
        assertNotNull(Endpoints.nodeBase());
        assertFalse(Endpoints.isCreatorHost());
    }

    @Test
    void vaultEncryptionRoundTripsAndRefreshLifecycleWorks() throws Exception {
        AppTimeReference.setClock(FIXED_CLOCK);
        LocalDateTime now = AppTimeReference.getCurrentDateTime();

        Vault.enable("user-42", "Abcdef1!");
        assertTrue(Vault.enabled());

        String sealed = Vault.seal("secret note");
        assertTrue(Vault.sealed(sealed));
        assertEquals("secret note", Vault.open(sealed));

        Task task = new Task("Protected", 1.0, 1,
                now.plusDays(1).toLocalDate(), now.plusDays(1).toLocalTime(), null, null);
        task.setPlacedDate(now.toLocalDate());
        task.setPlacedTime(now.minusMinutes(30).toLocalTime());
        task.setDescription("hello");
        Vault.openTask(task);
        assertEquals("hello", task.getDescription());

        Task past = new Task("Past", 1.0, 1,
                now.toLocalDate(), now.minusHours(1).toLocalTime(), null, null);
        past.setPlacedDate(now.toLocalDate());
        past.setPlacedTime(now.minusHours(2).toLocalTime());
        RefreshLifeCycle.refreshLifeCycle(past);
        assertEquals(TaskLifeCycle.PAST, past.getLifeCycle());

        Task ongoing = new Task("Ongoing", 2.0, 1,
                now.toLocalDate(), now.toLocalTime(), null, null);
        ongoing.setPlacedDate(now.toLocalDate());
        ongoing.setPlacedTime(now.minusMinutes(30).toLocalTime());
        RefreshLifeCycle.refreshLifeCycle(ongoing);
        assertEquals(TaskLifeCycle.ONGOING, ongoing.getLifeCycle());
    }

    @Test
    void dayGridAndBlockedTimeLogicStayDeterministic() {
        AppTimeReference.setClock(FIXED_CLOCK);
        BlockedTimePeriod period = new BlockedTimePeriod();
        period.replaceBlockedPeriods(List.of(
                BlockedTime.daily("lunch", LocalTime.of(12, 0), LocalTime.of(13, 0))));
        Day day = new Day(period);

        Task first = new Task("Task A", 1.0, 1,
                LocalDate.of(2026, 1, 16), LocalTime.of(9, 0), null, null);
        assertTrue(day.canPlace(first, LocalDate.of(2026, 1, 16), 6, false, false));
        day.placeTask(6, first);

        Task second = new Task("Task B", 1.0, 1,
                LocalDate.of(2026, 1, 16), LocalTime.of(9, 0), null, null);
        assertFalse(day.canPlace(second, LocalDate.of(2026, 1, 16), 6, false, false));

        assertEquals(44, day.getAvailableSlots());
        assertNotNull(day.returnDayGrid()[6]);
        day.clearAllSlots();
        assertEquals(46, day.getAvailableSlots());
        assertFalse(day.getOverloadFlag());
    }
}
