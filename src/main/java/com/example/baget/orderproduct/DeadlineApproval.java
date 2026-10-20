package com.example.baget.orderproduct;

import java.time.LocalDate;
import java.time.ZoneId;

public final class DeadlineApproval {
    public static final int STANDARD_LEAD_WORKING_DAYS = 5;
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Kyiv");

    private DeadlineApproval() {}

    public static boolean isRequired(LocalDate dueDate, LocalDate today) {
        if (dueDate == null || today == null) {
            return false;
        }
        LocalDate earliest = today;
        int daysToAdd = STANDARD_LEAD_WORKING_DAYS;
        while (daysToAdd > 0) {
            earliest = earliest.plusDays(1);
            if (!isWeekend(earliest)) {
                daysToAdd--;
            }
        }
        return dueDate.isBefore(earliest);
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY;
    }
}
