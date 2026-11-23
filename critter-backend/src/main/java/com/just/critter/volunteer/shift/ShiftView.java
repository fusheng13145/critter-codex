package com.just.critter.volunteer.shift;

import java.time.LocalDate;
import java.time.LocalTime;

public record ShiftView(
        long id,
        String shiftNo,
        String title,
        Long pointId,
        LocalDate shiftDate,
        LocalTime startTime,
        LocalTime endTime,
        int capacity,
        int booked,
        String status,
        boolean signed,
        boolean checkedIn,
        boolean absent) {
}
