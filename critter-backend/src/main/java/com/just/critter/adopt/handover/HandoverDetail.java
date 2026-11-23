package com.just.critter.adopt.handover;

import java.util.List;

public record HandoverDetail(HandoverView handover, List<FollowupView> followups) {
}
