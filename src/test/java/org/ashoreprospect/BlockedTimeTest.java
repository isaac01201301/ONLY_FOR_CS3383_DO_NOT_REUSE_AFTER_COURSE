/** Validates blocked-time windows, overlaps, and summaries. */
package org.ashoreprospect;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class BlockedTimeTest {

    @Test
    void blockedPeriodsDescribeDailyAndSingleDayUsage() {
        BlockedTime daily = BlockedTime.daily("Lunch", LocalTime.of(12, 0), LocalTime.of(13, 0));
        BlockedTime once = BlockedTime.once("Single", LocalDate.of(2026, 1, 20), LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertTrue(daily.isDaily());
        assertFalse(daily.isException());
        assertTrue(daily.appliesOn(LocalDate.of(2026, 1, 15)));
        assertTrue(once.appliesOn(LocalDate.of(2026, 1, 20)));
        assertFalse(once.appliesOn(LocalDate.of(2026, 1, 21)));
    }

    @Test
    void blockPeriodTotalsAndLookupWork() {
        BlockedTimePeriod period = new BlockedTimePeriod();
        period.blockedPeriod.add(BlockedTime.daily("Morning", LocalTime.of(8, 0), LocalTime.of(9, 0)));
        period.blockedPeriod.add(BlockedTime.daily("Lunch", LocalTime.of(12, 0), LocalTime.of(13, 0)));
        period.rebuildPeriods();

        assertEquals(2, period.returnTotalBlockedTime());
        assertTrue(period.isBlocked(LocalTime.of(8, 30)));
        assertTrue(period.isBlocked(LocalDate.of(2026, 1, 15), LocalTime.of(12, 30)));
        assertFalse(period.isBlocked(LocalDate.of(2026, 1, 15), LocalTime.of(11, 30)));
    }

    @Test
    void overlapRulesRejectSameKindClashes() {
        BlockedTime first = BlockedTime.daily("Lunch", LocalTime.of(12, 0), LocalTime.of(13, 0));
        BlockedTime second = BlockedTime.daily("Tea", LocalTime.of(12, 30), LocalTime.of(13, 30));
        BlockedTime single = BlockedTime.once("One-off", LocalDate.of(2026, 1, 21), LocalTime.of(12, 30), LocalTime.of(13, 30));

        assertArrayEquals(new LocalTime[] {LocalTime.of(12, 0), LocalTime.of(13, 0)}, first.asPeriod());
        assertTrue(BlockedTimePeriod.clockOverlap(first, second));
        assertTrue(BlockedTimePeriod.blockedOverlap(first, single));
        assertTrue(BlockedTimePeriod.daysCanMeet(first, single));

        BlockedTimePeriod period = new BlockedTimePeriod();
        period.blockedPeriod.add(first);
        period.rebuildPeriods();
        assertNotNull(period.conflictMessage(second));
        assertNotNull(period.conflictAmong(List.of(first, second)));
    }

    @Test
    void weeklyAndOnceBlockedItemsKeepCorrectFields() {
        BlockedTime weekly = BlockedTime.weekly("Weekly", DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0));
        BlockedTime once = BlockedTime.once("Once", LocalDate.of(2026, 1, 16), LocalTime.of(14, 0), LocalTime.of(15, 0));

        assertEquals(DayOfWeek.MONDAY, weekly.getWeekday());
        assertEquals(LocalDate.of(2026, 1, 16), once.getSpecificDate());
        assertFalse(weekly.isException());
    }
}
