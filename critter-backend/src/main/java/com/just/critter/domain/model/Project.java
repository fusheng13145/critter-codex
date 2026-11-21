package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.ProjectTargetType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 募捐项目，对应 project 表。金额单位为分；
 * raised_amount / spent_amount 是入账、复核事务维护的冗余汇总，本类只承载。
 */
@TableName("project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectNo;
    private String name;
    private String purpose;
    private ProjectTargetType targetType;
    private Integer targetAmount;
    private Integer raisedAmount;
    private Integer spentAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private ProjectStatus status;
    private Long auditorId;
    private String auditReason;
    private String coverFileId;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProjectNo() {
        return projectNo;
    }

    public void setProjectNo(String projectNo) {
        this.projectNo = projectNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public ProjectTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(ProjectTargetType targetType) {
        this.targetType = targetType;
    }

    public Integer getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(Integer targetAmount) {
        this.targetAmount = targetAmount;
    }

    public Integer getRaisedAmount() {
        return raisedAmount;
    }

    public void setRaisedAmount(Integer raisedAmount) {
        this.raisedAmount = raisedAmount;
    }

    public Integer getSpentAmount() {
        return spentAmount;
    }

    public void setSpentAmount(Integer spentAmount) {
        this.spentAmount = spentAmount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public Long getAuditorId() {
        return auditorId;
    }

    public void setAuditorId(Long auditorId) {
        this.auditorId = auditorId;
    }

    public String getAuditReason() {
        return auditReason;
    }

    public void setAuditReason(String auditReason) {
        this.auditReason = auditReason;
    }

    public String getCoverFileId() {
        return coverFileId;
    }

    public void setCoverFileId(String coverFileId) {
        this.coverFileId = coverFileId;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
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
