package com.just.critter.adopt.service;

import com.just.critter.adopt.repository.AdoptionExpiryRepository;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BR-05：每日扫描到期前 7 日与已过期认养。
 *
 * <p>提醒按 (type, biz_id, receiver_id) 去重，同一天重跑不会重复写；
 * 过期先改 adoption 再释放动物，均只命中 ACTIVE/ADOPTED，重跑无副作用。</p>
 */
@Service
public class AdoptionExpiryService {

    private static final String TYPE = "ADOPTION_EXPIRY";
    private static final String BIZ_TYPE = "ADOPTION";
    private static final String JOB_ACTION = "JOB_ADOPTION_EXPIRY";

    private final AdoptionExpiryRepository expiry;
    private final MessageRepository messages;
    private final AuditLogService auditLog;
    private final Clock clock;

    public AdoptionExpiryService(AdoptionExpiryRepository expiry,
                                 MessageRepository messages,
                                 AuditLogService auditLog,
                                 Clock clock) {
        this.expiry = expiry;
        this.messages = messages;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public AdoptionExpiryResult process(LocalDate today) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        int reminded = remind(today, now);
        int expired = expire(today, now);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("date", today.toString());
        summary.put("reminded", reminded);
        summary.put("expired", expired);
        auditLog.successAs(0L, "SYSTEM", JOB_ACTION, "JOB", today.toString(), null, summary);

        return new AdoptionExpiryResult(reminded, expired);
    }

    private int remind(LocalDate today, LocalDateTime now) {
        int count = 0;
        for (ExpiringAdoption adoption : expiry.findExpiring(today.plusDays(7))) {
            if (messages.exists(TYPE, adoption.adoptionNo(), adoption.userId())) {
                continue;
            }
            String title = "认养即将到期：" + adoption.animalCode()
                    + " 将于 " + adoption.endDate() + " 到期，请提前联系救助站";
            int rows = messages.insert(new MessageWrite(
                    adoption.userId(), TYPE, title, BIZ_TYPE, adoption.adoptionNo(), null, now));
            if (rows <= 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
            }
            count++;
        }
        return count;
    }

    private int expire(LocalDate today, LocalDateTime now) {
        int count = 0;
        for (ExpiringAdoption adoption : expiry.findExpired(today)) {
            if (expiry.expire(adoption.id(), now) <= 0) {
                continue;
            }
            if (expiry.releaseAnimal(adoption.animalId(), now) <= 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT,
                        Map.of("animalId", adoption.animalId()));
            }
            count++;
        }
        return count;
    }
}
