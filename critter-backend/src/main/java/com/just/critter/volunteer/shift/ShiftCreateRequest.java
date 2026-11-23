package com.just.critter.volunteer.shift;

import java.time.LocalDate;
import java.time.LocalTime;

public record ShiftCreateRequest(
        String title,
        Long pointId,
        LocalDate shiftDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer capacity) {

    public ShiftCreateCommand toCommand() {
        return new ShiftCreateCommand(title, pointId, shiftDate, startTime, endTime, capacity);
    }
}
