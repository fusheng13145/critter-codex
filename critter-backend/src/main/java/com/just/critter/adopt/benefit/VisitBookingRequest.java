package com.just.critter.adopt.benefit;

import java.time.LocalDate;

public record VisitBookingRequest(LocalDate visitDate, String timeSlot, String note) {

    public VisitBookingCommand toCommand() {
        return new VisitBookingCommand(visitDate, timeSlot, note);
    }
}
