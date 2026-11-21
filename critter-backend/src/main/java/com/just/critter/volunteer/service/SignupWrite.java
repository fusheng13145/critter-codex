package com.just.critter.volunteer.service;

import java.time.LocalDateTime;

public record SignupWrite(long taskId, long userId, LocalDateTime signedAt, long createdBy) {
}
