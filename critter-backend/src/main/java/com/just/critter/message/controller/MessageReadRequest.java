package com.just.critter.message.controller;

import java.util.List;

public record MessageReadRequest(List<Long> ids) {
}
