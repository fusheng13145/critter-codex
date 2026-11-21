package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.just.critter.domain.enums.HealthType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康与防疫记录，对应 health_record 表。一条记录只属于一只个体，
 * 疫苗与驱虫可带下次计划日期，用于到期提醒。
 */
@TableName("health_record")
public class HealthRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long animalId;
    private HealthType type;
    private LocalDateTime recordedAt;
    private String result;
    private LocalDate nextDate;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAnimalId() {
        return animalId;
    }

    public void setAnimalId(Long animalId) {
        this.animalId = animalId;
    }

    public HealthType getType() {
        return type;
    }

    public void setType(HealthType type) {
        this.type = type;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDate getNextDate() {
        return nextDate;
    }

    public void setNextDate(LocalDate nextDate) {
        this.nextDate = nextDate;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
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
