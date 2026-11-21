package com.just.critter.support.api;

import com.just.critter.domain.enums.AnimalEvent;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.domain.statemachine.StateMachines;
import com.just.critter.domain.enums.Role;
import com.just.critter.domain.statemachine.TransitionContext;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅测试用的契约探针：把各类异常从 HTTP 层走一遍，验证响应壳与状态码映射。
 */
@RestController
@RequestMapping("/__probe")
class ContractProbeController {

    @GetMapping("/ok")
    Result<Void> ok() {
        return Result.ok();
    }

    @GetMapping("/denied")
    Result<Void> denied() {
        throw new BusinessException(ErrorCode.NO_PERMISSION);
    }

    @GetMapping("/missing")
    Result<Void> missing() {
        throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
    }

    @GetMapping("/conflict")
    Result<Void> conflict() {
        throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
    }

    @GetMapping("/boom")
    Result<Void> boom() {
        throw new IllegalStateException("db connection refused");
    }

    @GetMapping("/transition")
    Result<Void> transition() {
        StateMachines.animal().fire(AnimalStatus.ON_CAMPUS, AnimalEvent.SEND_TO_HOSPITAL,
                TransitionContext.of(Role.ANIMAL_ADMIN));
        return Result.ok();
    }
}
