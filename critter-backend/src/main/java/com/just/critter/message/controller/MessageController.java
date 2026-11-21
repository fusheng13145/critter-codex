package com.just.critter.message.controller;

import com.just.critter.message.service.MessageQueryService;
import com.just.critter.message.service.MessageReadResult;
import com.just.critter.message.service.MessageReadService;
import com.just.critter.message.service.MessageUnreadCount;
import com.just.critter.message.service.MessageView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageQueryService queryService;
    private final MessageReadService readService;

    public MessageController(MessageQueryService queryService, MessageReadService readService) {
        this.queryService = queryService;
        this.readService = readService;
    }

    @GetMapping
    public Result<PageResult<MessageView>> list(
            @RequestParam(required = false) Boolean readFlag,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser user = AuthContext.require();
        return Result.ok(queryService.list(user.id(), readFlag, type, new PageQuery(page, size)));
    }

    @GetMapping("/unread")
    public Result<MessageUnreadCount> unread() {
        CurrentUser user = AuthContext.require();
        return Result.ok(queryService.unread(user.id()));
    }

    @PutMapping("/read")
    public Result<MessageReadResult> markRead(@RequestBody MessageReadRequest request) {
        CurrentUser user = AuthContext.require();
        return Result.ok(readService.markRead(user.id(), request == null ? null : request.ids()));
    }
}
