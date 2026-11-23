package com.just.critter.adopt.benefit;

import java.time.LocalDateTime;

public record AdoptionPostView(
        long id,
        String title,
        String content,
        String authorName,
        LocalDateTime createdAt) {
}
