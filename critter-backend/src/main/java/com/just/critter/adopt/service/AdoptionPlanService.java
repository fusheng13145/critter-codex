package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.repository.AdoptionPlanRepository;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdoptionPlanService {

    private static final int MAX_NAME = 32;
    private static final int MAX_BENEFITS = 255;

    private final AdoptionPlanRepository adoptionPlanRepository;
    private final AuditLogService audit;
    private final Clock clock;

    public AdoptionPlanService(AdoptionPlanRepository adoptionPlanRepository,
                               AuditLogService audit, Clock clock) {
        this.adoptionPlanRepository = adoptionPlanRepository;
        this.audit = audit;
        this.clock = clock;
    }

    public List<AdoptionPlan> listEnabledPlans() {
        return listPlans(false);
    }

    public List<AdoptionPlan> listPlans(boolean includeDisabled) {
        return adoptionPlanRepository.findAll().stream()
                .filter(plan -> includeDisabled || plan.enabled())
                .toList();
    }

    @Transactional
    public AdoptionPlan create(AdoptionPlanCommand command, long actorId) {
        String name = requireName(command.planName());
        AdoptionPeriod period = parsePeriod(command.period());
        int amount = requireAmount(command.amount());
        String benefits = normalizeBenefits(command.benefits());
        boolean enabled = command.enabled() == null || command.enabled();

        LocalDateTime now = LocalDateTime.now(clock);
        AdoptionPlan created = adoptionPlanRepository.insert(new AdoptionPlanWrite(
                name, period, amount, benefits, enabled, now, actorId));
        audit.success("PLAN_CREATE", "PLAN", String.valueOf(created.planId()), null, created);
        return created;
    }

    @Transactional
    public AdoptionPlan update(long planId, AdoptionPlanCommand command, long actorId) {
        AdoptionPlan before = adoptionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        String name = requireName(command.planName());
        AdoptionPeriod period = parsePeriod(command.period());
        int amount = requireAmount(command.amount());
        String benefits = normalizeBenefits(command.benefits());
        boolean enabled = command.enabled() == null ? before.enabled() : command.enabled();

        LocalDateTime now = LocalDateTime.now(clock);
        int updated = adoptionPlanRepository.update(planId, new AdoptionPlanWrite(
                name, period, amount, benefits, enabled, now, actorId), now);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        AdoptionPlan after = adoptionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        audit.success("PLAN_UPDATE", "PLAN", String.valueOf(planId), before, after);
        return after;
    }

    private String requireName(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return trimmed;
    }

    private AdoptionPeriod parsePeriod(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        try {
            return AdoptionPeriod.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.ENUM_INVALID);
        }
    }

    private int requireAmount(Integer value) {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return value;
    }

    private String normalizeBenefits(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_BENEFITS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return trimmed;
    }
}
