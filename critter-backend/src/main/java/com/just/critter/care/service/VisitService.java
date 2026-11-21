package com.just.critter.care.service;

import com.just.critter.care.repository.MedicalRepository;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 登记与纠错就诊。visit_no 格式：VS + 到院日期 + 3 位年度序号。
 * 无工单时必须填写来源备注；有工单时工单的动物必须与就诊动物一致。
 */
@Service
public class VisitService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_TEXT = 255;
    private static final int MAX_REASON = 255;

    private final MedicalRepository medical;
    private final SequencePort sequences;
    private final AuditLogService auditLog;
    private final Clock clock;

    public VisitService(MedicalRepository medical, SequencePort sequences,
                        AuditLogService auditLog, Clock clock) {
        this.medical = medical;
        this.sequences = sequences;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public VisitRegistration register(VisitCommand command, long createdBy) {
        validate(command);

        long animalId = command.animalId();
        long clinicId = command.clinicId();
        LocalDateTime visitedAt = command.visitedAt()
                .atZoneSameInstant(clock.getZone())
                .toLocalDateTime();

        ReferenceAnimal animal = medical.findAnimal(animalId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("animalId", animalId)));
        ReferenceClinic clinic = medical.findClinic(clinicId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("clinicId", clinicId)));

        Long orderId = command.orderId();
        if (orderId != null) {
            ReferenceOrder order = medical.findOrder(orderId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RECORD_NOT_FOUND, Map.of("orderId", orderId)));
            if (!Objects.equals(order.animalId(), animalId)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of(
                        "field", "orderId", "reason", "工单与就诊动物不一致"));
            }
        }

        String visitNo = "VS" + visitedAt.toLocalDate().format(NO_DATE)
                + String.format("%03d", sequences.next("VISIT", visitedAt.getYear()));
        LocalDateTime now = LocalDateTime.now(clock);

        VisitWrite write = new VisitWrite(
                visitNo, animalId, orderId, clinicId, visitedAt,
                trimToNull(command.complaint()), trimToNull(command.diagnosis()),
                trimToNull(command.treatment()), command.cost(),
                command.fileIds() == null ? List.of() : List.copyOf(command.fileIds()),
                trimToNull(command.sourceNote()), now, createdBy);

        if (medical.insertVisit(write) != 1) {
            throw new BusinessException(50002, "就诊记录写入失败");
        }
        return new VisitRegistration(visitNo, animalId, animal.code(), animal.name(),
                clinicId, clinic.clinicName(), visitedAt, command.cost());
    }

    @Transactional
    public VisitDetail update(String visitNo, VisitUpdateCommand command, long operatorId) {
        validateUpdate(visitNo, command);
        String no = visitNo.trim();

        VisitDetail before = medical.findVisit(no)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("visitNo", no)));
        LocalDateTime now = LocalDateTime.now(clock);
        VisitUpdateWrite write = new VisitUpdateWrite(
                trimToNull(command.complaint()), trimToNull(command.diagnosis()),
                trimToNull(command.treatment()), command.cost(),
                command.fileIds() == null ? List.of() : List.copyOf(command.fileIds()), now);

        if (medical.updateVisit(no, write) != 1) {
            throw new BusinessException(50002, "就诊记录更新失败");
        }
        VisitDetail after = medical.findVisit(no)
                .orElseThrow(() -> new BusinessException(50002, "就诊记录更新后读取失败"));

        auditLog.success("VISIT_CORRECT", "VISIT", no, before,
                Map.of("reason", command.reason().trim(), "visit", after));
        return after;
    }

    private void validateUpdate(String visitNo, VisitUpdateCommand command) {
        if (visitNo == null || visitNo.isBlank()) {
            throw invalid("visitNo");
        }
        if (command == null) {
            throw invalid("command");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException(ErrorCode.CHANGE_REASON_REQUIRED);
        }
        if (command.reason().trim().length() > MAX_REASON) {
            throw invalid("reason");
        }
        if (command.cost() == null || command.cost() < 0) {
            throw invalid("cost");
        }
        checkText("complaint", command.complaint());
        checkText("diagnosis", command.diagnosis());
        checkText("treatment", command.treatment());
        if (command.fileIds() != null
                && command.fileIds().stream().anyMatch(VisitService::isBlank)) {
            throw invalid("fileIds");
        }
    }

    private void validate(VisitCommand command) {
        if (command == null) {
            throw invalid("command");
        }
        if (command.animalId() == null || command.animalId() <= 0) {
            throw invalid("animalId");
        }
        if (command.clinicId() == null || command.clinicId() <= 0) {
            throw invalid("clinicId");
        }
        if (command.visitedAt() == null) {
            throw invalid("visitedAt");
        }
        if (command.visitedAt().isAfter(OffsetDateTime.now(clock))) {
            throw invalid("visitedAt");
        }
        if (command.cost() == null || command.cost() < 0) {
            throw invalid("cost");
        }
        if (command.orderId() != null && command.orderId() <= 0) {
            throw invalid("orderId");
        }
        if (command.orderId() == null && isBlank(command.sourceNote())) {
            throw invalid("sourceNote");
        }
        checkText("complaint", command.complaint());
        checkText("diagnosis", command.diagnosis());
        checkText("treatment", command.treatment());
        checkText("sourceNote", command.sourceNote());
        if (command.fileIds() != null
                && command.fileIds().stream().anyMatch(VisitService::isBlank)) {
            throw invalid("fileIds");
        }
    }

    private void checkText(String field, String value) {
        if (value != null && value.length() > MAX_TEXT) {
            throw invalid(field);
        }
    }

    private BusinessException invalid(String field) {
        return new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", field));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
