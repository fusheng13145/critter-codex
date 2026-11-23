package com.just.critter.adopt.handover;

public record FollowupCompleteRequest(String result, String note) {

    public FollowupCompleteCommand toCommand() {
        return new FollowupCompleteCommand(result, note);
    }
}
