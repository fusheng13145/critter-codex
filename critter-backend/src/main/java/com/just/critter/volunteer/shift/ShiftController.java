package com.just.critter.volunteer.shift;

import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shifts")
public class ShiftController {

    private final ShiftService shifts;

    public ShiftController(ShiftService shifts) {
        this.shifts = shifts;
    }

    @GetMapping
    public Result<List<ShiftView>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mine) {
        return Result.ok(shifts.list(from, to, AuthContext.require().id(), mine));
    }

    @GetMapping("/me")
    public Result<SuspensionView> me() {
        return Result.ok(shifts.me(AuthContext.require().id()));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @PostMapping
    public Result<ShiftView> create(@RequestBody(required = false) ShiftCreateRequest request) {
        return Result.ok(shifts.create(request == null ? null : request.toCommand(),
                AuthContext.require().id()));
    }

    @PostMapping("/{id}/signup")
    public Result<ShiftView> signup(@PathVariable long id) {
        return Result.ok(shifts.signup(id, AuthContext.require().id()));
    }

    @PutMapping("/{id}/checkin")
    public Result<ShiftView> checkin(@PathVariable long id) {
        return Result.ok(shifts.checkin(id, AuthContext.require().id()));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @GetMapping("/{id}/signups")
    public Result<List<ShiftSignupView>> signups(@PathVariable long id) {
        return Result.ok(shifts.signups(id));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @PutMapping("/{id}/close")
    public Result<ShiftView> close(@PathVariable long id,
                                   @RequestBody(required = false) Map<String, String> body) {
        return Result.ok(shifts.close(id, body == null ? null : body.get("action")));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @PutMapping("/{id}/signups/{userId}/absent")
    public Result<AbsenceResult> markAbsent(@PathVariable long id, @PathVariable long userId) {
        return Result.ok(shifts.markAbsent(id, userId, AuthContext.require().id()));
    }

    @PostMapping("/appeals")
    public Result<AppealView> appeal(@RequestBody(required = false) Map<String, String> body) {
        return Result.ok(shifts.appeal(AuthContext.require().id(),
                body == null ? null : body.get("reason")));
    }

    @GetMapping("/appeals/mine")
    public Result<List<AppealView>> myAppeals() {
        return Result.ok(shifts.myAppeals(AuthContext.require().id()));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @GetMapping("/appeals")
    public Result<PageResult<AppealView>> appeals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(shifts.listAppeals(status, new PageQuery(page, size)));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @PutMapping("/appeals/{id}/review")
    public Result<AppealView> reviewAppeal(@PathVariable long id,
                                           @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> safe = body == null ? Map.of() : body;
        return Result.ok(shifts.reviewAppeal(id, safe.get("decision"), safe.get("reason"),
                AuthContext.require().id()));
    }
}
