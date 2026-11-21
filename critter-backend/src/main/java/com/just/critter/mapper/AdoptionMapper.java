package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.AdoptionDetailRow;
import com.just.critter.domain.model.AdoptionEntity;
import com.just.critter.domain.model.AdoptionPaymentRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdoptionMapper extends BaseMapper<AdoptionEntity> {

    @Select("""
            SELECT adoption_no
            FROM adoption
            WHERE animal_id = #{animalId} AND status = 'ACTIVE'
            ORDER BY id DESC
            LIMIT 1
            """)
    String findActiveAdoptionNo(@Param("animalId") long animalId);

    @Select("""
            SELECT COUNT(*)
            FROM adoption
            WHERE user_id = #{userId} AND status = 'ACTIVE'
            """)
    long countActiveByUser(@Param("userId") long userId);

    @Select("""
            SELECT *
            FROM adoption
            WHERE adoption_no = #{adoptionNo}
            FOR UPDATE
            """)
    AdoptionEntity findByNoForUpdate(@Param("adoptionNo") String adoptionNo);

    @Select("""
            SELECT adoption_no
            FROM adoption
            WHERE animal_id = #{animalId}
              AND adoption_no <> #{excludedAdoptionNo}
              AND status IN ('PENDING_PAYMENT', 'ACTIVE')
            ORDER BY id DESC
            LIMIT 1
            """)
    String findBlockingAdoptionNo(@Param("animalId") long animalId,
                                  @Param("excludedAdoptionNo") String excludedAdoptionNo);

    @Update("""
            UPDATE adoption
            SET status = #{status},
                auditor_id = #{auditorId},
                audit_reason = #{reason},
                amount = #{amount},
                pay_deadline = CASE WHEN #{status} = 'PENDING_PAYMENT'
                                    THEN DATE_ADD(#{auditedAt}, INTERVAL 7 DAY)
                                    ELSE NULL END,
                updated_at = #{auditedAt}
            WHERE adoption_no = #{adoptionNo}
              AND status = 'PENDING_AUDIT'
            """)
    int updateAudit(@Param("adoptionNo") String adoptionNo,
                    @Param("status") String status,
                    @Param("auditorId") long auditorId,
                    @Param("reason") String reason,
                    @Param("amount") int amount,
                    @Param("auditedAt") LocalDateTime auditedAt);

    @Select("""
            SELECT COUNT(*)
            FROM adoption
            WHERE (#{userId} IS NULL OR user_id = #{userId})
              AND (#{status} IS NULL OR status = #{status})
            """)
    long count(@Param("userId") Long userId, @Param("status") String status);

    @Select("""
            <script>
            SELECT a.id,
                   a.adoption_no AS adoptionNo,
                   a.user_id AS userId,
                   u.nickname AS userName,
                   a.animal_code AS animalCode,
                   an.name AS animalName,
                   a.plan_id AS planId,
                   p.plan_name AS planName,
                   a.amount,
                   a.period,
                   a.status,
                   a.contact,
                   a.audit_reason AS auditReason,
                   a.start_date AS startDate,
                   a.end_date AS endDate,
                   a.certificate_no AS certificateNo,
                   a.pay_deadline AS payDeadline,
                   a.created_at AS createdAt
            FROM adoption a
            LEFT JOIN sys_user u ON u.id = a.user_id
            LEFT JOIN animal an ON an.id = a.animal_id
            LEFT JOIN adoption_plan p ON p.id = a.plan_id
            <where>
              <if test="userId != null">AND a.user_id = #{userId}</if>
              <if test="status != null and status != ''">AND a.status = #{status}</if>
            </where>
            ORDER BY a.created_at DESC, a.id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<AdoptionDetailRow> findPage(@Param("userId") Long userId,
                                     @Param("status") String status,
                                     @Param("offset") long offset,
                                     @Param("size") int size);

    @Select("""
            SELECT a.id,
                   a.adoption_no AS adoptionNo,
                   a.user_id AS userId,
                   u.nickname AS userName,
                   a.animal_code AS animalCode,
                   an.name AS animalName,
                   a.plan_id AS planId,
                   p.plan_name AS planName,
                   a.amount,
                   a.period,
                   a.status,
                   a.contact,
                   a.audit_reason AS auditReason,
                   a.start_date AS startDate,
                   a.end_date AS endDate,
                   a.certificate_no AS certificateNo,
                   a.pay_deadline AS payDeadline,
                   a.created_at AS createdAt
            FROM adoption a
            LEFT JOIN sys_user u ON u.id = a.user_id
            LEFT JOIN animal an ON an.id = a.animal_id
            LEFT JOIN adoption_plan p ON p.id = a.plan_id
            WHERE a.adoption_no = #{adoptionNo}
            """)
    AdoptionDetailRow findDetail(@Param("adoptionNo") String adoptionNo);

    @Select("""
            SELECT payment_no AS paymentNo,
                   amount,
                   paid_at AS paidAt,
                   voucher_file_ids AS voucherFileIds,
                   note,
                   verified_at AS verifiedAt
            FROM adoption_payment
            WHERE adoption_id = #{adoptionId}
            ORDER BY id ASC
            """)
    List<AdoptionPaymentRow> findPayments(@Param("adoptionId") long adoptionId);

    @Select("""
            SELECT COUNT(*)
            FROM adoption_payment
            WHERE adoption_id = #{adoptionId} AND verified_at IS NOT NULL
            """)
    long countVerifiedPayments(@Param("adoptionId") long adoptionId);

    @Insert("""
            INSERT INTO adoption_payment
                (payment_no, adoption_id, amount, paid_at, verifier_id, verified_at,
                 voucher_file_ids, note, created_by)
            VALUES
                (#{paymentNo}, #{adoptionId}, #{amount}, #{paidAt}, #{verifierId}, #{verifiedAt},
                 #{voucherJson}, #{note}, #{verifierId})
            """)
    int insertPayment(@Param("paymentNo") String paymentNo,
                      @Param("adoptionId") long adoptionId,
                      @Param("amount") int amount,
                      @Param("paidAt") LocalDateTime paidAt,
                      @Param("verifierId") long verifierId,
                      @Param("verifiedAt") LocalDateTime verifiedAt,
                      @Param("voucherJson") String voucherJson,
                      @Param("note") String note);

    @Update("""
            UPDATE adoption
            SET status = 'ACTIVE',
                start_date = #{startDate},
                end_date = #{endDate},
                certificate_no = #{certificateNo},
                pay_deadline = NULL,
                updated_at = #{updatedAt}
            WHERE id = #{adoptionId}
              AND status = 'PENDING_PAYMENT'
            """)
    int activate(@Param("adoptionId") long adoptionId,
                 @Param("certificateNo") String certificateNo,
                 @Param("startDate") LocalDate startDate,
                 @Param("endDate") LocalDate endDate,
                 @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE adoption
            SET status = 'TERMINATED',
                end_date = #{endDate},
                termination_reason = #{reason},
                updated_at = #{updatedAt}
            WHERE id = #{adoptionId}
              AND status = 'ACTIVE'
            """)
    int terminate(@Param("adoptionId") long adoptionId,
                  @Param("reason") String reason,
                  @Param("endDate") LocalDate endDate,
                  @Param("updatedAt") LocalDateTime updatedAt);
}
