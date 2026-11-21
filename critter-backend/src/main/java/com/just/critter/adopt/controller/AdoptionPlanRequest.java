package com.just.critter.adopt.controller;

public record AdoptionPlanRequest(String planName, String period, Integer amount,
                                  String benefits, Boolean enabled) {
}
