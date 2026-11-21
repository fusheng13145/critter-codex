package com.just.critter.care.service;

import com.just.critter.care.repository.MedicalRepository;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 合作医院字典维护。字段长度与表结构一致，避免截断或写库失败。
 */
@Service
public class ClinicService {

    private static final int MAX_NAME = 64;
    private static final int MAX_SHORT_TEXT = 32;
    private static final int MAX_LONG_TEXT = 255;

    private final MedicalRepository medical;
    private final AuditLogService auditLog;
    private final Clock clock;

    public ClinicService(MedicalRepository medical, AuditLogService auditLog, Clock clock) {
        this.medical = medical;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public ClinicView create(ClinicCommand command, long createdBy) {
        validate(command);

        LocalDateTime now = LocalDateTime.now(clock);
        ClinicWrite write = new ClinicWrite(
                command.clinicName().trim(),
                trimToNull(command.contact()),
                trimToNull(command.phone()),
                trimToNull(command.address()),
                trimToNull(command.priceNote()),
                now,
                createdBy);

        long clinicId = medical.insertClinic(write);
        if (clinicId <= 0) {
            throw new BusinessException(50002, "合作医院写入失败");
        }

        ClinicView view = new ClinicView(clinicId, write.clinicName(), write.contact(),
                write.phone(), write.address(), write.priceNote());
        auditLog.success("CLINIC_CREATE", "CLINIC", String.valueOf(clinicId), null, view);
        return view;
    }

    private void validate(ClinicCommand command) {
        if (command == null) {
            throw invalid("command");
        }
        if (isBlank(command.clinicName()) || command.clinicName().trim().length() > MAX_NAME) {
            throw invalid("clinicName");
        }
        checkLength("contact", command.contact(), MAX_SHORT_TEXT);
        checkLength("phone", command.phone(), MAX_SHORT_TEXT);
        checkLength("address", command.address(), MAX_LONG_TEXT);
        checkLength("priceNote", command.priceNote(), MAX_LONG_TEXT);
    }

    private void checkLength(String field, String value, int max) {
        if (value != null && value.trim().length() > max) {
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
