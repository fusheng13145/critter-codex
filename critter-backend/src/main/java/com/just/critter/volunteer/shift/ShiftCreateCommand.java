package com.just.critter.volunteer.shift;

import java.time.LocalDate;
import java.time.LocalTime;

public record ShiftCreateCommand(
        String title,
        Long pointId,
        LocalDate shiftDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer capacity) {
}
