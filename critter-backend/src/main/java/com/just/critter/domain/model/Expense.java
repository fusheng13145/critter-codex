package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.just.critter.domain.enums.ExpenseStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支出记录，对应 expense 表。登记只落 PENDING_REVIEW，复核通过才计入项目 spent_amount。
 */
@TableName(value = "expense", autoResultMap = true)
public class Expense {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String expenseNo;
    private Long projectId;
    private Long animalId;
    private String purpose;
    private Integer amount;
    private String payee;
    private LocalDateTime occurredAt;
    private Long handlerId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> voucherFileIds;
    private ExpenseStatus status;
    private Long firstReviewerId;
    private Long secondReviewerId;
    private String reviewReason;
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

    public String getExpenseNo() {
        return expenseNo;
    }

    public void setExpenseNo(String expenseNo) {
        this.expenseNo = expenseNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getAnimalId() {
        return animalId;
    }

    public void setAnimalId(Long animalId) {
        this.animalId = animalId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getPayee() {
        return payee;
    }

    public void setPayee(String payee) {
        this.payee = payee;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

    public List<String> getVoucherFileIds() {
        return voucherFileIds;
    }

    public void setVoucherFileIds(List<String> voucherFileIds) {
        this.voucherFileIds = voucherFileIds;
    }

    public ExpenseStatus getStatus() {
        return status;
    }

    public void setStatus(ExpenseStatus status) {
        this.status = status;
    }

    public Long getFirstReviewerId() {
        return firstReviewerId;
    }

    public void setFirstReviewerId(Long firstReviewerId) {
        this.firstReviewerId = firstReviewerId;
    }

    public Long getSecondReviewerId() {
        return secondReviewerId;
    }

    public void setSecondReviewerId(Long secondReviewerId) {
        this.secondReviewerId = secondReviewerId;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
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
