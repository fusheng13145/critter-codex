package com.just.critter.adopt.service;

import java.time.LocalDate;

/**
 * 到期提醒与释放的候选行。
 */
public record ExpiringAdoption(long id,
                               String adoptionNo,
                               long userId,
                               long animalId,
                               String animalCode,
                               LocalDate endDate) {
}
