package com.just.critter.adopt.service;

public record AdoptionPlanCommand(String planName, String period, Integer amount,
                                  String benefits, Boolean enabled) {
}
