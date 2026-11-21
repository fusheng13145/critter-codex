package com.just.critter.care.service;

import com.just.critter.care.repository.MedicalRepository;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MedicalQueryService {

    private final MedicalRepository medical;

    public MedicalQueryService(MedicalRepository medical) {
        this.medical = medical;
    }

    public List<ClinicView> clinics() {
        return medical.findClinics();
    }

    public VisitDetail visit(String visitNo) {
        if (visitNo == null || visitNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "visitNo"));
        }
        return medical.findVisit(visitNo.trim())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("visitNo", visitNo)));
    }
}
