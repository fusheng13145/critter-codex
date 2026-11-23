package com.just.critter.adopt.benefit;

import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdoptionBenefitService {

    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT");

    private final AdoptionBenefitRepository repository;
    private final Clock clock;

    public AdoptionBenefitService(AdoptionBenefitRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public AdoptionBenefitView view(String adoptionNo, long userId) {
        AdoptionBenefitRepository.Scope scope = requireAccess(adoptionNo, userId);
        return toView(scope);
    }

    @Transactional
    public AdoptionPostView addPost(String adoptionNo, long userId, String title, String content) {
        AdoptionBenefitRepository.Scope scope = requireAccess(adoptionNo, userId);
        String titleText = requireText(title, 128, "title");
        String contentText = requireText(content, 2000, "content");
        long id = repository.insertPost(scope.id(), userId, titleText, contentText,
                LocalDateTime.now(clock).withNano(0));
        if (id <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return repository.findPosts(scope.id()).getFirst();
    }

    @Transactional
    public AdoptionVisitView bookVisit(String adoptionNo, long userId, VisitBookingCommand command) {
        AdoptionBenefitRepository.Scope scope = requireAccess(adoptionNo, userId);
        if (command == null || command.visitDate() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "visitDate"));
        }
        if (command.visitDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "visitDate"));
        }
        String slot = requireText(command.timeSlot(), 16, "timeSlot");
        String note = command.note() == null ? null : command.note().trim();
        if (note != null && note.length() > 255) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "note"));
        }
        if (repository.countPendingOnDate(scope.id(), command.visitDate()) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "visitDate"));
        }
        long id = repository.insertVisit(scope.id(),
                new VisitBookingCommand(command.visitDate(), slot, note), userId);
        if (id <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return repository.findVisit(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    @Transactional
    public AdoptionVisitView cancelVisit(String adoptionNo, long userId, long visitId) {
        AdoptionBenefitRepository.Scope scope = requireAccess(adoptionNo, userId);
        repository.findVisitStatus(adoptionNo, visitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        int rows = repository.cancelVisit(visitId, scope.id(), userId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        return repository.findVisit(visitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    public List<AdoptionBenefitRepository.VisitAdminView> pendingVisits(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return repository.findPendingVisits(safeLimit);
    }

    @Transactional
    public AdoptionVisitView review(String adoptionNo, long visitId, String decision,
                                    String reason, long reviewerId) {
        AdoptionBenefitRepository.Scope scope = repository.findScope(adoptionNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        repository.findVisitStatus(adoptionNo, visitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        String normalized = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!DECISIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "decision"));
        }
        String status = "APPROVE".equals(normalized) ? "APPROVED" : "REJECTED";
        String reviewReason = reason == null ? null : reason.trim();
        if ("REJECTED".equals(status) && (reviewReason == null || reviewReason.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "reason"));
        }
        if (reviewReason != null && reviewReason.length() > 255) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "reason"));
        }
        int rows = repository.reviewVisit(visitId, status, reviewReason, reviewerId,
                LocalDateTime.now(clock).withNano(0));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        return repository.findVisit(visitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    private AdoptionBenefitView toView(AdoptionBenefitRepository.Scope scope) {
        return new AdoptionBenefitView(scope.adoptionNo(), scope.animalCode(), scope.animalName(),
                scope.startDate(), scope.endDate(), scope.status(),
                repository.findPosts(scope.id()), repository.findVisits(scope.id()));
    }

    /**
     * 专属页的唯一入口：关系必须是 ACTIVE，且访问者必须是认养人本人。
     * 终止、到期、审核中一律按 403 处理，不泄露页面内容。
     */
    private AdoptionBenefitRepository.Scope requireAccess(String adoptionNo, long userId) {
        AdoptionBenefitRepository.Scope scope = repository.findScope(adoptionNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (!"ACTIVE".equals(scope.status()) || scope.userId() != userId) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "认养关系已终止，专属权益已失效");
        }
        return scope;
    }

    private String requireText(String value, int max, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", field));
        }
        return text;
    }
}
