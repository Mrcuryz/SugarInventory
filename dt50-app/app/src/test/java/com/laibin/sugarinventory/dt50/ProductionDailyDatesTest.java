package com.laibin.sugarinventory.dt50;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProductionDailyDatesTest {
    @Test
    public void calendarUsesMondayFirstAndHandlesLeapDay() {
        assertEquals(3, ProductionDailyDates.mondayOffset("2024-02"));
        assertEquals(29, ProductionDailyDates.daysInMonth("2024-02"));
        assertEquals("四", ProductionDailyDates.weekday("2024-02-29"));
        assertEquals("2024-03-01", ProductionDailyDates.shiftDay("2024-02-29", 1));
        assertEquals("2024-02", ProductionDailyDates.shiftMonth("2024-01", 1));
        assertEquals("2024年2月29日", ProductionDailyDates.display("2024-02-29"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCalendarDateIsRejected() {
        ProductionDailyDates.parse("2025-02-29");
    }
}
