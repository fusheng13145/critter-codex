package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 校园生物巡查记录。巡查本身不改档案；需要留档时再转成一条目击记录。
 * converted_sighting_id 非空表示已转目击，一条巡查最多转一次。
 */
@TableName(value = "patrol_record", autoResultMap = true)
public class PatrolRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pointId;
    private LocalDateTime patrolAt;
    private Integer countEstimate;
    private Boolean hasAnomaly;
    private String anomalyNote;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fileIds;
    private Long recorderId;
    private Long convertedSightingId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public LocalDateTime getPatrolAt() {
        return patrolAt;
    }

    public void setPatrolAt(LocalDateTime patrolAt) {
        this.patrolAt = patrolAt;
    }

    public Integer getCountEstimate() {
        return countEstimate;
    }

    public void setCountEstimate(Integer countEstimate) {
        this.countEstimate = countEstimate;
    }

    public Boolean getHasAnomaly() {
        return hasAnomaly;
    }

    public void setHasAnomaly(Boolean hasAnomaly) {
        this.hasAnomaly = hasAnomaly;
    }

    public String getAnomalyNote() {
        return anomalyNote;
    }

    public void setAnomalyNote(String anomalyNote) {
        this.anomalyNote = anomalyNote;
    }

    public List<String> getFileIds() {
        return fileIds;
    }

    public void setFileIds(List<String> fileIds) {
        this.fileIds = fileIds;
    }

    public Long getRecorderId() {
        return recorderId;
    }

    public void setRecorderId(Long recorderId) {
        this.recorderId = recorderId;
    }

    public Long getConvertedSightingId() {
        return convertedSightingId;
    }

    public void setConvertedSightingId(Long convertedSightingId) {
        this.convertedSightingId = convertedSightingId;
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
