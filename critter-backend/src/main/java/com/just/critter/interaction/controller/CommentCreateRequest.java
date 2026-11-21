package com.just.critter.interaction.controller;

public record CommentCreateRequest(String bizType, String bizId, String content) {
}
