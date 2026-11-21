package com.just.critter.volunteer.service;

import java.math.BigDecimal;
import java.util.List;

public record VolunteerTaskView(String volunteerStatus, BigDecimal totalHours, long total,
                                List<TaskSignupView> tasks) {
}
