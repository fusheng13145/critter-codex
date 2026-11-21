package com.just.critter.volunteer.controller;

import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import com.just.critter.volunteer.service.TaskCheckinCommand;
import com.just.critter.volunteer.service.TaskCheckinView;
import com.just.critter.volunteer.service.TaskCommand;
import com.just.critter.volunteer.service.TaskService;
import com.just.critter.volunteer.service.TaskView;
import com.just.critter.volunteer.service.VolunteerTaskView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 志愿任务 5 接口：列表、发布、报名、打卡、我的任务。
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class VolunteerTaskController {

    private final TaskService taskService;

    public VolunteerTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public Result<PageResult<TaskView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser current = AuthContext.require();
        return Result.ok(taskService.list(status, type, new PageQuery(page, size), current.id()));
    }

    @PostMapping
    public Result<TaskView> create(@RequestBody TaskCreateRequest request) {
        CurrentUser current = requireAdmin();
        TaskCommand command = new TaskCommand(request.title(), request.type(), request.pointId(),
                request.startAt(), request.quota(), request.durationHours(),
                request.description());
        return Result.ok(taskService.create(command, current.id()));
    }

    @PostMapping("/{id}/join")
    public Result<TaskView> join(@PathVariable long id) {
        CurrentUser current = requireVolunteer();
        return Result.ok(taskService.join(id, current.id()));
    }

    @PostMapping("/{id}/checkin")
    public Result<TaskCheckinView> checkin(@PathVariable long id,
                                           @RequestBody TaskCheckinRequest request) {
        CurrentUser current = requireVolunteer();
        return Result.ok(taskService.checkin(id,
                new TaskCheckinCommand(request.photo()), current.id()));
    }

    @GetMapping("/mine")
    public Result<VolunteerTaskView> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser current = requireVolunteer();
        return Result.ok(taskService.mine(current.id(), page, size));
    }

    private CurrentUser requireAdmin() {
        CurrentUser current = AuthContext.require();
        if (!(current.isAdmin() || current.isSystemAdmin())) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH);
        }
        return current;
    }

    private CurrentUser requireVolunteer() {
        CurrentUser current = AuthContext.require();
        if (!current.hasRole("VOLUNTEER")) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH);
        }
        return current;
    }
}
