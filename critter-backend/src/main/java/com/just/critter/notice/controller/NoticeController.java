package com.just.critter.notice.controller;

import com.just.critter.notice.service.NoticeCommand;
import com.just.critter.notice.service.NoticeDetail;
import com.just.critter.notice.service.NoticeService;
import com.just.critter.notice.service.NoticeView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import com.just.critter.support.security.PublicEndpoint;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeService notices;

    public NoticeController(NoticeService notices) {
        this.notices = notices;
    }

    @PublicEndpoint
    @GetMapping
    public Result<PageResult<NoticeView>> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(notices.list(category, new PageQuery(page, size)));
    }

    @PublicEndpoint
    @GetMapping("/{id}")
    public Result<NoticeDetail> detail(@PathVariable long id) {
        return Result.ok(notices.detail(id));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    @PostMapping
    public Result<NoticeView> publish(@RequestBody(required = false) NoticeCreateRequest request) {
        CurrentUser user = AuthContext.require();
        NoticeCommand command = request == null ? null
                : new NoticeCommand(request.category(), request.title(), request.content(), request.pinned());
        return Result.ok(notices.publish(command, user.id()));
    }
}
