package com.just.critter.interaction.service;

public record CommentCreateCommand(String bizType, String bizId, String content) {
}
