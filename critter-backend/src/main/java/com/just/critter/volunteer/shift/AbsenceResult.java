package com.just.critter.volunteer.shift;

import java.math.BigDecimal;

public record AbsenceResult(int absenceStreak, boolean suspended, String message) {
}
