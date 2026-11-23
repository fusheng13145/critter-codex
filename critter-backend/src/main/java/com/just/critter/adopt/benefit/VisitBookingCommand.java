package com.just.critter.adopt.benefit;

import java.time.LocalDate;

public record VisitBookingCommand(LocalDate visitDate, String timeSlot, String note) {
}
