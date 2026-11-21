package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.just.critter.domain.enums.DonationStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 捐赠记录，对应 donation 表。登记与入账严格两态：
 * PENDING_VERIFY / ABNORMAL 不计入项目进度，只有 VERIFIED 才计入。
 */
@TableName(value = "donation", autoResultMap = true)
public class Donation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String donationNo;
    private Long projectId;
    private Long userId;
    private String donorDisplay;
    private Integer amount;
    private Boolean anonymous;
    private String message;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> voucherFileIds;
    private DonationStatus status;
    private Long verifierId;
    private LocalDateTime verifiedAt;
    private String abnormalReason;
    private Long reversalOf;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDonationNo() {
        return donationNo;
    }

    public void setDonationNo(String donationNo) {
        this.donationNo = donationNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDonorDisplay() {
        return donorDisplay;
    }

    public void setDonorDisplay(String donorDisplay) {
        this.donorDisplay = donorDisplay;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public Boolean getAnonymous() {
        return anonymous;
    }

    public void setAnonymous(Boolean anonymous) {
        this.anonymous = anonymous;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getVoucherFileIds() {
        return voucherFileIds;
    }

    public void setVoucherFileIds(List<String> voucherFileIds) {
        this.voucherFileIds = voucherFileIds;
    }

    public DonationStatus getStatus() {
        return status;
    }

    public void setStatus(DonationStatus status) {
        this.status = status;
    }

    public Long getVerifierId() {
        return verifierId;
    }

    public void setVerifierId(Long verifierId) {
        this.verifierId = verifierId;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getAbnormalReason() {
        return abnormalReason;
    }

    public void setAbnormalReason(String abnormalReason) {
        this.abnormalReason = abnormalReason;
    }

    public Long getReversalOf() {
        return reversalOf;
    }

    public void setReversalOf(Long reversalOf) {
        this.reversalOf = reversalOf;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
