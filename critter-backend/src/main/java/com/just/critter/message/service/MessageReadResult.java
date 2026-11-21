package com.just.critter.message.service;

import java.util.List;

public record MessageReadResult(int success, List<MessageReadFailure> failed) {
}
